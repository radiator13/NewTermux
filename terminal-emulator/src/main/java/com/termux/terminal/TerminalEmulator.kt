package com.termux.terminal

/**
 * Thin Java wrapper around the Rust terminal emulator (via [RustJNI]).
 * All escape-sequence processing, buffer management, and cursor state live in Rust.
 * This class provides the same public API so callers (TerminalSession, TerminalView,
 * TerminalRenderer) continue to work unchanged.
 */
class TerminalEmulator(
    private var mTerminalOutput: TerminalOutput,
    columns: Int,
    rows: Int,
    cellWidth: Int,
    cellHeight: Int,
    transcriptRows: Int?,
    client: TerminalSessionClient?
) {

    // -- Public constants (kept for callers) --
    companion object {
        const val MOUSE_LEFT_BUTTON = 0
        const val MOUSE_LEFT_BUTTON_MOVED = 32
        const val MOUSE_WHEELUP_BUTTON = 64
        const val MOUSE_WHEELDOWN_BUTTON = 65
        const val UNICODE_REPLACEMENT_CHAR = 0xFFFD
        const val TERMINAL_CURSOR_STYLE_BLOCK = 0
        const val TERMINAL_CURSOR_STYLE_UNDERLINE = 1
        const val TERMINAL_CURSOR_STYLE_BAR = 2
        const val DEFAULT_TERMINAL_CURSOR_STYLE = TERMINAL_CURSOR_STYLE_BLOCK
        const val TERMINAL_TRANSCRIPT_ROWS_MIN = 100
        const val TERMINAL_TRANSCRIPT_ROWS_MAX = 500000
        const val DEFAULT_TRANSCRIPT_ROWS = 5000
        const val DEFAULT_TERMINAL_TRANSCRIPT_ROWS = DEFAULT_TRANSCRIPT_ROWS
    }

    // -- Public fields (accessed by TerminalView / TerminalRenderer) --
    @JvmField var mRows: Int = rows
    @JvmField var mColumns: Int = columns
    @JvmField val mColors = TerminalColors()

    // -- Callback interfaces --
    private var mClient: TerminalSessionClient? = client

    // -- Native pointer --
    private var mNativePtr: Long
    private var mDisposed = false

    // -- Java-side state synced from Rust flags --
    private var mTitle = ""
    private var mAlternateBufferActive = false
    private var mCursorBlinkingEnabled = true
    private var mCursorBlinkState = false
    private var mAutoScrollDisabled = false
    private var mScrollCounter = 0

    // -- Screen buffer (backed by Rust) --
    private var mScreen: TerminalBuffer? = null

    init {
        val trRows = transcriptRows ?: DEFAULT_TRANSCRIPT_ROWS
        mNativePtr = RustJNI.termEmulatorNew(columns, rows, cellWidth, cellHeight, trRows)
        if (mNativePtr == 0L) {
            throw OutOfMemoryError("Failed to allocate native TerminalEmulator")
        }
    }

    // -- Lifecycle --

    fun updateTerminalSessionClient(client: TerminalSessionClient?) {
        mClient = client
    }

    @Synchronized
    fun dispose() {
        if (!mDisposed && mNativePtr != 0L) {
            mDisposed = true
            RustJNI.termEmulatorFree(mNativePtr)
            mNativePtr = 0
        }
    }

    @Throws(Throwable::class)
    protected fun finalize() {
        try {
            dispose()
        } finally {
            // super.finalize() not needed in Kotlin
        }
    }

    // -- Hot path: process bytes from PTY --

    fun append(data: ByteArray, length: Int) {
        if (mDisposed || mNativePtr == 0L) return
        RustJNI.termEmulatorProcessBytes(mNativePtr, data, 0, length)
    }

    // -- Read output bytes to send back to PTY --

    fun getOutput(buf: ByteArray, maxLen: Int): Int {
        if (mDisposed || mNativePtr == 0L) return 0
        return RustJNI.termEmulatorGetOutput(mNativePtr, buf, maxLen)
    }

    // -- Resize --

    fun resize(columns: Int, rows: Int, cellWidth: Int, cellHeight: Int) {
        mColumns = columns
        mRows = rows
        mScreen = null // Will be recreated with new dimensions
        if (!mDisposed && mNativePtr != 0L) {
            RustJNI.termEmulatorResize(mNativePtr, columns, rows, cellWidth, cellHeight)
        }
    }

    // -- Reset --

    fun reset() {
        if (!mDisposed && mNativePtr != 0L) {
            RustJNI.termEmulatorReset(mNativePtr)
        }
        mTitle = ""
        mScrollCounter = 0
    }

    // -- Title --

    fun getTitle(): String {
        return mTitle
    }

    // -- Cursor --

    fun getCursorRow(): Int {
        return if (mDisposed || mNativePtr == 0L) 0 else RustJNI.termEmulatorGetCursorRow(mNativePtr)
    }

    fun getCursorCol(): Int {
        return if (mDisposed || mNativePtr == 0L) 0 else RustJNI.termEmulatorGetCursorCol(mNativePtr)
    }

    fun getCursorStyle(): Int {
        return if (mDisposed || mNativePtr == 0L) DEFAULT_TERMINAL_CURSOR_STYLE
        else RustJNI.termEmulatorGetCursorStyle(mNativePtr)
    }

    fun shouldCursorBeVisible(): Boolean {
        return mCursorBlinkingEnabled && mCursorBlinkState
    }

    fun isCursorEnabled(): Boolean {
        return mCursorBlinkingEnabled
    }

    fun setCursorBlinkState(blinking: Boolean) {
        mCursorBlinkState = blinking
    }

    fun setCursorBlinkingEnabled(enabled: Boolean) {
        mCursorBlinkingEnabled = enabled
    }

    // -- Mode flags --

    fun isMouseTrackingActive(): Boolean {
        return !mDisposed && mNativePtr != 0L
                && RustJNI.termEmulatorIsMouseTrackingActive(mNativePtr)
    }

    fun isAlternateBufferActive(): Boolean {
        return mAlternateBufferActive
    }

    fun isReverseVideo(): Boolean {
        return !mDisposed && mNativePtr != 0L
                && RustJNI.termEmulatorIsReverseVideo(mNativePtr)
    }

    fun isCursorKeysApplicationMode(): Boolean {
        return false // TODO: expose via Rust flags
    }

    fun isKeypadApplicationMode(): Boolean {
        return false // TODO: expose via Rust flags
    }

    // -- Scroll --

    fun getScrollCounter(): Int {
        if (!mDisposed && mNativePtr != 0L) {
            mScrollCounter = RustJNI.termEmulatorGetScrollCounter(mNativePtr)
        }
        return mScrollCounter
    }

    fun clearScrollCounter() {
        mScrollCounter = 0
        if (!mDisposed && mNativePtr != 0L) {
            RustJNI.termEmulatorClearScrollCounter(mNativePtr)
        }
    }

    fun isAutoScrollDisabled(): Boolean {
        return mAutoScrollDisabled
    }

    fun toggleAutoScrollDisabled() {
        mAutoScrollDisabled = !mAutoScrollDisabled
    }

    // -- Mouse --

    fun sendMouseEvent(button: Int, col: Int, row: Int, pressed: Boolean) {
        if (!mDisposed && mNativePtr != 0L) {
            RustJNI.termEmulatorSendMouseEvent(mNativePtr, button, col, row, pressed)
        }
    }

    // -- Paste --

    fun paste(str: String?) {
        if (!mDisposed && mNativePtr != 0L && str != null && !str.isEmpty()) {
            RustJNI.termEmulatorPaste(mNativePtr, str)
        }
    }

    // -- Selection / rendering --

    fun getSelectedText(x1: Int, y1: Int, x2: Int, y2: Int): String {
        if (mDisposed || mNativePtr == 0L) return ""
        return RustJNI.termEmulatorGetSelectedText(mNativePtr, x1, y1, x2, y2) ?: ""
    }

    fun computeDrawRuns(row: Int): IntArray {
        if (mDisposed || mNativePtr == 0L) return IntArray(0)
        return RustJNI.termEmulatorComputeDrawRuns(mNativePtr, row) ?: IntArray(0)
    }

    fun getCharAt(row: Int, col: Int): Long {
        if (mDisposed || mNativePtr == 0L) return 0
        return RustJNI.termEmulatorGetCharAt(mNativePtr, row, col)
    }

    fun getColor(index: Int): Int {
        if (mDisposed || mNativePtr == 0L) return 0
        return RustJNI.termEmulatorGetColor(mNativePtr, index)
    }

    // -- Screen buffer (backed by Rust) --

    fun getScreen(): TerminalBuffer? {
        if (mScreen == null && !mDisposed && mNativePtr != 0L) {
            mScreen = TerminalBuffer(mNativePtr, mRows, mColumns)
        }
        return mScreen
    }

    // -- Sync Java-side state from Rust callback flags --

    /**
     * Returns the callback flags from the last processBytes call.
     * Callers (TerminalSession) should check these and dispatch to their clients.
     * Bit 0: bell, Bit 1: title changed, Bit 2: colors changed, Bit 3: cursor changed.
     */
    fun getLastFlags(): Int {
        if (mDisposed || mNativePtr == 0L) return 0
        return RustJNI.termEmulatorGetFlags(mNativePtr)
    }

    /**
     * Sync colors from Rust into mColors.mCurrentColors.
     */
    fun syncColorsFromRust() {
        if (mDisposed || mNativePtr == 0L) return
        for (i in mColors.mCurrentColors.indices) {
            mColors.mCurrentColors[i] = RustJNI.termEmulatorGetColor(mNativePtr, i)
        }
    }
}
