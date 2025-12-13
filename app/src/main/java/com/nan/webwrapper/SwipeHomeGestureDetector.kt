package com.nan.webwrapper

import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import kotlin.math.abs

/**
 * Detects a left-to-right then right-to-left swipe gesture
 * to navigate back to home page
 */
class SwipeHomeGestureDetector(
    context: Context,
    private val onHomeGesture: () -> Unit
) {
    
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val minSwipeDistance = touchSlop * 4 // Minimum distance for a valid swipe
    private val maxTimeForGesture = 800L // Max time for complete gesture in milliseconds
    private val maxVerticalDeviation = touchSlop * 3 // Max vertical movement allowed
    
    private enum class GestureState {
        IDLE,
        SWIPE_LEFT_TO_RIGHT,  // First swipe detected
        SWIPE_RIGHT_TO_LEFT   // Second swipe detected
    }
    
    private var state = GestureState.IDLE
    private var startX = 0f
    private var startY = 0f
    private var startTime = 0L
    private var lastX = 0f
    private var lastY = 0f
    private var maxRightX = 0f // Track the rightmost point reached
    
    fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (state == GestureState.IDLE) {
                    startX = event.x
                    startY = event.y
                    lastX = startX
                    lastY = startY
                    startTime = System.currentTimeMillis()
                    maxRightX = startX
                }
            }
            
            MotionEvent.ACTION_MOVE -> {
                val currentX = event.x
                val currentY = event.y
                val deltaX = currentX - lastX
                val deltaY = abs(currentY - lastY)
                val totalDeltaX = currentX - startX
                val totalDeltaY = abs(currentY - startY)
                val elapsedTime = System.currentTimeMillis() - startTime
                
                // Check if too much time has passed
                if (elapsedTime > maxTimeForGesture) {
                    reset()
                    return false
                }
                
                // Check if vertical movement is too much (not a horizontal swipe)
                if (totalDeltaY > maxVerticalDeviation && totalDeltaY > abs(totalDeltaX) * 0.5) {
                    reset()
                    return false
                }
                
                when (state) {
                    GestureState.IDLE -> {
                        // Check for left-to-right swipe
                        if (totalDeltaX > minSwipeDistance && abs(deltaX) > deltaY) {
                            // Valid left-to-right swipe detected
                            state = GestureState.SWIPE_LEFT_TO_RIGHT
                            maxRightX = currentX
                        } else if (totalDeltaX < -minSwipeDistance) {
                            // Swiped right-to-left first, reset
                            reset()
                        }
                    }
                    
                    GestureState.SWIPE_LEFT_TO_RIGHT -> {
                        // Update the rightmost point
                        if (currentX > maxRightX) {
                            maxRightX = currentX
                        }
                        
                        // Check for right-to-left swipe (must go back past the starting point)
                        val backDistance = maxRightX - currentX
                        if (backDistance > minSwipeDistance && deltaX < -touchSlop) {
                            // Valid right-to-left swipe detected - complete gesture!
                            state = GestureState.SWIPE_RIGHT_TO_LEFT
                            onHomeGesture()
                            reset()
                            return true
                        } else if (currentX > maxRightX + touchSlop) {
                            // Still moving right, update max
                            maxRightX = currentX
                        }
                    }
                    
                    GestureState.SWIPE_RIGHT_TO_LEFT -> {
                        // Gesture already completed, waiting for ACTION_UP
                    }
                }
                
                lastX = currentX
                lastY = currentY
            }
            
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                reset()
            }
        }
        
        return false
    }
    
    private fun reset() {
        state = GestureState.IDLE
        startX = 0f
        startY = 0f
        lastX = 0f
        lastY = 0f
        startTime = 0L
        maxRightX = 0f
    }
}

