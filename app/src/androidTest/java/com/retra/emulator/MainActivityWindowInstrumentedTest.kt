package com.retra.emulator

import android.view.WindowManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityWindowInstrumentedTest {
    @Test
    fun activityUsesResizeImePolicyAndExposesAValidAdaptiveDisplayMode() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val adjustMode = activity.window.attributes.softInputMode and
                    WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST
                assertTrue(adjustMode == WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

                val display = activity.window.decorView.display
                assertNotNull(display)
                val current = display!!.mode
                val manager = DisplayPerformanceManager(activity)
                val selected = manager.selectBestGameplayMode(display.supportedModes, current, 120f)
                assertNotNull(selected)
                assertTrue(selected!!.refreshRate > 0f)
                assertTrue(selected.refreshRate <= 120.6f || display.supportedModes.none { it.refreshRate <= 120.6f })
            }
        }
    }
}
