package com.retra.emulator

import android.content.Context
import android.os.Build
import android.os.PerformanceHintManager
import android.os.Process

/** Android 12+ scheduling hint for the long-lived mGBA worker; no-op elsewhere. */
internal class EmulationPerformanceHints(private val context: Context) {
    private var session: PerformanceHintManager.Session? = null
    private var targetNs = 0L

    fun start(initialTargetNs: Long) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || session != null) return
        val safeTarget = initialTargetNs.coerceAtLeast(MIN_TARGET_NS)
        runCatching {
            val manager = context.getSystemService(PerformanceHintManager::class.java) ?: return
            session = manager.createHintSession(intArrayOf(Process.myTid()), safeTarget)
            targetNs = safeTarget
        }
    }

    fun report(actualWorkNs: Long, desiredTargetNs: Long) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val active = session ?: return
        val safeTarget = desiredTargetNs.coerceAtLeast(MIN_TARGET_NS)
        runCatching {
            if (safeTarget != targetNs) {
                active.updateTargetWorkDuration(safeTarget)
                targetNs = safeTarget
            }
            active.reportActualWorkDuration(actualWorkNs.coerceAtLeast(1L))
        }
    }

    fun close() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { session?.close() }
        }
        session = null
        targetNs = 0L
    }

    companion object {
        private const val MIN_TARGET_NS = 250_000L
    }
}
