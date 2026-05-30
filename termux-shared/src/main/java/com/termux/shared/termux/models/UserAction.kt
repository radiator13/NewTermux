package com.termux.shared.termux.models

enum class UserAction(@JvmField val label: String) {
    CRASH_REPORT("crash report"),
    PLUGIN_EXECUTION_COMMAND("plugin execution command"),
    ABOUT("about"),
    REPORT_ISSUE_FROM_TRANSCRIPT("report issue from transcript");

    /** Java compat: getName() returns the human-readable label. */
    fun getName(): String = label
}
