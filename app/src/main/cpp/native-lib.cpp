#include <jni.h>
#include <string>
#include <atomic>
#include <mutex>
#include <cstring>
#include <sstream>
#include <algorithm>
#include <cctype>
#include <fcntl.h>
#include <cstdio>
#include <vector>
#include <deque>
#include <unordered_map>
#include <chrono>
#include <thread>
#include <array>
#include <cmath>
#include <iterator>

extern "C" {
#include <mgba/core/core.h>
#include <mgba/core/config.h>
#include <mgba/core/serialize.h>
#include <mgba/core/cheats.h>
#include <mgba/core/version.h>
#include <mgba/core/thread.h>
#include <mgba/core/lockstep.h>
#include <mgba/internal/gba/cheats.h>
#include <mgba/internal/gba/gba.h>
#include <mgba/internal/gba/io.h>
#include <mgba/internal/gba/savedata.h>
#include <mgba/internal/gba/video.h>
#include <mgba/internal/gba/sio/lockstep.h>
#include <mgba/internal/gb/cheats.h>
#include <mgba/internal/gb/gb.h>
#include <mgba-util/audio-buffer.h>
#include <mgba-util/vfs.h>
}

static constexpr unsigned MAX_VIDEO_WIDTH = 256;
static constexpr unsigned MAX_VIDEO_HEIGHT = 224;
static constexpr size_t VIDEO_STRIDE = MAX_VIDEO_WIDTH;
static constexpr size_t MAX_PIXEL_COUNT = MAX_VIDEO_WIDTH * MAX_VIDEO_HEIGHT;

static mCore* core = nullptr;
static mColor videoBuffer[MAX_PIXEL_COUNT];
static unsigned videoWidth = 240;
static unsigned videoHeight = 160;
static std::atomic<uint32_t> keyMask{0};
// Rising-edge latch: guarantees even a press+release shorter than one GBA frame
// is sampled by mGBA for one frame instead of disappearing between polls.
static std::atomic<uint32_t> keyPressLatch{0};
static std::mutex coreMutex;
static bool configInitialized = false;
static std::unordered_map<std::string, std::string> runtimeConfigOptions;

// mGBA's GBA core exposes PCM at the emulated hardware audio clock (commonly
// 32768 Hz, but games can switch it). Android output is normally 44.1/48 kHz,
// so Retra must perform one explicit sample-rate conversion before AudioTrack.
//
// Do not use mAudioResampler's int16 sinc output here. A band-limited sinc can
// overshoot between samples and an unchecked int16 conversion can wrap a loud
// transient instead of saturating it, which is perceived as harsh/distorted
// music. Retra keeps its own small polyphase FIR table, normalizes every phase
// for unity DC gain, applies an anti-alias cutoff while downsampling, and clamps
// the final accumulator to int16. The mGBA mixer itself remains untouched.
static constexpr int RETRA_RESAMPLER_TAPS = 32;
static constexpr int RETRA_RESAMPLER_LEFT_TAPS = 15;
static constexpr int RETRA_RESAMPLER_RIGHT_TAPS = 16;
static constexpr int RETRA_RESAMPLER_HISTORY = 16;
static constexpr int RETRA_RESAMPLER_PHASES = 1024;
static constexpr double RETRA_PI = 3.14159265358979323846264338327950288;

struct RetraAudioResamplerState {
    mAudioBuffer* source = nullptr;
    unsigned sourceRate = 0;
    unsigned destinationRate = 0;
    double speedFactor = 1.0;
    double position = 0.0;
    std::array<float, RETRA_RESAMPLER_PHASES * RETRA_RESAMPLER_TAPS> coefficients{};
};

// Final output conditioning is intentionally conservative. mGBA's libretro
// frontend offers a single-pole low-pass specifically to reduce generated
// audio harshness. Retra uses the same topology at a much gentler 22% strength
// so background music is smoother without making percussion or sampled audio
// sound muffled. A transparent ~18 Hz DC blocker removes sub-audible offset,
// and a tiny amount of headroom protects Android's PCM16 path from inter-stage
// peaks. Pitch, tempo, stereo placement, and the mGBA mixer are left untouched.
static constexpr double RETRA_DC_BLOCK_HZ = 18.0;
static constexpr double RETRA_SMOOTHING_STRENGTH = 0.22;
static constexpr double RETRA_OUTPUT_HEADROOM = 0.99;

struct RetraAudioConditionerState {
    mAudioBuffer* source = nullptr;
    unsigned sampleRate = 0;
    double dcCoefficient = 0.0;
    double previousInputLeft = 0.0;
    double previousInputRight = 0.0;
    double previousDcLeft = 0.0;
    double previousDcRight = 0.0;
    double smoothedLeft = 0.0;
    double smoothedRight = 0.0;
    bool initialized = false;
};

static RetraAudioResamplerState retraAudioResampler{};
static RetraAudioConditionerState retraAudioConditioner{};

static unsigned requestedAudioOutputRateLocked() {
    constexpr unsigned DEFAULT_RATE = 44100;

    // AudioController reports the actual physical AudioTrack clock separately
    // from Retra's user-facing quality preference. On common Android hardware
    // this is 48 kHz, avoiding an additional AudioFlinger conversion later.
    const auto outputIt = runtimeConfigOptions.find("retra.outputSampleRate");
    const auto sampleIt = runtimeConfigOptions.find("sampleRate");
    const auto* value = outputIt != runtimeConfigOptions.end()
            ? &outputIt->second
            : (sampleIt != runtimeConfigOptions.end() ? &sampleIt->second : nullptr);
    if (!value) return DEFAULT_RATE;
    try {
        const unsigned parsed = static_cast<unsigned>(std::stoul(*value));
        return std::max(8000U, std::min(96000U, parsed));
    } catch (...) {
        return DEFAULT_RATE;
    }
}

static void resetRetraAudioConditionerLocked() {
    retraAudioConditioner = {};
}

static void resetRetraAudioResamplerLocked() {
    retraAudioResampler = {};
    resetRetraAudioConditionerLocked();
}

static double retraNormalizedSinc(double x) {
    if (std::abs(x) < 1e-12) return 1.0;
    const double pix = RETRA_PI * x;
    return std::sin(pix) / pix;
}

static void rebuildRetraAudioResamplerCoefficientsLocked(
        unsigned sourceRate,
        unsigned destinationRate,
        double speedFactor) {
    // When reducing the sample rate, lower the FIR cutoff slightly below the
    // new Nyquist edge to keep GBA high-frequency energy from folding back as
    // gritty aliasing. Upsampling needs no spectral cut, only interpolation.
    const double effectiveSourceRate = static_cast<double>(sourceRate) *
            std::max(1.0, speedFactor);
    const double ratio = static_cast<double>(destinationRate) / effectiveSourceRate;
    const double cutoff = ratio < 1.0 ? std::max(0.05, ratio * 0.94) : 1.0;
    constexpr double TWO_PI = 2.0 * RETRA_PI;

    for (int phase = 0; phase < RETRA_RESAMPLER_PHASES; ++phase) {
        const double fraction = static_cast<double>(phase) / RETRA_RESAMPLER_PHASES;
        double weightSum = 0.0;

        for (int tap = 0; tap < RETRA_RESAMPLER_TAPS; ++tap) {
            const int relative = tap - RETRA_RESAMPLER_LEFT_TAPS;
            const double distance = static_cast<double>(relative) - fraction;

            // 32-tap Blackman-windowed low-pass sinc. The fixed window and a
            // phase-normalization pass below keep the output stable and free of
            // gain pumping as the fractional source position advances.
            const double n = static_cast<double>(tap);
            const double window = 0.42
                    - 0.5 * std::cos(TWO_PI * n / (RETRA_RESAMPLER_TAPS - 1))
                    + 0.08 * std::cos(2.0 * TWO_PI * n / (RETRA_RESAMPLER_TAPS - 1));
            const double weight = cutoff * retraNormalizedSinc(distance * cutoff) * window;
            retraAudioResampler.coefficients[
                    phase * RETRA_RESAMPLER_TAPS + tap] = static_cast<float>(weight);
            weightSum += weight;
        }

        // Unity gain for DC/steady music tones; also prevents phase-dependent
        // amplitude modulation from becoming audible on sustained notes.
        if (std::abs(weightSum) > 1e-12) {
            for (int tap = 0; tap < RETRA_RESAMPLER_TAPS; ++tap) {
                auto& coefficient = retraAudioResampler.coefficients[
                        phase * RETRA_RESAMPLER_TAPS + tap];
                coefficient = static_cast<float>(coefficient / weightSum);
            }
        }
    }
}

static bool ensureRetraAudioResamplerLocked(
        mAudioBuffer* source,
        unsigned sourceRate,
        unsigned destinationRate,
        double speedFactor) {
    if (!source || !sourceRate || !destinationRate) return false;

    const bool sourceChanged = retraAudioResampler.source != source;
    const double normalizedSpeed = std::max(1.0, std::min(16.0, speedFactor));
    const bool ratesChanged = retraAudioResampler.sourceRate != sourceRate ||
            retraAudioResampler.destinationRate != destinationRate ||
            std::abs(retraAudioResampler.speedFactor - normalizedSpeed) > 1e-9;

    if (sourceChanged) {
        retraAudioResampler.position = 0.0;
        retraAudioResampler.source = source;
    }
    if (sourceChanged || ratesChanged) {
        rebuildRetraAudioResamplerCoefficientsLocked(
                sourceRate, destinationRate, normalizedSpeed);
        retraAudioResampler.sourceRate = sourceRate;
        retraAudioResampler.destinationRate = destinationRate;
        retraAudioResampler.speedFactor = normalizedSpeed;
        // A time-scale/rate transition changes the spectrum presented to the
        // output conditioner. Reset its one-pole history and let AudioController's
        // short fade-in bridge the transition instead of carrying stale filter state.
        resetRetraAudioConditionerLocked();
    }
    return true;
}

static int16_t retraClampPcm16(double sample) {
    if (sample >= 32767.0) return 32767;
    if (sample <= -32768.0) return -32768;
    return static_cast<int16_t>(std::lrint(sample));
}

static void prepareRetraAudioConditionerLocked(mAudioBuffer* source, unsigned sampleRate) {
    if (!source || !sampleRate) return;
    if (retraAudioConditioner.source == source &&
            retraAudioConditioner.sampleRate == sampleRate) {
        return;
    }

    retraAudioConditioner = {};
    retraAudioConditioner.source = source;
    retraAudioConditioner.sampleRate = sampleRate;
    retraAudioConditioner.dcCoefficient = std::exp(
            -2.0 * RETRA_PI * RETRA_DC_BLOCK_HZ / static_cast<double>(sampleRate));
}

static void conditionRetraAudioLocked(
        mAudioBuffer* source,
        unsigned sampleRate,
        int16_t* samples,
        size_t frames) {
    if (!source || !samples || !frames || !sampleRate) return;
    prepareRetraAudioConditionerLocked(source, sampleRate);

    auto& state = retraAudioConditioner;
    const double smoothingA = RETRA_SMOOTHING_STRENGTH;
    const double smoothingB = 1.0 - smoothingA;

    for (size_t frame = 0; frame < frames; ++frame) {
        const double inputLeft = static_cast<double>(samples[frame * 2]);
        const double inputRight = static_cast<double>(samples[frame * 2 + 1]);

        if (!state.initialized) {
            state.previousInputLeft = inputLeft;
            state.previousInputRight = inputRight;
            state.smoothedLeft = 0.0;
            state.smoothedRight = 0.0;
            state.initialized = true;
        }

        // One-pole DC blocker: removes sub-audible bias without changing bass.
        const double dcLeft = inputLeft - state.previousInputLeft +
                state.dcCoefficient * state.previousDcLeft;
        const double dcRight = inputRight - state.previousInputRight +
                state.dcCoefficient * state.previousDcRight;
        state.previousInputLeft = inputLeft;
        state.previousInputRight = inputRight;
        state.previousDcLeft = dcLeft;
        state.previousDcRight = dcRight;

        // mGBA-style single-pole smoothing, intentionally lighter than the
        // libretro 60% default so Retra keeps detail while taming gritty highs.
        state.smoothedLeft = state.smoothedLeft * smoothingA + dcLeft * smoothingB;
        state.smoothedRight = state.smoothedRight * smoothingA + dcRight * smoothingB;

        samples[frame * 2] = retraClampPcm16(state.smoothedLeft * RETRA_OUTPUT_HEADROOM);
        samples[frame * 2 + 1] = retraClampPcm16(state.smoothedRight * RETRA_OUTPUT_HEADROOM);
    }
}

