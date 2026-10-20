package com.example.douqiuqiu.ui.components

import android.content.Context
import android.view.DragEvent
import android.widget.ScrollView

/** Observe drag locations before dispatching to child grids, so offscreen slots stay reachable. */
class DragScrollView(context: Context) : ScrollView(context) {
    private var direction = 0
    private var running = false
    private val step = object : Runnable {
        override fun run() {
            if (!running || direction == 0 || !isAttachedToWindow) { running = false; return }
            scrollBy(0, GameUi.dp(context, 9) * direction)
            postOnAnimation(this)
        }
    }
    override fun dispatchDragEvent(event: DragEvent): Boolean {
        when (event.action) {
            DragEvent.ACTION_DRAG_LOCATION -> {
                val edge = GameUi.dp(context, 64)
                direction = when { event.y < edge -> -1; event.y > height - edge -> 1; else -> 0 }
                if (!running && direction != 0) { running = true; postOnAnimation(step) }
            }
            DragEvent.ACTION_DRAG_ENDED, DragEvent.ACTION_DRAG_EXITED -> stop()
        }
        return super.dispatchDragEvent(event)
    }
    private fun stop() { running = false; direction = 0; removeCallbacks(step) }
    override fun onDetachedFromWindow() { stop(); super.onDetachedFromWindow() }
}
