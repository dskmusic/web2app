package com.dskmusic.web2app

import android.app.Activity
import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText

fun EditText.hideKeyboardOnImeAction() {
    setOnEditorActionListener { view, actionId, _ ->
        if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_SEARCH) {
            val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)
            view.clearFocus()
            true
        } else {
            false
        }
    }
}

/** Detects a fling starting near either screen edge, in either direction. Feed it from dispatchTouchEvent. */
fun Activity.edgeSwipeDetector(onEdgeSwipe: () -> Unit): GestureDetector {
    val edgeZonePx = resources.displayMetrics.density * 24
    return GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            if (e1 == null) return false
            val dx = e2.x - e1.x
            val dy = e2.y - e1.y
            if (kotlin.math.abs(dx) < kotlin.math.abs(dy)) return false
            if (kotlin.math.abs(dx) < 100 || kotlin.math.abs(velocityX) < 300) return false
            val screenWidth = resources.displayMetrics.widthPixels
            val fromLeftEdge = dx > 0 && e1.x <= edgeZonePx
            val fromRightEdge = dx < 0 && e1.x >= screenWidth - edgeZonePx
            if (fromLeftEdge || fromRightEdge) {
                onEdgeSwipe()
                return true
            }
            return false
        }
    })
}
