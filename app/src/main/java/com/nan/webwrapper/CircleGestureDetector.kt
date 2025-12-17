package com.nan.webwrapper

import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

class CircleGestureDetector(
    context: Context,
    private val onCircleDetected: () -> Unit
) {

    private data class Point(val x: Float, val y: Float)

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private val points = ArrayList<Point>(64)
    private var activePointerId: Int = -1
    private var startTimeMs: Long = 0L

    // Keep thresholds conservative to avoid accidental triggers during scroll.
    private val minPoints = 18
    private val maxGestureDurationMs = 900L

    fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                points.clear()
                activePointerId = event.getPointerId(0)
                startTimeMs = event.eventTime
                points.add(Point(event.x, event.y))
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                // Multi-touch (pinch zoom) should cancel circle detection.
                reset()
            }

            MotionEvent.ACTION_MOVE -> {
                if (activePointerId == -1) return false
                val index = event.findPointerIndex(activePointerId)
                if (index < 0) {
                    reset()
                    return false
                }

                points.add(Point(event.getX(index), event.getY(index)))

                // Prevent unbounded growth.
                if (points.size > 96) {
                    points.removeAt(0)
                }
            }

            MotionEvent.ACTION_UP -> {
                val detected = isCircleGesture(event.eventTime)
                reset()
                if (detected) {
                    onCircleDetected()
                    return true
                }
            }

            MotionEvent.ACTION_CANCEL -> reset()
        }

        return false
    }

    private fun isCircleGesture(endTimeMs: Long): Boolean {
        if (points.size < minPoints) return false
        if (endTimeMs - startTimeMs > maxGestureDurationMs) return false

        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY

        var sumX = 0f
        var sumY = 0f

        for (p in points) {
            minX = min(minX, p.x)
            minY = min(minY, p.y)
            maxX = max(maxX, p.x)
            maxY = max(maxY, p.y)
            sumX += p.x
            sumY += p.y
        }

        val width = maxX - minX
        val height = maxY - minY

        // Needs meaningful size.
        val minDiameter = touchSlop * 10f
        if (width < minDiameter || height < minDiameter) return false

        // Bounding box should be roughly square-ish.
        val aspect = max(width, height) / max(1f, min(width, height))
        if (aspect > 1.8f) return false

        val cx = sumX / points.size
        val cy = sumY / points.size

        // Radii distribution should be tight.
        var rSum = 0f
        val radii = FloatArray(points.size)
        for (i in points.indices) {
            val r = hypot(points[i].x - cx, points[i].y - cy)
            radii[i] = r
            rSum += r
        }

        val rMean = rSum / points.size
        if (rMean < minDiameter / 3f) return false

        var variance = 0f
        for (r in radii) {
            val d = r - rMean
            variance += d * d
        }
        val rStd = kotlin.math.sqrt(variance / points.size)
        if (rStd / rMean > 0.35f) return false

        // Start and end should be near.
        val start = points.first()
        val end = points.last()
        val closure = hypot(end.x - start.x, end.y - start.y)
        if (closure > rMean * 0.55f) return false

        // Approximate path length and compare to circumference.
        var pathLen = 0f
        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            pathLen += hypot(b.x - a.x, b.y - a.y)
        }

        val circumference = (2.0 * Math.PI * rMean).toFloat()
        val ratio = pathLen / max(1f, circumference)
        if (ratio < 0.55f || ratio > 1.8f) return false

        // Ensure the gesture changes direction enough (avoid straight swipe false positives)
        // by checking that the total turning angle isn't tiny.
        var turning = 0f
        for (i in 2 until points.size) {
            val p0 = points[i - 2]
            val p1 = points[i - 1]
            val p2 = points[i]

            val v1x = p1.x - p0.x
            val v1y = p1.y - p0.y
            val v2x = p2.x - p1.x
            val v2y = p2.y - p1.y

            val cross = v1x * v2y - v1y * v2x
            val dot = v1x * v2x + v1y * v2y
            val ang = abs(kotlin.math.atan2(cross, dot))
            turning += ang
        }

        // A circle will accumulate a fair amount of turning.
        if (turning < Math.PI.toFloat()) return false

        return true
    }

    private fun reset() {
        points.clear()
        activePointerId = -1
        startTimeMs = 0L
    }
}
