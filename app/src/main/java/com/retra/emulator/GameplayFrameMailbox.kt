package com.retra.emulator

import java.util.ArrayDeque

/**
 * Lock-bounded latest-frame mailbox shared by the emulator producer and the GL consumer.
 *
 * The hot path uses four permanently preallocated pixel buffers:
 *  - producer: currently being filled by mGBA/JNI
 *  - pending: newest completed frame waiting for the GL thread
 *  - rendering: frame currently owned by the GL thread
 *  - spare: immediate handoff headroom if producer/consumer cadence briefly diverges
 *
 * Older pending frames are recycled instead of queued, so fast-forward can never build
 * a visual backlog. The producer and GL consumer also reuse result holders, eliminating
 * per-frame Kotlin object/array allocation during normal gameplay.
 */
internal class GameplayFrameMailbox {
    data class Frame(
        val pixels: IntArray,
        val width: Int,
        val height: Int,
        val generation: Long,
        val publishedAtNs: Long
    )

    /** Mutable producer result intended to be allocated once and reused by the emulation thread. */
    class PublishResult internal constructor(
        var producerBuffer: IntArray = EMPTY_PIXELS,
        var generation: Long = 0L
    )

    /** Mutable render result intended to be allocated once and reused by the GL thread. */
    class RenderFrame internal constructor(
        var pixels: IntArray = EMPTY_PIXELS,
        var width: Int = 1,
        var height: Int = 1,
        var generation: Long = 0L,
        var publishedAtNs: Long = 0L
    )

    private val lock = Any()
    private val freeBuffers = ArrayDeque<IntArray>(TOTAL_FRAME_BUFFERS)

    private var width = 1
    private var height = 1
    private var pixelCount = 1
    private var pending: IntArray? = null
    private var rendering: IntArray? = null
    private var generation = 0L
    private var renderedGeneration = 0L
    private var pendingPublishedAtNs = 0L
    private var renderingPublishedAtNs = 0L

    /** Rebuild the four-buffer pool for a new native video size and return the producer buffer. */
    fun configure(width: Int, height: Int): IntArray = synchronized(lock) {
        this.width = width.coerceAtLeast(1)
        this.height = height.coerceAtLeast(1)
        pixelCount = this.width * this.height
        pending = null
        rendering = null
        generation = 0L
        renderedGeneration = 0L
        pendingPublishedAtNs = 0L
        renderingPublishedAtNs = 0L
        freeBuffers.clear()

        val producer = IntArray(pixelCount)
        repeat(TOTAL_FRAME_BUFFERS - 1) {
            freeBuffers.addLast(IntArray(pixelCount))
        }
        producer
    }

    /**
     * Allocation-free hot-path publish. [out] is owned by the producer thread and reused.
     * If an unexpected pool invariant is ever violated, the just-completed frame is dropped
     * and immediately returned as the producer rather than allocating during a GPU stall.
     */
    fun publishLatest(
        completed: IntArray,
        out: PublishResult,
        publishedAtNs: Long = System.nanoTime()
    ) = synchronized(lock) {
        if (completed.size != pixelCount) {
            out.producerBuffer = completed
            out.generation = generation
            return@synchronized
        }

        pending?.let { stale ->
            if (stale !== rendering && stale !== completed) freeBuffers.addLast(stale)
        }

        val nextProducer = if (freeBuffers.isNotEmpty()) {
            freeBuffers.removeFirst()
        } else {
            // Four buffers should make this unreachable in the single-producer/single-consumer
            // model. Preserve zero-allocation behavior if an OEM lifecycle race breaks that
            // invariant: drop this visual publish and keep emulation progressing safely.
            pending = null
            pendingPublishedAtNs = 0L
            out.producerBuffer = completed
            out.generation = generation
            return@synchronized
        }

        pending = completed
        pendingPublishedAtNs = publishedAtNs
        generation++
        out.producerBuffer = nextProducer
        out.generation = generation
    }

    /** Compatibility helper for non-hot-path callers/tests. */
    fun publishLatest(completed: IntArray, publishedAtNs: Long = System.nanoTime()): PublishResult =
        PublishResult().also { publishLatest(completed, it, publishedAtNs) }

    /** Compatibility helper for non-hot-path callers. */
    fun publish(completed: IntArray): IntArray = publishLatest(completed).producerBuffer

    /**
     * Allocation-free GL hot path. Returns true only when a newer frame was acquired.
     * The caller owns [out] and may reuse the same holder every render callback.
     */
    fun acquireLatestForRender(out: RenderFrame): Boolean = synchronized(lock) {
        val newest = pending ?: return@synchronized false
        if (generation == renderedGeneration && newest === rendering) return@synchronized false

        pending = null
        rendering?.let { previous ->
            if (previous !== newest) freeBuffers.addLast(previous)
        }
        rendering = newest
        renderingPublishedAtNs = pendingPublishedAtNs
        renderedGeneration = generation

        out.pixels = newest
        out.width = width
        out.height = height
        out.generation = renderedGeneration
        out.publishedAtNs = renderingPublishedAtNs
        true
    }

    /** Compatibility helper for fallback/off-hot-path callers. */
    fun acquireLatestForRender(): Frame? {
        val reusable = RenderFrame()
        if (!acquireLatestForRender(reusable)) return null
        return Frame(
            reusable.pixels,
            reusable.width,
            reusable.height,
            reusable.generation,
            reusable.publishedAtNs
        )
    }

    /**
     * Snapshot the frame a user most recently saw. Used by save-state thumbnails and
     * shader selection; this is intentionally off the hot presentation path.
     */
    fun copyPresentedOrLatest(): Frame? = synchronized(lock) {
        val source = rendering ?: pending ?: return@synchronized null
        val fromRendering = source === rendering
        val sourceGeneration = if (fromRendering) renderedGeneration else generation
        val sourcePublishedAtNs = if (fromRendering) renderingPublishedAtNs else pendingPublishedAtNs
        Frame(source.copyOf(), width, height, sourceGeneration, sourcePublishedAtNs)
    }

    fun hasPendingFrame(): Boolean = synchronized(lock) { pending != null }

    fun clear() = synchronized(lock) {
        pending = null
        rendering = null
        freeBuffers.clear()
        generation = 0L
        renderedGeneration = 0L
        pendingPublishedAtNs = 0L
        renderingPublishedAtNs = 0L
    }

    companion object {
        private const val TOTAL_FRAME_BUFFERS = 4
        private val EMPTY_PIXELS = IntArray(0)
    }
}
