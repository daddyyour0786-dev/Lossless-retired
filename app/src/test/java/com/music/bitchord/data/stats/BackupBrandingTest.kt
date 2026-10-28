package com.music.bitchord.data.stats

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupBrandingTest {
    private val json = Json { encodeDefaults = true }

    @Test
    fun `suggested backup filename is branded and dated`() {
        assertTrue(Backup.suggestedName().matches(Regex("Lossless-backup-\\d{4}-\\d{2}-\\d{2}\\.json")))
    }

    @Test
    fun `new backup identifies as Lossless and retains data`() {
        val original = Backup.BackupFile(
            versionName = "0.1.0",
            exportedAt = "2026-01-01T12:00:00Z",
            settings = mapOf(
                "volume" to Backup.PrefValue.of(0.5f)!!,
                "theme" to Backup.PrefValue.of("dark")!!,
                "enabled" to Backup.PrefValue.of(true)!!,
            ),
            listening = listOf(StoredBucket(month = "2026-01")),
        )
        val encoded = json.encodeToString(Backup.BackupFile.serializer(), original)
        assertTrue(encoded.contains("\"app\":\"lossless\""))
        assertFalse(encoded.contains("\"app\":\"bitchord\""))
        val restored = Backup.decodeBackup(encoded)
        assertEquals(original, restored)
        assertEquals(0.5f, restored.settings["volume"]?.decoded())
        assertEquals("dark", restored.settings["theme"]?.decoded())
        assertEquals(true, restored.settings["enabled"]?.decoded())
        assertEquals("2026-01", restored.listening.single().month)
    }

    @Test
    fun `legacy BitChord and pre-rebrand exports remain readable`() {
        val old = """{"app":"bitchord","version":1,"versionName":"0.1.0","settings":{"theme":{"type":"string","value":"dark"}},"listening":[{"month":"2026-05"}]}"""
        val restored = Backup.decodeBackup(old)
        assertEquals("bitchord", restored.app)
        assertEquals("dark", restored.settings["theme"]?.decoded())
        assertEquals("2026-05", restored.listening.single().month)

        // What the app wrote under its previous name.
        val rebranded = """{"app":"lsy-free","version":1,"versionName":"0.1.0","settings":{},"listening":[]}"""
        assertEquals("lsy-free", Backup.decodeBackup(rebranded).app)
    }

    @Test
    fun `backup from another app is rejected`() {
        val failure = runCatching { Backup.decodeBackup("""{"app":"other","version":1}""") }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure!!.message.orEmpty().contains("another app"))
    }

    @Test
    fun `newer schemas are rejected before import`() {
        val failure = runCatching { Backup.decodeBackup("""{"app":"lossless","version":99}""") }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure!!.message.orEmpty().contains("newer version of Lossless"))
    }

    @Test
    fun `invalid JSON is rejected with Lossless branding`() {
        val failure = runCatching { Backup.decodeBackup("not JSON") }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertTrue(failure!!.message.orEmpty().contains("Lossless"))
    }
}
