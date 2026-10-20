package com.example.douqiuqiu.ui.components

import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration

/** Short, stationary hold starts drag; ordinary vertical movement remains warehouse scrolling. */
object InventoryDragGesture {
    fun install(view: View, beginDrag: () -> Boolean) {
        val slop = ViewConfiguration.get(view.context).scaledTouchSlop.toFloat()
        var downX = 0f
        var downY = 0f
        var moved = false
        var started = false
        var attempted = false
        val hold = Runnable {
            if (!moved && view.isAttachedToWindow) {
                // Lock scrolling before native drag takes ownership of the pointer.
                view.parent?.requestDisallowInterceptTouchEvent(true)
                attempted = true; view.isPressed = false
                started = beginDrag()
                if (!started) view.parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x; downY = event.y; moved = false; started = false; attempted = false
                    view.isPressed = true
                    view.removeCallbacks(hold); view.postDelayed(hold, 250)
                }
                MotionEvent.ACTION_MOVE -> if (!started &&
                    (kotlin.math.abs(event.x - downX) > slop || kotlin.math.abs(event.y - downY) > slop)) {
                    moved = true; view.isPressed = false; view.removeCallbacks(hold)
                }
                MotionEvent.ACTION_UP -> {
                    view.removeCallbacks(hold); view.isPressed = false
                    view.parent?.requestDisallowInterceptTouchEvent(false)
                    if (!moved && !attempted) view.performClick()
                }
                MotionEvent.ACTION_CANCEL -> {
                    view.removeCallbacks(hold); view.isPressed = false
                    view.parent?.requestDisallowInterceptTouchEvent(false)
                }
            }
            true
        }
        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = Unit
            override fun onViewDetachedFromWindow(v: View) { v.removeCallbacks(hold) }
        })
    }
}