static size_t resampleRetraAudioLocked(
        mAudioBuffer* source,
        unsigned sourceRate,
        unsigned destinationRate,
        int16_t* output,
        size_t maxFrames,
        double speedFactor = 1.0) {
    if (!source || !output || !maxFrames || !sourceRate || !destinationRate) return 0;
    const double normalizedSpeed = std::max(1.0, std::min(16.0, speedFactor));
    if (!ensureRetraAudioResamplerLocked(
            source, sourceRate, destinationRate, normalizedSpeed)) return 0;

    // Exact-rate path is bit-transparent: no filter, no gain change, no extra
    // interpolation. This matters on devices/routes whose AudioTrack clock
    // already matches the emulated stream.
    if (sourceRate == destinationRate && std::abs(normalizedSpeed - 1.0) < 1e-9) {
        retraAudioResampler.position = 0.0;
        return mAudioBufferRead(source, output, maxFrames);
    }

    size_t produced = 0;
    size_t available = mAudioBufferAvailable(source);
    const double step = (static_cast<double>(sourceRate) * normalizedSpeed) / destinationRate;

    while (produced < maxFrames) {
        const double position = retraAudioResampler.position;
        const int center = static_cast<int>(std::floor(position));
        if (center + RETRA_RESAMPLER_RIGHT_TAPS >= static_cast<int>(available)) break;

        double fraction = position - center;
        int phase = static_cast<int>(fraction * RETRA_RESAMPLER_PHASES);
        if (phase < 0) phase = 0;
        if (phase >= RETRA_RESAMPLER_PHASES) phase = RETRA_RESAMPLER_PHASES - 1;
        const float* weights = &retraAudioResampler.coefficients[
                phase * RETRA_RESAMPLER_TAPS];

        double left = 0.0;
        double right = 0.0;
        for (int tap = 0; tap < RETRA_RESAMPLER_TAPS; ++tap) {
            const int index = center + tap - RETRA_RESAMPLER_LEFT_TAPS;
            if (index < 0) continue;
            const double weight = weights[tap];
            left += static_cast<double>(mAudioBufferPeek(source, 0, static_cast<size_t>(index))) * weight;
            if (source->channels > 1) {
                right += static_cast<double>(mAudioBufferPeek(source, 1, static_cast<size_t>(index))) * weight;
            } else {
                right = left;
            }
        }

        output[produced * 2] = retraClampPcm16(left);
        output[produced * 2 + 1] = retraClampPcm16(right);
        ++produced;
        retraAudioResampler.position += step;
    }

    // Keep enough history in mGBA's ring for the left half of the FIR while
    // consuming old source frames promptly so the native ring cannot overflow.
    if (retraAudioResampler.position > RETRA_RESAMPLER_HISTORY) {
        size_t drop = static_cast<size_t>(std::floor(retraAudioResampler.position))
                - RETRA_RESAMPLER_HISTORY;
        drop = std::min(drop, available);
        if (drop > 0) {
            const size_t consumed = mAudioBufferRead(source, nullptr, drop);
            retraAudioResampler.position -= consumed;
        }
    }

    return produced;
}

// Retra-only runtime options are kept outside mGBA's public config namespace.
// They drive behavior that must be applied directly to the native core.
static std::atomic<bool> retraMosaicEffectEnabled{true};
static std::atomic<int> retraSyncCheckLevel{4};

using RetraVideoRegisterWriter = uint16_t (*)(struct GBAVideoRenderer*, uint32_t, uint16_t);
struct RetraVideoHookEntry {
    struct GBAVideoRenderer* renderer = nullptr;
    RetraVideoRegisterWriter original = nullptr;
};
static RetraVideoHookEntry retraVideoHooks[3];

static RetraVideoRegisterWriter findOriginalVideoWriter(struct GBAVideoRenderer* renderer) {
    for (const auto& hook : retraVideoHooks) {
        if (hook.renderer == renderer) return hook.original;
    }
    return nullptr;
}

static uint16_t retraWriteVideoRegister(
        struct GBAVideoRenderer* renderer,
        uint32_t address,
        uint16_t value) {
    auto original = findOriginalVideoWriter(renderer);
    if (!original) return value;

    // Keep the emulated MOSAIC register readable by the game while allowing
    // Retra to suppress only the visual renderer effect. This is less invasive
    // than forcing the actual GBA I/O register to zero.
    if (address == GBA_REG_MOSAIC && !retraMosaicEffectEnabled.load(std::memory_order_relaxed)) {
        (void) original(renderer, address, 0);
        return value;
    }
    return original(renderer, address, value);
}

static void installGbaVideoHook(struct mCore* target) {
    if (!target || target->platform(target) != mPLATFORM_GBA || !target->board) return;
    auto* gba = static_cast<struct GBA*>(target->board);
    auto* renderer = gba->video.renderer;
    if (!renderer || !renderer->writeVideoRegister) return;
    if (findOriginalVideoWriter(renderer)) return;

    for (auto& hook : retraVideoHooks) {
        if (!hook.renderer) {
            hook.renderer = renderer;
            hook.original = renderer->writeVideoRegister;
            renderer->writeVideoRegister = retraWriteVideoRegister;
            return;
        }
    }
}

static void removeGbaVideoHook(struct mCore* target) {
    if (!target || target->platform(target) != mPLATFORM_GBA || !target->board) return;
    auto* gba = static_cast<struct GBA*>(target->board);
    auto* renderer = gba->video.renderer;
    if (!renderer) return;
    for (auto& hook : retraVideoHooks) {
        if (hook.renderer == renderer) {
            if (hook.original && renderer->writeVideoRegister == retraWriteVideoRegister) {
                renderer->writeVideoRegister = hook.original;
            }
            hook = {};
            return;
        }
    }
}

static void syncGbaMosaicRenderer(struct mCore* target) {
    if (!target || target->platform(target) != mPLATFORM_GBA || !target->board) return;
    auto* gba = static_cast<struct GBA*>(target->board);
    auto* renderer = gba->video.renderer;
    if (!renderer) return;
    auto original = findOriginalVideoWriter(renderer);
    if (!original) return;
    const uint16_t requested = gba->memory.io[GBA_REG_MOSAIC >> 1];
    (void) original(
            renderer,
            GBA_REG_MOSAIC,
            retraMosaicEffectEnabled.load(std::memory_order_relaxed) ? requested : 0);
}

static bool isRetraPrivateConfigKey(const std::string& key) {
    return key.rfind("retra.", 0) == 0;
}

static std::string normalizedCartridgeSaveType() {
    const auto it = runtimeConfigOptions.find("retra.cartridgeSaveType");
    if (it == runtimeConfigOptions.end()) return "automatic";
    std::string value = it->second;
    std::transform(value.begin(), value.end(), value.begin(), [](unsigned char c) {
        return static_cast<char>(std::tolower(c));
    });
    return value;
}

static bool selectedForcedGbaSaveType(enum GBASavedataType* outType) {
    if (!outType) return false;
    const std::string value = normalizedCartridgeSaveType();

    // Automatic means "leave mGBA's cartridge/ROM-hack decision alone".
    // mGBA can identify Pokémon ROM hacks and select FLASH1M + RTC itself.
    // Forcing AUTODETECT here destroys that already-correct selection and can
    // trigger the in-game "Flash memory not detected" message.
    if (value.empty() || value == "automatic") return false;

    if (value == "eeprom") *outType = GBA_SAVEDATA_EEPROM;
    else if (value == "sram") *outType = GBA_SAVEDATA_SRAM;
    else if (value == "flash 64k" || value == "flash64k" || value == "flash512") *outType = GBA_SAVEDATA_FLASH512;
    else if (value == "flash 128k" || value == "flash128k" || value == "flash1m") *outType = GBA_SAVEDATA_FLASH1M;
    else if (value == "none") *outType = GBA_SAVEDATA_FORCE_NONE;
    else return false;
    return true;
}

static bool romContainsAscii(const struct GBA* gba, const char* needle) {
    if (!gba || !gba->memory.rom || !needle || !needle[0]) return false;
    const size_t romSize = gba->memory.romSize;
    const size_t needleSize = std::strlen(needle);
    if (needleSize == 0 || romSize < needleSize) return false;
    const auto* begin = reinterpret_cast<const uint8_t*>(gba->memory.rom);
    const auto* end = begin + romSize;
    const auto* needleBegin = reinterpret_cast<const uint8_t*>(needle);
    return std::search(begin, end, needleBegin, needleBegin + needleSize) != end;
}

static bool looksLikePokemonFlash1MRom(const struct GBA* gba) {
    if (!gba || !gba->memory.rom || gba->memory.romSize < 0xB0) return false;
    const auto* rom = reinterpret_cast<const uint8_t*>(gba->memory.rom);

    // Main Pokémon GBA game-code families. Hacks commonly retain these even
    // after changing the visible ROM name/title.
    const char c0 = static_cast<char>(rom[0xAC]);
    const char c1 = static_cast<char>(rom[0xAD]);
    const char c2 = static_cast<char>(rom[0xAE]);
    const bool pokemonGameCode =
            (c0 == 'B' && c1 == 'P' && (c2 == 'R' || c2 == 'G' || c2 == 'E')) ||
            (c0 == 'A' && c1 == 'X' && (c2 == 'V' || c2 == 'P')) ||
            (c0 == 'B' && c1 == '2' && c2 == '4');
    if (pokemonGameCode) return true;

    // Direct SDK marker used by 1 Mbit flash save libraries.
    if (romContainsAscii(gba, "FLASH1M_V")) return true;

    // Conservative fallback for hacks that altered the code but retained a
    // Pokémon title in the standard 12-byte GBA header field.
    char title[13] = {};
    for (size_t i = 0; i < 12; ++i) {
        title[i] = static_cast<char>(std::toupper(static_cast<unsigned char>(rom[0xA0 + i])));
    }
    return std::string(title).find("POKEMON") != std::string::npos;
}

static void applyGbaSaveTypeOverride(struct mCore* target) {
    if (!target || target->platform(target) != mPLATFORM_GBA || !target->board) return;
    auto* gba = static_cast<struct GBA*>(target->board);

    enum GBASavedataType forcedType = GBA_SAVEDATA_AUTODETECT;
    if (selectedForcedGbaSaveType(&forcedType)) {
        GBASavedataForceType(&gba->memory.savedata, forcedType);
        return;
    }

    // Automatic: preserve mGBA's existing override/detection result. If mGBA
    // has already chosen FLASH1M, FLASH512, SRAM, EEPROM, etc., do not reset it.
    if (gba->memory.savedata.type != GBA_SAVEDATA_AUTODETECT) return;

    // Additional safety net for Pokémon-derived ROM hacks that do not match
    // mGBA's built-in heuristic. Force 128 KiB / 1 Mbit flash before the game
    // performs its flash-chip ID check.
    if (looksLikePokemonFlash1MRom(gba)) {
        GBASavedataForceType(&gba->memory.savedata, GBA_SAVEDATA_FLASH1M);
    }
}

static uint64_t retraFnv1a64(const void* data, size_t size) {
    const auto* bytes = static_cast<const uint8_t*>(data);
    uint64_t hash = 1469598103934665603ULL;
    for (size_t i = 0; i < size; ++i) {
        hash ^= bytes[i];
        hash *= 1099511628211ULL;
    }
    return hash ? hash : 1ULL;
}

static uint64_t combineLinkHashes(uint64_t a, uint64_t b) {
    uint64_t hash = 1469598103934665603ULL;
    hash ^= a; hash *= 1099511628211ULL;
    hash ^= b; hash *= 1099511628211ULL;
    return hash ? hash : 1ULL;
}

// My Boy!-style same-device GBA link support. mGBA already contains a
// lockstep SIO coordinator; Retra owns two cores and exposes one player's
// framebuffer/controls at a time. Both cores continue to run while linked.
static constexpr int LOCAL_LINK_PLAYERS = 2;
static constexpr int64_t LINK_FRAME_TIME_NS = 16'742'706LL;

struct RetraLockstepUser {
    mLockstepThreadUser base{};
    int preferredId = 0;
};

struct LocalLinkPlayer {
    mCore* core = nullptr;
    bool configInitialized = false;
    mColor videoBuffer[MAX_PIXEL_COUNT]{};
    mColor snapshot[MAX_PIXEL_COUNT]{};
    unsigned width = 240;
    unsigned height = 160;
    std::atomic<uint32_t> keys{0};
    std::atomic<uint32_t> keyPressLatch{0};
    std::atomic<uint64_t> frameNumber{0};
    int playerId = 0;
    std::mutex frameMutex;
    mCoreThread thread{};
    bool threadStarted = false;
    RetraLockstepUser lockstepUser{};
    GBASIOLockstepDriver linkDriver{};
    mCoreCallbacks inputCallbacks{};
    std::chrono::steady_clock::time_point nextFrame{};
};

static LocalLinkPlayer localPlayers[LOCAL_LINK_PLAYERS];
static GBASIOLockstepCoordinator localCoordinator{};
static bool localCoordinatorInitialized = false;
static std::atomic<bool> localLinkActive{false};
static std::atomic<bool> localLinkPaused{false};
static std::atomic<bool> localLinkSinglePakActive{false};
static std::atomic<int> activeLocalPlayer{0};
static constexpr uint32_t SINGLE_PAK_BOOT_KEYS = (1u << 2) | (1u << 3); // SELECT + START
static constexpr uint64_t SINGLE_PAK_BOOT_HOLD_FRAMES = 120;

struct ScheduledLinkInput {
    uint64_t frame = 0;
    uint32_t mask = 0;
};
static std::mutex linkInputScheduleMutex;
static std::deque<ScheduledLinkInput> linkInputSchedule[LOCAL_LINK_PLAYERS];


struct LinkCheckpoint {
    uint64_t frame = 0;
    uint64_t hash = 0;
};
static std::mutex linkCheckpointMutex;
static std::deque<LinkCheckpoint> linkCheckpoints[LOCAL_LINK_PLAYERS];

static uint64_t linkCheckpointIntervalFrames() {
    // The former "SMC check" slider is now a real Remote Link integrity
    // interval: lower values check more often (more CPU), higher values less.
    const int level = std::max(0, std::min(10, retraSyncCheckLevel.load(std::memory_order_relaxed)));
    return static_cast<uint64_t>(60 + level * 45); // 1s .. ~8.5s at 59.7 fps
}

