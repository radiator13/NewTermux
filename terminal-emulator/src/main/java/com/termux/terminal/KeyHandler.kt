package com.termux.terminal

import java.nio.charset.StandardCharsets

/**
 * Key handler: maps Android KeyEvent keycodes + modifiers to terminal escape sequences.
 * The actual mapping logic lives in Rust (key_handler.rs).
 * Only constants and the delegate method remain here.
 */
object KeyHandler {

    const val KEYMOD_ALT = -0x80000000.toInt()
    const val KEYMOD_CTRL = 0x40000000
    const val KEYMOD_SHIFT = 0x20000000
    const val KEYMOD_NUM_LOCK = 0x10000000

    /**
     * Get the escape sequence to send for a given keyCode + modifiers.
     * Returns null if the key has no special terminal mapping.
     */
    @JvmStatic
    fun getCode(keyCode: Int, keyMode: Int, cursorApp: Boolean, keypadApplication: Boolean): String? {
        val bytes = RustJNI.termKeyHandlerGetCode(keyCode, keyMode, cursorApp, keypadApplication)
        if (bytes == null || bytes.isEmpty()) return null
        return String(bytes, StandardCharsets.UTF_8)
    }
}
