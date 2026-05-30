package com.termux.terminal

/**
 * Terminal buffer backed by Rust. Fetches row data on demand via RustJNI.
 */
class TerminalBuffer(
    private val mNativePtr: Long,
    private val mScreenRows: Int,
    private val mColumns: Int
) {

    private val mActiveTranscriptRows: Int = 0 // TODO: expose via Rust
    private val mRowCache: Array<TerminalRow?> = arrayOfNulls(mScreenRows)

    fun getActiveTranscriptRows(): Int {
        return mActiveTranscriptRows
    }

    /** Total active rows (screen + scrollback). */
    fun getActiveRows(): Int {
        return mScreenRows + mActiveTranscriptRows
    }

    fun getScreenRows(): Int {
        return mScreenRows
    }

    fun getmColumns(): Int {
        return mColumns
    }

    fun getLineCount(): Int {
        return getActiveRows()
    }

    /** Map external row index to internal (no-op for flat buffer). */
    fun externalToInternalRow(row: Int): Int {
        return row
    }

    /**
     * Get a TerminalRow populated with data from Rust.
     * The renderer reads line.mText and line.getStyle(col) directly.
     */
    fun allocateFullLineIfNecessary(internalRow: Int): TerminalRow {
        if (internalRow < 0 || internalRow >= mScreenRows) {
            return TerminalRow(mColumns, 0)
        }

        var row = mRowCache[internalRow]
        if (row == null || row.mColumns != mColumns) {
            row = TerminalRow(mColumns)
            mRowCache[internalRow] = row
        }

        // Populate from Rust
        populateRowFromRust(row, internalRow)
        return row
    }

    /** Fetch char+style data from Rust for each column. */
    private fun populateRowFromRust(row: TerminalRow, screenRow: Int) {
        var lastNonSpace = 0
        for (col in 0 until mColumns) {
            val raw = RustJNI.termEmulatorGetCharAt(mNativePtr, screenRow, col)
            val codePoint = (raw and 0x7FFFFFFF).toInt()
            val style = raw ushr 32

            // Store as char (BMP only)
            if (codePoint in 0x20..0xFFFF) {
                row.mText[col] = codePoint.toChar()
            } else if (codePoint == 0) {
                row.mText[col] = ' '
            } else {
                row.mText[col] = '\uFFFD' // replacement char
            }
            row.mStyle[col] = style

            if (row.mText[col] != ' ') {
                lastNonSpace = col + 1
            }
        }
        row.mSpaceUsed = lastNonSpace
    }

    /** Invalidate the row cache (call after processBytes). */
    fun invalidateCache() {
        for (i in mRowCache.indices) {
            mRowCache[i] = null
        }
    }

    /** Get style at position. */
    fun getStyleAt(row: Int, col: Int): Long {
        val raw = RustJNI.termEmulatorGetCharAt(mNativePtr, row, col)
        return raw ushr 32
    }

    /** Get char at position. */
    fun getChar(row: Int, col: Int): Int {
        val raw = RustJNI.termEmulatorGetCharAt(mNativePtr, row, col)
        return (raw and 0x7FFFFFFF).toInt()
    }

    fun isBlankRow(row: Int): Boolean {
        for (c in 0 until mColumns) {
            if (getChar(row, c) != ' '.code && getChar(row, c) != 0) return false
        }
        return true
    }

    fun getSpaceUsedAt(row: Int): Int {
        var last = 0
        for (c in 0 until mColumns) {
            if (getChar(row, c) != ' '.code && getChar(row, c) != 0) last = c + 1
        }
        return last
    }

    /** Get selected text via Rust JNI. */
    fun getSelectedText(x1: Int, y1: Int, x2: Int, y2: Int): String {
        return RustJNI.termEmulatorGetSelectedText(mNativePtr, x1, y1, x2, y2) ?: ""
    }

    /** Get transcript text with full lines joined. */
    fun getTranscriptTextWithFullLinesJoined(): String {
        return getTranscriptText(true)
    }

    /** Get transcript text without joined lines. */
    fun getTranscriptTextWithoutJoinedLines(): String {
        return getTranscriptText(false)
    }

    /** No-arg version (defaults to joined). */
    fun getTranscriptText(): String {
        return getTranscriptText(true)
    }

    private fun getTranscriptText(joinLines: Boolean): String {
        val sb = StringBuilder()
        val rows = getActiveRows()
        for (row in 0 until rows) {
            val line = allocateFullLineIfNecessary(row)
            val used = line.getSpaceUsed()
            if (used > 0) {
                sb.append(line.mText, 0, used)
                if (!joinLines && row < rows - 1) sb.append('\n')
            }
            if (joinLines && row < rows - 1 && used > 0) sb.append('\n')
        }
        return sb.toString()
    }

    /** Find the word at the given column/row. Scans left and right for word boundaries. */
    fun getWordAtLocation(col: Int, row: Int): String {
        if (row < 0 || row >= mScreenRows) return ""
        val line = allocateFullLineIfNecessary(row)
        if (line.getSpaceUsed() == 0) return ""

        val used = line.getSpaceUsed()
        val text = line.mText

        // Find word boundaries
        var start = col
        var end = col

        // Move left to find word start
        while (start > 0 && start < used && isWordChar(text[start - 1])) start--
        if (start < used && !isWordChar(text[start])) start = col

        // Move right to find word end
        while (end < used && isWordChar(text[end])) end++

        if (start == end) return ""
        return String(text, start, end - start)
    }
}

private fun isWordChar(c: Char): Boolean {
    return c.isLetterOrDigit() || c == '_'
}
