package com.termux.terminal;

/**
 * JNI bridge to the Rust terminal emulator (termux_rs native library).
 * All methods are static native and map 1:1 to the exported C functions in ffi.rs.
 */
public class RustJNI {

    static {
        System.loadLibrary("termux_rs");
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
    public static native long termEmulatorNew(int cols, int rows, int cellW, int cellH, int transcriptRows);

    /**
     * Free a terminal emulator.
     * @param emulator handle returned by {@link #termEmulatorNew}
     */
    public static native void termEmulatorFree(long emulator);

    /**
     * Resize the terminal.
     * @param emulator handle
     * @param cols     new column count
     * @param rows     new row count
     * @param cellW    new cell width in pixels
     * @param cellH    new cell height in pixels
     */
    public static native void termEmulatorResize(long emulator, int cols, int rows, int cellW, int cellH);

    /**
     * Reset the terminal to its initial state.
     * @param emulator handle
     */
    public static native void termEmulatorReset(long emulator);

    // ======================== PROCESSING ========================

    /**
     * Feed raw bytes into the terminal parser.
     * @param emulator handle
     * @param data     byte array containing input data
     * @param offset   start offset within the array
     * @param length   number of bytes to process
     */
    public static native void termEmulatorProcessBytes(long emulator, byte[] data, int offset, int length);

    /**
     * Read pending output (e.g. responses to escape sequences) into a buffer.
     * @param emulator handle
     * @param buf      destination buffer
     * @param maxLen   maximum bytes to read
     * @return number of bytes actually written into buf
     */
    public static native int termEmulatorGetOutput(long emulator, byte[] buf, int maxLen);

    /**
     * Get the current terminal flags.
     * @param emulator handle
     * @return flags bitmask
     */
    public static native int termEmulatorGetFlags(long emulator);

    /**
     * Paste a string into the terminal (bracketed paste if enabled).
     * @param emulator handle
     * @param str      text to paste
     */
    public static native void termEmulatorPaste(long emulator, String str);

    // ======================== STATE ========================

    /**
     * Get the character (and style info) at a given cell.
     * @param emulator handle
     * @param row      0-based row
     * @param col      0-based column
     * @return packed character/style value
     */
    public static native long termEmulatorGetCharAt(long emulator, int row, int col);

    /**
     * Get the current cursor row.
     * @param emulator handle
     * @return 0-based row index
     */
    public static native int termEmulatorGetCursorRow(long emulator);

    /**
     * Get the current cursor column.
     * @param emulator handle
     * @return 0-based column index
     */
    public static native int termEmulatorGetCursorCol(long emulator);

    /**
     * Get the current cursor style.
     * @param emulator handle
     * @return cursor style identifier
     */
    public static native int termEmulatorGetCursorStyle(long emulator);

    /**
     * Get the number of rows.
     * @param emulator handle
     * @return row count
     */
    public static native int termEmulatorGetRows(long emulator);

    /**
     * Get the number of columns.
     * @param emulator handle
     * @return column count
     */
    public static native int termEmulatorGetColumns(long emulator);

    /**
     * Get a color from the terminal palette.
     * @param emulator handle
     * @param index    color index in the palette
     * @return ARGB color value
     */
    public static native int termEmulatorGetColor(long emulator, int index);

    /**
     * Check if reverse video mode is active.
     * @param emulator handle
     * @return true if reverse video is on
     */
    public static native boolean termEmulatorIsReverseVideo(long emulator);

    /**
     * Check if the alternate screen buffer is active.
     * @param emulator handle
     * @return true if alternate buffer is in use
     */
    public static native boolean termEmulatorIsAlternateBufferActive(long emulator);

    /**
     * Check if mouse tracking is active.
     * @param emulator handle
     * @return true if mouse tracking is enabled
     */
    public static native boolean termEmulatorIsMouseTrackingActive(long emulator);

    // ======================== MOUSE ========================

    /**
     * Send a mouse event to the terminal.
     * @param emulator handle
     * @param button   button identifier
     * @param col      0-based column
     * @param row      0-based row
     * @param pressed  true if the button was pressed, false if released
     */
    public static native void termEmulatorSendMouseEvent(long emulator, int button, int col, int row, boolean pressed);

    // ======================== SCROLL ========================

    /**
     * Get the scroll counter (number of lines scrolled since last clear).
     * @param emulator handle
     * @return scroll counter value
     */
    public static native int termEmulatorGetScrollCounter(long emulator);

    /**
     * Reset the scroll counter to zero.
     * @param emulator handle
     */
    public static native void termEmulatorClearScrollCounter(long emulator);

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
    public static native String termEmulatorGetSelectedText(long emulator, int x1, int y1, int x2, int y2);

    /**
     * Compute draw runs for a row (for rendering).
     * @param emulator handle
     * @param row      0-based row index
     * @return array of draw run descriptors
     */
    public static native int[] termEmulatorComputeDrawRuns(long emulator, int row);

    // ======================== WCWIDTH ========================

    /**
     * Get the display width of a Unicode code point.
     * @param codePoint Unicode code point
     * @return display width (0, 1, or 2)
     */
    public static native int termWcwidth(int codePoint);

    // ======================== BYTE QUEUE ========================

    /**
     * Create a new byte queue.
     * @param size capacity in bytes
     * @return pointer (handle) to the queue
     */
    public static native long termByteQueueNew(int size);

    /**
     * Free a byte queue.
     * @param queue handle returned by {@link #termByteQueueNew}
     */
    public static native void termByteQueueFree(long queue);

    /**
     * Write data into the byte queue.
     * @param queue  handle
     * @param data   source byte array
     * @param offset start offset within the array
     * @param length number of bytes to write
     * @return true if all data was written, false if the queue was closed before completion
     */
    public static native boolean termByteQueueWrite(long queue, byte[] data, int offset, int length);

    /**
     * Read data from the byte queue.
     * @param queue    handle
     * @param buf      destination buffer
     * @param maxLen   maximum number of bytes to read (typically buf.length)
     * @param blocking if true, block until data is available
     * @return number of bytes read, or -1 if the queue was closed
     */
    public static native int termByteQueueRead(long queue, byte[] buf, int maxLen, boolean blocking);

    /**
     * Close the byte queue (signals EOF to readers).
     * @param queue handle
     */
    public static native void termByteQueueClose(long queue);

    // ── Key Handler ──────────────────────────────────────────────────────────
    /** Get escape sequence bytes for keycode+modifiers. Returns null if no mapping. */
    public static native byte[] termKeyHandlerGetCode(int keyCode, int keyMode, boolean cursorApp, boolean keypadApp);
}
