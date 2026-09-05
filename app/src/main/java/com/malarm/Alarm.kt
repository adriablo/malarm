package com.malarm

import org.json.JSONArray
import org.json.JSONObject

data class Alarm(
    val id: Long,
    val hour: Int,
    val minute: Int,
    val label: String = "",
    val repeatDays: Set<Int> = emptySet(),
    val enabled: Boolean = true,
    val ringtone: String = "",
    val dateMillis: Long? = null,
    val monthlyDay: Int? = null,
) {
    val isRepeating: Boolean get() = repeatDays.isNotEmpty() || monthlyDay != null

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("hour", hour)
        put("minute", minute)
        put("label", label)
        put("days", JSONArray().apply { repeatDays.forEach { put(it) } })
        put("enabled", enabled)
        put("ringtone", ringtone)
        dateMillis?.let { put("date", it) }
        monthlyDay?.let { put("monthDay", it) }
    }

    companion object {
        fun fromJson(o: JSONObject): Alarm {
            val daysArr = o.optJSONArray("days") ?: JSONArray()
            val days = buildSet {
                for (i in 0 until daysArr.length()) add(daysArr.getInt(i))
            }
            val date = if (o.has("date")) o.getLong("date") else null
            val monthDay = if (o.has("monthDay")) o.getInt("monthDay") else null
            // Date wins over repeat: a combined payload (hostile import file,
            // dirty prefs from older builds) normalizes to a one-shot date
            // alarm instead of an immortal repeating one. Matches nextTrigger
            // (date branch) and AlarmFormatter (date branch).
            return Alarm(
                id = o.getLong("id"),
                hour = o.getInt("hour"),
                minute = o.getInt("minute"),
                label = o.optString("label"),
                repeatDays = if (date != null) emptySet() else days,
                enabled = o.optBoolean("enabled", true),
                ringtone = o.optString("ringtone"),
                dateMillis = date,
                monthlyDay = if (date != null) null else monthDay,
            )
        }
    }
}
