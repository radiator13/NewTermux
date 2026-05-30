package com.newtermux.features

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object TextExpansionStore {

    class TextExpansion {
        @JvmField var trigger: String = ""
        @JvmField var expansion: String = ""
    }

    private var sCache: List<TextExpansion>? = null

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences("newtermux_settings", Context.MODE_PRIVATE)

    @JvmStatic
    fun load(ctx: Context): List<TextExpansion> {
        sCache?.let { return it }
        val list = mutableListOf<TextExpansion>()
        try {
            val json = prefs(ctx).getString("text_expansions_json", "[]") ?: "[]"
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val te = TextExpansion()
                te.trigger = obj.optString("trigger", "")
                te.expansion = obj.optString("expansion", "")
                if (te.trigger.isNotEmpty()) list.add(te)
            }
        } catch (_: Exception) {
        }
        sCache = list
        return list
    }

    @JvmStatic
    fun save(ctx: Context, list: List<TextExpansion>) {
        try {
            val arr = JSONArray()
            for (te in list) {
                val obj = JSONObject()
                obj.put("trigger", te.trigger)
                obj.put("expansion", te.expansion)
                arr.put(obj)
            }
            prefs(ctx).edit().putString("text_expansions_json", arr.toString()).apply()
            sCache = null // invalidate cache so next load picks up the new data
        } catch (_: Exception) {
        }
    }

    @JvmStatic
    fun findExpansion(ctx: Context, trigger: String): String? {
        for (te in load(ctx)) {
            if (te.trigger == trigger) return te.expansion
        }
        return null
    }
}
