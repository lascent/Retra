package com.retra.emulator

import android.app.Activity
import android.os.SystemClock
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

/**
 * Network packet/timing layer for Retra Remote Link v2.
 *
 * Socket setup and ROM/save pairing remain in the gameplay coordinator. This
 * class owns the long-lived packet streams, adaptive input delay, heartbeat,
 * deterministic checkpoint verification and host-authoritative resync loop.
 */
class RemoteLinkTransport(
    private val activity: Activity,
    private val hooks: NativeHooks
) {
    data class NativeHooks(
        val getFrame: () -> Long,
        val getFrameSkew: () -> Long,
        val getLatestCheckpoint: () -> LongArray,
        val getCheckpointHash: (Long) -> Long,
        val exportSnapshot: () -> ByteArray?,
        val importSnapshot: (ByteArray) -> Boolean,
        val setPaused: (Boolean) -> Unit,
        val setKeyMask: (player: Int, mask: Int) -> Boolean,
        val scheduleKeyMask: (player: Int, frame: Long, mask: Int) -> Boolean,
        val clearInputSchedule: () -> Unit,
        val onDisconnect: (String) -> Unit,
        val onStopEmulation: () -> Unit,
        val onResumeEmulation: () -> Unit
    )

    @Volatile var isActive: Boolean = false
        private set
    @Volatile var role: Int = 0
        private set
    @Volatile var inputDelayFrames: Long = BASE_INPUT_DELAY_FRAMES
        private set
    @Volatile var rttMs: Long = 0L
        private set
    @Volatile var jitterMs: Long = 0L
        private set
    @Volatile var localSuspended: Boolean = false
        private set
    @Volatile var peerSuspended: Boolean = false
        private set
    @Volatile var resyncInProgress: Boolean = false
        private set

    var transportName: String = ""
        private set

    @Volatile private var localInputMask = 0
    @Volatile private var lastReceiveAtMs = 0L
    @Volatile private var lastHeartbeatAtMs = 0L
    @Volatile private var lastPingAtMs = 0L
    @Volatile private var lastHashSentFrame = 0L
    @Volatile private var lastResyncAtMs = 0L
    @Volatile private var resyncStartedAtMs = 0L
    @Volatile private var resyncAttempts = 0
    @Volatile var successfulResyncs: Int = 0
        private set
    @Volatile var resyncTimeouts: Int = 0
        private set
    @Volatile var lateInputPackets: Int = 0
        private set
    @Volatile private var packetsReceived: Long = 0L
    @Volatile private var packetsSent: Long = 0L
    @Volatile private var previousRttSampleMs: Long = 0L
    @Volatile private var peerFrame = 0L
    @Volatile private var peerCheckpointFrame = 0L
    @Volatile private var peerCheckpointHash = 0L

    private var input: DataInputStream? = null
    private var output: DataOutputStream? = null
    private var readerThread: Thread? = null
    private var monitorThread: Thread? = null
    private var connectionCloser: (() -> Unit)? = null
    private val writeLock = Any()
    private val disconnectNotified = AtomicBoolean(false)

    data class HealthSnapshot(
        val transport: String,
        val quality: String,
        val rttMs: Long,
        val jitterMs: Long,
        val inputDelayFrames: Long,
        val successfulResyncs: Int,
        val resyncTimeouts: Int,
        val lateInputPackets: Int,
        val packetsReceived: Long,
        val packetsSent: Long
    )

    val canRunEmulation: Boolean
        get() = !isActive || (!localSuspended && !peerSuspended && !resyncInProgress)

    fun healthSnapshot(): HealthSnapshot = HealthSnapshot(
        transport = transportName,
        quality = qualityLabel(),
        rttMs = rttMs,
        jitterMs = jitterMs,
        inputDelayFrames = inputDelayFrames,
        successfulResyncs = successfulResyncs,
        resyncTimeouts = resyncTimeouts,
        lateInputPackets = lateInputPackets,
        packetsReceived = packetsReceived,
        packetsSent = packetsSent
    )

    private fun qualityLabel(): String = when {
        !isActive -> "Disconnected"
        rttMs <= 0L -> "Connecting"
        rttMs <= 80L && jitterMs <= 12L -> "Excellent"
        rttMs <= 160L && jitterMs <= 25L -> "Good"
        rttMs <= 260L && jitterMs <= 45L -> "Fair"
        else -> "Poor"
    }

    fun attach(
        role: Int,
        transportName: String,
        input: DataInputStream,
        output: DataOutputStream,
        closer: () -> Unit
    ) {
        close(sendDisconnect = false)
        this.role = role
        this.transportName = transportName
        this.input = input
        this.output = output
        this.connectionCloser = closer
        this.localInputMask = 0
        this.inputDelayFrames = BASE_INPUT_DELAY_FRAMES
        this.lastReceiveAtMs = SystemClock.elapsedRealtime()
        this.lastHeartbeatAtMs = 0L
        this.lastPingAtMs = 0L
        this.lastHashSentFrame = 0L
        this.lastResyncAtMs = 0L
        this.resyncStartedAtMs = 0L
        this.resyncAttempts = 0
        this.successfulResyncs = 0
        this.resyncTimeouts = 0
        this.lateInputPackets = 0
        this.packetsReceived = 0L
        this.packetsSent = 0L
        this.previousRttSampleMs = 0L
        this.peerFrame = 0L
        this.peerCheckpointFrame = 0L
        this.peerCheckpointHash = 0L
        this.rttMs = 0L
        this.jitterMs = 0L
        this.disconnectNotified.set(false)
        this.localSuspended = false
        this.peerSuspended = false
        this.resyncInProgress = false
        this.isActive = true
        startReader()
        startMonitor()
    }

    fun handleGameplayKey(key: Int, pressed: Boolean): Boolean {
        if (!isActive) return false
        if (key !in 0..9) return true

        val bit = 1 shl key
        localInputMask = if (pressed) localInputMask or bit else localInputMask and bit.inv()
        val delay = inputDelayFrames.coerceIn(BASE_INPUT_DELAY_FRAMES, MAX_INPUT_DELAY_FRAMES)
        val frame = (hooks.getFrame() + delay).coerceAtLeast(delay)
        if (!hooks.scheduleKeyMask(role, frame, localInputMask)) {
            if (role == 0) sendResyncState(reasonCode = 7) else requestResync(reasonCode = 7)
            return true
        }
        sendInput(frame, localInputMask)
        return true
    }

    fun pauseForLifecycle() {
        if (!isActive || localSuspended) return
        localSuspended = true
        writePacket { it.writeByte(PACKET_PAUSE) }
        runCatching { hooks.setPaused(true) }
    }

    /** Returns true when resume synchronization was initiated and emulation should stay paused. */
    fun resumeFromLifecycle(): Boolean {
        if (!isActive || !localSuspended) return false
        localSuspended = false
        writePacket { it.writeByte(PACKET_RESUME) }
        return true
    }

    fun close(sendDisconnect: Boolean) {
        val wasActive = isActive
        isActive = false
        val out = output
        output = null
        if (sendDisconnect && wasActive && out != null) {
            runCatching {
                synchronized(writeLock) {
                    out.writeByte(PACKET_DISCONNECT)
                    out.flush()
                }
            }
        }
        runCatching { input?.close() }
        input = null
        runCatching { connectionCloser?.invoke() }
        connectionCloser = null
        readerThread?.interrupt()
        readerThread = null
        monitorThread?.interrupt()
        monitorThread = null
        resyncInProgress = false
        resyncStartedAtMs = 0L
        resyncAttempts = 0
        localSuspended = false
        peerSuspended = false
        localInputMask = 0
    }

    private fun notePacketReceived() {
        lastReceiveAtMs = SystemClock.elapsedRealtime()
        packetsReceived++
    }

    private fun notifyDisconnectOnce(message: String) {
        if (!isActive) return
        if (!disconnectNotified.compareAndSet(false, true)) return
        activity.runOnUiThread { hooks.onDisconnect(message) }
    }

    private fun writePacket(writer: (DataOutputStream) -> Unit): Boolean {
        val out = output ?: return false
        return try {
            synchronized(writeLock) {
                writer(out)
                out.flush()
                packetsSent++
            }
            true
        } catch (_: Exception) {
            notifyDisconnectOnce("Remote Link connection lost")
            false
        }
    }

    private fun requestResync(reasonCode: Int = 1) {
        if (!isActive || role != 1 || localSuspended || peerSuspended) return
        val now = SystemClock.elapsedRealtime()
        if (resyncInProgress || now - lastResyncAtMs < RESYNC_COOLDOWN_MS) return
        lastResyncAtMs = now
        resyncStartedAtMs = now
        resyncInProgress = true
        writePacket { out ->
            out.writeByte(PACKET_RESYNC_REQUEST)
            out.writeLong(hooks.getFrame())
            out.writeInt(reasonCode)
        }
    }

    private fun sendResyncState(reasonCode: Int = 1, force: Boolean = false) {
        if (!isActive || role != 0 || localSuspended || peerSuspended) return
        val now = SystemClock.elapsedRealtime()
        if (resyncInProgress) return
        if (!force && now - lastResyncAtMs < RESYNC_COOLDOWN_MS) return
        lastResyncAtMs = now
        resyncStartedAtMs = now
        resyncInProgress = true
        try {
            hooks.setPaused(true)
            val snapshot = hooks.exportSnapshot()
            if (snapshot == null || snapshot.isEmpty() || snapshot.size > MAX_SNAPSHOT_BYTES) {
                resyncInProgress = false
                resyncStartedAtMs = 0L
                hooks.setPaused(false)
                return
            }
            if (!writePacket { out ->
                    out.writeByte(PACKET_RESYNC_STATE)
                    out.writeInt(reasonCode)
                    out.writeInt(snapshot.size)
                    out.write(snapshot)
                }) {
                resyncInProgress = false
                resyncStartedAtMs = 0L
                if (!localSuspended && !peerSuspended) hooks.setPaused(false)
            }
        } catch (_: Throwable) {
            resyncInProgress = false
            resyncStartedAtMs = 0L
            if (!localSuspended && !peerSuspended) runCatching { hooks.setPaused(false) }
        }
    }

    private fun compareCheckpointIfReady() {
        if (!isActive || role != 1 || peerCheckpointFrame <= 0L) return
        val localHash = runCatching { hooks.getCheckpointHash(peerCheckpointFrame) }.getOrDefault(0L)
        if (localHash == 0L) return
        val expected = peerCheckpointHash
        peerCheckpointFrame = 0L
        peerCheckpointHash = 0L
        if (localHash != expected) requestResync(reasonCode = 2)
    }

    private fun startReader() {
        val stream = input ?: return
        readerThread = Thread {
            try {
                while (isActive) {
                    when (stream.readUnsignedByte()) {
                        PACKET_INPUT -> {
                            notePacketReceived()
                            val frame = stream.readLong()
                            val mask = stream.readInt()
                            if (frame < 0L) throw IOException("Invalid Remote Link input frame")
                            if ((mask and VALID_KEY_MASK.inv()) != 0) throw IOException("Invalid Remote Link input mask")
                            val localFrame = runCatching { hooks.getFrame() }.getOrDefault(frame)
                            if (frame > localFrame + MAX_INPUT_FUTURE_FRAMES) {
                                throw IOException("Remote Link input frame is too far ahead")
                            }
                            val peerRole = 1 - role
                            val scheduled = hooks.scheduleKeyMask(peerRole, frame, mask)
                            if (!scheduled) {
                                lateInputPackets++
                                hooks.setKeyMask(peerRole, mask)
                                if (role == 0) sendResyncState(reasonCode = 3) else requestResync(reasonCode = 3)
                            }
                        }
                        PACKET_DISCONNECT -> {
                            notePacketReceived()
                            throw IOException("Remote player disconnected")
                        }
                        PACKET_HEARTBEAT -> {
                            notePacketReceived()
                            peerFrame = stream.readLong()
                        }
                        PACKET_STATE_HASH -> {
                            notePacketReceived()
                            peerCheckpointFrame = stream.readLong()
                            peerCheckpointHash = stream.readLong()
                            compareCheckpointIfReady()
                        }
                        PACKET_RESYNC_REQUEST -> {
                            notePacketReceived()
                            stream.readLong()
                            val reason = stream.readInt()
                            if (role == 0) sendResyncState(reason, force = true)
                        }
                        PACKET_RESYNC_STATE -> {
                            notePacketReceived()
                            resyncInProgress = true
                            resyncStartedAtMs = SystemClock.elapsedRealtime()
                            stream.readInt()
                            val length = stream.readInt()
                            if (length !in 1..MAX_SNAPSHOT_BYTES) throw IOException("Invalid Remote Link snapshot")
                            val payload = ByteArray(length)
                            stream.readFully(payload)
                            if (role == 1) {
                                hooks.setPaused(true)
                                val imported = hooks.importSnapshot(payload)
                                hooks.clearInputSchedule()
                                if (!imported) throw IOException("Could not apply Remote Link recovery state")
                                writePacket { it.writeByte(PACKET_RESYNC_ACK) }
                            }
                        }
                        PACKET_RESYNC_ACK -> {
                            notePacketReceived()
                            if (role == 0 && resyncInProgress) {
                                writePacket { it.writeByte(PACKET_RESYNC_RESUME) }
                                resyncInProgress = false
                                resyncStartedAtMs = 0L
                                resyncAttempts = 0
                                successfulResyncs++
                                reseedLocalInput()
                                if (!localSuspended && !peerSuspended) {
                                    hooks.setPaused(false)
                                    activity.runOnUiThread { hooks.onResumeEmulation() }
                                }
                            }
                        }
                        PACKET_RESYNC_RESUME -> {
                            notePacketReceived()
                            if (role == 1) {
                                resyncInProgress = false
                                resyncStartedAtMs = 0L
                                resyncAttempts = 0
                                successfulResyncs++
                                reseedLocalInput()
                                if (!localSuspended && !peerSuspended) {
                                    hooks.setPaused(false)
                                    activity.runOnUiThread { hooks.onResumeEmulation() }
                                }
                            }
                        }
                        PACKET_PING -> {
                            notePacketReceived()
                            val token = stream.readLong()
                            writePacket { out ->
                                out.writeByte(PACKET_PONG)
                                out.writeLong(token)
                            }
                        }
                        PACKET_PONG -> {
                            notePacketReceived()
                            val token = stream.readLong()
                            val rtt = (SystemClock.elapsedRealtime() - token).coerceAtLeast(0L)
                            if (rtt < 30_000L) {
                                val previousSample = previousRttSampleMs
                                if (previousSample > 0L) {
                                    val deviation = abs(rtt - previousSample)
                                    jitterMs = if (jitterMs == 0L) deviation else ((jitterMs * 3L + deviation) / 4L)
                                }
                                previousRttSampleMs = rtt
                                rttMs = if (rttMs == 0L) rtt else ((rttMs * 3L + rtt) / 4L)

                                // Budget half the smoothed RTT plus two jitter windows.
                                // This avoids oscillating delay on noisy Wi-Fi/Bluetooth
                                // while staying responsive on stable low-latency links.
                                val oneWayBudgetMs = (rttMs / 2L) + (jitterMs * 2L)
                                val networkFrames = ((oneWayBudgetMs + FRAME_TIME_MS - 1L) / FRAME_TIME_MS)
                                    .coerceAtLeast(0L)
                                inputDelayFrames = (BASE_INPUT_DELAY_FRAMES + networkFrames)
                                    .coerceIn(BASE_INPUT_DELAY_FRAMES, MAX_INPUT_DELAY_FRAMES)
                            }
                        }
                        PACKET_PAUSE -> {
                            notePacketReceived()
                            peerSuspended = true
                            runCatching { hooks.setPaused(true) }
                            activity.runOnUiThread { hooks.onStopEmulation() }
                        }
                        PACKET_RESUME -> {
                            notePacketReceived()
                            peerSuspended = false
                            if (!localSuspended) {
                                if (role == 0) sendResyncState(reasonCode = 4) else requestResync(reasonCode = 4)
                            }
                        }
                        else -> throw IOException("Unknown Remote Link packet")
                    }
                }
            } catch (e: Exception) {
                notifyDisconnectOnce("Remote Link ended: ${e.message ?: "connection lost"}")
            }
        }.apply {
            name = "Retra-Remote-Link-Reader"
            isDaemon = true
            start()
        }
    }

    private fun startMonitor() {
        monitorThread?.interrupt()
        monitorThread = Thread {
            while (isActive && !Thread.currentThread().isInterrupted) {
                try { Thread.sleep(350L) } catch (_: InterruptedException) { break }
                if (!isActive) break
                val now = SystemClock.elapsedRealtime()

                if (now - lastHeartbeatAtMs >= HEARTBEAT_MS) {
                    lastHeartbeatAtMs = now
                    writePacket { out ->
                        out.writeByte(PACKET_HEARTBEAT)
                        out.writeLong(hooks.getFrame())
                    }
                }
                if (now - lastPingAtMs >= PING_MS) {
                    lastPingAtMs = now
                    writePacket { out ->
                        out.writeByte(PACKET_PING)
                        out.writeLong(now)
                    }
                }

                if (!localSuspended && !peerSuspended && !resyncInProgress && now - lastReceiveAtMs > TIMEOUT_MS) {
                    notifyDisconnectOnce("Remote Link timed out")
                    break
                }
                if (resyncInProgress && resyncStartedAtMs > 0L && now - resyncStartedAtMs > RESYNC_TIMEOUT_MS) {
                    resyncTimeouts++
                    if (++resyncAttempts > MAX_RESYNC_ATTEMPTS) {
                        notifyDisconnectOnce("Remote Link recovery failed after $MAX_RESYNC_ATTEMPTS attempts")
                        break
                    }
                    resyncInProgress = false
                    resyncStartedAtMs = 0L
                    lastResyncAtMs = 0L
                    if (role == 0) sendResyncState(reasonCode = 8, force = true) else requestResync(reasonCode = 8)
                }
                if (localSuspended || peerSuspended || resyncInProgress) continue

                if (role == 0) {
                    val signature = runCatching { hooks.getLatestCheckpoint() }.getOrDefault(LongArray(0))
                    if (signature.size >= 2 && signature[0] > lastHashSentFrame) {
                        lastHashSentFrame = signature[0]
                        writePacket { out ->
                            out.writeByte(PACKET_STATE_HASH)
                            out.writeLong(signature[0])
                            out.writeLong(signature[1])
                        }
                    }
                } else {
                    compareCheckpointIfReady()
                    val localFrame = hooks.getFrame()
                    val allowedDrift = (inputDelayFrames * 2L + 4L).coerceAtLeast(10L)
                    if (peerFrame > 0L && abs(localFrame - peerFrame) > allowedDrift) requestResync(reasonCode = 5)
                }

                val internalSkew = runCatching { hooks.getFrameSkew() }.getOrDefault(0L)
                if (internalSkew > 3L) {
                    if (role == 0) sendResyncState(reasonCode = 6) else requestResync(reasonCode = 6)
                }
            }
        }.apply {
            name = "Retra-Remote-Link-Monitor"
            isDaemon = true
            start()
        }
    }

    private fun reseedLocalInput() {
        hooks.clearInputSchedule()
        val delay = inputDelayFrames.coerceIn(BASE_INPUT_DELAY_FRAMES, MAX_INPUT_DELAY_FRAMES)
        val frame = (hooks.getFrame() + delay).coerceAtLeast(delay)
        hooks.scheduleKeyMask(role, frame, localInputMask)
        sendInput(frame, localInputMask)
    }

    private fun sendInput(frame: Long, mask: Int) {
        writePacket { out ->
            out.writeByte(PACKET_INPUT)
            out.writeLong(frame)
            out.writeInt(mask)
        }
    }

    private companion object {
        const val BASE_INPUT_DELAY_FRAMES = 4L
        const val MAX_INPUT_DELAY_FRAMES = 18L
        const val FRAME_TIME_MS = 17L
        const val MAX_INPUT_FUTURE_FRAMES = 300L
        const val VALID_KEY_MASK = (1 shl 10) - 1
        const val MAX_SNAPSHOT_BYTES = 40 * 1024 * 1024
        const val HEARTBEAT_MS = 1000L
        const val PING_MS = 2000L
        const val TIMEOUT_MS = 8000L
        const val RESYNC_COOLDOWN_MS = 1800L
        const val RESYNC_TIMEOUT_MS = 5000L
        const val MAX_RESYNC_ATTEMPTS = 3

        const val PACKET_INPUT = 1
        const val PACKET_DISCONNECT = 2
        const val PACKET_HEARTBEAT = 3
        const val PACKET_STATE_HASH = 4
        const val PACKET_RESYNC_REQUEST = 5
        const val PACKET_RESYNC_STATE = 6
        const val PACKET_PING = 7
        const val PACKET_PONG = 8
        const val PACKET_PAUSE = 9
        const val PACKET_RESUME = 10
        const val PACKET_RESYNC_ACK = 11
        const val PACKET_RESYNC_RESUME = 12
    }
}
