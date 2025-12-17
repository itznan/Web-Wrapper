package com.nan.webwrapper

import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import kotlin.math.abs
import kotlin.math.sqrt

class TwoFingerSwipeRightDetector(
    context: Context,
    private val onSwipeRight: () -> Unit
) {

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val minSwipeDistance = touchSlop * 5
    private val maxVerticalDeviation = touchSlop * 3
    private val maxTimeForGestureMs = 600L

    private var startX = 0f
    private var startY = 0f
    private var startTime = 0L
    private var active = false
    private var triggered = false

    // Avoid interfering with pinch zoom.
    private var initialFingerDistance = 0f

    fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_DOWN -> {
                if (event.pointerCount >= 2) {
                    startX = averageX(event)
                    startY = averageY(event)
                    startTime = event.eventTime
                    active = true
                    triggered = false
                    initialFingerDistance = getFingerDistance(event)
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (!active || event.pointerCount < 2 || triggered) return false

                val elapsed = event.eventTime - startTime
                if (elapsed > maxTimeForGestureMs) {
                    reset()
                    return false
                }

                val currentX = averageX(event)
                val currentY = averageY(event)

                val deltaX = currentX - startX
                val deltaY = abs(currentY - startY)

                // Treat strong pinch zoom as not our gesture.
                val currentDistance = getFingerDistance(event)
                val distanceChange = abs(currentDistance - initialFingerDistance)
                if (distanceChange > touchSlop * 2) {
                    reset()
                    return false
                }

                if (deltaY > maxVerticalDeviation) {
                    reset()
                    return false
                }

                if (deltaX > minSwipeDistance) {
                    triggered = true
                    onSwipeRight()
                    reset()
                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                reset()
            }
        }

        return false
    }

    private fun averageX(event: MotionEvent): Float {
        var sum = 0f
        val count = event.pointerCount
        for (i in 0 until count) {
            sum += event.getX(i)
        }
        return sum / count
    }

    private fun averageY(event: MotionEvent): Float {
        var sum = 0f
        val count = event.pointerCount
        for (i in 0 until count) {
            sum += event.getY(i)
        }
        return sum / count
    }

    private fun getFingerDistance(event: MotionEvent): Float {
        if (event.pointerCount >= 2) {
            val dx = event.getX(0) - event.getX(1)
            val dy = event.getY(0) - event.getY(1)
            return sqrt(dx * dx + dy * dy)
        }
        return 0f
    }

    private fun reset() {
        active = false
        triggered = false
        startX = 0f
        startY = 0f
        startTime = 0L
        initialFingerDistance = 0f
    }
}
