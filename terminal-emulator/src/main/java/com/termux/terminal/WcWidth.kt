package com.termux.terminal

/**
 * Implementation of wcwidth(3) for Unicode 15.
 *
 * Delegates to Rust via RustJNI.termWcwidth() for the actual lookup.
 * Lookup tables (ZERO_WIDTH, WIDE_EASTASIAN) and binary search are now in Rust.
 */
object WcWidth {

    /** Return the terminal display width of a code point: 0, 1 or 2. */
    @JvmStatic
    fun width(ucs: Int): Int {
        return RustJNI.termWcwidth(ucs)
    }

    /** The width at an index position in a java char array. */
    @JvmStatic
    fun width(chars: CharArray, index: Int): Int {
        val c = chars[index]
        return if (Character.isHighSurrogate(c)) width(Character.toCodePoint(c, chars[index + 1])) else width(c.toInt())
    }

    /**
     * The zero width characters count like combining characters in the `chars` array from start
     * index to end index (exclusive).
     */
    @JvmStatic
    fun zeroWidthCharsCount(chars: CharArray, start: Int, end: Int): Int {
        if (start < 0 || start >= chars.size)
            return 0

        var count = 0
        var i = start
        while (i < end && i < chars.size) {
            if (Character.isHighSurrogate(chars[i])) {
                if (width(Character.toCodePoint(chars[i], chars[i + 1])) <= 0) {
                    count++
                }
                i += 2
            } else {
                if (width(chars[i].toInt()) <= 0) {
                    count++
                }
                i++
            }
        }
        return count
    }
}
