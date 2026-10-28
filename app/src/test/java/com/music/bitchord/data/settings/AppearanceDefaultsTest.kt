package com.music.bitchord.data.settings

import android.content.SharedPreferences
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppearanceDefaultsTest {
    @Test
    fun `existing appearance changes to dark with glass off without altering other settings`() {
        val values = mutableMapOf<String, Any>(
            AppearanceDefaults.THEME_KEY to "LIGHT",
            AppearanceDefaults.LIQUID_GLASS_KEY to true,
            "unrelated_setting" to 42,
        )
        AppearanceDefaults.applyOnce(preferences(values))

        assertEquals("DARK", values[AppearanceDefaults.THEME_KEY])
        assertEquals(false, values[AppearanceDefaults.LIQUID_GLASS_KEY])
        assertEquals(true, values[AppearanceDefaults.MIGRATION_KEY])
        assertEquals(42, values["unrelated_setting"])
    }

    @Test
    fun `appearance remains changeable after the one-time migration`() {
        val values = mutableMapOf<String, Any>()
        val prefs = preferences(values)
        AppearanceDefaults.applyOnce(prefs)
        assertEquals("DARK", values[AppearanceDefaults.THEME_KEY])
        assertFalse(values[AppearanceDefaults.LIQUID_GLASS_KEY] as Boolean)

        prefs.edit()
            .putString(AppearanceDefaults.THEME_KEY, "LIGHT")
            .putBoolean(AppearanceDefaults.LIQUID_GLASS_KEY, true)
            .apply()
        AppearanceDefaults.applyOnce(prefs)

        assertEquals("LIGHT", values[AppearanceDefaults.THEME_KEY])
        assertTrue(values[AppearanceDefaults.LIQUID_GLASS_KEY] as Boolean)
    }

    /** Minimal in-memory preference store for this migration's operations. */
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
                "edit" -> editor
                else -> error("Unexpected preference method: ${method.name}")
            }
        } as SharedPreferences
    }
}
