package com.newtermux.features

import android.util.Log
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader

/**
 * Manages root access toggle for NewTermux sessions.
 * When enabled, commands run via 'su' giving full device root access.
 */
class RootToggleManager private constructor() {

    interface RootCallback {
        fun onRootGranted()
        fun onRootDenied(reason: String)
        fun onRootStateChanged(isRoot: Boolean)
    }

    var isRootEnabled: Boolean = false
        private set
    var isRootAvailable: Boolean = false
        private set

    fun requestRoot(callback: RootCallback?) {
        Thread {
            try {
                val process = Runtime.getRuntime().exec("su")
                val os = DataOutputStream(process.outputStream)
                os.writeBytes("id\n")
                os.writeBytes("exit\n")
                os.flush()

                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val output = reader.readLine()
                val exitCode = process.waitFor()

                if (exitCode == 0 && output != null && output.contains("uid=0")) {
                    isRootAvailable = true
                    isRootEnabled = true
                    Log.i(TAG, "Root access granted")
                    callback?.onRootGranted()
                } else {
                    isRootAvailable = false
                    isRootEnabled = false
                    callback?.onRootDenied("Root access denied or not available.")
                }
            } catch (e: Exception) {
                isRootAvailable = false
                isRootEnabled = false
                callback?.onRootDenied("Error: ${e.message}")
            }
        }.start()
    }

    /**
     * Execute a shell command with root privileges.
     */
    fun executeRootCommand(command: String): CommandResult {
        if (!isRootEnabled) {
            return CommandResult(-1, "", "Root mode not enabled")
        }
        try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            os.writeBytes("$command\n")
            os.writeBytes("exit\n")
            os.flush()

            val stdout = BufferedReader(InputStreamReader(process.inputStream))
            val stderr = BufferedReader(InputStreamReader(process.errorStream))

            val out = StringBuilder()
            val err = StringBuilder()
            var line: String?
            while (stdout.readLine().also { line = it } != null) out.append(line).append("\n")
            while (stderr.readLine().also { line = it } != null) err.append(line).append("\n")

            val exitCode = process.waitFor()
            return CommandResult(exitCode, out.toString(), err.toString())
        } catch (e: Exception) {
            return CommandResult(-1, "", e.message ?: "")
        }
    }

    fun toggleRoot(enable: Boolean, callback: RootCallback?) {
        if (enable) {
            requestRoot(callback)
        } else {
            isRootEnabled = false
            Log.i(TAG, "Root mode disabled")
            callback?.onRootStateChanged(false)
        }
    }

    /** Get the shell command prefix for root-aware execution */
    fun getShellPrefix(): String {
        return if (isRootEnabled) "su -c " else ""
    }

    data class CommandResult(val exitCode: Int, val stdout: String, val stderr: String) {
        fun success(): Boolean = exitCode == 0
    }

    companion object {
        private const val TAG = "RootToggleManager"

        @Volatile
        private var sInstance: RootToggleManager? = null

        @JvmStatic
        fun getInstance(): RootToggleManager {
            return sInstance ?: synchronized(this) {
                sInstance ?: RootToggleManager().also { sInstance = it }
            }
        }

        /**
         * Check if the device is rooted by attempting to execute 'su'.
         */
        @JvmStatic
        fun isDeviceRooted(): Boolean {
            try {
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val line = reader.readLine()
                process.waitFor()
                return line != null && line.contains("uid=0")
            } catch (e: Exception) {
                Log.d(TAG, "Device not rooted: ${e.message}")
                return false
            }
        }
    }
}
