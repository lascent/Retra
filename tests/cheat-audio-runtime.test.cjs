const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const read = (p) => fs.readFileSync(path.join(root, p), 'utf8');

const session = read('app/src/main/java/com/retra/emulator/EmulationSessionManager.kt');
const audio = read('app/src/main/java/com/retra/emulator/AudioController.kt');
const settings = read('app/src/main/java/com/retra/emulator/SettingsController.kt');
const gameplay = read('app/src/main/java/com/retra/emulator/GameplayController.kt');
const native = read('app/src/main/cpp/native-lib.cpp');

test('gameplay actively drains mGBA PCM and speed-syncs it into AudioTrack', () => {
  assert.match(session, /audioController\.pump\(speed = speed, flushOutput = true\)/);
  assert.match(audio, /readSamples\(nativeScratch\)/);
  assert.match(audio, /Thread\(::audioWriterLoop, "Retra-Audio"\)/);
  assert.match(audio, /Process\.THREAD_PRIORITY_AUDIO/);
  assert.match(audio, /AudioTrack\.WRITE_BLOCKING/);
  assert.match(audio, /flushPendingOutput\(\)[\s\S]*enqueueOutput\(packet\)/);
  assert.match(audio, /readSamples\(nativeScratch\)/);
  assert.match(audio, /appendSpeedAdjusted\(nativeScratch, count\)/);
  assert.match(audio, /appendTurboAveraged/);
  assert.match(audio, /appendSlowInterpolated/);
  assert.match(audio, /readSamplesAtSpeed\(nativeScratch, normalizedSpeed\)/);
});

test('audio pipeline prevents crackle from dropped partial writes and smooths speed transitions', () => {
  assert.match(audio, /PREBUFFER_MS = 32/);
  assert.match(audio, /TRACK_BUFFER_MS = 80/);
  assert.match(audio, /safeUnderrunCount/);
  assert.match(audio, /track\.flush\(\)/);
  assert.match(audio, /requestFadeIn\(\)/);
  assert.match(audio, /applyFadeIn\(pendingOutput, appendedFrom, pendingCount - appendedFrom\)/);
  assert.doesNotMatch(audio, /setPerformanceMode\(AudioTrack\.PERFORMANCE_MODE_LOW_LATENCY\)/);
  assert.match(audio, /ERROR_DEAD_OBJECT/);
  assert.match(audio, /MAX_PENDING_AUDIO_MS = 200/);
});

test('native PCM is resampled from the real mGBA hardware rate before Android playback', () => {
  assert.match(native, /#include <mgba-util\/audio-buffer\.h>/);
  assert.match(native, /audioCore->audioSampleRate\(audioCore\)/);
  assert.match(native, /resampleRetraAudioLocked/);
  assert.match(native, /retraNormalizedSinc/);
  assert.match(native, /cutoff = ratio < 1\.0/);
  assert.match(native, /mAudioBufferPeek\(source, 0/);
  assert.match(native, /mAudioBufferRead\(source, nullptr, drop\)/);
});

test('resampler saturates loud transients instead of allowing int16 wrap distortion', () => {
  assert.match(native, /sample >= 32767\.0[\s\S]*return 32767/);
  assert.match(native, /sample <= -32768\.0[\s\S]*return -32768/);
  assert.match(native, /weightSum[\s\S]*coefficient \/ weightSum/);
  assert.doesNotMatch(native, /mINTERPOLATOR_SINC|mAudioResamplerProcess/);
});

test('resampler state follows dynamic game audio rates and is reset when cores change', () => {
  assert.match(native, /retraAudioResampler\.source != source/);
  assert.match(native, /retraAudioResampler\.sourceRate != sourceRate/);
  assert.match(native, /retraAudioResampler\.destinationRate != destinationRate/);
  assert.match(native, /resetRetraAudioResamplerLocked\(\)/);
});

test('audio volume is applied once and sound settings rebuild the output track', () => {
  assert.match(settings, /setCoreConfigOption\("volume", "256"\)/);
  assert.match(settings, /audioController\.setVolume\(volume\)/);
  assert.match(settings, /key == "enableSound" \|\| key == "soundFrequency"/);
  assert.match(settings, /audioController\.configure\(romLoaded\)/);
});

test('GameplayController imports the global cheat preference constant', () => {
  assert.match(gameplay, /import com\.retra\.emulator\.MainActivity\.Companion\.ENABLE_CHEATS_PREF/);
});

test('global cheat setting immediately controls the running core', () => {
  assert.match(gameplay, /prefs\.getBoolean\(ENABLE_CHEATS_PREF, true\)/);
  assert.match(settings, /key == "enableCheats" && romLoaded && !localLinkActive/);
  assert.match(settings, /applyStoredCheatsToCore\(\)/);
  assert.match(gameplay, /if \(!clearNativeCheats\(\)\) return false/);
});

test('cheat parser accepts compact pasted codes and has auto-detect fallbacks', () => {
  assert.match(native, /normalizeCheatLineForParser/);
  assert.match(native, /compact\.size\(\) == 12 \|\| compact\.size\(\) == 16/);
  assert.match(native, /GBA_CHEAT_GAMESHARK/);
  assert.match(native, /GBA_CHEAT_PRO_ACTION_REPLAY/);
  assert.match(native, /GBA_CHEAT_CODEBREAKER/);
  assert.match(native, /GBA_CHEAT_VBA/);
  assert.match(gameplay, /normalizeCheatCode\(draft\.code\)/);
});


test('cheat editor keeps typed name, code and type readable in all appearance modes', () => {
  assert.match(gameplay, /Use a Retra-owned dialog instead of the platform AlertDialog input/);
  assert.match(gameplay, /setTextColor\(Color\.rgb\(242, 247, 247\)\)/);
  assert.match(gameplay, /setColor\(Color\.rgb\(31, 37, 38\)\)/);
  assert.match(gameplay, /setTextColor\(Color\.rgb\(238, 244, 244\)\)/);
  assert.match(gameplay, /text = if \(selected\) "✓" else ""/);
});

test('disabling a cheat synchronizes persisted, UI and native mGBA state', () => {
  assert.match(gameplay, /val previous = cheat\.enabled/);
  assert.match(gameplay, /if \(!applied\)/);
  assert.match(gameplay, /isChecked = previous/);
  assert.match(gameplay, /if \(!clearNativeCheats\(\)\) return false/);
  assert.match(native, /set->enabled = false;/);
  assert.match(native, /mCheatRefresh\(device, set\);/);
  assert.match(native, /mCheatRemoveSet\(device, set\);/);
});

test('cheat multiline input uses the Android TextView setter that compiles in Kotlin', () => {
  assert.match(gameplay, /setHorizontallyScrolling\(false\)/);
  assert.doesNotMatch(gameplay, /isHorizontallyScrolling\s*=\s*false/);
});
