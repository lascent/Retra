package com.retra.emulator

import android.os.Process
import java.io.Closeable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Central ownership for Activity-scoped background work.
 *
 * Storage stays serialized because import migration/journaling depends on strict
 * ordering. Network/link work is bounded so repeated reconnect/artwork/cloud
 * requests cannot create an unbounded number of threads on a low-memory phone.
 */
class RetraTaskExecutors : Closeable {
    val serialIo: ExecutorService = Executors.newSingleThreadExecutor(
        namedFactory("Retra-Storage", Process.THREAD_PRIORITY_BACKGROUND, Thread.NORM_PRIORITY - 1)
    )

    val network: ExecutorService = ThreadPoolExecutor(
        NETWORK_CORE_THREADS,
        NETWORK_MAX_THREADS,
        NETWORK_KEEP_ALIVE_SECONDS,
        TimeUnit.SECONDS,
        LinkedBlockingQueue(NETWORK_QUEUE_CAPACITY),
        namedFactory("Retra-Link"),
        ThreadPoolExecutor.CallerRunsPolicy()
    ).apply {
        allowCoreThreadTimeOut(true)
    }

    override fun close() {
        network.shutdownNow()
        serialIo.shutdownNow()
    }

    private fun namedFactory(
        prefix: String,
        androidPriority: Int? = null,
        javaPriority: Int = Thread.NORM_PRIORITY
    ): ThreadFactory {
        val sequence = AtomicInteger(1)
        return ThreadFactory { runnable ->
            Thread({
                if (androidPriority != null) {
                    runCatching { Process.setThreadPriority(androidPriority) }
                }
                runnable.run()
            }, "$prefix-${sequence.getAndIncrement()}").apply {
                priority = javaPriority.coerceIn(Thread.MIN_PRIORITY, Thread.MAX_PRIORITY)
                isDaemon = false
            }
        }
    }

    private companion object {
        const val NETWORK_CORE_THREADS = 2
        const val NETWORK_MAX_THREADS = 4
        const val NETWORK_KEEP_ALIVE_SECONDS = 20L
        const val NETWORK_QUEUE_CAPACITY = 32
    }
}
