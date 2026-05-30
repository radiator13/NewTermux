package com.termux.shared.shell.command.environment

/** Environment variable with name, value, and escaped flag. */
data class ShellEnvironmentVariable @JvmOverloads constructor(
    @JvmField val name: String,
    @JvmField var value: String,
    @JvmField var escaped: Boolean = false,
) : Comparable<ShellEnvironmentVariable> {

    override fun compareTo(other: ShellEnvironmentVariable): Int = name.compareTo(other.name)
}
