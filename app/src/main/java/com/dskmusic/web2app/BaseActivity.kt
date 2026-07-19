package com.dskmusic.web2app

import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
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

    /** Closes the keyboard whenever the user touches anything outside the focused text field. */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            val focused = currentFocus
            if (focused is EditText) {
                val rect = Rect()
                focused.getGlobalVisibleRect(rect)
                if (!rect.contains(ev.rawX.toInt(), ev.rawY.toInt())) {
                    focused.clearFocus()
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(focused.windowToken, 0)
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }
}
