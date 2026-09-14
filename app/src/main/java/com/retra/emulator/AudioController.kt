package com.retra.emulator

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Process
import java.util.ArrayDeque
import kotlin.math.max

/**
 * Owns Android audio output for the native emulator core.
 *
 * Audio is deliberately split into two stages:
 *  - the emulation thread only drains/resamples mGBA PCM and enqueues it,
 *  - a dedicated THREAD_PRIORITY_AUDIO writer feeds AudioTrack continuously.
 *
 * Keeping potentially blocking AudioTrack work off the emulation thread lets us
 * use stable blocking writes, a short pre-buffer and underrun recovery without
 * making gameplay pacing hitch. This is substantially less prone to static,
 * crackle and tiny silence gaps than one non-blocking write per video frame.
 */
internal class AudioController(
    private val isEnabled: () -> Boolean,
    private val sampleRate: () -> Int,
    private val volume: () -> Float,
    private val readSamples: (ShortArray) -> Int,
    private val onOutputRateChanged: (Int) -> Unit
) {
    @Volatile
    private var audioTrack: AudioTrack? = null

    @Volatile
    private var romActive = false

    @Volatile
    private var playRequested = false

    @Volatile
    private var writerRunning = false

    @Volatile
    private var turboMuted = false

    @Volatile
    private var outputResetSerial = 0

    private var writerThread: Thread? = null
    private var configuredRate = 44100

    // Keep the high-quality mGBA mixer setting separate from the physical
    // Android output clock. Most Android devices mix at 48 kHz. When Retra's
    // high-quality 44.1 kHz setting is selected we output at the device-native
    // clock when it is sane, letting mGBA's sinc resampler do the one explicit
    // conversion instead of asking AudioFlinger to resample the stream again.
    @Volatile
    private var adaptivePrebufferMs = BASE_PREBUFFER_MS

    @Volatile
    private var adaptiveTrackBufferMs = BASE_TRACK_BUFFER_MS

    @Volatile
    private var lastUnderrunNs = 0L

    @Volatile
    private var lastBufferRelaxNs = 0L

    // mGBA's native audio ring is configured for 8192 stereo frames. This
    // scratch array drains it in bounded chunks during audible-speed playback.
    private val nativeScratch = ShortArray(8192)

    // The emulation thread transforms into this staging area. flushPendingOutput
    // moves complete PCM blocks into the writer queue instead of touching
    // AudioTrack directly.
    private var pendingOutput = ShortArray(16384)
    private var pendingOffset = 0
    private var pendingCount = 0

    // The audio writer owns playback timing. Queue state is protected by one
    // monitor so producer work remains a very short copy + notify operation.
    private val outputLock = Object()
    private val outputQueue = ArrayDeque<ShortArray>()
    private var queuedOutputShorts = 0

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
    // AudioTrack recreation, an underrun recovery, or an abrupt speed change.
    @Volatile
    private var fadeFramesRemaining = 0

    @Volatile
    private var fadeFramesTotal = 0

    fun configure(romLoaded: Boolean) {
        romActive = romLoaded
        playRequested = false
        turboMuted = false
        stopWriterThread()
        releaseTrackOnly()
        clearPipelineState()
        clearOutputQueue()
        resetAdaptiveBuffering()
        if (!romLoaded || !isEnabled()) return

        if (buildTrack()) {
            startWriterThread()
        }
    }

    private fun buildTrack(): Boolean {
        if (!romActive || !isEnabled()) return false

        val requestedRate = sampleRate().coerceIn(8000, 96000)
        val rate = chooseAndroidOutputRate(requestedRate)
        configuredRate = rate
        val minBytes = AudioTrack.getMinBufferSize(
            rate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBytes <= 0) return false

        // Allocate enough capacity for adaptive recovery, but keep the active
        // AudioTrack buffer at the normal target until an actual underrun asks
        // for more headroom. Capacity alone does not force extra startup delay.
        val stableBytes = rate * BYTES_PER_STEREO_FRAME * MAX_TRACK_BUFFER_MS / 1000
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
                // v1.0.1 intentionally did not force PERFORMANCE_MODE_LOW_LATENCY.
                // Several Android audio HALs become more stable at high emulator
                // speed when AudioTrack is allowed to use its normal mixer path.
                .setBufferSizeInBytes(bufferBytes)
                .build()

            if (track.state != AudioTrack.STATE_INITIALIZED) {
                try { track.release() } catch (_: Exception) {}
                false
            } else {
                try { track.setVolume(volume().coerceIn(0f, 1f)) } catch (_: Exception) {}
                applyAdaptiveTrackBuffer(track)
                audioTrack = track
                try { onOutputRateChanged(track.sampleRate.coerceIn(8000, 96000)) } catch (_: Throwable) {}
                requestFadeIn()
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
        if (!romActive || !isEnabled()) return

        var track = audioTrack
        if (track == null || track.state != AudioTrack.STATE_INITIALIZED) {
            stopWriterThread()
            releaseTrackOnly()
            if (!buildTrack()) return
            startWriterThread()
            track = audioTrack
        } else if (!writerRunning) {
            startWriterThread()
        }
        if (track == null) return

        playRequested = true
        requestFadeIn()
        synchronized(outputLock) {
            outputLock.notifyAll()
        }
    }

    fun pauseAndFlush() {
        playRequested = false
        requestOutputReset(clearQueuedAudio = true)

        // Stopping playback immediately prevents the tail of an old frame from
        // leaking through menus/save-state operations. The dedicated writer is
        // left alive and will re-prime cleanly on the next play().
        try { audioTrack?.pause() } catch (_: Exception) {}
        try { audioTrack?.flush() } catch (_: Exception) {}

        clearPipelineState()
        discardNativeBuffer(maxChunks = 8)
        requestFadeIn()
    }


    /**
     * Emergency throughput fallback for a device/ROM that cannot sustain its
     * selected extreme-turbo speed even after renderer adaptation. Normal 8x/16x
     * now remains audible through the native speed-aware FIR path.
     */
    fun setTurboMuted(muted: Boolean) {
        if (turboMuted == muted) return
        turboMuted = muted
        pendingOffset = 0
        pendingCount = 0
        clearOutputQueue()
        requestOutputReset(clearQueuedAudio = true)
        if (muted) {
            try { audioTrack?.pause() } catch (_: Exception) {}
            try { audioTrack?.flush() } catch (_: Exception) {}
        } else {
            requestFadeIn()
        }
    }

    /**
     * Drain PCM generated by mGBA after one emulated core frame and transform
     * it to the selected game speed. For turbo, several core frames are run per
     * wall-clock frame; callers can defer queueing until the final turbo
     * sub-frame so 8x/16x still produces a sensible writer block size.
     */
    fun pump(speed: Double, flushOutput: Boolean = true) {
        if (turboMuted) return
        val normalizedSpeed = normalizeSpeed(speed)
        prepareTransform(normalizedSpeed)

        var chunks = 0
        while (chunks < 8) {
            // Retra v1.0.1 audio reference: always drain the normal mGBA PCM
            // stream, then perform the speed transform with the lightweight
            // integer accumulator below. This avoids the newer speed-aware
            // 24-tap FIR changing turbo tone/texture between ROMs and restores
            // the older audio character while keeping the dedicated writer.
            val shortCount = try {
                readSamples(nativeScratch)
            } catch (_: Throwable) {
                0
            }
            if (shortCount <= 0) break

            val boundedCount = shortCount.coerceAtMost(nativeScratch.size)
            val count = boundedCount - (boundedCount % CHANNEL_COUNT)
            if (count > 0 && isEnabled() && romActive) {
                if (pendingOffset > 0) compactPendingOutput()
                val appendedFrom = pendingCount
                // v1.0.1 transformed all non-normal speeds in the Android audio
                // stage: integer turbo ratios are averaged, slow motion is linearly
                // interpolated, and 1x remains a raw copy.
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
            clearOutputQueue()
            return
        }

        if (flushOutput) {
            // The old loop naturally emitted about one base-GBA-frame worth of
            // audible PCM per packet. With the newer ~120+ Hz visual slices,
            // accumulate to the same ~16 ms packet size before queueing turbo
            // audio so it stays steady instead of becoming many tiny writes.
            if (normalizedSpeed > 1.0) flushPendingOutputIfReady(TURBO_PACKET_MS)
            else flushPendingOutput()
        }
    }

    private fun flushPendingOutputIfReady(minimumMs: Int) {
        val available = (pendingCount - pendingOffset).coerceAtLeast(0)
        val minimumShorts =
            (configuredRate * CHANNEL_COUNT * minimumMs / 1000).coerceAtLeast(CHANNEL_COUNT)
        if (available >= minimumShorts) flushPendingOutput()
    }

    /**
     * Publish transformed PCM to the dedicated writer. This method never calls
     * AudioTrack and therefore cannot block the emulation frame loop.
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
            clearOutputQueue()
            return
        }

        val available = pendingCount - pendingOffset
        val alignedCount = available - (available % CHANNEL_COUNT)
        if (alignedCount <= 0) {
            pendingOffset = 0
            pendingCount = 0
            return
        }

        val packet = pendingOutput.copyOfRange(pendingOffset, pendingOffset + alignedCount)
        pendingOffset += alignedCount
        if (pendingOffset >= pendingCount) {
            pendingOffset = 0
            pendingCount = 0
        } else {
            compactPendingOutput()
        }

        enqueueOutput(packet)
    }

    private fun enqueueOutput(packet: ShortArray) {
        if (packet.isEmpty()) return

        val maxQueuedShorts = maxQueuedShorts()
        var data = packet
        if (data.size > maxQueuedShorts) {
            var keep = maxQueuedShorts
            keep -= keep % CHANNEL_COUNT
            data = data.copyOfRange(data.size - keep, data.size)
        }

        synchronized(outputLock) {
            // A backlog this large means the device/route stopped consuming
            // audio. Keeping stale PCM would sound delayed and can create a
            // burst when the route returns, so retain only fresh audio and make
            // the writer re-prime from a clean boundary.
            if (queuedOutputShorts + data.size > maxQueuedShorts) {
                outputQueue.clear()
                queuedOutputShorts = 0
                outputResetSerial++
                requestFadeIn()
            }

            outputQueue.addLast(data)
            queuedOutputShorts += data.size
            outputLock.notifyAll()
        }
    }

    private fun startWriterThread() {
        if (writerRunning || !romActive || !isEnabled()) return
        writerRunning = true
        writerThread = Thread(::audioWriterLoop, "Retra-Audio").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    private fun stopWriterThread() {
        writerRunning = false
        playRequested = false
        synchronized(outputLock) {
            outputLock.notifyAll()
        }

        // A blocking stream write is allowed only on this worker. Pausing the
        // track wakes/shortens an in-flight write on devices that would
        // otherwise wait for playback space.
        try { audioTrack?.pause() } catch (_: Exception) {}

        writerThread?.let { thread ->
            try { thread.interrupt() } catch (_: Exception) {}
            try { thread.join(400L) } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        writerThread = null
    }

    private fun audioWriterLoop() {
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        } catch (_: Throwable) {}

        var seenResetSerial = outputResetSerial
        var playbackStarted = false
        var lastUnderrunCount = 0

        while (writerRunning) {
            if (!romActive || !isEnabled() || !playRequested) {
                if (playbackStarted) {
                    try { audioTrack?.pause() } catch (_: Exception) {}
                    playbackStarted = false
                }
                waitForOutput(20L)
                continue
            }

            if (seenResetSerial != outputResetSerial) {
                try { audioTrack?.pause() } catch (_: Exception) {}
                try { audioTrack?.flush() } catch (_: Exception) {}
                playbackStarted = false
                seenResetSerial = outputResetSerial
                lastUnderrunCount = safeUnderrunCount(audioTrack)
            }

            var track = audioTrack
            if (track == null || track.state != AudioTrack.STATE_INITIALIZED) {
                releaseTrackOnly()
                if (!buildTrack()) {
                    waitForOutput(20L)
                    continue
                }
                track = audioTrack
                playbackStarted = false
                seenResetSerial = outputResetSerial
            }
            if (track == null) continue

            if (!playbackStarted) {
                if (!waitForPrebuffer(seenResetSerial)) continue

                val targetShorts = prebufferShorts()
                var primedShorts = 0
                var primeFailed = false

                // Pre-fill AudioTrack while paused. PREBUFFER_MS is kept below
                // TRACK_BUFFER_MS, so blocking writes here have room to finish
                // before playback starts.
                while (writerRunning && playRequested && primedShorts < targetShorts) {
                    if (seenResetSerial != outputResetSerial) {
                        primeFailed = true
                        break
                    }
                    val packet = pollOutput() ?: break
                    val needed = (targetShorts - primedShorts).coerceAtLeast(CHANNEL_COUNT)
                    var primeCount = minOf(packet.size, needed)
                    primeCount -= primeCount % CHANNEL_COUNT
                    if (primeCount <= 0) {
                        prependOutput(packet)
                        break
                    }

                    val primePacket = if (primeCount == packet.size) {
                        packet
                    } else {
                        packet.copyOfRange(0, primeCount)
                    }
                    if (!writeFully(track, primePacket)) {
                        primeFailed = true
                        break
                    }
                    primedShorts += primeCount

                    // A 0.2x frame can contain more PCM than the small startup
                    // pre-buffer target. Keep its unwritten tail queued rather
                    // than trying to fill a paused AudioTrack beyond the target.
                    if (primeCount < packet.size) {
                        prependOutput(packet.copyOfRange(primeCount, packet.size))
                    }
                }

                if (primeFailed || primedShorts <= 0) {
                    recoverWriterTrack()
                    playbackStarted = false
                    seenResetSerial = outputResetSerial
                    continue
                }

                try {
                    track.play()
                    playbackStarted = true
                    lastUnderrunCount = safeUnderrunCount(track)
                } catch (_: Exception) {
                    recoverWriterTrack()
                    playbackStarted = false
                }
                continue
            }

            val packet = takeOutput(20L)
            if (packet == null) {
                val underruns = safeUnderrunCount(track)
                if (underruns > lastUnderrunCount) {
                    noteUnderrun(track)
                    // Once Android has actually starved, do not resume by
                    // dropping a fresh block into an empty playing stream. A
                    // pause/flush/re-prime avoids the sharp edge heard as a
                    // click/static burst on slower devices and Bluetooth routes.
                    requestFadeIn()
                    try { track.pause() } catch (_: Exception) {}
                    try { track.flush() } catch (_: Exception) {}
                    playbackStarted = false
                    lastUnderrunCount = underruns
                }
                continue
            }

            val underrunsBeforeWrite = safeUnderrunCount(track)
            if (underrunsBeforeWrite > lastUnderrunCount) {
                noteUnderrun(track)
                fadePacketHead(packet)
                prependOutput(packet)
                requestFadeIn()
                try { track.pause() } catch (_: Exception) {}
                try { track.flush() } catch (_: Exception) {}
                playbackStarted = false
                lastUnderrunCount = underrunsBeforeWrite
                continue
            }

            if (!writeFully(track, packet)) {
                recoverWriterTrack()
                playbackStarted = false
                seenResetSerial = outputResetSerial
                continue
            }

            lastUnderrunCount = safeUnderrunCount(track)
            relaxAdaptiveBufferingIfStable(track)
        }
    }

    private fun writeFully(track: AudioTrack, data: ShortArray): Boolean {
        var offset = 0
        while (writerRunning && playRequested && offset < data.size) {
            val written = try {
                track.write(
                    data,
                    offset,
                    data.size - offset,
                    AudioTrack.WRITE_BLOCKING
                )
            } catch (_: Exception) {
                AudioTrack.ERROR_INVALID_OPERATION
            }

            when {
                written > 0 -> offset += written
                written == AudioTrack.ERROR_DEAD_OBJECT -> return false
                written < 0 -> return false
                else -> Thread.yield()
            }
        }
        return offset >= data.size
    }


    private fun fadePacketHead(packet: ShortArray) {
        if (packet.size < CHANNEL_COUNT) return
        val fadeFrames = minOf(packet.size / CHANNEL_COUNT, max(1, configuredRate / 200))
        var frame = 0
        var i = 0
        while (frame < fadeFrames && i + 1 < packet.size) {
            val step = frame + 1
            packet[i] = ((packet[i].toInt() * step) / fadeFrames).toShort()
            packet[i + 1] = ((packet[i + 1].toInt() * step) / fadeFrames).toShort()
            frame++
            i += CHANNEL_COUNT
        }
    }

    private fun recoverWriterTrack() {
        requestFadeIn()
        try { audioTrack?.pause() } catch (_: Exception) {}
        try { audioTrack?.flush() } catch (_: Exception) {}
        releaseTrackOnly()
        if (writerRunning && romActive && isEnabled() && playRequested) {
            buildTrack()
        }
    }

    private fun waitForPrebuffer(expectedResetSerial: Int): Boolean {
        val targetShorts = prebufferShorts()
        synchronized(outputLock) {
            while (
                writerRunning &&
                playRequested &&
                romActive &&
                isEnabled() &&
                expectedResetSerial == outputResetSerial &&
                queuedOutputShorts < targetShorts
            ) {
                try {
                    outputLock.wait(10L)
                } catch (_: InterruptedException) {
                    if (!writerRunning) return false
                }
            }
            return writerRunning &&
                playRequested &&
                expectedResetSerial == outputResetSerial &&
                queuedOutputShorts > 0
        }
    }

    private fun waitForOutput(timeoutMs: Long) {
        synchronized(outputLock) {
            try {
                outputLock.wait(timeoutMs)
            } catch (_: InterruptedException) {
                // stopWriterThread() uses interrupt only as a wake-up signal.
            }
        }
    }

    private fun takeOutput(timeoutMs: Long): ShortArray? {
        synchronized(outputLock) {
            if (outputQueue.isEmpty() && writerRunning && playRequested) {
                try {
                    outputLock.wait(timeoutMs)
                } catch (_: InterruptedException) {
                    if (!writerRunning) return null
                }
            }
            return pollOutputLocked()
        }
    }

    private fun pollOutput(): ShortArray? = synchronized(outputLock) {
        pollOutputLocked()
    }

    private fun pollOutputLocked(): ShortArray? {
        val packet = outputQueue.pollFirst() ?: return null
        queuedOutputShorts = (queuedOutputShorts - packet.size).coerceAtLeast(0)
        return packet
    }

    private fun prependOutput(packet: ShortArray) {
        synchronized(outputLock) {
            outputQueue.addFirst(packet)
            queuedOutputShorts += packet.size
            outputLock.notifyAll()
        }
    }

    private fun clearOutputQueue() {
        synchronized(outputLock) {
            outputQueue.clear()
            queuedOutputShorts = 0
            outputLock.notifyAll()
        }
    }

    private fun requestOutputReset(clearQueuedAudio: Boolean) {
        synchronized(outputLock) {
            if (clearQueuedAudio) {
                outputQueue.clear()
                queuedOutputShorts = 0
            }
            outputResetSerial++
            outputLock.notifyAll()
        }
    }

    private fun safeUnderrunCount(track: AudioTrack?): Int = try {
        track?.underrunCount ?: 0
    } catch (_: Throwable) {
        0
    }

    private fun prebufferShorts(): Int =
        (configuredRate * CHANNEL_COUNT * adaptivePrebufferMs / 1000)
            .coerceAtLeast(CHANNEL_COUNT)

    private fun maxQueuedShorts(): Int =
        (configuredRate * CHANNEL_COUNT * MAX_PENDING_AUDIO_MS / 1000)
            .coerceAtLeast(CHANNEL_COUNT)

    private fun chooseAndroidOutputRate(requestedRate: Int): Int {
        if (requestedRate != HIGH_QUALITY_SOURCE_RATE) return requestedRate
        val nativeRate = try {
            AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC)
        } catch (_: Throwable) {
            requestedRate
        }
        // Only use a normal high-quality device clock. Weird/legacy route
        // reports fall back to the exact Retra setting.
        return if (nativeRate in MIN_NATIVE_HQ_RATE..MAX_NATIVE_HQ_RATE) {
            nativeRate
        } else {
            requestedRate
        }
    }

    private fun resetAdaptiveBuffering() {
        adaptivePrebufferMs = BASE_PREBUFFER_MS
        adaptiveTrackBufferMs = BASE_TRACK_BUFFER_MS
        val now = System.nanoTime()
        lastUnderrunNs = 0L
        lastBufferRelaxNs = now
    }

    private fun applyAdaptiveTrackBuffer(track: AudioTrack) {
        try {
            val capacity = track.bufferCapacityInFrames.coerceAtLeast(1)
            val requestedFrames =
                (configuredRate * adaptiveTrackBufferMs / 1000).coerceAtLeast(1)
            track.setBufferSizeInFrames(requestedFrames.coerceAtMost(capacity))
        } catch (_: Throwable) {
            // Some vendor audio HALs reject runtime sizing. The constructor
            // capacity above remains a safe fallback in that case.
        }
    }

    private fun noteUnderrun(track: AudioTrack) {
        val now = System.nanoTime()
        lastUnderrunNs = now
        lastBufferRelaxNs = now
        adaptivePrebufferMs =
            (adaptivePrebufferMs + PREBUFFER_GROW_STEP_MS).coerceAtMost(MAX_PREBUFFER_MS)
        adaptiveTrackBufferMs =
            (adaptiveTrackBufferMs + TRACK_GROW_STEP_MS).coerceAtMost(MAX_TRACK_BUFFER_MS)
        applyAdaptiveTrackBuffer(track)
    }

    private fun relaxAdaptiveBufferingIfStable(track: AudioTrack) {
        val now = System.nanoTime()
        val lastProblem = lastUnderrunNs
        if (lastProblem != 0L && now - lastProblem < STABLE_RELAX_AFTER_NS) return
        if (now - lastBufferRelaxNs < STABLE_RELAX_AFTER_NS) return

        val newPrebuffer =
            (adaptivePrebufferMs - PREBUFFER_RELAX_STEP_MS).coerceAtLeast(BASE_PREBUFFER_MS)
        val newTrackBuffer =
            (adaptiveTrackBufferMs - TRACK_RELAX_STEP_MS).coerceAtLeast(BASE_TRACK_BUFFER_MS)
        val changed = newPrebuffer != adaptivePrebufferMs || newTrackBuffer != adaptiveTrackBufferMs
        adaptivePrebufferMs = newPrebuffer
        adaptiveTrackBufferMs = newTrackBuffer
        lastBufferRelaxNs = now
        if (changed) applyAdaptiveTrackBuffer(track)
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
     * This small anti-alias filter sounds significantly cleaner than plain
     * sample dropping while keeping turbo processing lightweight.
     */
    private fun appendTurboAveraged(input: ShortArray, count: Int, factor: Int) {
        var i = 0
        while (i + 1 < count) {
            turboAccumLeft += input[i].toInt()
            turboAccumRight += input[i + 1].toInt()
            turboAccumFrames++

            if (turboAccumFrames >= factor) {
                appendStereoFrame(
                    (turboAccumLeft / turboAccumFrames)
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                        .toShort(),
                    (turboAccumRight / turboAccumFrames)
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                        .toShort()
                )
                turboAccumLeft = 0
                turboAccumRight = 0
                turboAccumFrames = 0
            }
            i += CHANNEL_COUNT
        }
    }

    /** Expand 0.5x/0.2x audio with continuous stereo linear interpolation. */
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
            i += CHANNEL_COUNT
        }
    }

    private fun appendStereoFrame(left: Short, right: Short) {
        ensurePendingCapacity(pendingCount + CHANNEL_COUNT)
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
        val maxQueuedShorts = maxQueuedShorts()
        val queued = pendingCount - pendingOffset
        if (queued <= maxQueuedShorts) return

        // This staging backlog can only grow when an unusual core/device emits
        // much more PCM than expected. Keep the freshest complete stereo frames
        // and fade the retained stream so there is no hard waveform edge.
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
            // Never mix PCM from two time scales. Flush queued/AudioTrack data,
            // reset the resampler state and fade in the new rate cleanly.
            turboAccumLeft = 0
            turboAccumRight = 0
            turboAccumFrames = 0
            slowHasPrevious = false
            pendingOffset = 0
            pendingCount = 0
            transformMode = newMode
            transformFactor = newFactor
            requestOutputReset(clearQueuedAudio = true)
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
        fadeFramesTotal = max(1, configuredRate / 200) // ~5 ms
        fadeFramesRemaining = fadeFramesTotal
    }

    private fun applyFadeIn(samples: ShortArray, start: Int, count: Int) {
        if (fadeFramesRemaining <= 0 || fadeFramesTotal <= 0 || count <= 0) return
        var i = start.coerceAtLeast(0)
        val end = (start + count).coerceAtMost(samples.size)
        while (i + 1 < end && fadeFramesRemaining > 0) {
            val remaining = fadeFramesRemaining
            val total = fadeFramesTotal.coerceAtLeast(1)
            val progressed = total - remaining + 1
            samples[i] = ((samples[i].toInt() * progressed) / total).toShort()
            samples[i + 1] = ((samples[i + 1].toInt() * progressed) / total).toShort()
            fadeFramesRemaining = remaining - 1
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
        playRequested = false
        stopWriterThread()
        clearPipelineState()
        clearOutputQueue()
        releaseTrackOnly()
    }

    private companion object {
        const val BYTES_PER_STEREO_FRAME = 4
        const val CHANNEL_COUNT = 2
        const val HIGH_QUALITY_SOURCE_RATE = 44100
        const val MIN_NATIVE_HQ_RATE = 44100
        const val MAX_NATIVE_HQ_RATE = 96000

        // Legacy names remain as the documented baseline; adaptive logic only
        // grows above them after a real device underrun.
        const val TRACK_BUFFER_MS = 80
        const val PREBUFFER_MS = 32
        const val BASE_TRACK_BUFFER_MS = TRACK_BUFFER_MS
        const val MAX_TRACK_BUFFER_MS = 112
        const val TRACK_GROW_STEP_MS = 16
        const val TRACK_RELAX_STEP_MS = 8

        const val BASE_PREBUFFER_MS = PREBUFFER_MS
        const val MAX_PREBUFFER_MS = 64
        const val PREBUFFER_GROW_STEP_MS = 8
        const val PREBUFFER_RELAX_STEP_MS = 4
        const val STABLE_RELAX_AFTER_NS = 30_000_000_000L

        const val MAX_PENDING_AUDIO_MS = 200
        const val TURBO_PACKET_MS = 16
        const val MODE_NORMAL = 0
        const val MODE_TURBO = 1
        const val MODE_SLOW = 2
    }
}
