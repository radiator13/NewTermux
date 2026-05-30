package com.termux.terminal

/**
 * Text style constants. The actual encoding/decoding lives in Rust (text_style.rs).
 * These constants are used by TerminalColorScheme, TerminalColors, and TerminalRenderer.
 */
object TextStyle {

    // -- Effect flags --
    const val CHARACTER_ATTRIBUTE_BOLD = 1
    const val CHARACTER_ATTRIBUTE_ITALIC = 1 shl 1
    const val CHARACTER_ATTRIBUTE_UNDERLINE = 1 shl 2
    const val CHARACTER_ATTRIBUTE_BLINK = 1 shl 3
    const val CHARACTER_ATTRIBUTE_INVERSE = 1 shl 4
    const val CHARACTER_ATTRIBUTE_INVISIBLE = 1 shl 5
    const val CHARACTER_ATTRIBUTE_STRIKETHROUGH = 1 shl 6
    const val CHARACTER_ATTRIBUTE_DIM = 1 shl 7
    const val CHARACTER_ATTRIBUTE_HIDDEN = 1 shl 8
    const val CHARACTER_ATTRIBUTE_OVERLINE = 1 shl 9
    const val CHARACTER_ATTRIBUTE_UNDERLINE_DOUBLE = 1 shl 10

    // Aliases for Rust naming convention
    const val FX_BOLD = CHARACTER_ATTRIBUTE_BOLD
    const val FX_ITALIC = CHARACTER_ATTRIBUTE_ITALIC
    const val FX_UNDERLINE = CHARACTER_ATTRIBUTE_UNDERLINE
    const val FX_BLINK = CHARACTER_ATTRIBUTE_BLINK
    const val FX_INVERSE = CHARACTER_ATTRIBUTE_INVERSE
    const val FX_INVISIBLE = CHARACTER_ATTRIBUTE_INVISIBLE
    const val FX_STRIKETHROUGH = CHARACTER_ATTRIBUTE_STRIKETHROUGH
    const val FX_DIM = CHARACTER_ATTRIBUTE_DIM
    const val FX_HIDDEN = CHARACTER_ATTRIBUTE_HIDDEN
    const val FX_OVERLINE = CHARACTER_ATTRIBUTE_OVERLINE
    const val FX_UNDERLINE_DOUBLE = CHARACTER_ATTRIBUTE_UNDERLINE_DOUBLE

    // -- Color indices --
    const val COLOR_INDEX_FOREGROUND = 256
    const val COLOR_INDEX_BACKGROUND = 257
    const val COLOR_INDEX_CURSOR = 258
    const val NUM_INDEXED_COLORS = 259

    // -- Color encoding flags --
    /** If set, the color value is a 24-bit RGB value. Otherwise it's an index. */
    const val COLOR_RGB = 1 shl 25

    /** Encode a color value with the RGB flag. */
    @JvmStatic
    fun encode(foreColor: Int, backColor: Int, effect: Int): Long {
        var style = effect.toLong() and 0x7FFL
        style = style or ((backColor.toLong() and 0x1FFFFFF) shl 16)
        style = style or ((foreColor.toLong() and 0x1FFFFFF) shl 40)
        return style
    }

    @JvmStatic
    fun decodeForeColor(style: Long): Int {
        return ((style ushr 40) and 0x1FFFFFF).toInt()
    }

    @JvmStatic
    fun decodeBackColor(style: Long): Int {
        return ((style ushr 16) and 0x1FFFFFF).toInt()
    }

    @JvmStatic
    fun decodeEffect(style: Long): Int {
        return (style and 0x7FF).toInt()
    }

    /** Check if the color is an RGB value (vs indexed). */
    @JvmStatic
    fun isColorRGB(color: Int): Boolean {
        return (color and COLOR_RGB) != 0
    }
}