static void clearLinkCheckpointsLocked() {
    std::lock_guard<std::mutex> checkpointLock(linkCheckpointMutex);
    for (auto& queue : linkCheckpoints) queue.clear();
}

static void clearLocalLinkInputScheduleLocked() {
    std::lock_guard<std::mutex> scheduleLock(linkInputScheduleMutex);
    for (auto& queue : linkInputSchedule) {
        queue.clear();
    }
}

static void destroyLocalLinkLocked();

// Retra rewind is a bounded, RAM-only raw-state ring. States are sampled by
// wall clock rather than emulated frame count so 2x/4x/8x/16x fast-forward
// still represents roughly the last 15 seconds the player actually experienced.
struct RetraRewindSnapshot {
    int64_t activeTimelineMs = 0;
    std::vector<uint8_t> state;
};
static std::deque<RetraRewindSnapshot> rewindSnapshots;
// Recycle a few raw state buffers once the rewind window starts rolling. This
// avoids a large malloc/free pair every 500 ms during gameplay, which can show
// up as a periodic frame-time spike on memory-constrained Android devices.
static std::deque<std::vector<uint8_t>> rewindSpareBuffers;
static size_t rewindBytes = 0;
static int64_t rewindActiveTimelineMs = 0;
static int64_t lastRewindCaptureTimelineMs = -1;
static std::chrono::steady_clock::time_point lastRewindFrameWallClock{};
static constexpr int64_t RETRA_REWIND_CAPTURE_INTERVAL_MS = 500;
static constexpr int64_t RETRA_REWIND_MAX_AGE_MS = 16'500;
static constexpr int64_t RETRA_REWIND_MAX_FRAME_GAP_MS = 100;
static constexpr size_t RETRA_REWIND_MAX_BYTES = 32u * 1024u * 1024u;
static constexpr size_t RETRA_REWIND_MAX_SPARE_BUFFERS = 3u;

static void recycleRewindBufferLocked(std::vector<uint8_t>&& buffer) {
    if (buffer.empty() || rewindSpareBuffers.size() >= RETRA_REWIND_MAX_SPARE_BUFFERS) return;
    rewindSpareBuffers.emplace_back(std::move(buffer));
}

static std::vector<uint8_t> acquireRewindBufferLocked(size_t stateSize) {
    for (auto it = rewindSpareBuffers.begin(); it != rewindSpareBuffers.end(); ++it) {
        if (it->capacity() >= stateSize) {
            std::vector<uint8_t> buffer = std::move(*it);
            rewindSpareBuffers.erase(it);
            buffer.resize(stateSize);
            return buffer;
        }
    }
    return std::vector<uint8_t>(stateSize);
}

static void clearRewindLocked() {
    rewindSnapshots.clear();
    rewindSpareBuffers.clear();
    rewindBytes = 0;
    rewindActiveTimelineMs = 0;
    lastRewindCaptureTimelineMs = -1;
    lastRewindFrameWallClock = {};
}

static void captureRewindSnapshotLocked() {
    if (!core || localLinkActive.load(std::memory_order_acquire)) return;

    // Advance a gameplay-only wall-clock timeline. A long gap means gameplay
    // was paused/backgrounded/menu-open, so cap it instead of treating the
    // pause itself as rewindable gameplay time.
    const auto now = std::chrono::steady_clock::now();
    if (lastRewindFrameWallClock.time_since_epoch().count() != 0) {
        const auto deltaMs = std::chrono::duration_cast<std::chrono::milliseconds>(
            now - lastRewindFrameWallClock
        ).count();
        if (deltaMs > 0) rewindActiveTimelineMs += std::min<int64_t>(deltaMs, RETRA_REWIND_MAX_FRAME_GAP_MS);
    }
    lastRewindFrameWallClock = now;

    if (lastRewindCaptureTimelineMs >= 0 &&
        rewindActiveTimelineMs - lastRewindCaptureTimelineMs < RETRA_REWIND_CAPTURE_INTERVAL_MS) {
        return;
    }

    // Prune old snapshots before allocating the next one so their backing
    // storage can be reused immediately by this capture.
    while (!rewindSnapshots.empty() &&
           rewindActiveTimelineMs - rewindSnapshots.front().activeTimelineMs > RETRA_REWIND_MAX_AGE_MS) {
        rewindBytes -= rewindSnapshots.front().state.size();
        auto buffer = std::move(rewindSnapshots.front().state);
        rewindSnapshots.pop_front();
        recycleRewindBufferLocked(std::move(buffer));
    }

    const size_t stateSize = core->stateSize(core);
    if (stateSize == 0 || stateSize > RETRA_REWIND_MAX_BYTES) return;

    std::vector<uint8_t> bytes = acquireRewindBufferLocked(stateSize);
    core->saveState(core, bytes.data());

    rewindBytes += bytes.size();
    rewindSnapshots.push_back({rewindActiveTimelineMs, std::move(bytes)});
    lastRewindCaptureTimelineMs = rewindActiveTimelineMs;

    while (!rewindSnapshots.empty() && rewindBytes > RETRA_REWIND_MAX_BYTES) {
        rewindBytes -= rewindSnapshots.front().state.size();
        auto buffer = std::move(rewindSnapshots.front().state);
        rewindSnapshots.pop_front();
        recycleRewindBufferLocked(std::move(buffer));
    }
}

static int rewindAvailableSecondsLocked() {
    if (!core || localLinkActive.load(std::memory_order_acquire) || rewindSnapshots.empty()) return 0;
    const int64_t ageMs = rewindActiveTimelineMs - rewindSnapshots.front().activeTimelineMs;
    return static_cast<int>(std::max<int64_t>(0, std::min<int64_t>(15, ageMs / 1000)));
}

static bool rewindBySecondsLocked(int seconds) {
    if (!core || localLinkActive.load(std::memory_order_acquire)) return false;
    if (seconds != 5 && seconds != 10 && seconds != 15) return false;

    const int64_t targetTimelineMs = rewindActiveTimelineMs - static_cast<int64_t>(seconds) * 1000;
    auto selected = rewindSnapshots.end();
    for (auto it = rewindSnapshots.rbegin(); it != rewindSnapshots.rend(); ++it) {
        if (it->activeTimelineMs <= targetTimelineMs) {
            selected = std::prev(it.base());
            break;
        }
    }
    if (selected == rewindSnapshots.end() || selected->state.empty()) return false;
    if (selected->state.size() != core->stateSize(core)) return false;

    if (!core->loadState(core, selected->state.data())) return false;
    keyMask.store(0, std::memory_order_relaxed);
    keyPressLatch.store(0, std::memory_order_relaxed);

    // Never play PCM queued by the pre-rewind timeline. The next frame starts
    // a fresh audio history from the restored state.
    if (core->getAudioBuffer) {
        if (mAudioBuffer* audio = core->getAudioBuffer(core)) {
            const size_t available = mAudioBufferAvailable(audio);
            if (available) mAudioBufferRead(audio, nullptr, available);
        }
    }
    resetRetraAudioResamplerLocked();

    // Old snapshots belong to the abandoned future timeline. Clearing them
    // prevents repeatedly jumping between incompatible histories.
    clearRewindLocked();
    return true;
}

static int mapCheatTypeForCore(int requestedType) {
    if (!core) {
        return 0;
    }

    const auto platform = core->platform(core);
    if (platform == mPLATFORM_GBA) {
        switch (requestedType) {
            case 1: return GBA_CHEAT_GAMESHARK;
            case 2: return GBA_CHEAT_PRO_ACTION_REPLAY;
            case 3: return GBA_CHEAT_CODEBREAKER;
            case 4: return GBA_CHEAT_VBA;
            case 0:
            default: return GBA_CHEAT_AUTODETECT;
        }
    }

    if (platform == mPLATFORM_GB) {
        switch (requestedType) {
            case 1: return GB_CHEAT_GAMESHARK;
            case 4: return GB_CHEAT_VBA;
            case 0:
            default: return GB_CHEAT_AUTODETECT;
        }
    }

    return 0;
}

static std::string trimLine(std::string line) {
    line.erase(line.begin(), std::find_if(line.begin(), line.end(), [](unsigned char ch) {
        return !std::isspace(ch);
    }));
    line.erase(std::find_if(line.rbegin(), line.rend(), [](unsigned char ch) {
        return !std::isspace(ch);
    }).base(), line.end());
    return line;
}

static void clearCheatDeviceLocked(struct mCheatDevice* device) {
    if (!device) {
        return;
    }

    while (mCheatSetsSize(&device->cheats) > 0) {
        struct mCheatSet* set = *mCheatSetsGetPointer(&device->cheats, 0);
        // Disable and refresh first so mGBA can unpatch ROM-backed cheats
        // immediately. Then remove hooks and free the set. This makes OFF a
        // real native state change instead of only a persisted UI flag.
        set->enabled = false;
        mCheatRefresh(device, set);
        mCheatRemoveSet(device, set);
        mCheatSetDeinit(set);
    }
}


static std::string normalizeCheatLineForParser(std::string line, int requestedType) {
    line = trimLine(line);
    if (line.empty()) {
        return {};
    }
    if (line.rfind("#", 0) == 0 || line.rfind(";", 0) == 0 || line.rfind("//", 0) == 0) {
        return {};
    }

    // Raw/VBA codes can intentionally use punctuation such as ':', so leave
    // that syntax intact. For the standard GBA code formats, accept codes
    // pasted either as "XXXXXXXX YYYYYYYY" or as one continuous hex string.
    if (requestedType != 4) {
        std::string compact;
        compact.reserve(line.size());
        bool onlyHexAndSpacing = true;
        for (unsigned char ch : line) {
            if (std::isxdigit(ch)) {
                compact.push_back(static_cast<char>(std::toupper(ch)));
            } else if (std::isspace(ch) || ch == '-') {
                continue;
            } else {
                onlyHexAndSpacing = false;
                break;
            }
        }
        if (onlyHexAndSpacing && (compact.size() == 12 || compact.size() == 16)) {
            return compact.substr(0, 8) + " " + compact.substr(8);
        }
    }
    return line;
}

static bool addCheatSetWithTypeLocked(
        struct mCheatDevice* device,
        const char* name,
        const std::vector<std::string>& rawLines,
        int nativeType,
        int requestedType,
        bool enabled) {
    if (!device || !device->createSet) {
        return false;
    }

    struct mCheatSet* set = device->createSet(device, name);
    if (!set) {
        return false;
    }

    bool anyLine = false;
    bool allLinesValid = true;
    for (const auto& rawLine : rawLines) {
        const std::string line = normalizeCheatLineForParser(rawLine, requestedType);
        if (line.empty()) {
            continue;
        }
        anyLine = true;
        if (!mCheatAddLine(set, line.c_str(), nativeType)) {
            allLinesValid = false;
            break;
        }
    }

    if (anyLine && allLinesValid) {
        set->enabled = enabled;
        mCheatAddSet(device, set);
        return true;
    }

    mCheatSetDeinit(set);
    return false;
}

static void destroyCoreLocked() {
    clearRewindLocked();
    if (!core) {
        return;
    }

    resetRetraAudioResamplerLocked();
    removeGbaVideoHook(core);
    if (configInitialized) {
        mCoreConfigDeinit(&core->config);
        configInitialized = false;
    }

    core->deinit(core);
    core = nullptr;
    keyMask.store(0, std::memory_order_relaxed);
    keyPressLatch.store(0, std::memory_order_relaxed);
    videoWidth = 240;
    videoHeight = 160;
    std::memset(videoBuffer, 0, sizeof(videoBuffer));
}

static bool loadRomLocked(const char* romPath, const char* patchPath, const char* savePath) {
    destroyLocalLinkLocked();
    destroyCoreLocked();

    /*
     * mCoreFind sniffs the actual file contents, not just the extension.
     * This lets Retra accept a raw cartridge image renamed to .mgba.
     * ZIP files are extracted on the Android/Kotlin side because the
     * current embedded build does not enable libzip/minizip.
     */
    core = mCoreFind(romPath);
    if (!core) {
        return false;
    }

    if (!core->init(core)) {
        core = nullptr;
        return false;
    }

    mCoreInitConfig(core, "retra");
    configInitialized = true;

    std::memset(videoBuffer, 0, sizeof(videoBuffer));
    core->setVideoBuffer(core, videoBuffer, VIDEO_STRIDE);

    if (!mCoreLoadFile(core, romPath)) {
        destroyCoreLocked();
        return false;
    }

    if (patchPath && patchPath[0] != '\0') {
        VFile* patch = VFileOpen(patchPath, O_RDONLY);
        if (!patch) {
            destroyCoreLocked();
            return false;
        }

        const bool patched = core->loadPatch(core, patch);
        patch->close(patch);

        if (!patched) {
            destroyCoreLocked();
            return false;
        }
    }

    for (const auto& option : runtimeConfigOptions) {
        if (!isRetraPrivateConfigKey(option.first)) {
            mCoreConfigSetOverrideValue(&core->config, option.first.c_str(), option.second.c_str());
        }
    }
    mCoreLoadConfig(core);
    // Retra owns all wall-clock pacing. Never let mGBA's frontend sync options
    // throttle direct core->runFrame() turbo execution back toward real time.
    core->opts.videoSync = false;
    core->opts.audioSync = false;
    installGbaVideoHook(core);
    applyGbaSaveTypeOverride(core);
    if (core->setAudioBufferSize) {
        core->setAudioBufferSize(core, 8192);
    }
    // Retra owns battery saves by permanent romId, not by the ROM filename.
    // mCoreLoadSaveFile creates/attaches this file only when the game starts;
    // importing a ROM never creates or truncates a battery save.
    if (savePath && savePath[0] != '\0') {
        if (!mCoreLoadSaveFile(core, savePath, false)) {
            destroyCoreLocked();
            return false;
        }
    } else {
        (void) mCoreAutoloadSave(core);
    }
    core->reset(core);
    // Reassert an explicit cartridge save type after reset, since mGBA's
    // cartridge override database may otherwise re-select an autodetected type.
    applyGbaSaveTypeOverride(core);
    syncGbaMosaicRenderer(core);

    unsigned width = 0;
    unsigned height = 0;
    core->currentVideoSize(core, &width, &height);

    if (width == 0 || height == 0 ||
        width > MAX_VIDEO_WIDTH || height > MAX_VIDEO_HEIGHT) {
        destroyCoreLocked();
        return false;
    }

    videoWidth = width;
    videoHeight = height;
    return true;
}

