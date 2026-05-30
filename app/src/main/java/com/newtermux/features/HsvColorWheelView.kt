package com.newtermux.features

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Custom View rendering a 2-D HS disc (angle = hue 0–360°, radius = saturation 0–1).
 * Brightness is controlled externally via a SeekBar.
 */
class HsvColorWheelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    interface OnColorChangedListener {
        fun onColorChanged(color: Int)
    }

    private var mBitmap: Bitmap? = null
    private val mPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mSelectorPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var mHue = 0f
    private var mSaturation = 0f
    private var mBrightness = 1f

    private var mListener: OnColorChangedListener? = null

    init {
        mSelectorPaint.style = Paint.Style.STROKE
        mSelectorPaint.strokeWidth = 3f
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        generateBitmap(w, h)
    }

    private fun generateBitmap(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        val size = min(w, h)
        mBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val cx = size / 2f
        val cy = size / 2f
        val radius = size / 2f
        val pixels = IntArray(size * size)
        val hsv = floatArrayOf(0f, 0f, mBrightness)
        for (y in 0 until size) {
            for (x in 0 until size) {
                val dx = x - cx
                val dy = y - cy
                val dist = sqrt(dx * dx + dy * dy)
                if (dist > radius) {
                    pixels[y * size + x] = 0
                } else {
                    var angle = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
                    if (angle < 0) angle += 360f
                    hsv[0] = angle
                    hsv[1] = dist / radius
                    pixels[y * size + x] = Color.HSVToColor(hsv)
                }
            }
        }
        mBitmap?.setPixels(pixels, 0, size, 0, 0, size, size)
    }

    /** Call when brightness changes externally; regenerates the disc bitmap. */
    fun setBrightness(brightness: Float) {
        mBrightness = brightness
        generateBitmap(width, height)
        invalidate()
    }

    fun setOnColorChangedListener(listener: OnColorChangedListener?) {
        mListener = listener
    }

    fun setColor(argb: Int) {
        val hsv = FloatArray(3)
        Color.colorToHSV(argb, hsv)
        mHue = hsv[0]
        mSaturation = hsv[1]
        mBrightness = hsv[2]
        generateBitmap(width, height)
        invalidate()
    }

    fun getColor(): Int = Color.HSVToColor(floatArrayOf(mHue, mSaturation, mBrightness))

    fun getBrightness(): Float = mBrightness

    override fun onDraw(canvas: Canvas) {
        val bmp = mBitmap ?: return
        val w = width
        val h = height
        val size = min(w, h)
        val offsetX = (w - size) / 2f
        val offsetY = (h - size) / 2f
        canvas.drawBitmap(bmp, offsetX, offsetY, mPaint)

        val cx = offsetX + size / 2f
        val cy = offsetY + size / 2f
        val radius = size / 2f
        val angle = Math.toRadians(mHue.toDouble()).toFloat()
        val sx = cx + cos(angle) * mSaturation * radius
        val sy = cy + sin(angle) * mSaturation * radius

        mSelectorPaint.color = Color.BLACK
        canvas.drawCircle(sx, sy, 10f, mSelectorPaint)
        mSelectorPaint.color = Color.WHITE
        canvas.drawCircle(sx, sy, 13f, mSelectorPaint)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = min(
            MeasureSpec.getSize(widthMeasureSpec),
            MeasureSpec.getSize(heightMeasureSpec)
        )
        setMeasuredDimension(size, size)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.action
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
            val w = width
            val h = height
            val size = min(w, h)
            val cx = (w - size) / 2f + size / 2f
            val cy = (h - size) / 2f + size / 2f
            val radius = size / 2f
            val dx = event.x - cx
            val dy = event.y - cy
            val dist = sqrt(dx * dx + dy * dy)
            var angle = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
            if (angle < 0) angle += 360f
            mHue = angle
            mSaturation = min(dist / radius, 1f)
            invalidate()
            mListener?.onColorChanged(getColor())
            return true
        }
        return super.onTouchEvent(event)
    }
}
