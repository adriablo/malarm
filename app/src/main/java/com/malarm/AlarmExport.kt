package com.malarm

import org.json.JSONArray
import org.json.JSONObject

object AlarmExport {

    private const val FORMAT = "malarm"
    private const val SCHEMA_VERSION = 1

    fun export(alarms: List<Alarm>): JSONObject = JSONObject().apply {
        put("format", FORMAT)
        put("schemaVersion", SCHEMA_VERSION)
        put("appVersion", BuildConfig.VERSION_NAME)
        put("appVersionCode", BuildConfig.VERSION_CODE)
        put("exportedAt", System.currentTimeMillis())
        put("alarms", JSONArray().apply { alarms.forEach { put(it.toJson()) } })
    }

    fun import(content: String): List<Alarm>? = runCatching {
        val trimmed = content.trim()
        val alarms = if (trimmed.startsWith("[")) {
            parse(JSONArray(trimmed))
        } else {
            val obj = JSONObject(trimmed)
            if (obj.has("format") && obj.optString("format") != FORMAT) return@runCatching null
            parse(obj.optJSONArray("alarms") ?: JSONArray())
        }
        alarms
    }.getOrNull()

    private fun parse(arr: JSONArray): List<Alarm> = buildList {
        for (i in 0 until arr.length()) {
            val parsed = runCatching { Alarm.fromJson(arr.getJSONObject(i)) }.getOrNull()
            if (parsed != null) add(parsed)
        }
    }
}
