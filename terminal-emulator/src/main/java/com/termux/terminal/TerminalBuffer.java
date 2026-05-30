package com.termux.terminal;

/**
 * Terminal buffer backed by Rust. Fetches row data on demand via RustJNI.
 */
public final class TerminalBuffer {

    private final long mNativePtr;
    private final int mScreenRows;
    private final int mColumns;
    private final int mActiveTranscriptRows;
    private final TerminalRow[] mRowCache;

    public TerminalBuffer(long nativePtr, int screenRows, int columns) {
        mNativePtr = nativePtr;
        mScreenRows = screenRows;
        mColumns = columns;
        mActiveTranscriptRows = 0; // TODO: expose via Rust
        mRowCache = new TerminalRow[screenRows];
    }

    public int getActiveTranscriptRows() {
        return mActiveTranscriptRows;
    }

    /** Total active rows (screen + scrollback). */
    public int getActiveRows() {
        return mScreenRows + mActiveTranscriptRows;
    }

    public int getScreenRows() {
        return mScreenRows;
    }

    public int getmColumns() {
        return mColumns;
    }

    public int getLineCount() {
        return getActiveRows();
    }

    /** Map external row index to internal (no-op for flat buffer). */
    public int externalToInternalRow(int row) {
        return row;
    }

    /**
     * Get a TerminalRow populated with data from Rust.
     * The renderer reads line.mText and line.getStyle(col) directly.
     */
    public TerminalRow allocateFullLineIfNecessary(int internalRow) {
        if (internalRow < 0 || internalRow >= mScreenRows) {
            return new TerminalRow(mColumns, 0);
        }

        TerminalRow row = mRowCache[internalRow];
        if (row == null || row.mColumns != mColumns) {
            row = new TerminalRow(mColumns);
            mRowCache[internalRow] = row;
        }

        // Populate from Rust
        populateRowFromRust(row, internalRow);
        return row;
    }

    /** Fetch char+style data from Rust for each column. */
    private void populateRowFromRust(TerminalRow row, int screenRow) {
        int lastNonSpace = 0;
        for (int col = 0; col < mColumns; col++) {
            long raw = RustJNI.termEmulatorGetCharAt(mNativePtr, screenRow, col);
            int codePoint = (int) (raw & 0x7FFFFFFF);
            long style = raw >>> 32;

            // Store as char (BMP only)
            if (codePoint >= 0x20 && codePoint <= 0xFFFF) {
                row.mText[col] = (char) codePoint;
            } else if (codePoint == 0) {
                row.mText[col] = ' ';
            } else {
                row.mText[col] = '\uFFFD'; // replacement char
            }
            row.mStyle[col] = style;

            if (row.mText[col] != ' ') {
                lastNonSpace = col + 1;
            }
        }
        row.mSpaceUsed = lastNonSpace;
    }

    /** Invalidate the row cache (call after processBytes). */
    public void invalidateCache() {
        for (int i = 0; i < mRowCache.length; i++) {
            mRowCache[i] = null;
        }
    }

    /** Get style at position. */
    public long getStyleAt(int row, int col) {
        long raw = RustJNI.termEmulatorGetCharAt(mNativePtr, row, col);
        return raw >>> 32;
    }

    /** Get char at position. */
    public int getChar(int row, int col) {
        long raw = RustJNI.termEmulatorGetCharAt(mNativePtr, row, col);
        return (int) (raw & 0x7FFFFFFF);
    }

    public boolean isBlankRow(int row) {
        for (int c = 0; c < mColumns; c++) {
            if (getChar(row, c) != ' ' && getChar(row, c) != 0) return false;
        }
        return true;
    }

    public int getSpaceUsedAt(int row) {
        int last = 0;
        for (int c = 0; c < mColumns; c++) {
            if (getChar(row, c) != ' ' && getChar(row, c) != 0) last = c + 1;
        }
        return last;
    }

    /** Get selected text via Rust JNI. */
    public String getSelectedText(int x1, int y1, int x2, int y2) {
        return RustJNI.termEmulatorGetSelectedText(mNativePtr, x1, y1, x2, y2);
    }

    /** Get transcript text with full lines joined. */
    public String getTranscriptTextWithFullLinesJoined() {
        return getTranscriptText(true);
    }

    /** Get transcript text without joined lines. */
    public String getTranscriptTextWithoutJoinedLines() {
        return getTranscriptText(false);
    }

    /** No-arg version (defaults to joined). */
    public String getTranscriptText() {
        return getTranscriptText(true);
    }

    private String getTranscriptText(boolean joinLines) {
        StringBuilder sb = new StringBuilder();
        int rows = getActiveRows();
        for (int row = 0; row < rows; row++) {
            TerminalRow line = allocateFullLineIfNecessary(row);
            int used = line.getSpaceUsed();
            if (used > 0) {
                sb.append(line.mText, 0, used);
                if (!joinLines && row < rows - 1) sb.append('\n');
            }
            if (joinLines && row < rows - 1 && used > 0) sb.append('\n');
        }
        return sb.toString();
    }

    /** Find the word at the given column/row. Scans left and right for word boundaries. */
    public String getWordAtLocation(int col, int row) {
        if (row < 0 || row >= mScreenRows) return "";
        TerminalRow line = allocateFullLineIfNecessary(row);
        if (line.getSpaceUsed() == 0) return "";

        int used = line.getSpaceUsed();
        char[] text = line.mText;

        // Find word boundaries
        int start = col;
        int end = col;

        // Move left to find word start
        while (start > 0 && start < used && isWordChar(text[start - 1])) start--;
        if (start < used && !isWordChar(text[start])) start = col;

        // Move right to find word end
        while (end < used && isWordChar(text[end])) end++;

        if (start == end) return "";
        return new String(text, start, end - start);
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }
}
