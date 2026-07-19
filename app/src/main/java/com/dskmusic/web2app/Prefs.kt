package com.dskmusic.web2app

import android.content.Context

object Prefs {
    private const val PREFS_NAME = "web2app_prefs"
    private const val KEY_THEME = "theme"
    private const val KEY_LANGUAGE = "language"
    private const val KEY_DEFAULT_SHORTCUT_THEME = "default_shortcut_theme"
    private const val KEY_DEFAULT_ALLOW_ROTATION = "default_allow_rotation"
    private const val KEY_DEFAULT_DESKTOP_MODE = "default_desktop_mode"
    private const val KEY_DEFAULT_INCOGNITO = "default_incognito"
    private const val KEY_DEFAULT_ALLOW_ZOOM = "default_allow_zoom"
    private const val KEY_DEFAULT_ALLOW_SELECTION = "default_allow_selection"

    const val THEME_SYSTEM = "system"
    const val THEME_LIGHT = "light"
    const val THEME_DARK = "dark"
    const val THEME_BLUE = "blue"
    const val THEME_GREEN = "green"
    const val THEME_DARK_BLUE = "dark_blue"
    const val THEME_DARK_GREEN = "dark_green"
    const val THEME_AMOLED = "amoled"

    const val LANG_SYSTEM = "system"
    const val LANG_ES = "es"
    const val LANG_EN = "en"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getTheme(context: Context) = prefs(context).getString(KEY_THEME, THEME_SYSTEM) ?: THEME_SYSTEM
    fun setTheme(context: Context, value: String) = prefs(context).edit().putString(KEY_THEME, value).apply()

    fun getLanguage(context: Context) = prefs(context).getString(KEY_LANGUAGE, LANG_SYSTEM) ?: LANG_SYSTEM
    fun setLanguage(context: Context, value: String) = prefs(context).edit().putString(KEY_LANGUAGE, value).apply()

    // Last options the user picked when creating/editing a shortcut, reused as defaults for the next one.
    fun getDefaultShortcutTheme(context: Context) = prefs(context).getString(KEY_DEFAULT_SHORTCUT_THEME, WebViewActivity.THEME_SYSTEM) ?: WebViewActivity.THEME_SYSTEM
    fun getDefaultAllowRotation(context: Context) = prefs(context).getBoolean(KEY_DEFAULT_ALLOW_ROTATION, false)
    fun getDefaultDesktopMode(context: Context) = prefs(context).getBoolean(KEY_DEFAULT_DESKTOP_MODE, false)
    fun getDefaultIncognito(context: Context) = prefs(context).getBoolean(KEY_DEFAULT_INCOGNITO, false)
    fun getDefaultAllowZoom(context: Context) = prefs(context).getBoolean(KEY_DEFAULT_ALLOW_ZOOM, false)
    fun getDefaultAllowSelection(context: Context) = prefs(context).getBoolean(KEY_DEFAULT_ALLOW_SELECTION, false)

    fun setDefaultShortcutOptions(
        context: Context,
        forcedTheme: String,
        allowRotation: Boolean,
        desktopMode: Boolean,
        incognito: Boolean,
        allowZoom: Boolean,
        allowSelection: Boolean
    ) {
        prefs(context).edit()
            .putString(KEY_DEFAULT_SHORTCUT_THEME, forcedTheme)
            .putBoolean(KEY_DEFAULT_ALLOW_ROTATION, allowRotation)
            .putBoolean(KEY_DEFAULT_DESKTOP_MODE, desktopMode)
            .putBoolean(KEY_DEFAULT_INCOGNITO, incognito)
            .putBoolean(KEY_DEFAULT_ALLOW_ZOOM, allowZoom)
            .putBoolean(KEY_DEFAULT_ALLOW_SELECTION, allowSelection)
            .apply()
    }

    fun clear(context: Context) = prefs(context).edit().clear().apply()
}
