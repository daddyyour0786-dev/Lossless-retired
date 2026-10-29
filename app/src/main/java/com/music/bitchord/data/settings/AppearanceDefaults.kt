package com.music.bitchord.data.settings

import android.content.SharedPreferences

/**
 * Seeds the theme for a device that has never chosen one.
 *
 * This used to write [ThemeMode.DARK] on every install, once, guarded by
 * [MIGRATION_KEY] — including installs that had already picked a theme
 * themselves. The default is [ThemeMode.SYSTEM] now, so the seeding job is the
 * narrower one it should always have been: write nothing when a theme is
 * already stored, and otherwise let the app follow the phone.
 *
 * Deliberately *not* re-run as a migration that overwrites the stored value.
 * A `DARK` left behind by the old version and a `DARK` the listener chose are
 * the same string in the same file, so a second migration could not tell them
 * apart — it would flip the theme of everyone who deliberately chose dark in
 * order to correct the ones who never chose at all. Leaving the existing value
 * alone is the recoverable mistake of the two: someone who wanted System can
 * pick it, whereas someone whose app went light overnight has no way to know
 * why.
 */
internal object AppearanceDefaults {
    const val THEME_KEY = "theme_mode"
    const val MIGRATION_KEY = "lossless_dark_appearance_v1"

    fun applyOnce(prefs: SharedPreferences) {
        // The v1 flag being set is not a reason to skip: v1 also ran for
        // installs that never had a theme of their own, and the check that
        // matters is whether one is stored. Keeping the flag written anyway
        // stops an older build sharing the file from re-running v1.
        val seeded = prefs.getBoolean(MIGRATION_KEY, false)
        if (prefs.contains(THEME_KEY)) {
            if (!seeded) prefs.edit().putBoolean(MIGRATION_KEY, true).apply()
            return
        }
        prefs.edit()
            .putString(THEME_KEY, ThemeMode.SYSTEM.name)
            .putBoolean(MIGRATION_KEY, true)
            .apply()
    }
}
