package com.termux.terminal

/**
 * JNI bridge to the Rust terminal emulator (termux_rs native library).
 * All methods are static native and map 1:1 to the exported C functions in ffi.rs.
 */
object RustJNI {

    init {
        System.loadLibrary("termux_rs")
    }

    // ======================== LIFECYCLE ========================

    /**
     * Create a new terminal emulator.
     * @param cols       number of columns
     * @param rows       number of rows
     * @param cellW      cell width in pixels
     * @param cellH      cell height in pixels
     * @param transcriptRows number of rows to keep in scrollback
     * @return pointer (handle) to the emulator instance
     */
    @JvmStatic external fun termEmulatorNew(cols: Int, rows: Int, cellW: Int, cellH: Int, transcriptRows: Int): Long

    /**
     * Free a terminal emulator.
     * @param emulator handle returned by [termEmulatorNew]
     */
    @JvmStatic external fun termEmulatorFree(emulator: Long)

    /**
     * Resize the terminal.
     * @param emulator handle
     * @param cols     new column count
     * @param rows     new row count
     * @param cellW    new cell width in pixels
     * @param cellH    new cell height in pixels
     */
    @JvmStatic external fun termEmulatorResize(emulator: Long, cols: Int, rows: Int, cellW: Int, cellH: Int)

    /**
     * Reset the terminal to its initial state.
     * @param emulator handle
     */
    @JvmStatic external fun termEmulatorReset(emulator: Long)

    // ======================== PROCESSING ========================

    /**
     * Feed raw bytes into the terminal parser.
     * @param emulator handle
     * @param data     byte array containing input data
     * @param offset   start offset within the array
     * @param length   number of bytes to process
     */
    @JvmStatic external fun termEmulatorProcessBytes(emulator: Long, data: ByteArray, offset: Int, length: Int)

    /**
     * Read pending output (e.g. responses to escape sequences) into a buffer.
     * @param emulator handle
     * @param buf      destination buffer
     * @param maxLen   maximum bytes to read
     * @return number of bytes actually written into buf
     */
    @JvmStatic external fun termEmulatorGetOutput(emulator: Long, buf: ByteArray, maxLen: Int): Int

    /**
     * Get the current terminal flags.
     * @param emulator handle
     * @return flags bitmask
     */
    @JvmStatic external fun termEmulatorGetFlags(emulator: Long): Int

    /**
     * Paste a string into the terminal (bracketed paste if enabled).
     * @param emulator handle
     * @param str      text to paste
     */
    @JvmStatic external fun termEmulatorPaste(emulator: Long, str: String)

    // ======================== STATE ========================

    /**
     * Get the character (and style info) at a given cell.
     * @param emulator handle
     * @param row      0-based row
     * @param col      0-based column
     * @return packed character/style value
     */
    @JvmStatic external fun termEmulatorGetCharAt(emulator: Long, row: Int, col: Int): Long

    /**
     * Get the current cursor row.
     * @param emulator handle
     * @return 0-based row index
     */
    @JvmStatic external fun termEmulatorGetCursorRow(emulator: Long): Int

    /**
     * Get the current cursor column.
     * @param emulator handle
     * @return 0-based column index
     */
    @JvmStatic external fun termEmulatorGetCursorCol(emulator: Long): Int

    /**
     * Get the current cursor style.
     * @param emulator handle
     * @return cursor style identifier
     */
    @JvmStatic external fun termEmulatorGetCursorStyle(emulator: Long): Int

    /**
     * Get the number of rows.
     * @param emulator handle
     * @return row count
     */
    @JvmStatic external fun termEmulatorGetRows(emulator: Long): Int

    /**
     * Get the number of columns.
     * @param emulator handle
     * @return column count
     */
    @JvmStatic external fun termEmulatorGetColumns(emulator: Long): Int

    /**
     * Get a color from the terminal palette.
     * @param emulator handle
     * @param index    color index in the palette
     * @return ARGB color value
     */
    @JvmStatic external fun termEmulatorGetColor(emulator: Long, index: Int): Int