static int localLinkRequestedId(struct mLockstepUser* user) {
    if (!user) {
        return -1;
    }
    auto* threadUser = reinterpret_cast<mLockstepThreadUser*>(user);
    auto* retraUser = reinterpret_cast<RetraLockstepUser*>(threadUser);
    return retraUser->preferredId;
}

static void localLinkKeysRead(void* context) {
    auto* player = static_cast<LocalLinkPlayer*>(context);
    if (!player || !player->core) {
        return;
    }

    // Remote Link can queue controller masks for a specific emulated frame.
    // Applying them here (immediately before mGBA samples keys) makes both
    // replicated phone sessions see the same input on the same logical frame.
    const uint64_t upcomingFrame = player->frameNumber.load(std::memory_order_relaxed) + 1;
    if (player->playerId >= 0 && player->playerId < LOCAL_LINK_PLAYERS) {
        std::lock_guard<std::mutex> scheduleLock(linkInputScheduleMutex);
        auto& queue = linkInputSchedule[player->playerId];
        while (!queue.empty() && queue.front().frame <= upcomingFrame) {
            const uint32_t nextMask = queue.front().mask;
            const uint32_t previousMask = player->keys.exchange(nextMask, std::memory_order_relaxed);
            const uint32_t risingEdges = nextMask & ~previousMask;
            if (risingEdges != 0) {
                player->keyPressLatch.fetch_or(risingEdges, std::memory_order_relaxed);
            }
            queue.pop_front();
        }
    }

    const uint32_t heldKeys = player->keys.load(std::memory_order_relaxed);
    const uint32_t tappedKeys = player->keyPressLatch.exchange(0, std::memory_order_relaxed);
    player->core->setKeys(player->core, heldKeys | tappedKeys);
}

static void localLinkFrameEnded(struct mCoreThread* threadContext) {
    auto* player = threadContext
            ? static_cast<LocalLinkPlayer*>(threadContext->userData)
            : nullptr;
    if (!player || !player->core) {
        return;
    }

    const uint64_t completedFrame = player->frameNumber.fetch_add(1, std::memory_order_relaxed) + 1;
    if (localLinkSinglePakActive.load(std::memory_order_relaxed) &&
        player->playerId == 1 && completedFrame >= SINGLE_PAK_BOOT_HOLD_FRAMES) {
        // Hold START+SELECT only through the receiver BIOS boot window. This
        // enters multiboot receive mode without leaking those buttons into the
        // downloaded client program once the transfer begins.
        player->keys.fetch_and(~SINGLE_PAK_BOOT_KEYS, std::memory_order_relaxed);
    }
    syncGbaMosaicRenderer(player->core);

    const uint64_t interval = linkCheckpointIntervalFrames();
    if (interval > 0 && (completedFrame % interval) == 0) {
        const size_t stateSize = player->core->stateSize(player->core);
        if (stateSize > 0 && stateSize <= 64 * 1024 * 1024) {
            std::vector<uint8_t> rawState(stateSize);
            if (player->core->saveState(player->core, rawState.data())) {
                const uint64_t hash = retraFnv1a64(rawState.data(), rawState.size());
                std::lock_guard<std::mutex> checkpointLock(linkCheckpointMutex);
                auto& queue = linkCheckpoints[player->playerId];
                queue.push_back({completedFrame, hash});
                while (queue.size() > 12) queue.pop_front();
            }
        }
    }

    unsigned width = 0;
    unsigned height = 0;
    player->core->currentVideoSize(player->core, &width, &height);
    if (width > 0 && height > 0 &&
        width <= MAX_VIDEO_WIDTH && height <= MAX_VIDEO_HEIGHT) {
        std::lock_guard<std::mutex> frameLock(player->frameMutex);
        player->width = width;
        player->height = height;
        std::memcpy(player->snapshot, player->videoBuffer, sizeof(player->snapshot));
    }

    // The Android renderer consumes snapshots independently, so the two native
    // mGBA threads need their own real-time pacing instead of video-sync waits.
    const auto now = std::chrono::steady_clock::now();
    if (player->nextFrame.time_since_epoch().count() == 0) {
        player->nextFrame = now;
    }
    player->nextFrame += std::chrono::nanoseconds(LINK_FRAME_TIME_NS);
    if (player->nextFrame > now) {
        std::this_thread::sleep_until(player->nextFrame);
    } else if (now - player->nextFrame >
               std::chrono::nanoseconds(LINK_FRAME_TIME_NS * 4)) {
        player->nextFrame = now;
    }
}

static void clearLocalPlayer(LocalLinkPlayer& player) {
    player.keys.store(0, std::memory_order_relaxed);
    player.keyPressLatch.store(0, std::memory_order_relaxed);
    player.frameNumber.store(0, std::memory_order_relaxed);
    player.playerId = 0;
    player.width = 240;
    player.height = 160;
    std::memset(player.videoBuffer, 0, sizeof(player.videoBuffer));
    {
        std::lock_guard<std::mutex> frameLock(player.frameMutex);
        std::memset(player.snapshot, 0, sizeof(player.snapshot));
    }
    std::memset(&player.thread, 0, sizeof(player.thread));
    std::memset(&player.lockstepUser, 0, sizeof(player.lockstepUser));
    std::memset(&player.linkDriver, 0, sizeof(player.linkDriver));
    std::memset(&player.inputCallbacks, 0, sizeof(player.inputCallbacks));
    player.threadStarted = false;
    player.nextFrame = {};
}

static bool prepareLocalPlayer(
        LocalLinkPlayer& player,
        const char* romPath,
        const char* savePath,
        int preferredId,
        int savePlayerId) {
    clearLocalPlayer(player);
    player.playerId = preferredId;

    player.core = mCoreFind(romPath);
    if (!player.core) {
        return false;
    }
    if (!player.core->init(player.core)) {
        player.core = nullptr;
        return false;
    }

    mCoreInitConfig(player.core, "retra-link");
    player.configInitialized = true;

    player.core->setVideoBuffer(player.core, player.videoBuffer, VIDEO_STRIDE);
    if (!mCoreLoadFile(player.core, romPath)) {
        return false;
    }
    if (player.core->platform(player.core) != mPLATFORM_GBA) {
        return false;
    }

    for (const auto& option : runtimeConfigOptions) {
        if (!isRetraPrivateConfigKey(option.first)) {
            mCoreConfigSetOverrideValue(&player.core->config, option.first.c_str(), option.second.c_str());
        }
    }
    mCoreLoadConfig(player.core);
    installGbaVideoHook(player.core);
    applyGbaSaveTypeOverride(player.core);
    if (player.core->setAudioBufferSize) {
        player.core->setAudioBufferSize(player.core, 8192);
    }
    // Retra supplies explicit persistent save paths for each linked core.
    // savePlayerId remains as a fallback for legacy callers only.
    mCoreConfigSetIntValue(&player.core->config, "savePlayerId", savePlayerId);
    if (savePath && savePath[0] != '\0') {
        if (!mCoreLoadSaveFile(player.core, savePath, false)) {
            return false;
        }
    } else {
        (void) mCoreAutoloadSave(player.core);
    }
    // Force again after save attachment; this makes the UI cartridge type
    // actually control mGBA for both normal and linked cores.
    applyGbaSaveTypeOverride(player.core);
    syncGbaMosaicRenderer(player.core);
    player.core->opts.videoSync = false;
    player.core->opts.audioSync = false;
    player.core->opts.rewindEnable = false;

    unsigned width = 0;
    unsigned height = 0;
    player.core->baseVideoSize(player.core, &width, &height);
    if (width == 0 || height == 0 ||
        width > MAX_VIDEO_WIDTH || height > MAX_VIDEO_HEIGHT) {
        return false;
    }
    player.width = width;
    player.height = height;

    player.inputCallbacks.context = &player;
    player.inputCallbacks.keysRead = localLinkKeysRead;
    player.core->addCoreCallbacks(player.core, &player.inputCallbacks);

    player.thread.core = player.core;
    player.thread.userData = &player;
    player.thread.frameCallback = localLinkFrameEnded;

    mLockstepThreadUserInit(&player.lockstepUser.base, &player.thread);
    player.lockstepUser.preferredId = preferredId;
    player.lockstepUser.base.d.requestedId = localLinkRequestedId;
    GBASIOLockstepDriverCreate(&player.linkDriver, &player.lockstepUser.base.d);
    return true;
}

static bool prepareLocalSinglePakClient(
        LocalLinkPlayer& player,
        const char* biosPath,
        int preferredId) {
    clearLocalPlayer(player);
    player.playerId = preferredId;

    // Single-Pak's receiving GBA has no cartridge. Create a bare GBA core and
    // boot the user-provided BIOS so the sender can transfer its multiboot image
    // over the same mGBA lockstep SIO cable used by normal Local Link.
    player.core = mCoreCreate(mPLATFORM_GBA);
    if (!player.core || !player.core->init(player.core)) {
        player.core = nullptr;
        return false;
    }

    mCoreInitConfig(player.core, "retra-singlepak");
    player.configInitialized = true;
    player.core->setVideoBuffer(player.core, player.videoBuffer, VIDEO_STRIDE);

    for (const auto& option : runtimeConfigOptions) {
        if (!isRetraPrivateConfigKey(option.first)) {
            mCoreConfigSetOverrideValue(&player.core->config, option.first.c_str(), option.second.c_str());
        }
    }
    mCoreConfigSetOverrideValue(&player.core->config, "useBios", "1");
    mCoreConfigSetOverrideValue(&player.core->config, "skipBios", "0");
    mCoreConfigSetOverrideValue(&player.core->config, "gba.bios", biosPath);
    mCoreLoadConfig(player.core);
    player.core->opts.useBios = true;
    player.core->opts.skipBios = false;

    VFile* bios = VFileOpen(biosPath, O_RDONLY);
    if (!bios) {
        return false;
    }
    if (!player.core->loadBIOS(player.core, bios, 0)) {
        bios->close(bios);
        return false;
    }

    installGbaVideoHook(player.core);
    if (player.core->setAudioBufferSize) {
        player.core->setAudioBufferSize(player.core, 8192);
    }
    syncGbaMosaicRenderer(player.core);
    player.core->opts.videoSync = false;
    player.core->opts.audioSync = false;
    player.core->opts.rewindEnable = false;

    unsigned width = 0;
    unsigned height = 0;
    player.core->baseVideoSize(player.core, &width, &height);
    if (width == 0 || height == 0 || width > MAX_VIDEO_WIDTH || height > MAX_VIDEO_HEIGHT) {
        return false;
    }
    player.width = width;
    player.height = height;

    player.inputCallbacks.context = &player;
    player.inputCallbacks.keysRead = localLinkKeysRead;
    player.core->addCoreCallbacks(player.core, &player.inputCallbacks);
    player.thread.core = player.core;
    player.thread.userData = &player;
    player.thread.frameCallback = localLinkFrameEnded;

    mLockstepThreadUserInit(&player.lockstepUser.base, &player.thread);
    player.lockstepUser.preferredId = preferredId;
    player.lockstepUser.base.d.requestedId = localLinkRequestedId;
    GBASIOLockstepDriverCreate(&player.linkDriver, &player.lockstepUser.base.d);
    return true;
}

static void destroyPreparedLocalPlayer(LocalLinkPlayer& player) {
    if (player.configInitialized && player.core) {
        mCoreConfigDeinit(&player.core->config);
    }
    player.configInitialized = false;
    if (player.core) {
        removeGbaVideoHook(player.core);
        player.core->deinit(player.core);
        player.core = nullptr;
    }
    clearLocalPlayer(player);
}

static void destroyLocalLinkLocked() {
    resetRetraAudioResamplerLocked();
    const bool hadLink = localLinkActive.exchange(false, std::memory_order_acq_rel);
    localLinkSinglePakActive.store(false, std::memory_order_relaxed);
    localLinkPaused.store(false, std::memory_order_relaxed);
    activeLocalPlayer.store(0, std::memory_order_relaxed);
    clearLocalLinkInputScheduleLocked();
    clearLinkCheckpointsLocked();

    // Tell both CPU threads to exit before joining either one. A link transfer
    // can legitimately have one player sleeping while it waits for the other.
    for (auto& player : localPlayers) {
        if (player.threadStarted && player.thread.impl) {
            mCoreThreadEnd(&player.thread);
        }
    }
    for (auto& player : localPlayers) {
        if (player.threadStarted && player.thread.impl) {
            mCoreThreadJoin(&player.thread);
            player.threadStarted = false;
        }
    }

    if (localCoordinatorInitialized) {
        for (auto& player : localPlayers) {
            if (player.core) {
                player.core->setPeripheral(player.core, mPERIPH_GBA_LINK_PORT, nullptr);
            }
        }
        for (auto& player : localPlayers) {
            if (player.linkDriver.coordinator == &localCoordinator) {
                GBASIOLockstepCoordinatorDetach(&localCoordinator, &player.linkDriver);
            }
        }
        GBASIOLockstepCoordinatorDeinit(&localCoordinator);
        std::memset(&localCoordinator, 0, sizeof(localCoordinator));
        localCoordinatorInitialized = false;
    }

    for (auto& player : localPlayers) {
        destroyPreparedLocalPlayer(player);
    }
    (void) hadLink;
}

