package com.retra.emulator

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class RetraFileOpsInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var root: File
    private lateinit var fileOps: RetraFileOps

    @Before
    fun setUp() {
        root = File(context.cacheDir, "retra-fileops-test").apply {
            deleteRecursively()
            mkdirs()
        }
        fileOps = RetraFileOps(context)
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun atomicWriteAndVerifiedCopyPreserveExactBytes() {
        val bytes = ByteArray(128 * 1024 + 17) { index -> (index * 31).toByte() }
        val source = File(root, "source.bin")
        val copy = File(root, "nested/copy.bin")

        fileOps.atomicWriteBytes(source, bytes)
        fileOps.atomicCopyVerified(source, copy)

        assertArrayEquals(bytes, source.readBytes())
        assertArrayEquals(bytes, copy.readBytes())
        assertEquals(fileOps.sha256File(source), fileOps.sha256File(copy))
        assertFalse(root.walkTopDown().any { it.name.endsWith(".tmp") })
    }

    @Test
    fun sha256AndSanitizeAreDeterministicOnAndroidFilesystem() {
        val target = File(root, "text.txt")
        fileOps.atomicWriteText(target, "Retra\nAndroid\n")
        val expected = MessageDigest.getInstance("SHA-256")
            .digest("Retra\nAndroid\n".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

        assertEquals(expected, fileOps.sha256File(target))
        assertEquals("Pok_mon_Emerald_USA_.gba", fileOps.sanitizeFileName("Pokémon Emerald (USA).gba"))
        assertTrue(target.isFile)
    }
}
