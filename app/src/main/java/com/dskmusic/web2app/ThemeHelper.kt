package com.dskmusic.web2app

import androidx.appcompat.app.AppCompatDelegate

object ThemeHelper {
    fun applyNightMode(theme: String) {
        val mode = when (theme) {
            Prefs.THEME_LIGHT, Prefs.THEME_BLUE, Prefs.THEME_GREEN -> AppCompatDelegate.MODE_NIGHT_NO
            Prefs.THEME_DARK, Prefs.THEME_DARK_BLUE, Prefs.THEME_DARK_GREEN, Prefs.THEME_AMOLED -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        if (AppCompatDelegate.getDefaultNightMode() != mode) {
            AppCompatDelegate.setDefaultNightMode(mode)
        }
    }

    fun styleRes(theme: String, noActionBar: Boolean): Int = when (theme) {
        Prefs.THEME_BLUE -> if (noActionBar) R.style.Theme_Web2App_Blue_NoActionBar else R.style.Theme_Web2App_Blue
        Prefs.THEME_GREEN -> if (noActionBar) R.style.Theme_Web2App_Green_NoActionBar else R.style.Theme_Web2App_Green
        Prefs.THEME_DARK_BLUE -> if (noActionBar) R.style.Theme_Web2App_DarkBlue_NoActionBar else R.style.Theme_Web2App_DarkBlue
        Prefs.THEME_DARK_GREEN -> if (noActionBar) R.style.Theme_Web2App_DarkGreen_NoActionBar else R.style.Theme_Web2App_DarkGreen
        Prefs.THEME_AMOLED -> if (noActionBar) R.style.Theme_Web2App_Amoled_NoActionBar else R.style.Theme_Web2App_Amoled
        else -> if (noActionBar) R.style.Theme_Web2App_NoActionBar else R.style.Theme_Web2App
    }
}
