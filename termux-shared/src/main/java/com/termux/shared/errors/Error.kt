package com.termux.shared.errors

import android.content.Context
import com.termux.shared.logger.Logger
import com.termux.shared.markdown.MarkdownUtils
import java.io.Serializable

class Error : Serializable {

    /** The optional error label. */
    var label: String? = null
        private set

    /** The error type. */
    var type: String? = null
        private set

    /** The error code. */
    var code: Int = 0
        private set

    /** The error message. */
    var message: String? = null
        private set

    /** The error exceptions. */
    private var throwablesList: List<Throwable>? = ArrayList()

    constructor() {
        initError(null, null, null, null)
    }

    constructor(type: String?, code: Int?, message: String?, throwablesList: List<Throwable>?) {
        initError(type, code, message, throwablesList)
    }

    constructor(type: String?, code: Int?, message: String?, throwable: Throwable) {
        initError(type, code, message, listOf(throwable))
    }

    constructor(type: String?, code: Int?, message: String?) {
        initError(type, code, message, null)
    }

    constructor(code: Int?, message: String?, throwablesList: List<Throwable>?) {
        initError(null, code, message, throwablesList)
    }

    constructor(code: Int?, message: String?, throwable: Throwable) {
        initError(null, code, message, listOf(throwable))
    }

    constructor(code: Int?, message: String?) {
        initError(null, code, message, null)
    }

    constructor(message: String?, throwable: Throwable) {
        initError(null, null, message, listOf(throwable))
    }

    constructor(message: String?, throwablesList: List<Throwable>?) {
        initError(null, null, message, throwablesList)
    }

    constructor(message: String?) {
        initError(null, null, message, null)
    }

    private fun initError(type: String?, code: Int?, message: String?, throwablesList: List<Throwable>?) {
        this.type = if (!type.isNullOrEmpty()) type else Errno.TYPE

        this.code = if (code != null && code > Errno.ERRNO_SUCCESS.code) code
                    else Errno.ERRNO_SUCCESS.code

        this.message = message

        if (throwablesList != null) {
            this.throwablesList = throwablesList
        }
    }

    fun setLabel(label: String): Error {
        this.label = label
        return this
    }

    fun prependMessage(message: String?) {
        if (message != null && isStateFailed)
            this.message = message + this.message
    }

    fun appendMessage(message: String?) {
        if (message != null && isStateFailed)
            this.message = this.message + message
    }

    fun getThrowablesList(): List<Throwable> {
        return java.util.Collections.unmodifiableList(throwablesList)
    }

    @Synchronized
    fun setStateFailed(error: Error): Boolean {
        return setStateFailed(error.type, error.code, error.message, null)
    }

    @Synchronized
    fun setStateFailed(error: Error, throwable: Throwable): Boolean {
        return setStateFailed(error.type, error.code, error.message, listOf(throwable))
    }

    @Synchronized
    fun setStateFailed(error: Error, throwablesList: List<Throwable>?): Boolean {
        return setStateFailed(error.type, error.code, error.message, throwablesList)
    }

    @Synchronized
    fun setStateFailed(code: Int, message: String?): Boolean {
        return setStateFailed(this.type, code, message, null)
    }

    @Synchronized
    fun setStateFailed(code: Int, message: String?, throwable: Throwable): Boolean {
        return setStateFailed(this.type, code, message, listOf(throwable))
    }

    @Synchronized
    fun setStateFailed(code: Int, message: String?, throwablesList: List<Throwable>?): Boolean {
        return setStateFailed(this.type, code, message, throwablesList)
    }

    @Synchronized
    fun setStateFailed(type: String?, code: Int, message: String?, throwablesList: List<Throwable>?): Boolean {
        this.message = message
        this.throwablesList = throwablesList

        if (!type.isNullOrEmpty()) {
            this.type = type
        }

        return if (code > Errno.ERRNO_SUCCESS.code) {
            this.code = code
            true
        } else {
            Logger.logWarn(LOG_TAG, "Ignoring invalid error code value \"$code\". Force setting it to RESULT_CODE_FAILED \"${Errno.ERRNO_FAILED.code}\"")
            this.code = Errno.ERRNO_FAILED.code
            false
        }
    }

    val isStateFailed: Boolean
        get() = code > Errno.ERRNO_SUCCESS.code

    override fun toString(): String {
        return getErrorLogString(this)
    }

    fun logErrorAndShowToast(context: Context?, logTag: String?) {
        Logger.logErrorExtended(logTag, getErrorLogString())
        Logger.showToast(context, getMinimalErrorLogString(), true)
    }

    fun getErrorLogString(): String {
        val logString = StringBuilder()

        logString.append(codeString)
        logString.append("\n").append(typeAndMessageLogString)
        if (!throwablesList.isNullOrEmpty())
            logString.append("\n").append(geStackTracesLogString())

        return logString.toString()
    }

    fun getMinimalErrorLogString(): String {
        val logString = StringBuilder()

        logString.append(codeString)
        logString.append(typeAndMessageLogString)

        return logString.toString()
    }

    fun getMinimalErrorString(): String {
        val logString = StringBuilder()

        logString.append("(").append(code).append(") ")
        logString.append(type).append(": ").append(message)

        return logString.toString()
    }

    fun getErrorMarkdownString(): String {
        val markdownString = StringBuilder()

        markdownString.append(MarkdownUtils.getSingleLineMarkdownStringEntry("Error Code", code, "-"))
        markdownString.append("\n").append(MarkdownUtils.getMultiLineMarkdownStringEntry(
            (if (Errno.TYPE == type) "Error Message" else "Error Message ($type)"), message, "-"))
        if (!throwablesList.isNullOrEmpty())
            markdownString.append("\n\n").append(geStackTracesMarkdownString())

        return markdownString.toString()
    }

    val codeString: String
        get() = Logger.getSingleLineLogStringEntry("Error Code", code, "-")

    val typeAndMessageLogString: String
        get() = Logger.getMultiLineLogStringEntry(if (Errno.TYPE == type) "Error Message" else "Error Message ($type)", message, "-")

    fun geStackTracesLogString(): String {
        return Logger.getStackTracesString("StackTraces:", Logger.getStackTracesStringArray(throwablesList))
    }

    fun geStackTracesMarkdownString(): String {
        return Logger.getStackTracesMarkdownString("StackTraces", Logger.getStackTracesStringArray(throwablesList))
    }

    companion object {
        private const val LOG_TAG = "Error"

        @JvmStatic
        fun logErrorAndShowToast(context: Context?, logTag: String?, error: Error?) {
            error ?: return
            error.logErrorAndShowToast(context, logTag)
        }

        @JvmStatic
        fun getErrorLogString(error: Error?): String {
            return error?.getErrorLogString() ?: "null"
        }

        @JvmStatic
        fun getMinimalErrorLogString(error: Error?): String {
            return error?.getMinimalErrorLogString() ?: "null"
        }

        @JvmStatic
        fun getMinimalErrorString(error: Error?): String {
            return error?.getMinimalErrorString() ?: "null"
        }

        @JvmStatic
        fun getErrorMarkdownString(error: Error?): String {
            return error?.getErrorMarkdownString() ?: "null"
        }
    }
}
