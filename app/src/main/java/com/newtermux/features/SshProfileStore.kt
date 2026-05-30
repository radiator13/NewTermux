package com.newtermux.features

import com.termux.shared.termux.TermuxConstants
import org.json.JSONArray
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.FileWriter

object SshProfileStore {

    private const val FILE_NAME = ".termux/ssh-profiles.json"

    private fun profilesFile(): File {
        return File(TermuxConstants.TERMUX_HOME_DIR_PATH, FILE_NAME)
    }

    @JvmStatic
    fun load(): List<SshProfile> {
        val list = mutableListOf<SshProfile>()
        val f = profilesFile()
        if (!f.exists()) return list
        try {
            BufferedReader(FileReader(f)).use { br ->
                val sb = StringBuilder()
                var line: String?
                while (br.readLine().also { line = it } != null) sb.append(line)
                val arr = JSONArray(sb.toString())
                for (i in 0 until arr.length()) {
                    list.add(SshProfile.fromJson(arr.getJSONObject(i)))
                }
            }
        } catch (_: Exception) {
        }
        return list
    }

    @JvmStatic
    fun save(profiles: List<SshProfile>) {
        try {
            val f = profilesFile()
            f.parentFile?.mkdirs()
            val arr = JSONArray()
            for (p in profiles) arr.put(p.toJson())
            FileWriter(f).use { fw ->
                fw.write(arr.toString(2))
            }
        } catch (_: Exception) {
        }
    }
}
