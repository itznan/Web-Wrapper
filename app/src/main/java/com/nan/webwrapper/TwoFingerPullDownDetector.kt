package com.nan.webwrapper

import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import kotlin.math.abs

class TwoFingerPullDownDetector(
    context: Context,
    private val onPullDown: () -> Unit
) {

    private val threshold = ViewConfiguration.get(context).scaledTouchSlop * 3
    private var startY = 0f
    private var active = false
    private var triggered = false

    fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_DOWN -> {
                if (event.pointerCount >= 2) {
                    startY = averageY(event)
                    active = true
                    triggered = false
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (active && event.pointerCount >= 2) {
                    val currentY = averageY(event)
                    if (!triggered && currentY - startY > threshold) {
                        triggered = true
                        onPullDown()
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                active = false
                triggered = false
            }
        }
        return triggered
    }

    private fun averageY(event: MotionEvent): Float {
        var sum = 0f
        for (i in 0 until event.pointerCount) {
            sum += event.getY(i)
        }
        return sum / event.pointerCount
    }
}