static bool startPreparedLocalLinkLocked(bool singlePak) {
    GBASIOLockstepCoordinatorInit(&localCoordinator);
    localCoordinatorInitialized = true;
    for (int i = 0; i < LOCAL_LINK_PLAYERS; ++i) {
        GBASIOLockstepCoordinatorAttach(&localCoordinator, &localPlayers[i].linkDriver);
        localPlayers[i].core->setPeripheral(
                localPlayers[i].core,
                mPERIPH_GBA_LINK_PORT,
                &localPlayers[i].linkDriver.d);
    }

    // A cartridge-less GBA enters the official multiboot receiver path when
    // START+SELECT are held during BIOS boot. Holding before either CPU starts
    // makes the handshake deterministic; the frame callback releases them.
    localLinkSinglePakActive.store(singlePak, std::memory_order_relaxed);
    if (singlePak) {
        localPlayers[1].keys.store(SINGLE_PAK_BOOT_KEYS, std::memory_order_relaxed);
    }

    for (int i = 0; i < LOCAL_LINK_PLAYERS; ++i) {
        if (!mCoreThreadStart(&localPlayers[i].thread)) {
            destroyLocalLinkLocked();
            return false;
        }
        localPlayers[i].threadStarted = true;
    }

    for (auto& player : localPlayers) {
        std::lock_guard<std::mutex> frameLock(player.frameMutex);
        std::memcpy(player.snapshot, player.videoBuffer, sizeof(player.snapshot));
    }

    activeLocalPlayer.store(0, std::memory_order_relaxed);
    localLinkPaused.store(false, std::memory_order_relaxed);
    localLinkActive.store(true, std::memory_order_release);
    return true;
}

static bool startLocalLinkLocked(
        const char* firstRomPath,
        const char* secondRomPath,
        const char* firstSavePath,
        const char* secondSavePath) {
    destroyLocalLinkLocked();
    destroyCoreLocked();
    clearLinkCheckpointsLocked();

    const bool sameRom = std::strcmp(firstRomPath, secondRomPath) == 0;
    if (!prepareLocalPlayer(localPlayers[0], firstRomPath, firstSavePath, 0, 1) ||
        !prepareLocalPlayer(localPlayers[1], secondRomPath, secondSavePath, 1, sameRom ? 2 : 1)) {
        destroyLocalLinkLocked();
        return false;
    }

    // Both drivers are attached before either CPU starts, matching mGBA's
    // desktop multiplayer topology and avoiding an unlinked first reset.
    return startPreparedLocalLinkLocked(false);
}

static bool startLocalSinglePakLocked(
        const char* firstRomPath,
        const char* firstSavePath,
        const char* gbaBiosPath) {
    destroyLocalLinkLocked();
    destroyCoreLocked();
    clearLinkCheckpointsLocked();

    if (!prepareLocalPlayer(localPlayers[0], firstRomPath, firstSavePath, 0, 1) ||
        !prepareLocalSinglePakClient(localPlayers[1], gbaBiosPath, 1)) {
        destroyLocalLinkLocked();
        return false;
    }
    return startPreparedLocalLinkLocked(true);
}

