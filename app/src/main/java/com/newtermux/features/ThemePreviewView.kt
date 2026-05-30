package com.newtermux.features

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View

/** Draws a mini terminal mockup for the theme picker grid. */
class ThemePreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var mBg = 0
    private var mFg = 0
    private var mToolbar = 0
    private var mGreen = 0
    private var mCursor = 0
    private var mAccent = 0
    private var mActive = false

    private val mFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mText = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mBorder = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        mBorder.style = Paint.Style.STROKE
        mText.typeface = Typeface.MONOSPACE
    }

    /** colors: {bg, fg, toolbar, green, cursor} */
    fun setTheme(colors: IntArray, active: Boolean, accentColor: Int) {
        mBg = colors[0]
        mFg = colors[1]
        mToolbar = colors[2]
        mGreen = colors[3]
        mCursor = colors[4]
        mActive = active
        mAccent = accentColor
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val d = resources.displayMetrics.density
        val w = width
        val h = height

        // Terminal background
        mFill.color = mBg
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), mFill)

        // Toolbar strip
        val toolH = 11 * d
        mFill.color = mToolbar
        canvas.drawRect(0f, 0f, w.toFloat(), toolH, mFill)

        // Window-control dots
        val dotY = toolH / 2f
        val dotR = 1.8f * d
        mFill.color = 0xFFFF5F57.toInt()
        canvas.drawCircle(5.5f * d, dotY, dotR, mFill)
        mFill.color = 0xFFFFBD2E.toInt()
        canvas.drawCircle(10.5f * d, dotY, dotR, mFill)
        mFill.color = 0xFF28CA41.toInt()
        canvas.drawCircle(15.5f * d, dotY, dotR, mFill)

        // Terminal text lines
        val ts = 6f * d
        mText.textSize = ts
        val x = 4 * d
        var y = toolH + 8 * d
        val lh = 8 * d

        // Line 1: prompt + command
        mText.color = mGreen
        val prompt = "$ "
        canvas.drawText(prompt, x, y, mText)
        val pw = mText.measureText(prompt)
        mText.color = mFg
        canvas.drawText("ls -la", x + pw, y, mText)

        // Line 2: dim output
        y += lh
        mText.color = dim(mFg)
        canvas.drawText("total 8", x, y, mText)

        // Line 3: colored directory entry
        y += lh
        mText.color = mGreen
        val perm = "drwx "
        canvas.drawText(perm, x, y, mText)
        val permW = mText.measureText(perm)
        mText.color = mFg
        canvas.drawText("home", x + permW, y, mText)

        // Line 4: next prompt + block cursor
        if (y + lh + 2 * d < h) {
            y += lh
            mText.color = mGreen
            canvas.drawText(prompt, x, y, mText)
            val cx2 = x + mText.measureText(prompt)
            mFill.color = mCursor
            canvas.drawRect(cx2, y - ts, cx2 + 5 * d, y + 1.5f * d, mFill)
        }

        // Active border
        if (mActive) {
            val sw = 2.5f * d
            mBorder.color = mAccent
            mBorder.strokeWidth = sw
            val half = sw / 2f
            canvas.drawRect(half, half, w - half, h - half, mBorder)
        }
    }

    companion object {
        private fun dim(color: Int): Int {
            val r = ((color shr 16 and 0xFF) * 0.55f).toInt()
            val g = ((color shr 8 and 0xFF) * 0.55f).toInt()
            val b = ((color and 0xFF) * 0.55f).toInt()
            return 0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
        }
    }
}
