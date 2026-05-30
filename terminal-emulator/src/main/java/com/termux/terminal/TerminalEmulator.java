package com.termux.terminal;

/**
 * Thin Java wrapper around the Rust terminal emulator (via {@link RustJNI}).
 * All escape-sequence processing, buffer management, and cursor state live in Rust.
 * This class provides the same public API so callers (TerminalSession, TerminalView,
 * TerminalRenderer) continue to work unchanged.
 */
public final class TerminalEmulator {

    // ── Public constants (kept for callers) ─────────────────────────────────
    public static final int MOUSE_LEFT_BUTTON = 0;
    public static final int MOUSE_LEFT_BUTTON_MOVED = 32;
    public static final int MOUSE_WHEELUP_BUTTON = 64;
    public static final int MOUSE_WHEELDOWN_BUTTON = 65;
    public static final int UNICODE_REPLACEMENT_CHAR = 0xFFFD;
    public static final int TERMINAL_CURSOR_STYLE_BLOCK = 0;
    public static final int TERMINAL_CURSOR_STYLE_UNDERLINE = 1;
    public static final int TERMINAL_CURSOR_STYLE_BAR = 2;
    public static final int DEFAULT_TERMINAL_CURSOR_STYLE = TERMINAL_CURSOR_STYLE_BLOCK;
    public static final int TERMINAL_TRANSCRIPT_ROWS_MIN = 100;
    public static final int TERMINAL_TRANSCRIPT_ROWS_MAX = 500000;
    public static final int DEFAULT_TRANSCRIPT_ROWS = 5000;
    public static final int DEFAULT_TERMINAL_TRANSCRIPT_ROWS = DEFAULT_TRANSCRIPT_ROWS;

    // ── Public fields (accessed by TerminalView / TerminalRenderer) ──────────
    public int mRows;
    public int mColumns;
    public final TerminalColors mColors = new TerminalColors();

    // ── Callback interfaces ─────────────────────────────────────────────────
    private final TerminalOutput mTerminalOutput;
    private TerminalSessionClient mClient;

    // ── Native pointer ──────────────────────────────────────────────────────
    private long mNativePtr;
    private boolean mDisposed;

    // ── Java-side state synced from Rust flags ──────────────────────────────
    private String mTitle = "";
    private boolean mAlternateBufferActive;
    private boolean mCursorBlinkingEnabled = true;
    private boolean mCursorBlinkState;
    private boolean mAutoScrollDisabled;
    private int mScrollCounter;

    // ── Constructor ─────────────────────────────────────────────────────────

    public TerminalEmulator(TerminalOutput output, int columns, int rows,
                            int cellWidth, int cellHeight,
                            Integer transcriptRows,
                            TerminalSessionClient client) {
        mTerminalOutput = output;
        mClient = client;
        mColumns = columns;
        mRows = rows;

        int trRows = (transcriptRows != null) ? transcriptRows : DEFAULT_TRANSCRIPT_ROWS;
        mNativePtr = RustJNI.termEmulatorNew(columns, rows, cellWidth, cellHeight, trRows);
        if (mNativePtr == 0) {
            throw new OutOfMemoryError("Failed to allocate native TerminalEmulator");
        }
    }

    // ── Lifecycle ───────────────────────────────────────────────────────────

    public void updateTerminalSessionClient(TerminalSessionClient client) {
        mClient = client;
    }

