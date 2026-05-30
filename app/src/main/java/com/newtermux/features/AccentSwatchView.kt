package com.newtermux.features

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.View

/** Draws a colored circle swatch for the accent color picker grid. */
class AccentSwatchView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var mColor: Int = 0
    private var mActive: Boolean = false
    private var mIsCustomSlot: Boolean = false

    private val mFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mBorder = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mCheck = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        mBorder.style = Paint.Style.STROKE
        mCheck.color = 0xFFFFFFFF.toInt()
        mCheck.textAlign = Paint.Align.CENTER
        mCheck.isFakeBoldText = true
    }

    fun setColor(color: Int, active: Boolean, isCustomSlot: Boolean) {
        mColor = color
        mActive = active
        mIsCustomSlot = isCustomSlot
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val d = resources.displayMetrics.density
        val cx = width / 2f
        val cy = height / 2f
        val radius = minOf(cx, cy) - 3 * d

        if (mIsCustomSlot) {
            val sg = SweepGradient(cx, cy,
                intArrayOf(0xFFFF0000.toInt(), 0xFFFF8C00.toInt(), 0xFFFFFF00.toInt(),
                           0xFF00CC00.toInt(), 0xFF0088FF.toInt(), 0xFF8800FF.toInt(), 0xFFFF0000.toInt()),
                null)
            mFill.shader = sg
            canvas.drawCircle(cx, cy, radius, mFill)
            mFill.shader = null
        } else {
            mFill.color = mColor
            canvas.drawCircle(cx, cy, radius, mFill)
        }

        if (mActive) {
            val sw = 2.5f * d
            mBorder.color = 0xFFFFFFFF.toInt()
            mBorder.strokeWidth = sw
            canvas.drawCircle(cx, cy, radius - sw / 2f, mBorder)

            mCheck.textSize = 14 * d
            canvas.drawText("✓", cx, cy + 5 * d, mCheck)
        }
    }
}
