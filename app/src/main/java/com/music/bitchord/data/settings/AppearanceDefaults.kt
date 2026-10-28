package com.music.bitchord.data.settings

import android.content.SharedPreferences

/**
 * Applies Lossless's darker appearance once, including for existing installs.
 * The regular settings remain user-selectable after this migration.
 */
internal object AppearanceDefaults {
    const val THEME_KEY = "theme_mode"
    const val MIGRATION_KEY = "lossless_dark_appearance_v1"

    fun applyOnce(prefs: SharedPreferences) {
        if (prefs.getBoolean(MIGRATION_KEY, false)) return
        prefs.edit()
            .putString(THEME_KEY, ThemeMode.DARK.name)
            .putBoolean(MIGRATION_KEY, true)
            .apply()
    }
}
