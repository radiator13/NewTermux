/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 * (GPL v2 + Classpath exception — see git history for full text.)
 */
package com.termux.shared.file.filesystem

import com.termux.shared.file.filesystem.FilePermission.*

/**
 * Static methods that operate on sets of [FilePermission] objects.
 * Port of java.nio.file.attribute.PosixFilePermissions.
 */
object FilePermissions {

    private fun StringBuilder.writeBits(r: Boolean, w: Boolean, x: Boolean) {
        append(if (r) 'r' else '-')
        append(if (w) 'w' else '-')
        append(if (x) 'x' else '-')
    }

    /** Returns the String representation of a set of permissions (e.g. "rwxr-x---"). */
    @JvmStatic
    fun toString(perms: Set<FilePermission>): String = buildString(9) {
        writeBits(perms.contains(OWNER_READ), perms.contains(OWNER_WRITE), perms.contains(OWNER_EXECUTE))
        writeBits(perms.contains(GROUP_READ), perms.contains(GROUP_WRITE), perms.contains(GROUP_EXECUTE))
        writeBits(perms.contains(OTHERS_READ), perms.contains(OTHERS_WRITE), perms.contains(OTHERS_EXECUTE))
    }

    /** Parses a 9-character permission string (e.g. "rwxr-x---") into a set of [FilePermission]. */
    @JvmStatic
    fun fromString(perms: String): Set<FilePermission> {
        require(perms.length == 9) { "Invalid mode" }
        val result = mutableSetOf<FilePermission>()
        fun check(c: Char, set: Char): Boolean = when (c) {
            set -> true
            '-' -> false
            else -> throw IllegalArgumentException("Invalid mode")
        }
        if (check(perms[0], 'r')) result.add(OWNER_READ)
        if (check(perms[1], 'w')) result.add(OWNER_WRITE)
        if (check(perms[2], 'x')) result.add(OWNER_EXECUTE)
        if (check(perms[3], 'r')) result.add(GROUP_READ)
        if (check(perms[4], 'w')) result.add(GROUP_WRITE)
        if (check(perms[5], 'x')) result.add(GROUP_EXECUTE)
        if (check(perms[6], 'r')) result.add(OTHERS_READ)
        if (check(perms[7], 'w')) result.add(OTHERS_WRITE)
        if (check(perms[8], 'x')) result.add(OTHERS_EXECUTE)
        return result
    }
}
