package com.termux.terminal

/**
 * Single terminal row — now a thin data holder populated from Rust.
 * The renderer reads mText and mStyle directly, so we keep those fields.
 */
class TerminalRow {
    /** The characters in this row. Populated from Rust via getCharAt. */
    @JvmField var mText: CharArray
    /** Per-column style. Populated from Rust via getCharAt. */
    @JvmField var mStyle: LongArray
    @JvmField var mSpaceUsed: Int
    @JvmField val mColumns: Int

    constructor(columns: Int) {
        mColumns = columns
        mText = CharArray(columns)
        mStyle = LongArray(columns)
        mSpaceUsed = columns
    }

    constructor(columns: Int, style: Long) : this(columns) {
        for (i in 0 until columns) {
            mStyle[i] = style
        }
    }

    /** Get style at column. */
    fun getStyle(column: Int): Long {
        return if (column in 0 until mStyle.size) mStyle[column] else 0L
    }

    /** Check if this row is blank. */
    fun isBlank(): Boolean {
        for (i in 0 until mSpaceUsed) {
            if (mText[i] != ' ') return false
        }
        return true
    }

    /** No-op: Rust handles all storage now. */
    fun clear(style: Long) {
        for (i in 0 until mColumns) {
            mText[i] = ' '
            mStyle[i] = style
        }
        mSpaceUsed = mColumns
    }

    /** Get number of chars used (non-space at end). */
    fun getSpaceUsed(): Int {
        return mSpaceUsed
    }

    /** No-op stubs for compatibility */
    fun copyInterval(line: TerminalRow, srcX1: Int, srcX2: Int, destX: Int) {}
    fun setChar(columnToSet: Int, codePoint: Int, style: Long) {}
}