    public synchronized void dispose() {
        if (!mDisposed && mNativePtr != 0) {
            mDisposed = true;
            RustJNI.termEmulatorFree(mNativePtr);
            mNativePtr = 0;
        }
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            dispose();
        } finally {
            super.finalize();
        }
    }

    // ── Hot path: process bytes from PTY ────────────────────────────────────

    public void append(byte[] data, int length) {
        if (mDisposed || mNativePtr == 0) return;
        RustJNI.termEmulatorProcessBytes(mNativePtr, data, 0, length);
    }

    // ── Read output bytes to send back to PTY ───────────────────────────────

    public int getOutput(byte[] buf, int maxLen) {
        if (mDisposed || mNativePtr == 0) return 0;
        return RustJNI.termEmulatorGetOutput(mNativePtr, buf, maxLen);
    }

    // ── Resize ──────────────────────────────────────────────────────────────

    public void resize(int columns, int rows, int cellWidth, int cellHeight) {
        mColumns = columns;
        mRows = rows;
        mScreen = null; // Will be recreated with new dimensions
        if (!mDisposed && mNativePtr != 0) {
            RustJNI.termEmulatorResize(mNativePtr, columns, rows, cellWidth, cellHeight);
        }
    }

    // ── Reset ───────────────────────────────────────────────────────────────

    public void reset() {
        if (!mDisposed && mNativePtr != 0) {
            RustJNI.termEmulatorReset(mNativePtr);
        }
        mTitle = "";
        mScrollCounter = 0;
    }

    // ── Title ───────────────────────────────────────────────────────────────

    public String getTitle() {
        return mTitle;
    }

    // ── Cursor ──────────────────────────────────────────────────────────────

    public int getCursorRow() {
        return (mDisposed || mNativePtr == 0) ? 0 : RustJNI.termEmulatorGetCursorRow(mNativePtr);
    }

    public int getCursorCol() {
        return (mDisposed || mNativePtr == 0) ? 0 : RustJNI.termEmulatorGetCursorCol(mNativePtr);
    }

    public int getCursorStyle() {
        return (mDisposed || mNativePtr == 0) ? DEFAULT_TERMINAL_CURSOR_STYLE
                : RustJNI.termEmulatorGetCursorStyle(mNativePtr);
    }

    public boolean shouldCursorBeVisible() {
        return mCursorBlinkingEnabled && mCursorBlinkState;
    }

    public boolean isCursorEnabled() {
        return mCursorBlinkingEnabled;
    }

    public void setCursorBlinkState(boolean blinking) {
        mCursorBlinkState = blinking;
    }

    public void setCursorBlinkingEnabled(boolean enabled) {
        mCursorBlinkingEnabled = enabled;
    }

    // ── Mode flags ──────────────────────────────────────────────────────────

    public boolean isMouseTrackingActive() {
        return !mDisposed && mNativePtr != 0
                && RustJNI.termEmulatorIsMouseTrackingActive(mNativePtr);
    }

    public boolean isAlternateBufferActive() {
        return mAlternateBufferActive;
    }

    public boolean isReverseVideo() {
        return !mDisposed && mNativePtr != 0
                && RustJNI.termEmulatorIsReverseVideo(mNativePtr);
    }

    public boolean isCursorKeysApplicationMode() {
        return false; // TODO: expose via Rust flags
    }

    public boolean isKeypadApplicationMode() {
        return false; // TODO: expose via Rust flags
    }

    // ── Scroll ──────────────────────────────────────────────────────────────

    public int getScrollCounter() {
        if (!mDisposed && mNativePtr != 0) {
            mScrollCounter = RustJNI.termEmulatorGetScrollCounter(mNativePtr);
        }
        return mScrollCounter;
    }

    public void clearScrollCounter() {
        mScrollCounter = 0;
        if (!mDisposed && mNativePtr != 0) {
            RustJNI.termEmulatorClearScrollCounter(mNativePtr);
        }
    }

    public boolean isAutoScrollDisabled() {
        return mAutoScrollDisabled;
    }

    public void toggleAutoScrollDisabled() {
        mAutoScrollDisabled = !mAutoScrollDisabled;
    }

    // ── Mouse ───────────────────────────────────────────────────────────────

    public void sendMouseEvent(int button, int col, int row, boolean pressed) {
        if (!mDisposed && mNativePtr != 0) {
            RustJNI.termEmulatorSendMouseEvent(mNativePtr, button, col, row, pressed);
        }
    }

    // ── Paste ───────────────────────────────────────────────────────────────

    public void paste(String str) {
        if (!mDisposed && mNativePtr != 0 && str != null && !str.isEmpty()) {
            RustJNI.termEmulatorPaste(mNativePtr, str);
        }
    }

    // ── Selection / rendering ───────────────────────────────────────────────

    public String getSelectedText(int x1, int y1, int x2, int y2) {
        if (mDisposed || mNativePtr == 0) return "";
        return RustJNI.termEmulatorGetSelectedText(mNativePtr, x1, y1, x2, y2);
    }

    public int[] computeDrawRuns(int row) {
        if (mDisposed || mNativePtr == 0) return new int[0];
        return RustJNI.termEmulatorComputeDrawRuns(mNativePtr, row);
    }

    public long getCharAt(int row, int col) {
        if (mDisposed || mNativePtr == 0) return 0;
        return RustJNI.termEmulatorGetCharAt(mNativePtr, row, col);
    }

    public int getColor(int index) {
        if (mDisposed || mNativePtr == 0) return 0;
        return RustJNI.termEmulatorGetColor(mNativePtr, index);
    }

    // ── Screen buffer (backed by Rust) ──────────────────────────────────────

    private TerminalBuffer mScreen;

    public TerminalBuffer getScreen() {
        if (mScreen == null && !mDisposed && mNativePtr != 0) {
            mScreen = new TerminalBuffer(mNativePtr, mRows, mColumns);
        }
        return mScreen;
    }

    // ── Sync Java-side state from Rust callback flags ───────────────────────

    /**
     * Returns the callback flags from the last processBytes call.
     * Callers (TerminalSession) should check these and dispatch to their clients.
     * Bit 0: bell, Bit 1: title changed, Bit 2: colors changed, Bit 3: cursor changed.
     */
    public int getLastFlags() {
        if (mDisposed || mNativePtr == 0) return 0;
        return RustJNI.termEmulatorGetFlags(mNativePtr);
    }

    /**
     * Sync colors from Rust into mColors.mCurrentColors.
     */
    public void syncColorsFromRust() {
        if (mDisposed || mNativePtr == 0 || mColors == null) return;
        for (int i = 0; i < mColors.mCurrentColors.length; i++) {
            mColors.mCurrentColors[i] = RustJNI.termEmulatorGetColor(mNativePtr, i);
        }
    }
}
