package com.termux.shared.shell

/**
 * Tokenizes a command string into arguments, handling quotes and escapes.
 * Ported from DrJava (Rice University) — see original license in git history.
 * Pure Kotlin port matching the Rust implementation in terminal-emulator/src/main/rust/src/argument_tokenizer.rs.
 */
object ArgumentTokenizer {

    private const val NO_TOKEN = 0
    private const val NORMAL = 1
    private const val SINGLE_QUOTE = 2
    private const val DOUBLE_QUOTE = 3

    @JvmStatic
    fun tokenize(arguments: String): List<String> = tokenize(arguments, stringify = false)

    @JvmStatic
    fun tokenize(arguments: String, stringify: Boolean): List<String> {
        val args = mutableListOf<String>()
        val buf = StringBuilder()
        var escaped = false
        var state = NO_TOKEN
        val chars = arguments.toCharArray()
        var i = 0

        while (i < chars.size) {
            val c = chars[i]
            if (escaped) {
                escaped = false
                buf.append(c)
                i++
                continue
            }
            when (state) {
                SINGLE_QUOTE -> {
                    if (c == '\'') state = NORMAL else buf.append(c)
                }
                DOUBLE_QUOTE -> {
                    when {
                        c == '"' -> state = NORMAL
                        c == '\\' -> {
                            i++
                            if (i < chars.size) {
                                val next = chars[i]
                                if (next == '"' || next == '\\') buf.append(next)
                                else { buf.append(c); buf.append(next) }
                            } else buf.append(c)
                        }
                        else -> buf.append(c)
                    }
                }
                NO_TOKEN, NORMAL -> {
                    when (c) {
                        '\\' -> { escaped = true; state = NORMAL }
                        '\'' -> state = SINGLE_QUOTE
                        '"' -> state = DOUBLE_QUOTE
                        else -> {
                            if (!c.isWhitespace()) {
                                buf.append(c); state = NORMAL
                            } else if (state == NORMAL) {
                                args.add(buf.toString()); buf.clear(); state = NO_TOKEN
                            }
                        }
                    }
                }
            }
            i++
        }

        when {
            escaped -> { buf.append('\\'); args.add(buf.toString()) }
            state != NO_TOKEN -> args.add(buf.toString())
        }

        return if (stringify) {
            args.map { "\"${escapeQuotesAndBackslashes(it)}\"" }
        } else {
            args
        }
    }

    private fun escapeQuotesAndBackslashes(s: String): String = buildString(s.length + 4) {
        for (c in s) {
            when (c) {
                '\\', '"' -> { append('\\'); append(c) }
                '\n' -> append("\\n")
                '\t' -> append("\\t")
                '\r' -> append("\\r")
                '\u0008' -> append("\\b")
                '\u000C' -> append("\\f")
                else -> append(c)
            }
        }
    }
}
