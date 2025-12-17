package com.nan.webwrapper

import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import kotlin.math.abs
import kotlin.math.sqrt

class TwoFingerPullDownDetector(
    context: Context,
    private val onPullDown: () -> Unit
) {

    private val threshold = ViewConfiguration.get(context).scaledTouchSlop * 3
    private val maxHorizontalDeviation = ViewConfiguration.get(context).scaledTouchSlop * 2
    private var startY = 0f
    private var startX = 0f
    private var active = false
    private var triggered = false
    private var isPinchZoom = false
    private var initialFingerDistance = 0f

    fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_DOWN -> {
                if (event.pointerCount >= 2) {
                    startY = averageY(event)
                    startX = averageX(event)
                    initialFingerDistance = getFingerDistance(event)
                    active = true
                    triggered = false
                    isPinchZoom = false
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (active && event.pointerCount >= 2) {
                    val currentY = averageY(event)
                    val currentX = averageX(event)
                    val deltaY = currentY - startY
                    val deltaX = abs(currentX - startX)
                    
                    // Check if this is a pinch zoom gesture (fingers moving apart/together)
                    val currentDistance = getFingerDistance(event)
                    val distanceChange = abs(currentDistance - initialFingerDistance)
                    
                    // If horizontal movement is significant or fingers are moving apart/together, it's likely zoom
                    if (deltaX > maxHorizontalDeviation || distanceChange > threshold * 0.5f) {
                        isPinchZoom = true
                        return false // Don't interfere with zoom
                    }
                    
                    // Only trigger if it's a clear downward pull (not zoom)
                    if (!isPinchZoom && !triggered && deltaY > threshold && deltaX < maxHorizontalDeviation) {
                        triggered = true
                        onPullDown()
                        return true
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                active = false
                triggered = false
                isPinchZoom = false
                initialFingerDistance = 0f
            }
        }
        return false // Don't consume the event, let WebView handle zoom
    }

    private fun averageY(event: MotionEvent): Float {
        var sum = 0f
        for (i in 0 until event.pointerCount) {
            sum += event.getY(i)
        }
        return sum / event.pointerCount
    }

    private fun averageX(event: MotionEvent): Float {
        var sum = 0f
        for (i in 0 until event.pointerCount) {
            sum += event.getX(i)
        }
        return sum / event.pointerCount
    }

    private fun getFingerDistance(event: MotionEvent): Float {
        if (event.pointerCount >= 2) {
            val dx = event.getX(0) - event.getX(1)
            val dy = event.getY(0) - event.getY(1)
            return sqrt(dx * dx + dy * dy)
        }
        return 0f
    }
}

