package org.nova.messages.helpers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.RectF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView

class CropperView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private val matrix = Matrix()
    private val scaleDetector: ScaleGestureDetector
    private val gestureDetector: GestureDetector
    private var scaleFactor = 1.0f

    init {
        scaleType = ScaleType.MATRIX
        scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val factor = detector.scaleFactor
                scaleFactor *= factor
                scaleFactor = scaleFactor.coerceIn(0.1f, 10.0f)
                matrix.postScale(factor, factor, detector.focusX, detector.focusY)
                imageMatrix = matrix
                return true
            }
        })

        gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                matrix.postTranslate(-distanceX, -distanceY)
                imageMatrix = matrix
                return true
            }
        })
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        if (event.action == MotionEvent.ACTION_UP) {
            performClick()
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    fun crop(targetRect: RectF): Bitmap? {
        val drawable = drawable ?: return null
        
        // Create a bitmap that matches the CropperView's current state
        val fullViewBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(fullViewBitmap)
        
        // Apply the same matrix used for the display
        canvas.concat(matrix)
        drawable.draw(canvas)
        
        // Now crop the target area from this transformed bitmap
        val left = targetRect.left.toInt().coerceIn(0, width)
        val top = targetRect.top.toInt().coerceIn(0, height)
        val width = targetRect.width().toInt().coerceAtMost(width - left)
        val height = targetRect.height().toInt().coerceAtMost(height - top)
        
        if (width <= 0 || height <= 0) return null
        
        return Bitmap.createBitmap(fullViewBitmap, left, top, width, height)
    }
}
