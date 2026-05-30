package com.termux.terminal;

/**
 * Key handler: maps Android KeyEvent keycodes + modifiers to terminal escape sequences.
 * The actual mapping logic lives in Rust (key_handler.rs).
 * Only constants and the delegate method remain here.
 */
public final class KeyHandler {

    public static final int KEYMOD_ALT = 0x80000000;
    public static final int KEYMOD_CTRL = 0x40000000;
    public static final int KEYMOD_SHIFT = 0x20000000;
    public static final int KEYMOD_NUM_LOCK = 0x10000000;

    private KeyHandler() {} // not instantiable

    /**
     * Get the escape sequence to send for a given keyCode + modifiers.
     * Returns null if the key has no special terminal mapping.
     */
    public static String getCode(int keyCode, int keyMode, boolean cursorApp, boolean keypadApplication) {
        byte[] bytes = RustJNI.termKeyHandlerGetCode(keyCode, keyMode, cursorApp, keypadApplication);
        if (bytes == null || bytes.length == 0) return null;
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }
}
