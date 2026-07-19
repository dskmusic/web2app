package com.dskmusic.web2app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

abstract class BaseActivity : AppCompatActivity() {
    private var appliedTheme: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        appliedTheme = Prefs.getTheme(this)
        ThemeHelper.applyNightMode(appliedTheme)
        setTheme(ThemeHelper.styleRes(appliedTheme, useNoActionBar()))
        super.onCreate(savedInstanceState)
    }

    protected open fun useNoActionBar(): Boolean = false

    override fun onResume() {
        super.onResume()
        val current = Prefs.getTheme(this)
        if (current != appliedTheme) {
            recreate()
        }
    }
}