static bool setLocalLinkPausedLocked(bool paused) {
    if (!localLinkActive.load(std::memory_order_acquire)) {
        return false;
    }
    const bool current = localLinkPaused.load(std::memory_order_relaxed);
    if (current == paused) {
        return true;
    }

    // Issue pause/unpause requests to both linked CPUs concurrently. Doing
    // this sequentially can strand one CPU at an SIO hard-sync while the other
    // is already paused.
    std::thread controls[LOCAL_LINK_PLAYERS];
    const auto now = std::chrono::steady_clock::now();
    for (int i = 0; i < LOCAL_LINK_PLAYERS; ++i) {
        auto& player = localPlayers[i];
        if (!paused) {
            player.nextFrame = now;
        }
        controls[i] = std::thread([&player, paused]() {
            if (!player.threadStarted || !player.thread.impl) {
                return;
            }
            if (paused) {
                mCoreThreadPause(&player.thread);
            } else {
                mCoreThreadUnpause(&player.thread);
            }
        });
    }
    for (auto& control : controls) {
        if (control.joinable()) {
            control.join();
        }
    }
    localLinkPaused.store(paused, std::memory_order_relaxed);
    return true;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_retra_emulator_MainActivity_stringFromJNI(
        JNIEnv* env,
        jobject) {

    std::string text = "mGBA ";
    text += projectVersion;
    text += " connected to Retra";
    return env->NewStringUTF(text.c_str());
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_retra_emulator_MainActivity_loadRom(
        JNIEnv* env,
        jobject,
        jstring path,
        jstring savePathString) {

    const char* romPath = env->GetStringUTFChars(path, nullptr);
    if (!romPath) {
        return JNI_FALSE;
    }
    const char* savePath = env->GetStringUTFChars(savePathString, nullptr);
    if (!savePath) {
        env->ReleaseStringUTFChars(path, romPath);
        return JNI_FALSE;
    }

    std::lock_guard<std::mutex> lock(coreMutex);
    const bool loaded = loadRomLocked(romPath, nullptr, savePath);

    env->ReleaseStringUTFChars(savePathString, savePath);
    env->ReleaseStringUTFChars(path, romPath);
    return loaded ? JNI_TRUE : JNI_FALSE;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_retra_emulator_MainActivity_loadRomWithPatch(
        JNIEnv* env,
        jobject,
        jstring path,
        jstring patchPathString,
        jstring savePathString) {

    const char* romPath = env->GetStringUTFChars(path, nullptr);
    if (!romPath) return JNI_FALSE;
    const char* patchPath = env->GetStringUTFChars(patchPathString, nullptr);
    if (!patchPath) {
        env->ReleaseStringUTFChars(path, romPath);
        return JNI_FALSE;
    }
    const char* savePath = env->GetStringUTFChars(savePathString, nullptr);
    if (!savePath) {
        env->ReleaseStringUTFChars(patchPathString, patchPath);
        env->ReleaseStringUTFChars(path, romPath);
        return JNI_FALSE;
    }

    std::lock_guard<std::mutex> lock(coreMutex);
    const bool loaded = loadRomLocked(romPath, patchPath, savePath);

    env->ReleaseStringUTFChars(savePathString, savePath);
    env->ReleaseStringUTFChars(patchPathString, patchPath);
    env->ReleaseStringUTFChars(path, romPath);
    return loaded ? JNI_TRUE : JNI_FALSE;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_retra_emulator_MainActivity_materializePatchedRom(
        JNIEnv* env,
        jobject,
        jstring path,
        jstring patchPathString,
        jstring outputPathString) {

    const char* romPath = env->GetStringUTFChars(path, nullptr);
    if (!romPath) return JNI_FALSE;
    const char* patchPath = env->GetStringUTFChars(patchPathString, nullptr);
    if (!patchPath) {
        env->ReleaseStringUTFChars(path, romPath);
        return JNI_FALSE;
    }
    const char* outputPath = env->GetStringUTFChars(outputPathString, nullptr);
    if (!outputPath) {
        env->ReleaseStringUTFChars(patchPathString, patchPath);
        env->ReleaseStringUTFChars(path, romPath);
        return JNI_FALSE;
    }

    bool ok = false;
    bool initialized = false;
    std::lock_guard<std::mutex> lock(coreMutex);
    mCore* temporary = mCoreFind(romPath);
    if (temporary) {
        initialized = temporary->init(temporary);
    }
    if (temporary && initialized && mCoreLoadFile(temporary, romPath)) {
        VFile* patch = VFileOpen(patchPath, O_RDONLY);
        if (patch) {
            const bool patched = temporary->loadPatch(temporary, patch);
            patch->close(patch);
            if (patched && temporary->board) {
                const void* bytes = nullptr;
                size_t byteCount = 0;
                const auto platform = temporary->platform(temporary);
                if (platform == mPLATFORM_GBA) {
                    auto* gba = static_cast<struct GBA*>(temporary->board);
                    bytes = reinterpret_cast<const void*>(gba->memory.rom);
                    byteCount = gba->memory.romSize;
                } else if (platform == mPLATFORM_GB) {
                    auto* gb = static_cast<struct GB*>(temporary->board);
                    bytes = reinterpret_cast<const void*>(gb->memory.rom);
                    byteCount = gb->memory.romSize;
                }

                if (bytes && byteCount > 0) {
                    FILE* output = std::fopen(outputPath, "wb");
                    if (output) {
                        ok = std::fwrite(bytes, 1, byteCount, output) == byteCount;
                        ok = ok && std::fflush(output) == 0;
                        std::fclose(output);
                    }
                }
            }
        }
    }
    if (temporary && initialized) {
        temporary->deinit(temporary);
    }

    env->ReleaseStringUTFChars(outputPathString, outputPath);
    env->ReleaseStringUTFChars(patchPathString, patchPath);
    env->ReleaseStringUTFChars(path, romPath);
    return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_retra_emulator_MainActivity_startLocalLink(
        JNIEnv* env,
        jobject,
        jstring firstPathString,
        jstring secondPathString,
        jstring firstSavePathString,
        jstring secondSavePathString) {
    const char* firstPath = env->GetStringUTFChars(firstPathString, nullptr);
    if (!firstPath) return JNI_FALSE;
    const char* secondPath = env->GetStringUTFChars(secondPathString, nullptr);
    if (!secondPath) {
        env->ReleaseStringUTFChars(firstPathString, firstPath);
        return JNI_FALSE;
    }
    const char* firstSavePath = env->GetStringUTFChars(firstSavePathString, nullptr);
    if (!firstSavePath) {
        env->ReleaseStringUTFChars(secondPathString, secondPath);
        env->ReleaseStringUTFChars(firstPathString, firstPath);
        return JNI_FALSE;
    }
    const char* secondSavePath = env->GetStringUTFChars(secondSavePathString, nullptr);
    if (!secondSavePath) {
        env->ReleaseStringUTFChars(firstSavePathString, firstSavePath);
        env->ReleaseStringUTFChars(secondPathString, secondPath);
        env->ReleaseStringUTFChars(firstPathString, firstPath);
        return JNI_FALSE;
    }

    std::lock_guard<std::mutex> lock(coreMutex);
    const bool started = startLocalLinkLocked(firstPath, secondPath, firstSavePath, secondSavePath);

    env->ReleaseStringUTFChars(secondSavePathString, secondSavePath);
    env->ReleaseStringUTFChars(firstSavePathString, firstSavePath);
    env->ReleaseStringUTFChars(secondPathString, secondPath);
    env->ReleaseStringUTFChars(firstPathString, firstPath);
    return started ? JNI_TRUE : JNI_FALSE;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_retra_emulator_MainActivity_startLocalSinglePak(
        JNIEnv* env,
        jobject,
        jstring firstPathString,
        jstring firstSavePathString,
        jstring biosPathString) {
    const char* firstPath = env->GetStringUTFChars(firstPathString, nullptr);
    if (!firstPath) return JNI_FALSE;
    const char* firstSavePath = env->GetStringUTFChars(firstSavePathString, nullptr);
    if (!firstSavePath) {
        env->ReleaseStringUTFChars(firstPathString, firstPath);
        return JNI_FALSE;
    }
    const char* biosPath = env->GetStringUTFChars(biosPathString, nullptr);
    if (!biosPath) {
        env->ReleaseStringUTFChars(firstSavePathString, firstSavePath);
        env->ReleaseStringUTFChars(firstPathString, firstPath);
        return JNI_FALSE;
    }

    std::lock_guard<std::mutex> lock(coreMutex);
    const bool started = startLocalSinglePakLocked(firstPath, firstSavePath, biosPath);

    env->ReleaseStringUTFChars(biosPathString, biosPath);
    env->ReleaseStringUTFChars(firstSavePathString, firstSavePath);
    env->ReleaseStringUTFChars(firstPathString, firstPath);
    return started ? JNI_TRUE : JNI_FALSE;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_retra_emulator_MainActivity_stopLocalLink(
        JNIEnv*,
        jobject) {
    std::lock_guard<std::mutex> lock(coreMutex);
    const bool wasActive = localLinkActive.load(std::memory_order_acquire);
    destroyLocalLinkLocked();
    return wasActive ? JNI_TRUE : JNI_FALSE;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_retra_emulator_MainActivity_isNativeLocalLinkActive(
        JNIEnv*,
        jobject) {
    return localLinkActive.load(std::memory_order_acquire) ? JNI_TRUE : JNI_FALSE;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_retra_emulator_MainActivity_getLocalLinkPlayer(
        JNIEnv*,
        jobject) {
    return static_cast<jint>(activeLocalPlayer.load(std::memory_order_relaxed));
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_retra_emulator_MainActivity_setLocalLinkPlayer(
        JNIEnv*,
        jobject,
        jint player) {
    if (!localLinkActive.load(std::memory_order_acquire) ||
        player < 0 || player >= LOCAL_LINK_PLAYERS) {
        return JNI_FALSE;
    }
    activeLocalPlayer.store(static_cast<int>(player), std::memory_order_relaxed);
    return JNI_TRUE;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_retra_emulator_MainActivity_setLocalLinkPaused(
        JNIEnv*,
        jobject,
        jboolean paused) {
    std::lock_guard<std::mutex> lock(coreMutex);
    return setLocalLinkPausedLocked(paused == JNI_TRUE) ? JNI_TRUE : JNI_FALSE;
}

extern "C"
JNIEXPORT jlong JNICALL
Java_com_retra_emulator_MainActivity_getLocalLinkFrame(
        JNIEnv*,
        jobject) {
    if (!localLinkActive.load(std::memory_order_acquire)) {
        return 0;
    }
    const uint64_t a = localPlayers[0].frameNumber.load(std::memory_order_relaxed);
    const uint64_t b = localPlayers[1].frameNumber.load(std::memory_order_relaxed);
    return static_cast<jlong>(std::min(a, b));
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_retra_emulator_MainActivity_setLocalLinkKeyMask(
        JNIEnv*,
        jobject,
        jint player,
        jint mask) {
    if (!localLinkActive.load(std::memory_order_acquire) ||
        player < 0 || player >= LOCAL_LINK_PLAYERS) {
        return JNI_FALSE;
    }
    const uint32_t nextMask = static_cast<uint32_t>(mask) & 0x3FFu;
    const uint32_t previousMask = localPlayers[player].keys.exchange(nextMask, std::memory_order_relaxed);
    const uint32_t risingEdges = nextMask & ~previousMask;
    if (risingEdges != 0) {
        localPlayers[player].keyPressLatch.fetch_or(risingEdges, std::memory_order_relaxed);
    }
    return JNI_TRUE;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_retra_emulator_MainActivity_scheduleLocalLinkKeyMask(
        JNIEnv*,
        jobject,
        jint player,
        jlong frame,
        jint mask) {
    if (!localLinkActive.load(std::memory_order_acquire) ||
        player < 0 || player >= LOCAL_LINK_PLAYERS || frame < 0) {
        return JNI_FALSE;
    }
    const uint64_t targetFrame = static_cast<uint64_t>(frame);
    const uint64_t currentFrame = localPlayers[player].frameNumber.load(std::memory_order_relaxed);
    // Inputs that arrive after their sampling window are not silently applied
    // late. Returning false lets Remote Link request a deterministic resync.
    if (targetFrame <= currentFrame + 1) {
        return JNI_FALSE;
    }
    ScheduledLinkInput item{targetFrame, static_cast<uint32_t>(mask) & 0x3FFu};
    std::lock_guard<std::mutex> scheduleLock(linkInputScheduleMutex);
    auto& queue = linkInputSchedule[player];
    if (queue.empty() || queue.back().frame <= item.frame) {
        queue.push_back(item);
    } else {
        const auto it = std::upper_bound(
                queue.begin(), queue.end(), item.frame,
                [](uint64_t value, const ScheduledLinkInput& entry) { return value < entry.frame; });
        queue.insert(it, item);
    }
    return JNI_TRUE;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_retra_emulator_MainActivity_clearLocalLinkInputSchedule(
        JNIEnv*,
        jobject) {
    clearLocalLinkInputScheduleLocked();
}


static uint64_t checkpointHashForFrameLocked(int player, uint64_t frame) {
    if (player < 0 || player >= LOCAL_LINK_PLAYERS) return 0;
    for (auto it = linkCheckpoints[player].rbegin(); it != linkCheckpoints[player].rend(); ++it) {
        if (it->frame == frame) return it->hash;
        if (it->frame < frame) break;
    }
    return 0;
}

extern "C"
JNIEXPORT jlongArray JNICALL
Java_com_retra_emulator_MainActivity_getLatestLocalLinkCheckpoint(
        JNIEnv* env,
        jobject) {
    jlong values[2] = {0, 0};
    if (localLinkActive.load(std::memory_order_acquire)) {
        std::lock_guard<std::mutex> checkpointLock(linkCheckpointMutex);
        for (auto a = linkCheckpoints[0].rbegin(); a != linkCheckpoints[0].rend(); ++a) {
            const uint64_t bHash = checkpointHashForFrameLocked(1, a->frame);
            if (bHash) {
                values[0] = static_cast<jlong>(a->frame);
                values[1] = static_cast<jlong>(combineLinkHashes(a->hash, bHash));
                break;
            }
        }
    }
    jlongArray result = env->NewLongArray(2);
    if (result) env->SetLongArrayRegion(result, 0, 2, values);
    return result;
}

extern "C"
JNIEXPORT jlong JNICALL
Java_com_retra_emulator_MainActivity_getLocalLinkCheckpointHash(
        JNIEnv*,
        jobject,
        jlong frame) {
    if (!localLinkActive.load(std::memory_order_acquire) || frame <= 0) return 0;
    std::lock_guard<std::mutex> checkpointLock(linkCheckpointMutex);
    const uint64_t a = checkpointHashForFrameLocked(0, static_cast<uint64_t>(frame));
    const uint64_t b = checkpointHashForFrameLocked(1, static_cast<uint64_t>(frame));
    if (!a || !b) return 0;
    return static_cast<jlong>(combineLinkHashes(a, b));
}

extern "C"
JNIEXPORT jlong JNICALL
Java_com_retra_emulator_MainActivity_getLocalLinkFrameSkew(
        JNIEnv*,
        jobject) {
    if (!localLinkActive.load(std::memory_order_acquire)) return 0;
    const uint64_t a = localPlayers[0].frameNumber.load(std::memory_order_relaxed);
    const uint64_t b = localPlayers[1].frameNumber.load(std::memory_order_relaxed);
    return static_cast<jlong>(a > b ? a - b : b - a);
}

static constexpr int RETRA_LINK_SNAPSHOT_FLAGS =
        SAVESTATE_SAVEDATA | SAVESTATE_CHEATS | SAVESTATE_RTC;

static bool saveCoreContainer(struct mCore* target, std::vector<uint8_t>& out) {
    if (!target) return false;
    VFile* memoryFile = VFileMemChunk(nullptr, 0);
    if (!memoryFile) return false;
    const bool serialized = mCoreSaveStateNamed(target, memoryFile, RETRA_LINK_SNAPSHOT_FLAGS);
    if (!serialized) {
        memoryFile->close(memoryFile);
        return false;
    }
    const ssize_t size = memoryFile->size(memoryFile);
    if (size <= 0 || size > 16 * 1024 * 1024) {
        memoryFile->close(memoryFile);
        return false;
    }
    out.resize(static_cast<size_t>(size));
    memoryFile->seek(memoryFile, 0, SEEK_SET);
    const ssize_t read = memoryFile->read(memoryFile, out.data(), out.size());
    memoryFile->close(memoryFile);
    return read == size;
}

static bool loadCoreContainer(struct mCore* target, const uint8_t* data, size_t size) {
    if (!target || !data || size == 0) return false;
    VFile* memoryFile = VFileFromConstMemory(data, size);
    if (!memoryFile) return false;
    const bool loaded = mCoreLoadStateNamed(target, memoryFile, RETRA_LINK_SNAPSHOT_FLAGS);
    memoryFile->close(memoryFile);
    return loaded;
}

template<typename T>
static void appendSnapshotValue(std::vector<uint8_t>& out, T value) {
    const size_t offset = out.size();
    out.resize(offset + sizeof(T));
    std::memcpy(out.data() + offset, &value, sizeof(T));
}

template<typename T>
static bool readSnapshotValue(const uint8_t*& cursor, const uint8_t* end, T* out) {
    if (!out || static_cast<size_t>(end - cursor) < sizeof(T)) return false;
    std::memcpy(out, cursor, sizeof(T));
    cursor += sizeof(T);
    return true;
}

static bool exportLocalLinkSnapshotLocked(std::vector<uint8_t>& out) {
    if (!localLinkActive.load(std::memory_order_acquire)) return false;
    const bool wasPaused = localLinkPaused.load(std::memory_order_relaxed);
    if (!wasPaused && !setLocalLinkPausedLocked(true)) return false;

    std::vector<uint8_t> states[LOCAL_LINK_PLAYERS];
    bool ok = saveCoreContainer(localPlayers[0].core, states[0]) &&
              saveCoreContainer(localPlayers[1].core, states[1]);
    if (ok) {
        out.clear();
        appendSnapshotValue<uint32_t>(out, 0x32544C52u); // "RLT2"
        appendSnapshotValue<uint32_t>(out, 2u);
        appendSnapshotValue<uint64_t>(out, localPlayers[0].frameNumber.load(std::memory_order_relaxed));
        appendSnapshotValue<uint64_t>(out, localPlayers[1].frameNumber.load(std::memory_order_relaxed));
        appendSnapshotValue<uint32_t>(out, localPlayers[0].keys.load(std::memory_order_relaxed));
        appendSnapshotValue<uint32_t>(out, localPlayers[1].keys.load(std::memory_order_relaxed));
        appendSnapshotValue<uint32_t>(out, static_cast<uint32_t>(states[0].size()));
        appendSnapshotValue<uint32_t>(out, static_cast<uint32_t>(states[1].size()));
        out.insert(out.end(), states[0].begin(), states[0].end());
        out.insert(out.end(), states[1].begin(), states[1].end());
    }

    if (!wasPaused) setLocalLinkPausedLocked(false);
    return ok;
}

static bool importLocalLinkSnapshotLocked(const uint8_t* data, size_t size) {
    if (!localLinkActive.load(std::memory_order_acquire) || !data || size < 40) return false;
    const bool wasPaused = localLinkPaused.load(std::memory_order_relaxed);
    if (!wasPaused && !setLocalLinkPausedLocked(true)) return false;

    const uint8_t* cursor = data;
    const uint8_t* end = data + size;
    uint32_t magic = 0, version = 0, keys0 = 0, keys1 = 0, size0 = 0, size1 = 0;
    uint64_t frame0 = 0, frame1 = 0;
    bool ok = readSnapshotValue(cursor, end, &magic) &&
              readSnapshotValue(cursor, end, &version) &&
              readSnapshotValue(cursor, end, &frame0) &&
              readSnapshotValue(cursor, end, &frame1) &&
              readSnapshotValue(cursor, end, &keys0) &&
              readSnapshotValue(cursor, end, &keys1) &&
              readSnapshotValue(cursor, end, &size0) &&
              readSnapshotValue(cursor, end, &size1);
    ok = ok && magic == 0x32544C52u && version == 2u &&
         size0 > 0 && size1 > 0 &&
         size0 <= 16 * 1024 * 1024 && size1 <= 16 * 1024 * 1024 &&
         static_cast<size_t>(end - cursor) == static_cast<size_t>(size0) + static_cast<size_t>(size1);
    if (ok) {
        const uint8_t* state0 = cursor;
        const uint8_t* state1 = cursor + size0;
        ok = loadCoreContainer(localPlayers[0].core, state0, size0) &&
             loadCoreContainer(localPlayers[1].core, state1, size1);
    }
    if (ok) {
        localPlayers[0].frameNumber.store(frame0, std::memory_order_relaxed);
        localPlayers[1].frameNumber.store(frame1, std::memory_order_relaxed);
        localPlayers[0].keys.store(keys0 & 0x3FFu, std::memory_order_relaxed);
        localPlayers[1].keys.store(keys1 & 0x3FFu, std::memory_order_relaxed);
        clearLocalLinkInputScheduleLocked();
        clearLinkCheckpointsLocked();
        syncGbaMosaicRenderer(localPlayers[0].core);
        syncGbaMosaicRenderer(localPlayers[1].core);
    }

    if (!wasPaused) setLocalLinkPausedLocked(false);
    return ok;
}

extern "C"
JNIEXPORT jbyteArray JNICALL
Java_com_retra_emulator_MainActivity_exportLocalLinkSnapshot(
        JNIEnv* env,
        jobject) {
    std::lock_guard<std::mutex> lock(coreMutex);
    std::vector<uint8_t> bytes;
    if (!exportLocalLinkSnapshotLocked(bytes) || bytes.empty()) return nullptr;
    jbyteArray result = env->NewByteArray(static_cast<jsize>(bytes.size()));
    if (!result) return nullptr;
    env->SetByteArrayRegion(result, 0, static_cast<jsize>(bytes.size()), reinterpret_cast<const jbyte*>(bytes.data()));
    return result;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_retra_emulator_MainActivity_importLocalLinkSnapshot(
        JNIEnv* env,
        jobject,
        jbyteArray payload) {
    if (!payload) return JNI_FALSE;
    const jsize length = env->GetArrayLength(payload);
    if (length <= 0 || length > 40 * 1024 * 1024) return JNI_FALSE;
    std::vector<uint8_t> bytes(static_cast<size_t>(length));
    env->GetByteArrayRegion(payload, 0, length, reinterpret_cast<jbyte*>(bytes.data()));
    std::lock_guard<std::mutex> lock(coreMutex);
    return importLocalLinkSnapshotLocked(bytes.data(), bytes.size()) ? JNI_TRUE : JNI_FALSE;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_retra_emulator_MainActivity_getVideoWidth(
        JNIEnv*,
        jobject) {
    std::lock_guard<std::mutex> lock(coreMutex);
    if (localLinkActive.load(std::memory_order_acquire)) {
        const int playerIndex = activeLocalPlayer.load(std::memory_order_relaxed);
        std::lock_guard<std::mutex> frameLock(localPlayers[playerIndex].frameMutex);
        return static_cast<jint>(localPlayers[playerIndex].width);
    }
    return static_cast<jint>(videoWidth);
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_retra_emulator_MainActivity_getVideoHeight(
        JNIEnv*,
        jobject) {
    std::lock_guard<std::mutex> lock(coreMutex);
    if (localLinkActive.load(std::memory_order_acquire)) {
        const int playerIndex = activeLocalPlayer.load(std::memory_order_relaxed);
        std::lock_guard<std::mutex> frameLock(localPlayers[playerIndex].frameMutex);
        return static_cast<jint>(localPlayers[playerIndex].height);
    }
    return static_cast<jint>(videoHeight);
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_retra_emulator_MainActivity_getPlatform(
        JNIEnv*,
        jobject) {

    std::lock_guard<std::mutex> lock(coreMutex);
    if (localLinkActive.load(std::memory_order_acquire)) {
        return static_cast<jint>(mPLATFORM_GBA);
    }
    if (!core) {
        return static_cast<jint>(mPLATFORM_NONE);
    }
    return static_cast<jint>(core->platform(core));
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_retra_emulator_MainActivity_runFrameNoVideo(
        JNIEnv*,
        jobject) {

    std::lock_guard<std::mutex> lock(coreMutex);

    // Speed changes are disabled while Local Link is active.  Its two native
    // cores already advance on their own worker threads, so there is no single
    // core frame to step here.
    if (localLinkActive.load(std::memory_order_acquire)) {
        return JNI_TRUE;
    }
    if (!core) {
        return JNI_FALSE;
    }

    const uint32_t heldKeys = keyMask.load(std::memory_order_relaxed);
    const uint32_t tappedKeys = keyPressLatch.exchange(0, std::memory_order_relaxed);
    core->setKeys(core, heldKeys | tappedKeys);
    syncGbaMosaicRenderer(core);
    core->runFrame(core);
    captureRewindSnapshotLocked();
    return JNI_TRUE;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_retra_emulator_MainActivity_runTurboSlice(
        JNIEnv* env,
        jobject,
        jintArray outputPixels,
        jint frameCount,
        jboolean captureVideo,
        jboolean discardAudio) {

    std::lock_guard<std::mutex> lock(coreMutex);

    // Fast-forward is disabled during Local Link. Treat a stray call as a
    // successful no-op rather than fighting the two dedicated link workers.
    if (localLinkActive.load(std::memory_order_acquire)) {
        return JNI_TRUE;
    }
    if (!core) {
        return JNI_FALSE;
    }

    // Smooth turbo batches. Android keeps 1/2/4/8-frame slices for
    // 2x/4x/8x/16x even under quality fallback so fresh visual states are not
    // accidentally halved. The native cap remains defensive only. Input is
    // sampled every emulated frame, so controls remain responsive.
    const int frames = std::max(1, std::min(16, static_cast<int>(frameCount)));
    for (int i = 0; i < frames; ++i) {
        // Sample held and edge-latched input on every emulated frame so batching
        // JNI calls never makes 8x/16x controls less responsive.
        const uint32_t heldKeys = keyMask.load(std::memory_order_relaxed);
        const uint32_t tappedKeys = keyPressLatch.exchange(0, std::memory_order_relaxed);
        core->setKeys(core, heldKeys | tappedKeys);
        // The installed GBA video-register hook already applies Retra's mosaic
        // policy whenever the game writes MOSAIC, and changing the setting
        // explicitly resynchronizes it. Re-writing that renderer register on
        // every hidden 8x/16x frame is redundant hot-path work.
        core->runFrame(core);
    }
    captureRewindSnapshotLocked();

    if (discardAudio == JNI_TRUE && core->getAudioBuffer) {
        // Extreme turbo intentionally has no audible PCM. Drain mGBA's audio ring
        // under the same native lock/JNI call as the core batch, bypassing Retra's
        // 24-tap resampler and conditioner and avoiding a second JNI transition.
        if (mAudioBuffer* audio = core->getAudioBuffer(core)) {
            const size_t available = mAudioBufferAvailable(audio);
            if (available) mAudioBufferRead(audio, nullptr, available);
        }
        resetRetraAudioResamplerLocked();
    }

    if (captureVideo != JNI_TRUE) {
        return JNI_TRUE;
    }
    if (!outputPixels) {
        return JNI_FALSE;
    }

    const unsigned width = videoWidth;
    const unsigned height = videoHeight;
    const size_t requiredPixels = static_cast<size_t>(width) * static_cast<size_t>(height);
    if (width == 0 || height == 0 ||
        static_cast<size_t>(env->GetArrayLength(outputPixels)) < requiredPixels) {
        return JNI_FALSE;
    }

    // Convert only the final frame in the turbo slice. The hidden frames above
    // never cross JNI as pixels, which is the key rendering win at 8x/16x.
    jint* destination = static_cast<jint*>(
            env->GetPrimitiveArrayCritical(outputPixels, nullptr));
    if (!destination) {
        return JNI_FALSE;
    }

    for (unsigned y = 0; y < height; ++y) {
        const mColor* sourceRow = videoBuffer + static_cast<size_t>(y) * VIDEO_STRIDE;
        jint* destinationRow = destination + static_cast<size_t>(y) * width;
        for (unsigned x = 0; x < width; ++x) {
#ifndef COLOR_16_BIT
            const uint32_t color = static_cast<uint32_t>(sourceRow[x]);
            const uint32_t red = (color & 0x000000FFu) << 16;
            const uint32_t green = color & 0x0000FF00u;
            const uint32_t blue = (color & 0x00FF0000u) >> 16;
            destinationRow[x] = static_cast<jint>(
                    0xFF000000u | red | green | blue
            );
#else
            const uint32_t color = static_cast<uint32_t>(sourceRow[x]);
#ifdef COLOR_5_6_5
            const uint32_t red = ((color >> 11) & 0x1Fu) * 255u / 31u;
            const uint32_t green = ((color >> 5) & 0x3Fu) * 255u / 63u;
            const uint32_t blue = (color & 0x1Fu) * 255u / 31u;
#else
            const uint32_t red = (color & 0x1Fu) * 255u / 31u;
            const uint32_t green = ((color >> 5) & 0x1Fu) * 255u / 31u;
            const uint32_t blue = ((color >> 10) & 0x1Fu) * 255u / 31u;
#endif
            destinationRow[x] = static_cast<jint>(
                    0xFF000000u | (red << 16) | (green << 8) | blue
            );
#endif
        }
    }

    env->ReleasePrimitiveArrayCritical(outputPixels, destination, 0);
    return JNI_TRUE;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_retra_emulator_MainActivity_runFrame(
        JNIEnv* env,
        jobject,
        jintArray outputPixels) {

    std::lock_guard<std::mutex> lock(coreMutex);

    const mColor* sourceBuffer = nullptr;
    unsigned width = 0;
    unsigned height = 0;
    std::unique_lock<std::mutex> localFrameLock;

    if (localLinkActive.load(std::memory_order_acquire)) {
        const int playerIndex = activeLocalPlayer.load(std::memory_order_relaxed);
        auto& player = localPlayers[playerIndex];
        localFrameLock = std::unique_lock<std::mutex>(player.frameMutex);
        width = player.width;
        height = player.height;
        sourceBuffer = player.snapshot;
    } else {
        if (!core) {
            return JNI_FALSE;
        }
        const uint32_t heldKeys = keyMask.load(std::memory_order_relaxed);
        const uint32_t tappedKeys = keyPressLatch.exchange(0, std::memory_order_relaxed);
        core->setKeys(core, heldKeys | tappedKeys);
        syncGbaMosaicRenderer(core);
        core->runFrame(core);
        captureRewindSnapshotLocked();
        width = videoWidth;
        height = videoHeight;
        sourceBuffer = videoBuffer;
    }

    const size_t requiredPixels =
            static_cast<size_t>(width) * static_cast<size_t>(height);
    if (width == 0 || height == 0 ||
        static_cast<size_t>(env->GetArrayLength(outputPixels)) < requiredPixels) {
        return JNI_FALSE;
    }

    // Pin the Java framebuffer only for the short pixel-conversion phase.
    // core->runFrame() has already completed above, so the critical section is
    // brief and avoids a possible temporary JNI array copy every frame.
    jint* destination = static_cast<jint*>(
            env->GetPrimitiveArrayCritical(outputPixels, nullptr));
    if (!destination) {
        return JNI_FALSE;
    }

    for (unsigned y = 0; y < height; ++y) {
        const mColor* sourceRow = sourceBuffer + static_cast<size_t>(y) * VIDEO_STRIDE;
        jint* destinationRow = destination + static_cast<size_t>(y) * width;
        for (unsigned x = 0; x < width; ++x) {
#ifndef COLOR_16_BIT
            const uint32_t color = static_cast<uint32_t>(sourceRow[x]);
            const uint32_t red = (color & 0x000000FFu) << 16;
            const uint32_t green = color & 0x0000FF00u;
            const uint32_t blue = (color & 0x00FF0000u) >> 16;
            destinationRow[x] = static_cast<jint>(
                    0xFF000000u | red | green | blue
            );
#else
            const uint32_t color = static_cast<uint32_t>(sourceRow[x]);
#ifdef COLOR_5_6_5
            const uint32_t red = ((color >> 11) & 0x1Fu) * 255u / 31u;
            const uint32_t green = ((color >> 5) & 0x3Fu) * 255u / 63u;
            const uint32_t blue = (color & 0x1Fu) * 255u / 31u;
#else
            const uint32_t red = (color & 0x1Fu) * 255u / 31u;
            const uint32_t green = ((color >> 5) & 0x1Fu) * 255u / 31u;
            const uint32_t blue = ((color >> 10) & 0x1Fu) * 255u / 31u;
#endif
            destinationRow[x] = static_cast<jint>(
                    0xFF000000u | (red << 16) | (green << 8) | blue
            );
#endif
        }
    }

    env->ReleasePrimitiveArrayCritical(outputPixels, destination, 0);
    return JNI_TRUE;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_retra_emulator_MainActivity_setKey(
        JNIEnv*,
        jobject,
        jint key,
        jboolean pressed) {

    if (key < 0 || key > 9) {
        return;
    }

    const uint32_t bit = 1u << static_cast<uint32_t>(key);
    std::atomic<uint32_t>* target = &keyMask;
    std::atomic<uint32_t>* pressLatch = &keyPressLatch;
    if (localLinkActive.load(std::memory_order_acquire)) {
        const int playerIndex = activeLocalPlayer.load(std::memory_order_relaxed);
        target = &localPlayers[playerIndex].keys;
        pressLatch = &localPlayers[playerIndex].keyPressLatch;
    }
    if (pressed) {
        target->fetch_or(bit, std::memory_order_relaxed);
        pressLatch->fetch_or(bit, std::memory_order_relaxed);
    } else {
        target->fetch_and(~bit, std::memory_order_relaxed);
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_retra_emulator_MainActivity_clearKeyPressLatches(
        JNIEnv*,
        jobject) {
    keyPressLatch.store(0, std::memory_order_relaxed);
    for (auto& player : localPlayers) {
        player.keyPressLatch.store(0, std::memory_order_relaxed);
    }
}

static bool writeBytesToFile(const char* path, const void* data, size_t size) {
    if (!path || !data || size == 0) {
        return false;
    }

    FILE* file = std::fopen(path, "wb");
    if (!file) {
        return false;
    }

    const size_t written = std::fwrite(data, 1, size, file);
    const bool flushed = std::fflush(file) == 0;
    const bool closed = std::fclose(file) == 0;
    return written == size && flushed && closed;
}

static bool readBytesFromFile(const char* path, std::vector<uint8_t>& out) {
    if (!path) {
        return false;
    }

    FILE* file = std::fopen(path, "rb");
    if (!file) {
        return false;
    }

    if (std::fseek(file, 0, SEEK_END) != 0) {
        std::fclose(file);
        return false;
    }

    const long fileSize = std::ftell(file);
    if (fileSize <= 0 || fileSize > 64L * 1024L * 1024L) {
        std::fclose(file);
        return false;
    }

    if (std::fseek(file, 0, SEEK_SET) != 0) {
        std::fclose(file);
        return false;
    }

    out.resize(static_cast<size_t>(fileSize));
    const size_t read = std::fread(out.data(), 1, out.size(), file);
    const bool closed = std::fclose(file) == 0;
    return read == out.size() && closed;
}

static bool saveStateLocked(const char* statePath) {
    if (!core || !statePath) {
        return false;
    }

    // Save to an in-memory VFile first. This avoids Android/filesystem mmap
    // differences that can make mCoreSaveStateNamed fail on a directly-opened
    // O_WRONLY state file. It also preserves mGBA's metadata/RTC/cheat extras.
    VFile* memoryFile = VFileMemChunk(nullptr, 0);
    if (memoryFile) {
        bool written = false;
        const bool serialized = mCoreSaveStateNamed(core, memoryFile, SAVESTATE_ALL);
        if (serialized) {
            const ssize_t size = memoryFile->size(memoryFile);
            if (size > 0 && size <= 64 * 1024 * 1024) {
                std::vector<uint8_t> bytes(static_cast<size_t>(size));
                memoryFile->seek(memoryFile, 0, SEEK_SET);
                const ssize_t read = memoryFile->read(memoryFile, bytes.data(), bytes.size());
                if (read == size) {
                    written = writeBytesToFile(statePath, bytes.data(), bytes.size());
                }
            }
        }
        memoryFile->close(memoryFile);
        if (written) {
            return true;
        }
    }

    // Fallback: raw core state. This keeps Quick Save functional even if the
    // current embedded mGBA build cannot serialize optional extdata.
    const size_t stateSize = core->stateSize(core);
    if (stateSize == 0 || stateSize > 64 * 1024 * 1024) {
        return false;
    }

    std::vector<uint8_t> rawState(stateSize);
    core->saveState(core, rawState.data());
    return writeBytesToFile(statePath, rawState.data(), rawState.size());
}

static bool loadStateLocked(const char* statePath) {
    if (!core || !statePath) {
        return false;
    }

    std::vector<uint8_t> bytes;
    if (!readBytesFromFile(statePath, bytes)) {
        return false;
    }

    // First load normal mGBA state containers produced by saveStateLocked.
    VFile* memoryFile = VFileFromConstMemory(bytes.data(), bytes.size());
    if (memoryFile) {
        const bool loaded = mCoreLoadStateNamed(
                core,
                memoryFile,
                SAVESTATE_ALL & ~SAVESTATE_SAVEDATA
        );
        memoryFile->close(memoryFile);
        if (loaded) {
            return true;
        }
    }

    // Raw-state fallback for states written when optional mGBA serialization
    // was unavailable. Only accept the exact core state size.
    const size_t stateSize = core->stateSize(core);
    if (bytes.size() != stateSize) {
        return false;
    }
    return core->loadState(core, bytes.data());
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_retra_emulator_MainActivity_rewindSeconds(
        JNIEnv*,
        jobject,
        jint seconds) {
    std::lock_guard<std::mutex> lock(coreMutex);
    return rewindBySecondsLocked(static_cast<int>(seconds)) ? JNI_TRUE : JNI_FALSE;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_retra_emulator_MainActivity_getRewindAvailableSeconds(
        JNIEnv*,
        jobject) {
    std::lock_guard<std::mutex> lock(coreMutex);
    return static_cast<jint>(rewindAvailableSecondsLocked());
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_retra_emulator_MainActivity_quickSaveState(
        JNIEnv* env,
        jobject,
        jstring path) {

    const char* statePath = env->GetStringUTFChars(path, nullptr);
    if (!statePath) {
        return JNI_FALSE;
    }

    std::lock_guard<std::mutex> lock(coreMutex);
    const bool saved = !localLinkActive.load(std::memory_order_acquire) &&
            saveStateLocked(statePath);
    env->ReleaseStringUTFChars(path, statePath);
    return saved ? JNI_TRUE : JNI_FALSE;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_retra_emulator_MainActivity_quickLoadState(
        JNIEnv* env,
        jobject,
        jstring path) {

    const char* statePath = env->GetStringUTFChars(path, nullptr);
    if (!statePath) {
        return JNI_FALSE;
    }

    std::lock_guard<std::mutex> lock(coreMutex);
    const bool loaded = !localLinkActive.load(std::memory_order_acquire) &&
            loadStateLocked(statePath);
    if (loaded) clearRewindLocked();
    env->ReleaseStringUTFChars(path, statePath);
    return loaded ? JNI_TRUE : JNI_FALSE;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_retra_emulator_MainActivity_readAudioSamples(
        JNIEnv* env,
        jobject,
        jshortArray output) {
    if (!output) return 0;
    std::lock_guard<std::mutex> lock(coreMutex);

    mCore* audioCore = core;
    if (localLinkActive.load(std::memory_order_acquire)) {
        const int playerIndex = activeLocalPlayer.load(std::memory_order_relaxed);
        if (playerIndex >= 0 && playerIndex < LOCAL_LINK_PLAYERS) {
            audioCore = localPlayers[playerIndex].core;
        }
    }
    if (!audioCore || !audioCore->getAudioBuffer || !audioCore->audioSampleRate) return 0;

    struct mAudioBuffer* source = audioCore->getAudioBuffer(audioCore);
    if (!source) return 0;
    const unsigned sourceRate = audioCore->audioSampleRate(audioCore);
    const unsigned destinationRate = requestedAudioOutputRateLocked();

    // Convert from the core's actual hardware-rate PCM into the exact Android
    // stream clock with Retra's saturating band-limited resampler. This keeps
    // pitch/timing correct without allowing loud sinc overshoots to wrap int16.
    const jsize shortCapacity = env->GetArrayLength(output);
    if (shortCapacity < 2) return 0;
    const size_t maxFrames = static_cast<size_t>(shortCapacity) / 2;

    jshort* samples = env->GetShortArrayElements(output, nullptr);
    if (!samples) return 0;
    const size_t produced = resampleRetraAudioLocked(
            source,
            sourceRate,
            destinationRate,
            reinterpret_cast<int16_t*>(samples),
            maxFrames);
    if (produced > 0) {
        conditionRetraAudioLocked(
                source,
                destinationRate,
                reinterpret_cast<int16_t*>(samples),
                produced);
    }
    env->ReleaseShortArrayElements(output, samples, 0);
    return static_cast<jint>(produced * 2);
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_retra_emulator_MainActivity_readAudioSamplesAtSpeed(
        JNIEnv* env,
        jobject,
        jshortArray output,
        jdouble speed) {
    if (!output) return 0;
    std::lock_guard<std::mutex> lock(coreMutex);

    mCore* audioCore = core;
    if (localLinkActive.load(std::memory_order_acquire)) {
        const int playerIndex = activeLocalPlayer.load(std::memory_order_relaxed);
        if (playerIndex >= 0 && playerIndex < LOCAL_LINK_PLAYERS) {
            audioCore = localPlayers[playerIndex].core;
        }
    }
    if (!audioCore || !audioCore->getAudioBuffer || !audioCore->audioSampleRate) return 0;

    mAudioBuffer* source = audioCore->getAudioBuffer(audioCore);
    if (!source) return 0;
    const unsigned sourceRate = audioCore->audioSampleRate(audioCore);
    const unsigned destinationRate = requestedAudioOutputRateLocked();
    const double normalizedSpeed = std::max(1.0, std::min(16.0, static_cast<double>(speed)));

    const jsize shortCapacity = env->GetArrayLength(output);
    if (shortCapacity < 2) return 0;
    const size_t maxFrames = static_cast<size_t>(shortCapacity) / 2;

    jshort* samples = env->GetShortArrayElements(output, nullptr);
    if (!samples) return 0;
    // One speed-aware FIR pass performs sample-rate conversion and fast-forward
    // time compression together. At 8x/16x this generates only the PCM Android
    // will actually play, avoiding the old resample-then-discard workload.
    const size_t produced = resampleRetraAudioLocked(
            source,
            sourceRate,
            destinationRate,
            reinterpret_cast<int16_t*>(samples),
            maxFrames,
            normalizedSpeed);
    if (produced > 0) {
        conditionRetraAudioLocked(
                source,
                destinationRate,
                reinterpret_cast<int16_t*>(samples),
                produced);
    }
    env->ReleaseShortArrayElements(output, samples, 0);
    return static_cast<jint>(produced * 2);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_retra_emulator_MainActivity_setCoreConfigOption(
        JNIEnv* env,
        jobject,
        jstring keyString,
        jstring valueString) {
    const char* key = env->GetStringUTFChars(keyString, nullptr);
    const char* value = env->GetStringUTFChars(valueString, nullptr);
    if (!key || !value) {
        if (key) env->ReleaseStringUTFChars(keyString, key);
        if (value) env->ReleaseStringUTFChars(valueString, value);
        return;
    }

    std::lock_guard<std::mutex> lock(coreMutex);
    const std::string keyValue(key);
    const std::string optionValue(value);
    runtimeConfigOptions[keyValue] = optionValue;

    if (keyValue == "retra.cartridgeSaveType") {
        // Safe for the normal single-core path because this JNI call owns
        // coreMutex. Linked threaded cores pick the option up when created.
        if (core && !localLinkActive.load(std::memory_order_acquire)) {
            applyGbaSaveTypeOverride(core);
        }
    } else if (keyValue == "retra.mosaicEffect") {
        retraMosaicEffectEnabled.store(
                optionValue == "1" || optionValue == "true" || optionValue == "on",
                std::memory_order_relaxed);
        if (core) syncGbaMosaicRenderer(core);
    } else if (keyValue == "retra.syncCheckLevel") {
        try {
            retraSyncCheckLevel.store(std::max(0, std::min(10, std::stoi(optionValue))), std::memory_order_relaxed);
        } catch (...) {
            retraSyncCheckLevel.store(4, std::memory_order_relaxed);
        }
    }

    if (core && configInitialized && !isRetraPrivateConfigKey(keyValue)) {
        mCoreConfigSetOverrideValue(&core->config, key, value);
        mCoreLoadConfig(core);
        core->opts.videoSync = false;
        core->opts.audioSync = false;
    }

    env->ReleaseStringUTFChars(keyString, key);
    env->ReleaseStringUTFChars(valueString, value);
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_retra_emulator_MainActivity_resetCore(
        JNIEnv*,
        jobject) {

    std::lock_guard<std::mutex> lock(coreMutex);
    if (localLinkActive.load(std::memory_order_acquire)) {
        clearLocalLinkInputScheduleLocked();
        clearLinkCheckpointsLocked();
        for (auto& player : localPlayers) {
            player.keys.store(
                    localLinkSinglePakActive.load(std::memory_order_relaxed) && player.playerId == 1
                        ? SINGLE_PAK_BOOT_KEYS
                        : 0,
                    std::memory_order_relaxed);
            player.keyPressLatch.store(0, std::memory_order_relaxed);
            player.frameNumber.store(0, std::memory_order_relaxed);
            if (player.threadStarted && player.thread.impl) {
                mCoreThreadReset(&player.thread);
            }
        }
        return JNI_TRUE;
    }
    if (!core) {
        return JNI_FALSE;
    }

    keyMask.store(0, std::memory_order_relaxed);
    keyPressLatch.store(0, std::memory_order_relaxed);
    clearRewindLocked();
    core->reset(core);
    // Reapply manual choices after Reset. In Automatic mode this handler is
    // non-destructive and keeps mGBA's Pokémon ROM-hack save selection intact.
    applyGbaSaveTypeOverride(core);
    syncGbaMosaicRenderer(core);
    return JNI_TRUE;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_retra_emulator_MainActivity_clearNativeCheats(
        JNIEnv*,
        jobject) {

    std::lock_guard<std::mutex> lock(coreMutex);
    if (localLinkActive.load(std::memory_order_acquire) || !core) {
        return JNI_FALSE;
    }

    struct mCheatDevice* device = core->cheatDevice(core);
    if (!device) {
        return JNI_FALSE;
    }

    clearCheatDeviceLocked(device);
    return JNI_TRUE;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_retra_emulator_MainActivity_addNativeCheat(
        JNIEnv* env,
        jobject,
        jstring nameString,
        jstring codeString,
        jint requestedType,
        jboolean enabled) {

    const char* name = env->GetStringUTFChars(nameString, nullptr);
    if (!name) {
        return JNI_FALSE;
    }

    const char* code = env->GetStringUTFChars(codeString, nullptr);
    if (!code) {
        env->ReleaseStringUTFChars(nameString, name);
        return JNI_FALSE;
    }

    std::vector<std::string> rawLines;
    {
        std::istringstream stream(code);
        std::string line;
        while (std::getline(stream, line)) {
            rawLines.push_back(line);
        }
    }

    bool success = false;
    {
        std::lock_guard<std::mutex> lock(coreMutex);
        if (core && !localLinkActive.load(std::memory_order_acquire)) {
            struct mCheatDevice* device = core->cheatDevice(core);
            if (device && device->createSet) {
                const int requested = static_cast<int>(requestedType);
                const int primaryType = mapCheatTypeForCore(requested);
                success = addCheatSetWithTypeLocked(
                        device,
                        name,
                        rawLines,
                        primaryType,
                        requested,
                        enabled == JNI_TRUE);

                // mGBA's autodetection is intentionally conservative. If the
                // user chose Auto detect and it cannot classify a pasted code,
                // retry the supported formats without changing an explicitly
                // selected type. This makes common GameShark/AR/CodeBreaker
                // lists work reliably while preserving type semantics.
                if (!success && requested == 0) {
                    const auto platform = core->platform(core);
                    if (platform == mPLATFORM_GBA) {
                        const int fallbackTypes[][2] = {
                                {GBA_CHEAT_GAMESHARK, 1},
                                {GBA_CHEAT_PRO_ACTION_REPLAY, 2},
                                {GBA_CHEAT_CODEBREAKER, 3},
                                {GBA_CHEAT_VBA, 4}
                        };
                        for (const auto& candidate : fallbackTypes) {
                            if (addCheatSetWithTypeLocked(device, name, rawLines, candidate[0], candidate[1], enabled == JNI_TRUE)) {
                                success = true;
                                break;
                            }
                        }
                    } else if (platform == mPLATFORM_GB) {
                        const int fallbackTypes[][2] = {{GB_CHEAT_GAMESHARK, 1}, {GB_CHEAT_VBA, 4}};
                        for (const auto& candidate : fallbackTypes) {
                            if (addCheatSetWithTypeLocked(device, name, rawLines, candidate[0], candidate[1], enabled == JNI_TRUE)) {
                                success = true;
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    env->ReleaseStringUTFChars(codeString, code);
    env->ReleaseStringUTFChars(nameString, name);
    return success ? JNI_TRUE : JNI_FALSE;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_retra_emulator_MainActivity_shutdownCore(
        JNIEnv*,
        jobject) {

    std::lock_guard<std::mutex> lock(coreMutex);
    destroyLocalLinkLocked();
    destroyCoreLocked();
}
