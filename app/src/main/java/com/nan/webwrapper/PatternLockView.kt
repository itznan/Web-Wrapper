package com.nan.webwrapper

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.google.android.material.color.MaterialColors
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

class PatternLockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var patternSize = 3 // 3x3 grid = 9 dots
    private var dotRadius = 0f
    private var dotSpacing = 0f
    private var lineWidth = 0f
    
    private val dots = mutableListOf<Dot>()
    private val pattern = mutableListOf<Int>()
    private var currentPath: Path? = null
    private var lastDotIndex = -1
    
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    
    private val selectedDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    
    var onPatternComplete: ((List<Int>) -> Unit)? = null
    var patternColor: Int = 0
    var selectedColor: Int = 0
    var lineColor: Int = 0
    
    init {
        patternColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutline)
        selectedColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary)
        lineColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary)
        
        dotPaint.color = patternColor
        selectedDotPaint.color = selectedColor
        linePaint.color = lineColor
    }
    
    data class Dot(
        val x: Float,
        val y: Float,
        val index: Int
    )
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        calculateDimensions()
        initializeDots()
    }
    
    private fun calculateDimensions() {
        val width = width.toFloat()
        val height = height.toFloat()
        val size = min(width, height)
        
        dotRadius = size * 0.08f
        dotSpacing = size / (patternSize + 1)
        lineWidth = dotRadius * 0.5f
        linePaint.strokeWidth = lineWidth
    }
    
    private fun initializeDots() {
        dots.clear()
        val startX = dotSpacing
        val startY = dotSpacing
        
        for (row in 0 until patternSize) {
            for (col in 0 until patternSize) {
                val x = startX + col * dotSpacing
                val y = startY + row * dotSpacing
                val index = row * patternSize + col
                dots.add(Dot(x, y, index))
            }
        }
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Draw lines first (behind dots)
        currentPath?.let { path ->
            canvas.drawPath(path, linePaint)
        }
        
        // Draw dots
        dots.forEachIndexed { index, dot ->
            val paint = if (pattern.contains(index)) selectedDotPaint else dotPaint
            canvas.drawCircle(dot.x, dot.y, dotRadius, paint)
            
            // Draw inner circle for selected dots
            if (pattern.contains(index)) {
                canvas.drawCircle(dot.x, dot.y, dotRadius * 0.4f, selectedDotPaint)
            }
        }
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                pattern.clear()
                currentPath = Path()
                handleTouch(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                handleTouch(event.x, event.y)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (pattern.isNotEmpty()) {
                    onPatternComplete?.invoke(pattern.toList())
                }
                pattern.clear()
                currentPath = null
                lastDotIndex = -1
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }
    
    private fun handleTouch(x: Float, y: Float) {
        val dot = findNearestDot(x, y)
        
        if (dot != null) {
            val dotIndex = dot.index
            if (!pattern.contains(dotIndex)) {
                // Add intermediate dots if needed
                if (lastDotIndex != -1 && lastDotIndex != dotIndex) {
                    addIntermediateDots(lastDotIndex, dotIndex)
                }
                pattern.add(dotIndex)
                lastDotIndex = dotIndex
                
                // Update path
                currentPath?.let { path ->
                    if (pattern.size == 1) {
                        path.moveTo(dot.x, dot.y)
                    } else {
                        path.lineTo(dot.x, dot.y)
                    }
                } ?: run {
                    currentPath = Path().apply {
                        moveTo(dot.x, dot.y)
                    }
                }
            }
        } else if (lastDotIndex != -1) {
            // Continue line to current touch point
            currentPath?.let { path ->
                val lastDot = dots[lastDotIndex]
                path.moveTo(lastDot.x, lastDot.y)
                path.lineTo(x, y)
            }
        }
    }
    
    private fun findNearestDot(x: Float, y: Float): Dot? {
        val threshold = dotRadius * 2f
        return dots.minByOrNull { dot ->
            val dx = dot.x - x
            val dy = dot.y - y
            sqrt(dx * dx + dy * dy)
        }?.takeIf { dot ->
            val dx = dot.x - x
            val dy = dot.y - y
            sqrt(dx * dx + dy * dy) <= threshold
        }
    }
    
    private fun addIntermediateDots(from: Int, to: Int) {
        val fromRow = from / patternSize
        val fromCol = from % patternSize
        val toRow = to / patternSize
        val toCol = to % patternSize
        
        val rowDiff = toRow - fromRow
        val colDiff = toCol - fromCol
        
        // Check if dots are aligned (same row, same column, or diagonal)
        if (rowDiff == 0 || colDiff == 0 || abs(rowDiff) == abs(colDiff)) {
            val steps = maxOf(abs(rowDiff), abs(colDiff))
            if (steps > 1) {
                for (i in 1 until steps) {
                    val intermediateRow = fromRow + (rowDiff * i / steps)
                    val intermediateCol = fromCol + (colDiff * i / steps)
                    val intermediateIndex = intermediateRow * patternSize + intermediateCol
                    if (!pattern.contains(intermediateIndex)) {
                        pattern.add(intermediateIndex)
                    }
                }
            }
        }
    }
    
    fun reset() {
        pattern.clear()
        currentPath = null
        lastDotIndex = -1
        invalidate()
    }
    
    fun setPattern(patternList: List<Int>) {
        pattern.clear()
        pattern.addAll(patternList)
        invalidate()
    }
}

