package com.termux.terminal;

/**
 * Implementation of wcwidth(3) for Unicode 15.
 *
 * Delegates to Rust via RustJNI.termWcwidth() for the actual lookup.
 * Lookup tables (ZERO_WIDTH, WIDE_EASTASIAN) and binary search are now in Rust.
 */
public final class WcWidth {

    /** Return the terminal display width of a code point: 0, 1 or 2. */
    public static int width(int ucs) {
        return RustJNI.termWcwidth(ucs);
    }

    /** The width at an index position in a java char array. */
    public static int width(char[] chars, int index) {
        char c = chars[index];
        return Character.isHighSurrogate(c) ? width(Character.toCodePoint(c, chars[index + 1])) : width(c);
    }

    /**
     * The zero width characters count like combining characters in the `chars` array from start
     * index to end index (exclusive).
     */
    public static int zeroWidthCharsCount(char[] chars, int start, int end) {
        if (start < 0 || start >= chars.length)
            return 0;

        int count = 0;
        for (int i = start; i < end && i < chars.length;) {
            if (Character.isHighSurrogate(chars[i])) {
                if (width(Character.toCodePoint(chars[i], chars[i + 1])) <= 0) {
                    count++;
                }
                i += 2;
            } else {
                if (width(chars[i]) <= 0) {
                    count++;
                }
                i++;
            }
        }
        return count;
    }

}
