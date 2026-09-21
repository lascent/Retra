package com.retra.emulator

import android.view.MotionEvent

/**
 * Mutable state shared by the on-screen input surface and the native/link key
 * dispatchers. Centralizing it prevents MainActivity from becoming the owner of
 * pointer bookkeeping and multi-source key-hold semantics.
 */
internal class GameplayInputState {
    var activeDpadMask: Int = 0
    var activeDpadPointerId: Int = MotionEvent.INVALID_POINTER_ID
    var activeGameplayKeyMask: Int = 0
    val keyHoldCounts: IntArray = IntArray(10)
    var generation: Long = 0L
}
