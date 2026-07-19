package com.dskmusic.web2app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

class Web2AppApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        applyLocale(Prefs.getLanguage(this))
    }
}

fun applyLocale(lang: String) {
    val locales = when (lang) {
        Prefs.LANG_ES -> LocaleListCompat.forLanguageTags("es")
        Prefs.LANG_EN -> LocaleListCompat.forLanguageTags("en")
        else -> LocaleListCompat.getEmptyLocaleList()
    }
    AppCompatDelegate.setApplicationLocales(locales)
}
