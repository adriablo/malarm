package com.malarm

import android.content.Context
import org.json.JSONArray

class AlarmStore(context: Context) {
    private val prefs = context.getSharedPreferences("malarm", Context.MODE_PRIVATE)

    fun all(): List<Alarm> {
        val raw = prefs.getString(KEY_ALARMS, null) ?: return emptyList()
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val parsed = runCatching { Alarm.fromJson(arr.getJSONObject(i)) }.getOrNull()
                if (parsed != null) add(parsed)
            }
        }.sortedBy { it.hour * 60 + it.minute }
    }

    fun get(id: Long): Alarm? = all().find { it.id == id }

    fun save(alarm: Alarm) {
        val list = all().toMutableList()
        val index = list.indexOfFirst { it.id == alarm.id }
        if (index >= 0) list[index] = alarm else list.add(alarm)
        persist(list)
    }

    fun delete(id: Long) {
        persist(all().filterNot { it.id == id })
    }

    fun importAll(alarms: List<Alarm>): List<Alarm> {
        val renumbered = alarms.map { it.copy(id = nextId()) }
        persist(renumbered)
        return renumbered
    }

    fun nextId(): Long {
        var id = prefs.getLong(KEY_NEXT_ID, 1L)
        val used = all().mapTo(mutableSetOf()) { it.id }
        while (id in used) {
            id++
        }
        prefs.edit().putLong(KEY_NEXT_ID, id + 1).commit()
        return id
    }

    fun snoozeMinutes(): Int = prefs.getInt(KEY_SNOOZE_MINUTES, DEFAULT_SNOOZE_MINUTES)

    fun setSnoozeMinutes(minutes: Int) {
        prefs.edit().putInt(KEY_SNOOZE_MINUTES, minutes).apply()
    }

    private fun persist(list: List<Alarm>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY_ALARMS, arr.toString()).commit()
    }

    companion object {
        private const val KEY_ALARMS = "alarms"
        private const val KEY_NEXT_ID = "nextId"
        private const val KEY_SNOOZE_MINUTES = "snoozeMinutes"
        const val DEFAULT_SNOOZE_MINUTES = 5
    }
}
