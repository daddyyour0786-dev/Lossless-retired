package com.music.bitchord.data.settings

import android.content.SharedPreferences

/**
 * Apply LSY Free's darker appearance once, including for existing installs.
 * The regular settings remain user-selectable after this migration.
 */
internal object AppearanceDefaults {
    const val THEME_KEY = "theme_mode"
    const val LIQUID_GLASS_KEY = "liquid_glass"
    const val MIGRATION_KEY = "lsy_dark_appearance_v1"

    fun applyOnce(prefs: SharedPreferences) {
        if (prefs.getBoolean(MIGRATION_KEY, false)) return
        prefs.edit()
            .putString(THEME_KEY, ThemeMode.DARK.name)
            .putBoolean(LIQUID_GLASS_KEY, false)
            .putBoolean(MIGRATION_KEY, true)
            .apply()
    }
}
