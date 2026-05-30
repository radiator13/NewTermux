package com.termux.terminal

import androidx.annotation.NonNull
import androidx.annotation.Nullable

/**
 * The interface for communication between [TerminalSession] and its client. It is used to
 * send callbacks to the client when [TerminalSession] changes or for sending other
 * back data to the client like logs.
 */
interface TerminalSessionClient {

    fun onTextChanged(@NonNull changedSession: TerminalSession)

    fun onTitleChanged(@NonNull changedSession: TerminalSession)

    fun onSessionFinished(@NonNull finishedSession: TerminalSession)

    fun onCopyTextToClipboard(@NonNull session: TerminalSession, text: String)

    fun onPasteTextFromClipboard(@Nullable session: TerminalSession?)

    fun onBell(@NonNull session: TerminalSession)

    fun onColorsChanged(@NonNull session: TerminalSession)

    fun onTerminalCursorStateChange(state: Boolean)

    fun setTerminalShellPid(@NonNull session: TerminalSession, pid: Int)

    fun getTerminalCursorStyle(): Int?

    fun logError(tag: String, message: String)

    fun logWarn(tag: String, message: String)

    fun logInfo(tag: String, message: String)

    fun logDebug(tag: String, message: String)

    fun logVerbose(tag: String, message: String)

    fun logStackTraceWithMessage(tag: String, message: String, e: Exception)

    fun logStackTrace(tag: String, e: Exception)
}
