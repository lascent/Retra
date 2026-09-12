package com.retra.emulator

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.max

/**
 * Owns Android audio output for the native emulator core.
 *
 * Design goals:
 *  - never drop the unwritten tail of a partial AudioTrack write (a common
 *    source of audible clicks/crackle),
 *  - keep audio duration locked to the selected emulation speed,
 *  - low-pass integer turbo ratios before decimation to reduce aliasing,
 *  - interpolate slow-motion audio instead of repeating hard-edged blocks,
 *  - keep the native mGBA audio ring drained even when sound is disabled.
 */
internal class AudioController(
    private val isEnabled: () -> Boolean,
    private val sampleRate: () -> Int,
    private val volume: () -> Float,
    private val readSamples: (ShortArray) -> Int
) {
    @Volatile
    private var audioTrack: AudioTrack? = null

    @Volatile
    private var romActive = false

    private var configuredRate = 44100

    // mGBA's native audio ring is configured for 4096 stereo frames. This
    // scratch array can therefore drain the whole ring in one JNI read.
    private val nativeScratch = ShortArray(8192)

    // One wall-clock frame of transformed audio is normally small, even at
    // 0.2x. This grows only if an unusual device/core combination needs more.
    private var pendingOutput = ShortArray(16384)
    private var pendingOffset = 0
    private var pendingCount = 0

    // Current speed-transform state. Integer turbo ratios keep an averaging
    // accumulator across core-frame boundaries, preventing timing drift and
    // reducing high-frequency aliasing. Slow-motion keeps the previous stereo
    // frame so interpolation remains continuous between pump calls.
    private var transformMode = MODE_NORMAL
    private var transformFactor = 1
    private var turboAccumLeft = 0
    private var turboAccumRight = 0
    private var turboAccumFrames = 0
    private var slowPreviousLeft = 0
    private var slowPreviousRight = 0
    private var slowHasPrevious = false

    // A very short fade-in suppresses discontinuity clicks after pause/resume,
    // AudioTrack recreation, or an abrupt speed-mode change.
    private var fadeFramesRemaining = 0
    private var fadeFramesTotal = 0

    fun configure(romLoaded: Boolean) {
        romActive = romLoaded
        releaseTrackOnly()
        clearPipelineState()
        if (!romLoaded || !isEnabled()) return
        buildTrack()
    }

    private fun buildTrack(): Boolean {
        if (!romActive || !isEnabled()) return false

        val rate = sampleRate().coerceIn(8000, 96000)
        configuredRate = rate
        val minBytes = AudioTrack.getMinBufferSize(
            rate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBytes <= 0) return false

        // Keep enough headroom for scheduler jitter without letting audio
        // buffering add unnecessary game latency. The writer is non-blocking,
        // so the emulator thread never waits for AudioTrack playback.
        val stableBytes = rate / 20 * BYTES_PER_STEREO_FRAME // ~50 ms
        val bufferBytes = max(minBytes * 2, stableBytes)

        return try {
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(rate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(bufferBytes)
                .build()

            if (track.state != AudioTrack.STATE_INITIALIZED) {
                try { track.release() } catch (_: Exception) {}
                false
            } else {
                try { track.setVolume(volume().coerceIn(0f, 1f)) } catch (_: Exception) {}
                audioTrack = track
                requestFadeIn()
                try { track.play() } catch (_: Exception) {}
                true
            }
        } catch (_: Exception) {
            releaseTrackOnly()
            false
        }
    }

    fun setVolume(value: Float) {
        try { audioTrack?.setVolume(value.coerceIn(0f, 1f)) } catch (_: Exception) {}
    }

    fun play() {
        var track = audioTrack
        if ((track == null || track.state != AudioTrack.STATE_INITIALIZED) && romActive && isEnabled()) {
            if (!buildTrack()) return
            track = audioTrack
        }
        if (track == null) return

        try {
            if (track.state == AudioTrack.STATE_INITIALIZED && track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                requestFadeIn()
                track.play()
            }
        } catch (_: Exception) {}
    }

    fun pauseAndFlush() {
        try { audioTrack?.pause() } catch (_: Exception) {}
        try { audioTrack?.flush() } catch (_: Exception) {}
        clearPipelineState()
        discardNativeBuffer(maxChunks = 8)
        requestFadeIn()
    }

    /**
     * Drain PCM generated by mGBA after one emulated core frame and transform
     * it to the selected game speed. For turbo, several core frames are run per
     * wall-clock frame; callers can defer the Android write until the final
     * core frame so 8x/16x does not issue hundreds of tiny AudioTrack writes.
     */
    fun pump(speed: Double, flushOutput: Boolean = true) {
        val normalizedSpeed = normalizeSpeed(speed)
        prepareTransform(normalizedSpeed)

        var chunks = 0
        while (chunks < 8) {
            val shortCount = try { readSamples(nativeScratch) } catch (_: Throwable) { 0 }
            if (shortCount <= 0) break

            val boundedCount = shortCount.coerceAtMost(nativeScratch.size)
            val count = boundedCount - (boundedCount % 2)
            if (count > 0 && isEnabled() && romActive) {
                if (pendingOffset > 0) compactPendingOutput()
                val appendedFrom = pendingCount
                appendSpeedAdjusted(nativeScratch, count)
                applyFadeIn(pendingOutput, appendedFrom, pendingCount - appendedFrom)
                trimAudioBacklogIfNeeded()
            }

            chunks++
            if (shortCount < nativeScratch.size) break
        }

        if (!isEnabled() || !romActive) {
            pendingOffset = 0
            pendingCount = 0
            return
        }

        if (flushOutput) flushPendingOutput()
    }

    /**
     * Offer queued PCM to AudioTrack without ever blocking the emulation loop.
     * Partial writes keep their unwritten tail for the next frame rather than
     * discarding it, which preserves the crackle protection of the old path
     * while removing AudioTrack scheduling stalls from gameplay pacing.
     */
    fun flushPendingOutput() {
        if (pendingCount <= pendingOffset) {
            pendingOffset = 0
            pendingCount = 0
            return
        }
        if (!isEnabled() || !romActive) {
            pendingOffset = 0
            pendingCount = 0
            return
        }

        var track = audioTrack
        if (track == null || track.state != AudioTrack.STATE_INITIALIZED) {
            if (!buildTrack()) {
                pendingOffset = 0
                pendingCount = 0
                return
            }
            track = audioTrack
        }
        if (track == null) {
            pendingOffset = 0
            pendingCount = 0
            return
        }

        val written = try {
            track.write(
                pendingOutput,
                pendingOffset,
                pendingCount - pendingOffset,
                AudioTrack.WRITE_NON_BLOCKING
            )
        } catch (_: Exception) {
            AudioTrack.ERROR_INVALID_OPERATION
        }

        when {
            written > 0 -> {
                pendingOffset += written
                if (pendingOffset >= pendingCount) {
                    pendingOffset = 0
                    pendingCount = 0
                } else if (pendingOffset >= pendingOutput.size / 2) {
                    compactPendingOutput()
                }
            }
            written == AudioTrack.ERROR_DEAD_OBJECT -> {
                // Route changes can invalidate the track. Drop stale queued
                // audio, recreate on the next pump, and fade the new stream in.
                pendingOffset = 0
                pendingCount = 0
                releaseTrackOnly()
                requestFadeIn()
            }
            written < 0 -> {
                // Avoid retaining a permanently invalid block forever. A later
                // pump can rebuild the track if needed.
                pendingOffset = 0
                pendingCount = 0
            }
            // written == 0 means Android's buffer is momentarily full. Keep the
            // entire unwritten tail and retry next frame without blocking.
        }
    }

    private fun appendSpeedAdjusted(input: ShortArray, count: Int) {
        when (transformMode) {
            MODE_NORMAL -> appendRaw(input, count)
            MODE_TURBO -> appendTurboAveraged(input, count, transformFactor)
            MODE_SLOW -> appendSlowInterpolated(input, count, transformFactor)
        }
    }

    private fun appendRaw(input: ShortArray, count: Int) {
        ensurePendingCapacity(pendingCount + count)
        System.arraycopy(input, 0, pendingOutput, pendingCount, count)
        pendingCount += count
    }

    /**
     * Downsample an integer fast-forward ratio by averaging each source block.
     * This is intentionally a tiny box low-pass filter: it substantially
     * reduces the harsh aliasing/crackle that plain sample dropping produces.
     */
    private fun appendTurboAveraged(input: ShortArray, count: Int, factor: Int) {
        var i = 0
        while (i + 1 < count) {
            turboAccumLeft += input[i].toInt()
            turboAccumRight += input[i + 1].toInt()
            turboAccumFrames++

            if (turboAccumFrames >= factor) {
                appendStereoFrame(
                    (turboAccumLeft / turboAccumFrames).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort(),
                    (turboAccumRight / turboAccumFrames).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                )
                turboAccumLeft = 0
                turboAccumRight = 0
                turboAccumFrames = 0
            }
            i += 2
        }
    }

    /** Expand 0.5x/0.2x audio with stereo linear interpolation. */
    private fun appendSlowInterpolated(input: ShortArray, count: Int, factor: Int) {
        var i = 0
        while (i + 1 < count) {
            val currentLeft = input[i].toInt()
            val currentRight = input[i + 1].toInt()

            if (!slowHasPrevious) {
                slowPreviousLeft = currentLeft
                slowPreviousRight = currentRight
                slowHasPrevious = true
                repeat(factor) {
                    appendStereoFrame(currentLeft.toShort(), currentRight.toShort())
                }
            } else {
                val startLeft = slowPreviousLeft
                val startRight = slowPreviousRight
                for (step in 1..factor) {
                    val left = startLeft + ((currentLeft - startLeft) * step) / factor
                    val right = startRight + ((currentRight - startRight) * step) / factor
                    appendStereoFrame(left.toShort(), right.toShort())
                }
                slowPreviousLeft = currentLeft
                slowPreviousRight = currentRight
            }
            i += 2
        }
    }

    private fun appendStereoFrame(left: Short, right: Short) {
        ensurePendingCapacity(pendingCount + 2)
        pendingOutput[pendingCount++] = left
        pendingOutput[pendingCount++] = right
    }

    private fun ensurePendingCapacity(required: Int) {
        if (required <= pendingOutput.size) return
        val additional = (required - pendingCount).coerceAtLeast(0)
        if (pendingOffset > 0) {
            compactPendingOutput()
        }
        val adjustedRequired = pendingCount + additional
        if (adjustedRequired <= pendingOutput.size) return
        var newSize = pendingOutput.size
        while (newSize < adjustedRequired) newSize *= 2
        pendingOutput = pendingOutput.copyOf(newSize)
    }

    private fun compactPendingOutput() {
        if (pendingOffset <= 0) return
        val remaining = (pendingCount - pendingOffset).coerceAtLeast(0)
        if (remaining > 0) {
            System.arraycopy(pendingOutput, pendingOffset, pendingOutput, 0, remaining)
        }
        pendingOffset = 0
        pendingCount = remaining
    }

    private fun trimAudioBacklogIfNeeded() {
        val maxQueuedShorts = (configuredRate * CHANNEL_COUNT * MAX_PENDING_AUDIO_MS / 1000)
            .coerceAtLeast(CHANNEL_COUNT)
        val queued = pendingCount - pendingOffset
        if (queued <= maxQueuedShorts) return

        // If the output device stalls for too long (for example during a
        // Bluetooth route transition), keeping hundreds of milliseconds of old
        // PCM would make controls feel delayed. Drop only the oldest complete
        // stereo frames and fade the retained stream to hide the discontinuity.
        var drop = queued - maxQueuedShorts
        drop -= drop % CHANNEL_COUNT
        pendingOffset += drop
        compactPendingOutput()
        requestFadeIn()
    }

    private fun prepareTransform(speed: Double) {
        val newMode: Int
        val newFactor: Int
        when {
            speed >= 1.5 -> {
                newMode = MODE_TURBO
                newFactor = speed.toInt().coerceIn(2, 16)
            }
            speed <= 0.75 -> {
                newMode = MODE_SLOW
                newFactor = if (speed <= 0.25) 5 else 2
            }
            else -> {
                newMode = MODE_NORMAL
                newFactor = 1
            }
        }

        if (newMode != transformMode || newFactor != transformFactor) {
            // Finish no partial resampling block from the old rate. A 4 ms fade
            // masks the unavoidable waveform discontinuity at the speed switch.
            turboAccumLeft = 0
            turboAccumRight = 0
            turboAccumFrames = 0
            slowHasPrevious = false
            pendingOffset = 0
            pendingCount = 0
            transformMode = newMode
            transformFactor = newFactor
            requestFadeIn()
        }
    }

    private fun normalizeSpeed(value: Double): Double = when {
        value <= 0.35 -> 0.2
        value <= 0.75 -> 0.5
        value < 1.5 -> 1.0
        value < 3.0 -> 2.0
        value < 6.0 -> 4.0
        value < 12.0 -> 8.0
        else -> 16.0
    }

    private fun requestFadeIn() {
        fadeFramesTotal = max(1, configuredRate / 250) // ~4 ms
        fadeFramesRemaining = fadeFramesTotal
    }

    private fun applyFadeIn(samples: ShortArray, start: Int, count: Int) {
        if (fadeFramesRemaining <= 0 || fadeFramesTotal <= 0 || count <= 0) return
        var i = start.coerceAtLeast(0)
        val end = (start + count).coerceAtMost(samples.size)
        while (i + 1 < end && fadeFramesRemaining > 0) {
            val progressed = fadeFramesTotal - fadeFramesRemaining + 1
            samples[i] = ((samples[i].toInt() * progressed) / fadeFramesTotal).toShort()
            samples[i + 1] = ((samples[i + 1].toInt() * progressed) / fadeFramesTotal).toShort()
            fadeFramesRemaining--
            i += CHANNEL_COUNT
        }
    }

    private fun discardNativeBuffer(maxChunks: Int) {
        var chunks = 0
        while (chunks < maxChunks) {
            val count = try { readSamples(nativeScratch) } catch (_: Throwable) { 0 }
            if (count <= 0) break
            chunks++
            if (count < nativeScratch.size) break
        }
    }

    private fun clearPipelineState() {
        pendingOffset = 0
        pendingCount = 0
        turboAccumLeft = 0
        turboAccumRight = 0
        turboAccumFrames = 0
        slowHasPrevious = false
        transformMode = MODE_NORMAL
        transformFactor = 1
    }

    private fun releaseTrackOnly() {
        val track = audioTrack
        audioTrack = null
        if (track != null) {
            try { track.pause() } catch (_: Exception) {}
            try { track.flush() } catch (_: Exception) {}
            try { track.release() } catch (_: Exception) {}
        }
    }

    fun release() {
        romActive = false
        clearPipelineState()
        releaseTrackOnly()
    }

    private companion object {
        const val BYTES_PER_STEREO_FRAME = 4
        const val CHANNEL_COUNT = 2
        const val MAX_PENDING_AUDIO_MS = 200
        const val MODE_NORMAL = 0
        const val MODE_TURBO = 1
        const val MODE_SLOW = 2
    }
}