    /**
     * Check if reverse video mode is active.
     * @param emulator handle
     * @return true if reverse video is on
     */
    @JvmStatic external fun termEmulatorIsReverseVideo(emulator: Long): Boolean

    /**
     * Check if the alternate screen buffer is active.
     * @param emulator handle
     * @return true if alternate buffer is in use
     */
    @JvmStatic external fun termEmulatorIsAlternateBufferActive(emulator: Long): Boolean

    /**
     * Check if mouse tracking is active.
     * @param emulator handle
     * @return true if mouse tracking is enabled
     */
    @JvmStatic external fun termEmulatorIsMouseTrackingActive(emulator: Long): Boolean

    // ======================== MOUSE ========================

    /**
     * Send a mouse event to the terminal.
     * @param emulator handle
     * @param button   button identifier
     * @param col      0-based column
     * @param row      0-based row
     * @param pressed  true if the button was pressed, false if released
     */
    @JvmStatic external fun termEmulatorSendMouseEvent(emulator: Long, button: Int, col: Int, row: Int, pressed: Boolean)

    // ======================== SCROLL ========================

    /**
     * Get the scroll counter (number of lines scrolled since last clear).
     * @param emulator handle
     * @return scroll counter value
     */
    @JvmStatic external fun termEmulatorGetScrollCounter(emulator: Long): Int

    /**
     * Reset the scroll counter to zero.
     * @param emulator handle
     */
    @JvmStatic external fun termEmulatorClearScrollCounter(emulator: Long)

    // ======================== TITLE / TEXT ========================

    /**
     * Get the text content within a rectangular selection.
     * @param emulator handle
     * @param x1       start column
     * @param y1       start row
     * @param x2       end column
     * @param y2       end row
     * @return selected text
     */
    @JvmStatic external fun termEmulatorGetSelectedText(emulator: Long, x1: Int, y1: Int, x2: Int, y2: Int): String?

    /**
     * Compute draw runs for a row (for rendering).
     * @param emulator handle
     * @param row      0-based row index
     * @return array of draw run descriptors
     */
    @JvmStatic external fun termEmulatorComputeDrawRuns(emulator: Long, row: Int): IntArray?

    // ======================== WCWIDTH ========================

    /**
     * Get the display width of a Unicode code point.
     * @param codePoint Unicode code point
     * @return display width (0, 1, or 2)
     */
    @JvmStatic external fun termWcwidth(codePoint: Int): Int

    // ======================== BYTE QUEUE ========================

    /**
     * Create a new byte queue.
     * @param size capacity in bytes
     * @return pointer (handle) to the queue
     */
    @JvmStatic external fun termByteQueueNew(size: Int): Long

    /**
     * Free a byte queue.
     * @param queue handle returned by [termByteQueueNew]
     */
    @JvmStatic external fun termByteQueueFree(queue: Long)

    /**
     * Write data into the byte queue.
     * @param queue  handle
     * @param data   source byte array
     * @param offset start offset within the array
     * @param length number of bytes to write
     * @return true if all data was written, false if the queue was closed before completion
     */
    @JvmStatic external fun termByteQueueWrite(queue: Long, data: ByteArray, offset: Int, length: Int): Boolean

    /**
     * Read data from the byte queue.
     * @param queue    handle
     * @param buf      destination buffer
     * @param maxLen   maximum number of bytes to read (typically buf.length)
     * @param blocking if true, block until data is available
     * @return number of bytes read, or -1 if the queue was closed
     */
    @JvmStatic external fun termByteQueueRead(queue: Long, buf: ByteArray, maxLen: Int, blocking: Boolean): Int

    /**
     * Close the byte queue (signals EOF to readers).
     * @param queue handle
     */
    @JvmStatic external fun termByteQueueClose(queue: Long)

    // -- Key Handler --
    /** Get escape sequence bytes for keycode+modifiers. Returns null if no mapping. */
    @JvmStatic external fun termKeyHandlerGetCode(keyCode: Int, keyMode: Int, cursorApp: Boolean, keypadApp: Boolean): ByteArray?
}
