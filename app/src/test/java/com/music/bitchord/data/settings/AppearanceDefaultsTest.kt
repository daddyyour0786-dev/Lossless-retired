package com.music.bitchord.data.settings

import android.content.SharedPreferences
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppearanceDefaultsTest {
    /**
     * A theme already on the device is the listener's, not ours.
     *
     * This used to be the opposite assertion — the old seeding wrote `DARK`
     * over whatever was stored, which was fine while dark *was* the intended
     * default and wrong the moment it stopped being one: the same code that
     * corrected a device that had never chosen would also have corrected one
     * that had.
     */
    @Test
    fun `a stored appearance is left alone`() {
        val values = mutableMapOf<String, Any>(
            AppearanceDefaults.THEME_KEY to "LIGHT",
            "unrelated_setting" to 42,
        )
        AppearanceDefaults.applyOnce(preferences(values))

        assertEquals("LIGHT", values[AppearanceDefaults.THEME_KEY])
        assertEquals(42, values["unrelated_setting"])
    }

    @Test
    fun `a device with no theme follows the system`() {
        val values = mutableMapOf<String, Any>()
        AppearanceDefaults.applyOnce(preferences(values))

        assertEquals(ThemeMode.SYSTEM.name, values[AppearanceDefaults.THEME_KEY])
        assertEquals(true, values[AppearanceDefaults.MIGRATION_KEY])
    }

    /**
     * The flag is bookkeeping for an older build sharing this preference file,
     * not a gate: an install that predates the flag but has a theme of its own
     * still must not have one written for it.
     */
    @Test
    fun `a stored appearance survives with the flag unset`() {
        val values = mutableMapOf<String, Any>(AppearanceDefaults.THEME_KEY to "DARK")
        AppearanceDefaults.applyOnce(preferences(values))

        assertEquals("DARK", values[AppearanceDefaults.THEME_KEY])
        assertTrue(values[AppearanceDefaults.MIGRATION_KEY] as Boolean)
    }

    @Test
    fun `appearance remains changeable afterwards`() {
        val values = mutableMapOf<String, Any>()
        val prefs = preferences(values)
        AppearanceDefaults.applyOnce(prefs)
        assertEquals(ThemeMode.SYSTEM.name, values[AppearanceDefaults.THEME_KEY])

        prefs.edit()
            .putString(AppearanceDefaults.THEME_KEY, "LIGHT")
            .apply()
        AppearanceDefaults.applyOnce(prefs)

        assertEquals("LIGHT", values[AppearanceDefaults.THEME_KEY])
    }

    /** Minimal in-memory preference store for this seeding's operations. */
    private fun preferences(values: MutableMap<String, Any>): SharedPreferences {
        val changes = mutableMapOf<String, Any>()
        val editor = Proxy.newProxyInstance(
            SharedPreferences.Editor::class.java.classLoader,
            arrayOf(SharedPreferences.Editor::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "putString", "putBoolean" -> {
                    val arguments = requireNotNull(args)
                    changes[arguments[0] as String] = requireNotNull(arguments[1])
                    proxy
                }
                "apply" -> {
                    values.putAll(changes)
                    changes.clear()
                    null
                }
                else -> error("Unexpected editor method: ${method.name}")
            }
        } as SharedPreferences.Editor
        return Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java),
        ) { _, method, args ->
            when (method.name) {
                "getBoolean" -> {
                    val arguments = requireNotNull(args)
                    values[arguments[0] as String] as? Boolean ?: arguments[1]
                }
                "contains" -> values.containsKey(requireNotNull(args)[0] as String)
                "edit" -> editor
                else -> error("Unexpected preference method: ${method.name}")
            }
        } as SharedPreferences
    }
}
