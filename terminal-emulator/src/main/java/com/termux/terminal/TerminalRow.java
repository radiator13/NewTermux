package com.termux.terminal;

/**
 * Single terminal row — now a thin data holder populated from Rust.
 * The renderer reads mText and mStyle directly, so we keep those fields.
 */
public final class TerminalRow {
    /** The characters in this row. Populated from Rust via getCharAt. */
    public char[] mText;
    /** Per-column style. Populated from Rust via getCharAt. */
    public long[] mStyle;
    public int mSpaceUsed;
    public final int mColumns;

    public TerminalRow(int columns) {
        mColumns = columns;
        mText = new char[columns];
        mStyle = new long[columns];
        mSpaceUsed = columns;
    }

    public TerminalRow(int columns, long style) {
        this(columns);
        for (int i = 0; i < columns; i++) {
            mStyle[i] = style;
        }
    }

    /** Get style at column. */
    public long getStyle(int column) {
        return (column >= 0 && column < mStyle.length) ? mStyle[column] : 0;
    }

    /** Check if this row is blank. */
    public boolean isBlank() {
        for (int i = 0; i < mSpaceUsed; i++) {
            if (mText[i] != ' ') return false;
        }
        return true;
    }

    /** No-op: Rust handles all storage now. */
    public void clear(long style) {
        for (int i = 0; i < mColumns; i++) {
            mText[i] = ' ';
            mStyle[i] = style;
        }
        mSpaceUsed = mColumns;
    }

    /** Get number of chars used (non-space at end). */
    public int getSpaceUsed() {
        return mSpaceUsed;
    }

    /** No-op stubs for compatibility */
    public void copyInterval(TerminalRow line, int srcX1, int srcX2, int destX) {}
    public void setChar(int columnToSet, int codePoint, long style) {}
}
