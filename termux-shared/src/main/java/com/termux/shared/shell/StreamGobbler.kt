/*
 * Copyright (C) 2012-2019 Jorrit "Chainfire" Jongma
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.termux.shared.shell

import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import com.termux.shared.logger.Logger
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.util.Locale

/**
 * Thread utility class continuously reading from an InputStream
 *
 * https://github.com/Chainfire/libsuperuser/blob/1.1.0.201907261845/libsuperuser/src/eu/chainfire/libsuperuser/Shell.java#L141
 * https://github.com/Chainfire/libsuperuser/blob/1.1.0.201907261845/libsuperuser/src/eu/chainfire/libsuperuser/StreamGobbler.java
 */
class StreamGobbler : Thread {

    private val shell: String
    val inputStream: InputStream
    private val reader: BufferedReader
    private val listWriter: List<String>?
    private val stringWriter: StringBuilder?
    private val lineListener: OnLineListener?
    private val streamClosedListener: OnStreamClosedListener?
    private val mLogLevel: Int?
    @Volatile private var active = true
    @Volatile private var calledOnClose = false

    /**
     * Line callback interface
     */
    fun interface OnLineListener {
        /**
         * Line callback
         *
         * This callback should process the line as quickly as possible.
         * Delays in this callback may pause the native process or even
         * result in a deadlock
         *
         * @param line String that was gobbled
         */
        fun onLine(line: String)
    }

    /**
     * Stream closed callback interface
     */
    fun interface OnStreamClosedListener {
        /**
         * Stream closed callback
         */
        fun onStreamClosed()
    }

    /**
     * StreamGobbler constructor
     *
     * We use this class because shell STDOUT and STDERR should be read as quickly as
     * possible to prevent a deadlock from occurring, or Process.waitFor() never
     * returning (as the buffer is full, pausing the native process)
     *
     * @param shell Name of the shell
     * @param inputStream InputStream to read from
     * @param outputList List to write to, or null
     * @param logLevel The custom log level to use for logging the command output.
     */
    @AnyThread
    constructor(shell: String, inputStream: InputStream, outputList: List<String>?, logLevel: Int?) : super("Gobbler#${incThreadCounter()}") {
        this.shell = shell
        this.inputStream = inputStream
        reader = BufferedReader(InputStreamReader(inputStream))
        streamClosedListener = null

        listWriter = outputList
        stringWriter = null
        lineListener = null

        mLogLevel = logLevel
    }

    /**
     * StreamGobbler constructor
     *
     * Do not use this for concurrent reading for STDOUT and STDERR for the same StringBuilder since
     * its not synchronized.
     *
     * @param shell Name of the shell
     * @param inputStream InputStream to read from
     * @param outputString StringBuilder to write to, or null
     * @param logLevel The custom log level to use for logging the command output.
     */
    @AnyThread
    constructor(shell: String, inputStream: InputStream, outputString: StringBuilder?, logLevel: Int?) : super("Gobbler#${incThreadCounter()}") {
        this.shell = shell
        this.inputStream = inputStream
        reader = BufferedReader(InputStreamReader(inputStream))
        streamClosedListener = null

        listWriter = null
        stringWriter = outputString
        lineListener = null

        mLogLevel = logLevel
    }

    /**
     * StreamGobbler constructor
     *
     * @param shell Name of the shell
     * @param inputStream InputStream to read from
     * @param onLineListener OnLineListener callback
     * @param onStreamClosedListener OnStreamClosedListener callback
     * @param logLevel The custom log level to use for logging the command output.
     */
    @AnyThread
    constructor(shell: String, inputStream: InputStream, onLineListener: OnLineListener?, onStreamClosedListener: OnStreamClosedListener?, logLevel: Int?) : super("Gobbler#${incThreadCounter()}") {
        this.shell = shell
        this.inputStream = inputStream
        reader = BufferedReader(InputStreamReader(inputStream))
        streamClosedListener = onStreamClosedListener

        listWriter = null
        stringWriter = null
        lineListener = onLineListener

        mLogLevel = logLevel
    }

    override fun run() {
        val defaultLogTag = Logger.getDefaultLogTag()
        val loggingEnabled = Logger.shouldEnableLoggingForCustomLogLevel(mLogLevel)
        if (loggingEnabled)
            Logger.logVerbose(LOG_TAG, "Using custom log level: $mLogLevel, current log level: ${Logger.getLogLevel()}")

        // keep reading the InputStream until it ends (or an error occurs)
        try {
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (loggingEnabled)
                    Logger.logVerboseForce("${defaultLogTag}Command", String.format(Locale.ENGLISH, "[%s] %s", shell, line))

                stringWriter?.append(line)?.append("\n")
                (listWriter as? java.util.List<String>)?.add(line!!)
                lineListener?.onLine(line!!)
                while (!active) {
                    synchronized(this) {
                        try {
                            (this as Object).wait(128)
                        } catch (e: InterruptedException) {
                            // no action
                        }
                    }
                }
            }
        } catch (e: IOException) {
            // reader probably closed, expected exit condition
            if (streamClosedListener != null) {
                calledOnClose = true
                streamClosedListener.onStreamClosed()
            }
        }

        // make sure our stream is closed and resources will be freed
        try {
            reader.close()
        } catch (e: IOException) {
            // read already closed
        }

        if (!calledOnClose) {
            if (streamClosedListener != null) {
                calledOnClose = true
                streamClosedListener.onStreamClosed()
            }
        }
    }

    /**
     * Resume consuming the input from the stream
     */
    @AnyThread
    fun resumeGobbling() {
        if (!active) {
            synchronized(this) {
                active = true
                (this as Object).notifyAll()
            }
        }
    }

    /**
     * Suspend gobbling, so other code may read from the InputStream instead
     *
     * This should *only* be called from the OnLineListener callback!
     */
    @AnyThread
    fun suspendGobbling() {
        synchronized(this) {
            active = false
            (this as Object).notifyAll()
        }
    }

    /**
     * Wait for gobbling to be suspended
     *
     * Obviously this cannot be called from the same thread as [suspendGobbling]
     */
    @WorkerThread
    fun waitForSuspend() {
        synchronized(this) {
            while (active) {
                try {
                    (this as Object).wait(32)
                } catch (e: InterruptedException) {
                    // no action
                }
            }
        }
    }

    /**
     * Is gobbling suspended?
     *
     * @return is gobbling suspended?
     */
    @AnyThread
    fun isSuspended(): Boolean {
        synchronized(this) {
            return !active
        }
    }

    /**
     * Get current OnLineListener
     *
     * @return OnLineListener
     */
    @AnyThread
    fun getOnLineListener(): OnLineListener? {
        return lineListener
    }

    @Throws(InterruptedException::class)
    internal fun conditionalJoin() {
        if (calledOnClose) return // deadlock from callback, we're inside exit procedure
        if (Thread.currentThread() == this) return // can't join self
        join()
    }

    companion object {
        private const val LOG_TAG = "StreamGobbler"

        private var threadCounter = 0

        @Synchronized
        private fun incThreadCounter(): Int {
            return threadCounter++
        }
    }
}
