package com.termux.shared.data

import android.os.Bundle
import com.google.common.base.Strings
import java.io.ByteArrayOutputStream
import java.io.ObjectOutputStream
import java.io.Serializable

object DataUtils {

    /** Max safe limit of data size to prevent TransactionTooLargeException when transferring data
     * inside or to other apps via transactions. */
    const val TRANSACTION_SIZE_LIMIT_IN_BYTES = 100 * 1024 // 100KB

    private val HEX_ARRAY = "0123456789ABCDEF".toCharArray()

    @JvmStatic
    fun getTruncatedCommandOutput(text: String?, maxLength: Int, fromEnd: Boolean, onNewline: Boolean, addPrefix: Boolean): String? {
        if (text == null) return null

        val prefix = "(truncated) "

        var effectiveMaxLength = if (addPrefix) maxLength - prefix.length else maxLength

        if (effectiveMaxLength < 0 || text.length < effectiveMaxLength) return text

        var result: String = if (fromEnd) {
            text.substring(0, effectiveMaxLength)
        } else {
            var cutOffIndex = text.length - effectiveMaxLength

            if (onNewline) {
                val nextNewlineIndex = text.indexOf('\n', cutOffIndex)
                if (nextNewlineIndex != -1 && nextNewlineIndex != text.length - 1) {
                    cutOffIndex = nextNewlineIndex + 1
                }
            }
            text.substring(cutOffIndex)
        }

        if (addPrefix) {
            result = prefix + result
        }

        return result
    }

    /**
     * Replace a sub string in each item of a [String][].
     *
     * @param array The [String][] to replace in.
     * @param find The sub string to replace.
     * @param replace The sub string to replace with.
     */
    @JvmStatic
    fun replaceSubStringsInStringArrayItems(array: Array<String>?, find: String, replace: String) {
        if (array.isNullOrEmpty()) return

        for (i in array.indices) {
            array[i] = array[i].replace(find, replace)
        }
    }

    /**
     * Get the `float` from a [String].
     *
     * @param value The [String] value.
     * @param def The default value if failed to read a valid value.
     * @return Returns the `float` value after parsing the [String] value, otherwise
     * returns default if failed to read a valid value, like in case of an exception.
     */
    @JvmStatic
    fun getFloatFromString(value: String?, def: Float): Float {
        if (value == null) return def

        return try {
            java.lang.Float.parseFloat(value)
        } catch (e: Exception) {
            def
        }
    }

    /**
     * Get the `int` from a [String].
     *
     * @param value The [String] value.
     * @param def The default value if failed to read a valid value.
     * @return Returns the `int` value after parsing the [String] value, otherwise
     * returns default if failed to read a valid value, like in case of an exception.
     */
    @JvmStatic
    fun getIntFromString(value: String?, def: Int): Int {
        if (value == null) return def

        return try {
            Integer.parseInt(value)
        } catch (e: Exception) {
            def
        }
    }

    /**
     * Get the `String` from an [Integer].
     *
     * @param value The [Integer] value.
     * @param def The default [String] value.
     * @return Returns `value` if it is not `null`, otherwise returns `def`.
     */
    @JvmStatic
    fun getStringFromInteger(value: Int?, def: String): String {
        return if (value == null) def else value.toString()
    }

    /**
     * Get the `hex string` from a [byte][].
     *
     * @param bytes The [byte][] value.
     * @return Returns the `hex string` value.
     */
    @JvmStatic
    fun bytesToHex(bytes: ByteArray): String {
        val hexChars = CharArray(bytes.size * 2)
        for (j in bytes.indices) {
            val v = bytes[j].toInt() and 0xFF
            hexChars[j * 2] = HEX_ARRAY[v ushr 4]
            hexChars[j * 2 + 1] = HEX_ARRAY[v and 0x0F]
        }
        return String(hexChars)
    }

    /**
     * Get an `int` from [Bundle] that is stored as a [String].
     *
     * @param bundle The [Bundle] to get the value from.
     * @param key The key for the value.
     * @param def The default value if failed to read a valid value.
     * @return Returns the `int` value after parsing the [String] value stored in
     * [Bundle], otherwise returns default if failed to read a valid value,
     * like in case of an exception.
     */
    @JvmStatic
    fun getIntStoredAsStringFromBundle(bundle: Bundle?, key: String, def: Int): Int {
        if (bundle == null) return def
        return getIntFromString(bundle.getString(key, Integer.toString(def)), def)
    }

    /**
     * If value is not in the range [min, max], set it to either min or max.
     */
    @JvmStatic
    fun clamp(value: Int, min: Int, max: Int): Int {
        return Math.min(Math.max(value, min), max)
    }

    /**
     * If value is not in the range [min, max], set it to default.
     */
    @JvmStatic
    fun rangedOrDefault(value: Float, def: Float, min: Float, max: Float): Float {
        return if (value < min || value > max) def else value
    }

    /**
     * Add a space indent to a [String]. Each indent is 4 space characters long.
     *
     * @param string The [String] to add indent to.
     * @param count The indent count.
     * @return Returns the indented [String].
     */
    @JvmStatic
    fun getSpaceIndentedString(string: String?, count: Int): String? {
        return if (string.isNullOrEmpty()) string
        else getIndentedString(string, "    ", count)
    }

    /**
     * Add a tab indent to a [String]. Each indent is 1 tab character long.
     *
     * @param string The [String] to add indent to.
     * @param count The indent count.
     * @return Returns the indented [String].
     */
    @JvmStatic
    fun getTabIndentedString(string: String?, count: Int): String? {
        return if (string.isNullOrEmpty()) string
        else getIndentedString(string, "\t", count)
    }

    /**
     * Add an indent to a [String].
     *
     * @param string The [String] to add indent to.
     * @param indent The indent characters.
     * @param count The indent count.
     * @return Returns the indented [String].
     */
    @JvmStatic
    fun getIndentedString(string: String?, indent: String, count: Int): String? {
        return if (string.isNullOrEmpty()) string
        else string.replaceFirst("(?m)^".toRegex(), Strings.repeat(indent, Math.max(count, 1)))
    }

    /**
     * Get the object itself if it is not `null`, otherwise default.
     *
     * @param object The [Object] to check.
     * @param def The default [Object].
     * @return Returns `object` if it is not `null`, otherwise returns `def`.
     */
    @JvmStatic
    fun <T> getDefaultIfNull(`object`: T?, def: T?): T? {
        return `object` ?: def
    }

    /**
     * Get the [String] itself if it is not `null` or empty, otherwise default.
     *
     * @param value The [String] to check.
     * @param def The default [String].
     * @return Returns `value` if it is not `null` or empty, otherwise returns `def`.
     */
    @JvmStatic
    fun getDefaultIfUnset(value: String?, def: String): String {
        return if (value.isNullOrEmpty()) def else value
    }

    /** Check if a string is null or empty. */
    @JvmStatic
    fun isNullOrEmpty(string: String?): Boolean {
        return string.isNullOrEmpty()
    }

    /** Get size of a serializable object. */
    @JvmStatic
    fun getSerializedSize(`object`: Serializable?): Long {
        if (`object` == null) return 0
        return try {
            val byteOutputStream = ByteArrayOutputStream()
            val objectOutputStream = ObjectOutputStream(byteOutputStream)
            objectOutputStream.writeObject(`object`)
            objectOutputStream.flush()
            objectOutputStream.close()
            byteOutputStream.toByteArray().size.toLong()
        } catch (e: Exception) {
            -1
        }
    }
}
