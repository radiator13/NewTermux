package com.termux.terminal

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileDescriptor
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * A terminal session, consisting of a process coupled to a terminal interface.
 *
 * The subprocess will be executed by the constructor, and when the size is made known by a call to
 * [updateSize] terminal emulation will begin and threads will be spawned to handle the subprocess I/O.
 * All terminal emulation and callback methods will be performed on the main thread.
 *
 * The child process may be exited forcefully by using the [finishIfRunning] method.
 *
 * NOTE: The terminal session may outlive the EmulatorView, so be careful with callbacks!
 */
class TerminalSession(
    private val mShellPath: String,
    private val mCwd: String,
    private val mArgs: Array<String>,
    private val mEnv: Array<String>,
    private val mTranscriptRows: Int?,
    client: TerminalSessionClient
) : TerminalOutput() {

    companion object {
        private const val MSG_NEW_INPUT = 1
        private const val MSG_PROCESS_EXITED = 4
        private const val LOG_TAG = "TerminalSession"

        private fun wrapFileDescriptor(fileDescriptor: Int, client: TerminalSessionClient?): java.io.FileDescriptor? {
            return try {
                val pfd = ParcelFileDescriptor.adoptFd(fileDescriptor)
                val result = pfd.fileDescriptor
                pfd.detachFd()
                result
            } catch (e: Exception) {
                Logger.logStackTraceWithMessage(client, LOG_TAG, "Error wrapping file descriptor", e)
                System.exit(1)
                null // unreachable
            }
        }
    }

    @JvmField
    val mHandle: String = UUID.randomUUID().toString()

    @JvmField
    var mEmulator: TerminalEmulator? = null

    /**
     * A queue written to from a separate thread when the process outputs, and read by main thread to process by
     * terminal emulator. Implemented in Rust via RustJNI.
     */
    @JvmField
    val mProcessToTerminalIOQueue: Long = RustJNI.termByteQueueNew(64 * 1024)

    /**
     * A queue written to from the main thread due to user interaction, and read by another thread which forwards by
     * writing to the [mTerminalFileDescriptor]. Implemented in Rust via RustJNI.
     */
    @JvmField
    val mTerminalToProcessIOQueue: Long = RustJNI.termByteQueueNew(4096)

    /** Buffer to write translate code points into utf8 before writing to mTerminalToProcessIOQueue */
    private val mUtf8InputBuffer = ByteArray(5)

    /** Callback which gets notified when a session finishes or changes title. */
    @JvmField
    var mClient: TerminalSessionClient = client

    /** The pid of the shell process. 0 if not started and -1 if finished running. */
    @JvmField
    var mShellPid: Int = 0

    /** The exit status of the shell process. Only valid if [mShellPid] is -1. */
    @JvmField
    var mShellExitStatus: Int = 0

    /**
     * The file descriptor referencing the master half of a pseudo-terminal pair, resulting from calling
     * [JNI.createSubprocess].
     */
    private var mTerminalFileDescriptor: Int = 0

    /** Set by the application for user identification of session, not by terminal. */
    @JvmField
    var mSessionName: String? = null

    @JvmField
    val mMainThreadHandler: Handler = MainThreadHandler()

    /**
     * @param client The [TerminalSessionClient] interface implementation to allow
     *               for communication between [TerminalSession] and its client.
     */
    fun updateTerminalSessionClient(client: TerminalSessionClient) {
        mClient = client
        mEmulator?.updateTerminalSessionClient(client)
    }

    /** Inform the attached pty of the new size and reflow or initialize the emulator. */
    fun updateSize(columns: Int, rows: Int, cellWidthPixels: Int, cellHeightPixels: Int) {
        if (mEmulator == null) {
            initializeEmulator(columns, rows, cellWidthPixels, cellHeightPixels)
        } else {
            JNI.setPtyWindowSize(mTerminalFileDescriptor, rows, columns, cellWidthPixels, cellHeightPixels)
            mEmulator!!.resize(columns, rows, cellWidthPixels, cellHeightPixels)
        }
    }

    /** The terminal title as set through escape sequences or null if none set. */
    fun getTitle(): String? {
        return mEmulator?.getTitle()
    }

    /**
     * Set the terminal emulator's window size and start terminal emulation.
     *
     * @param columns The number of columns in the terminal window.
     * @param rows    The number of rows in the terminal window.
     */
    fun initializeEmulator(columns: Int, rows: Int, cellWidthPixels: Int, cellHeightPixels: Int) {
        mEmulator = TerminalEmulator(this, columns, rows, cellWidthPixels, cellHeightPixels, mTranscriptRows, mClient)

        val processId = IntArray(1)
        mTerminalFileDescriptor = JNI.createSubprocess(mShellPath, mCwd, mArgs, mEnv, processId, rows, columns, cellWidthPixels, cellHeightPixels)
        mShellPid = processId[0]
        mClient.setTerminalShellPid(this, mShellPid)

        val terminalFileDescriptorWrapped = wrapFileDescriptor(mTerminalFileDescriptor, mClient)

        Thread({
            try {
                val buffer = ByteArray(4096)
                while (true) {
                    // Use Os.read() directly instead of FileInputStream.read().
                    // Same nterp interpreter crash avoidance as the output writer.
                    val read = Os.read(terminalFileDescriptorWrapped, buffer, 0, buffer.size)
                    if (read == -1) return@Thread
                    if (!RustJNI.termByteQueueWrite(mProcessToTerminalIOQueue, buffer, 0, read)) return@Thread
                    mMainThreadHandler.sendEmptyMessage(MSG_NEW_INPUT)
                }
            } catch (e: Exception) {
                // Ignore, just shutting down.
            }
        }, "TermSessionInputReader[pid=$mShellPid]").start()

        Thread({
            val buffer = ByteArray(4096)
            try {
                while (true) {
                    val bytesToWrite = RustJNI.termByteQueueRead(mTerminalToProcessIOQueue, buffer, buffer.size, true)
                    if (bytesToWrite == -1) return@Thread
                    // Use Os.write() directly instead of FileOutputStream.write().
                    // FileOutputStream.write() goes through ART's nterp interpreter which
                    // crashes on this device due to corrupted boot.art dalvik-cache
                    // (version mismatch: expected 0x32363500, got 0x32353900).
                    Os.write(terminalFileDescriptorWrapped, buffer, 0, bytesToWrite)
                }
            } catch (e: Exception) {
                // Ignore, just shutting down.
            }
        }, "TermSessionOutputWriter[pid=$mShellPid]").start()

        Thread({
            val processExitCode = JNI.waitFor(mShellPid)
            mMainThreadHandler.sendMessage(mMainThreadHandler.obtainMessage(MSG_PROCESS_EXITED, processExitCode))
        }, "TermSessionWaiter[pid=$mShellPid]").start()
    }

    /** Write data to the shell process. */
    override fun write(data: ByteArray, offset: Int, count: Int) {
        if (mShellPid > 0) RustJNI.termByteQueueWrite(mTerminalToProcessIOQueue, data, offset, count)
    }

    /** Write the Unicode code point to the terminal encoded in UTF-8. */
    fun writeCodePoint(prependEscape: Boolean, codePoint: Int) {
        if (codePoint > 1114111 || codePoint in 0xD800..0xDFFF) {
            // 1114111 (= 2**16 + 1024**2 - 1) is the highest code point, [0xD800,0xDFFF] is the surrogate range.
            throw IllegalArgumentException("Invalid code point: $codePoint")
        }

        var bufferPosition = 0
        if (prependEscape) mUtf8InputBuffer[bufferPosition++] = 27

        if (codePoint <= /* 7 bits */0b1111111) {
            mUtf8InputBuffer[bufferPosition++] = codePoint.toByte()
        } else if (codePoint <= /* 11 bits */0b11111111111) {
            /* 110xxxxx leading byte with leading 5 bits */
            mUtf8InputBuffer[bufferPosition++] = (0b11000000 or (codePoint shr 6)).toByte()
            /* 10xxxxxx continuation byte with following 6 bits */
            mUtf8InputBuffer[bufferPosition++] = (0b10000000 or (codePoint and 0b111111)).toByte()
        } else if (codePoint <= /* 16 bits */0b1111111111111111) {
            /* 1110xxxx leading byte with leading 4 bits */
            mUtf8InputBuffer[bufferPosition++] = (0b11100000 or (codePoint shr 12)).toByte()
            /* 10xxxxxx continuation byte with following 6 bits */
            mUtf8InputBuffer[bufferPosition++] = (0b10000000 or ((codePoint shr 6) and 0b111111)).toByte()
            /* 10xxxxxx continuation byte with following 6 bits */
            mUtf8InputBuffer[bufferPosition++] = (0b10000000 or (codePoint and 0b111111)).toByte()
        } else { /* We have checked codePoint <= 1114111 above, so we have max 21 bits = 0b111111111111111111111 */
            /* 11110xxx leading byte with leading 3 bits */
            mUtf8InputBuffer[bufferPosition++] = (0b11110000 or (codePoint shr 18)).toByte()
            /* 10xxxxxx continuation byte with following 6 bits */
            mUtf8InputBuffer[bufferPosition++] = (0b10000000 or ((codePoint shr 12) and 0b111111)).toByte()
            /* 10xxxxxx continuation byte with following 6 bits */
            mUtf8InputBuffer[bufferPosition++] = (0b10000000 or ((codePoint shr 6) and 0b111111)).toByte()
            /* 10xxxxxx continuation byte with following 6 bits */
            mUtf8InputBuffer[bufferPosition++] = (0b10000000 or (codePoint and 0b111111)).toByte()
        }
        write(mUtf8InputBuffer, 0, bufferPosition)
    }

    fun getEmulator(): TerminalEmulator? {
        return mEmulator
    }

    /** Notify the [mClient] that the screen has changed. */
    protected fun notifyScreenUpdate() {
        mClient.onTextChanged(this)
    }

    /** Reset state for terminal emulator state. */
    fun reset() {
        mEmulator!!.reset()
        notifyScreenUpdate()
    }

    /** Finish this terminal session by sending SIGKILL to the shell. */
    fun finishIfRunning() {
        if (isRunning()) {
            try {
                Os.kill(mShellPid, OsConstants.SIGKILL)
            } catch (e: ErrnoException) {
                Logger.logWarn(mClient, LOG_TAG, "Failed sending SIGKILL: ${e.message}")
            }
        }
    }

    /** Cleanup resources when the process exits. */
    internal fun cleanupResources(exitStatus: Int) {
        synchronized(this) {
            mShellPid = -1
            mShellExitStatus = exitStatus
        }

        // Stop the reader and writer threads, and close the I/O streams
        RustJNI.termByteQueueClose(mTerminalToProcessIOQueue)
        RustJNI.termByteQueueClose(mProcessToTerminalIOQueue)
        JNI.close(mTerminalFileDescriptor)
    }

    override fun titleChanged(oldTitle: String?, newTitle: String?) {
        mClient.onTitleChanged(this)
    }

    @Synchronized
    fun isRunning(): Boolean {
        return mShellPid != -1
    }

    /** Only valid if not [isRunning]. */
    @Synchronized
    fun getExitStatus(): Int {
        return mShellExitStatus
    }

    override fun onCopyTextToClipboard(text: String?) {
        if (text != null) mClient.onCopyTextToClipboard(this, text)
    }

    override fun onPasteTextFromClipboard() {
        mClient.onPasteTextFromClipboard(this)
    }

    override fun onBell() {
        mClient.onBell(this)
    }

    override fun onColorsChanged() {
        mClient.onColorsChanged(this)
    }

    fun getPid(): Int {
        return mShellPid
    }

    /** Returns the shell's working directory or null if it was unavailable. */
    fun getCwd(): String? {
        if (mShellPid < 1) {
            return null
        }
        try {
            val cwdSymlink = String.format("/proc/%s/cwd/", mShellPid)
            val outputPath = File(cwdSymlink).canonicalPath
            var outputPathWithTrailingSlash = outputPath
            if (!outputPath.endsWith("/")) {
                outputPathWithTrailingSlash += '/'
            }
            if (cwdSymlink != outputPathWithTrailingSlash) {
                return outputPath
            }
        } catch (e: IOException) {
            Logger.logStackTraceWithMessage(mClient, LOG_TAG, "Error getting current directory", e)
        } catch (e: SecurityException) {
            Logger.logStackTraceWithMessage(mClient, LOG_TAG, "Error getting current directory", e)
        }
        return null
    }

    @SuppressLint("HandlerLeak")
    internal inner class MainThreadHandler : Handler(Looper.getMainLooper()) {

        val mReceiveBuffer = ByteArray(64 * 1024)

        override fun handleMessage(msg: Message) {
            val bytesRead = RustJNI.termByteQueueRead(mProcessToTerminalIOQueue, mReceiveBuffer, mReceiveBuffer.size, false)
            if (bytesRead > 0) {
                mEmulator!!.append(mReceiveBuffer, bytesRead)
                val flags = mEmulator!!.getLastFlags()
                if ((flags and 1) != 0) mClient.onBell(this@TerminalSession)
                if ((flags and 2) != 0) mClient.onTitleChanged(this@TerminalSession)
                if ((flags and 4) != 0) {
                    mEmulator!!.syncColorsFromRust()
                    mClient.onColorsChanged(this@TerminalSession)
                }
                if ((flags and 8) != 0) mClient.onTerminalCursorStateChange(true)
                notifyScreenUpdate()
            }

            if (msg.what == MSG_PROCESS_EXITED) {
                val exitCode = msg.obj as Int
                cleanupResources(exitCode)

                var exitDescription = "\r\n[Process completed"
                if (exitCode > 0) {
                    // Non-zero process exit.
                    exitDescription += " (code $exitCode)"
                } else if (exitCode < 0) {
                    // Negated signal.
                    exitDescription += " (signal ${-exitCode})"
                }
                exitDescription += " - press Enter]"

                val bytesToWrite = exitDescription.toByteArray(StandardCharsets.UTF_8)
                mEmulator!!.append(bytesToWrite, bytesToWrite.size)
                notifyScreenUpdate()

                mClient.onSessionFinished(this@TerminalSession)
            }
        }
    }
}
