package com.retra.emulator

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RetraPreferencesInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var prefs: RetraPreferences
    private val keys = listOf(
        "use_bios_v1",
        "boot_bios_v1",
        "bios_last_label_v1",
        "bios_gba_path_v1",
        "volume_v1"
    )
    private lateinit var previous: Map<String, Any?>

    @Before
    fun setUp() {
        prefs = RetraPreferences(context)
        val all = prefs.all
        previous = keys.associateWith { all[it] }
    }

    @After
    fun tearDown() {
        val editor = prefs.edit()
        keys.forEach(editor::remove)
        previous.forEach { (key, value) ->
            when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
            }
        }
        editor.commit()
    }

    @Test
    fun portableSnapshotKeepsBiosDependentStateDeviceLocal() {
        prefs.edit()
            .putBoolean("use_bios_v1", true)
            .putBoolean("boot_bios_v1", true)
            .putString("bios_last_label_v1", "gba_bios.bin")
            .putString("bios_gba_path_v1", "/device/private/gba_bios.bin")
            .putInt("volume_v1", 37)
            .commit()

        val snapshot = prefs.portableSettingsSnapshot()

        assertFalse(snapshot.containsKey("use_bios_v1"))
        assertFalse(snapshot.containsKey("boot_bios_v1"))
        assertFalse(snapshot.containsKey("bios_last_label_v1"))
        assertFalse(snapshot.containsKey("bios_gba_path_v1"))
        assertEquals(37, snapshot["volume_v1"])
    }

    @Test
    fun restoreFromOlderBackupIgnoresBiosStateButRestoresPortableValues() {
        prefs.edit()
            .putBoolean("use_bios_v1", false)
            .putBoolean("boot_bios_v1", false)
            .putString("bios_last_label_v1", "Device BIOS")
            .remove("bios_gba_path_v1")
            .putInt("volume_v1", 100)
            .commit()

        val restored = prefs.restorePortableSettings(
            mapOf(
                "use_bios_v1" to true,
                "boot_bios_v1" to true,
                "bios_last_label_v1" to "Backup BIOS",
                "bios_gba_path_v1" to "/other/device/gba_bios.bin",
                "volume_v1" to 42
            )
        )

        assertEquals(1, restored)
        assertFalse(prefs.getBoolean("use_bios_v1", true))
        assertFalse(prefs.getBoolean("boot_bios_v1", true))
        assertEquals("Device BIOS", prefs.getString("bios_last_label_v1", null))
        assertNull(prefs.getString("bios_gba_path_v1", null))
        assertEquals(42, prefs.getInt("volume_v1", 0))
    }
}
