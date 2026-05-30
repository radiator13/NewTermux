package com.termux.terminal;

/**
 * Text style constants. The actual encoding/decoding lives in Rust (text_style.rs).
 * These constants are used by TerminalColorScheme, TerminalColors, and TerminalRenderer.
 */
public final class TextStyle {

    // ── Effect flags ────────────────────────────────────────────────────────
    public static final int CHARACTER_ATTRIBUTE_BOLD = 1;
    public static final int CHARACTER_ATTRIBUTE_ITALIC = 1 << 1;
    public static final int CHARACTER_ATTRIBUTE_UNDERLINE = 1 << 2;
    public static final int CHARACTER_ATTRIBUTE_BLINK = 1 << 3;
    public static final int CHARACTER_ATTRIBUTE_INVERSE = 1 << 4;
    public static final int CHARACTER_ATTRIBUTE_INVISIBLE = 1 << 5;
    public static final int CHARACTER_ATTRIBUTE_STRIKETHROUGH = 1 << 6;
    public static final int CHARACTER_ATTRIBUTE_DIM = 1 << 7;
    public static final int CHARACTER_ATTRIBUTE_HIDDEN = 1 << 8;
    public static final int CHARACTER_ATTRIBUTE_OVERLINE = 1 << 9;
    public static final int CHARACTER_ATTRIBUTE_UNDERLINE_DOUBLE = 1 << 10;

    // Aliases for Rust naming convention
    public static final int FX_BOLD = CHARACTER_ATTRIBUTE_BOLD;
    public static final int FX_ITALIC = CHARACTER_ATTRIBUTE_ITALIC;
    public static final int FX_UNDERLINE = CHARACTER_ATTRIBUTE_UNDERLINE;
    public static final int FX_BLINK = CHARACTER_ATTRIBUTE_BLINK;
    public static final int FX_INVERSE = CHARACTER_ATTRIBUTE_INVERSE;
    public static final int FX_INVISIBLE = CHARACTER_ATTRIBUTE_INVISIBLE;
    public static final int FX_STRIKETHROUGH = CHARACTER_ATTRIBUTE_STRIKETHROUGH;
    public static final int FX_DIM = CHARACTER_ATTRIBUTE_DIM;
    public static final int FX_HIDDEN = CHARACTER_ATTRIBUTE_HIDDEN;
    public static final int FX_OVERLINE = CHARACTER_ATTRIBUTE_OVERLINE;
    public static final int FX_UNDERLINE_DOUBLE = CHARACTER_ATTRIBUTE_UNDERLINE_DOUBLE;

    // ── Color indices ───────────────────────────────────────────────────────
    public static final int COLOR_INDEX_FOREGROUND = 256;
    public static final int COLOR_INDEX_BACKGROUND = 257;
    public static final int COLOR_INDEX_CURSOR = 258;
    public static final int NUM_INDEXED_COLORS = 259;

    // ── Color encoding flags ────────────────────────────────────────────────
    /** If set, the color value is a 24-bit RGB value. Otherwise it's an index. */
    static final int COLOR_RGB = 1 << 25;

    /** Encode a color value with the RGB flag. */
    public static final long encode(int foreColor, int backColor, int effect) {
        long style = effect & 0x7FFL;
        style |= ((long) (backColor & 0x1FFFFFF)) << 16;
        style |= ((long) (foreColor & 0x1FFFFFF)) << 40;
        return style;
    }

    public static final int decodeForeColor(long style) {
        return (int) ((style >>> 40) & 0x1FFFFFF);
    }

    public static final int decodeBackColor(long style) {
        return (int) ((style >>> 16) & 0x1FFFFFF);
    }

    public static final int decodeEffect(long style) {
        return (int) (style & 0x7FF);
    }

    /** Check if the color is an RGB value (vs indexed). */
    public static final boolean isColorRGB(int color) {
        return (color & COLOR_RGB) != 0;
    }

    private TextStyle() {} // not instantiable
}
