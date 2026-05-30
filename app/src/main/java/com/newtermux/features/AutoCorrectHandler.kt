package com.newtermux.features

import android.content.Context
import android.util.Log
import android.view.textservice.SentenceSuggestionsInfo
import android.view.textservice.SpellCheckerSession
import android.view.textservice.SuggestionsInfo
import android.view.textservice.TextInfo
import android.view.textservice.TextServicesManager
import java.util.Locale

/**
 * Handles auto-correct and command suggestions for NewTermux.
 * Provides two layers: common shell command corrections + Android spell checker.
 */
class AutoCorrectHandler(private val mContext: Context) : SpellCheckerSession.SpellCheckerSessionListener {

    companion object {
        private const val TAG = "AutoCorrectHandler"

        private val COMMAND_CORRECTIONS = mapOf(
            "sl" to "ls",
            "lsa" to "ls -a",
            "lsl" to "ls -l",
            "grpe" to "grep",
            "gerp" to "grep",
            "rn" to "rm",
            "mdkir" to "mkdir",
            "mkidr" to "mkdir",
            "cta" to "cat",
            "ehco" to "echo",
            "ecoh" to "echo",
            "pyhton" to "python",
            "pytohn" to "python",
            "pythno" to "python",
            "pyhton3" to "python3",
            "gti" to "git",
            "got" to "git",
            "suod" to "sudo",
            "sduo" to "sudo",
            "apt-ge" to "apt-get",
            "apt-gt" to "apt-get",
            "apg-get" to "apt-get",
            "pck" to "pkg",
            "pkc" to "pkg",
            "namo" to "nano",
            "naon" to "nano",
            "fim" to "vim",
            "vi m" to "vim",
            "sssh" to "ssh",
            "scp " to "scp",
            "wgte" to "wget",
            "wgeet" to "wget",
            "curll" to "curl",
            "curlk" to "curl",
            "pythin" to "python",
            "exti" to "exit",
            "exitt" to "exit",
            "clrea" to "clear",
            "celar" to "clear",
            "clar" to "clear",
            "histyory" to "history",
            "histroy" to "history",
            "chnmod" to "chmod",
            "chmo" to "chmod",
            "chonw" to "chown",
            "cdown" to "chown",
            "fild" to "find",
            "finf" to "find",
            "pwn" to "pwd",
            "pd" to "pwd",
            "pdw" to "pwd",
            "mkae" to "make",
            "amke" to "make",
            "isntall" to "install",
            "insatl" to "install",
            "unzip-" to "unzip",
            "unzpi" to "unzip",
            "souce" to "source",
            "soruce" to "source",
            "export-" to "export",
            "expor" to "export",
            "alais" to "alias",
            "ailas" to "alias",
            "whcih" to "which",
            "wihch" to "which",
            "touhc" to "touch",
            "tuoch" to "touch",
            "clea" to "clear",
            "claer" to "clear",
            "sud0" to "sudo",
            "aptget" to "apt-get",
            "apt-p" to "apt-cache",
            "pkg-" to "pkg",
            "pk-" to "pkg",
            "chmox" to "chmod +x",
            "chomd" to "chmod"
        )
    }

    interface SuggestionCallback {
        fun onSuggestions(suggestions: List<String>, originalWord: String)
    }

    private var mSpellCheckerSession: SpellCheckerSession? = null
    var callback: SuggestionCallback? = null
    var isEnabled: Boolean = true

    init {
        initSpellChecker()
    }

    private fun initSpellChecker() {
        try {
            val tsm = mContext.getSystemService(Context.TEXT_SERVICES_MANAGER_SERVICE) as? TextServicesManager
            mSpellCheckerSession = tsm?.newSpellCheckerSession(null, Locale.getDefault(), this, true)
        } catch (e: Exception) {
            Log.w(TAG, "Spell checker unavailable: ${e.message}")
        }
    }

    /**
     * Check if a command has a known correction and return it.
     * @return corrected command or null if no correction available.
     */
    fun getCommandCorrection(input: String?): String? {
        if (!isEnabled || input.isNullOrBlank()) return null
        val trimmed = input.trim().lowercase(Locale.ROOT)
        val parts = trimmed.split("\\s+".toRegex(), 2)
        val cmd = parts[0]
        val correction = COMMAND_CORRECTIONS[cmd] ?: return null
        return if (parts.size > 1) "$correction ${parts[1]}" else correction
    }

    /**
     * Get all possible suggestions for a word using the spell checker.
     */
    fun getSuggestions(word: String) {
        if (!isEnabled) return
        mSpellCheckerSession?.getSuggestions(TextInfo(word), 5)
    }

    override fun onGetSuggestions(results: Array<out SuggestionsInfo>?) {
        if (results == null || callback == null) return
        for (info in results) {
            if (info != null && info.suggestionsCount > 0) {
                val suggestions = mutableListOf<String>()
                for (i in 0 until info.suggestionsCount) {
                    suggestions.add(info.getSuggestionAt(i))
                }
                val orig = if (suggestions.isEmpty()) "" else suggestions[0]
                callback?.onSuggestions(suggestions, orig)
            }
        }
    }

    override fun onGetSentenceSuggestions(results: Array<out SentenceSuggestionsInfo>?) {}

    fun destroy() {
        mSpellCheckerSession?.close()
        mSpellCheckerSession = null
    }
}
