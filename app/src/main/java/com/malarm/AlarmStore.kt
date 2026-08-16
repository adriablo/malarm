package com.malarm

import android.content.Context
import org.json.JSONObject

class AlarmStore(context: Context) {
    private val prefs = context.getSharedPreferences("malarm", Context.MODE_PRIVATE)

    fun all(): List<Alarm> {
        val obj = read() ?: return emptyList()
        return buildList {
            val keys = obj.keys()
            while (keys.hasNext()) {
                val parsed = runCatching { Alarm.fromJson(obj.getJSONObject(keys.next())) }.getOrNull()
                if (parsed != null) add(parsed)
            }
        }.sortedBy { it.hour * 60 + it.minute }
    }

    fun get(id: Long): Alarm? {
        val obj = read() ?: return null
        val alarmJson = obj.optJSONObject(id.toString()) ?: return null
        return runCatching { Alarm.fromJson(alarmJson) }.getOrNull()
    }

    fun save(alarm: Alarm) {
        val obj = read() ?: JSONObject()
        obj.put(alarm.id.toString(), alarm.toJson())
        persist(obj)
    }

    fun delete(id: Long) {
        val obj = read() ?: return
        if (obj.remove(id.toString()) != null) {
            persist(obj)
        }
    }

    fun deleteAll(ids: Set<Long>) {
        val obj = read() ?: return
        var changed = false
        for (id in ids) {
            if (obj.remove(id.toString()) != null) {
                changed = true
            }
        }
        if (changed) {
            persist(obj)
        }
    }

    fun importAll(alarms: List<Alarm>): List<Alarm> {
        val renumbered = alarms.map { it.copy(id = nextId()) }
        val obj = JSONObject()
        renumbered.forEach { obj.put(it.id.toString(), it.toJson()) }
        persist(obj)
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

    fun timeZoneId(): String? = prefs.getString(KEY_TIME_ZONE_ID, null)

    fun setTimeZoneId(id: String) {
        prefs.edit().putString(KEY_TIME_ZONE_ID, id).apply()
    }

    fun clockCalibration(): Pair<Long, Long>? {
        val elapsed = prefs.getLong(KEY_CALIB_ELAPSED, -1L)
        val wall = prefs.getLong(KEY_CALIB_WALL, -1L)
        return if (elapsed >= 0 && wall >= 0) elapsed to wall else null
    }

    fun setClockCalibration(elapsedRealtime: Long, wallClockMillis: Long) {
        prefs.edit()
            .putLong(KEY_CALIB_ELAPSED, elapsedRealtime)
            .putLong(KEY_CALIB_WALL, wallClockMillis)
            .apply()
    }

    private fun read(): JSONObject? {
        val raw = prefs.getString(KEY_ALARMS, null) ?: return null
        return runCatching { JSONObject(raw) }.getOrNull()
    }

    private fun persist(obj: JSONObject) {
        prefs.edit().putString(KEY_ALARMS, obj.toString()).commit()
    }

    companion object {
        private const val KEY_ALARMS = "alarms"
        private const val KEY_NEXT_ID = "nextId"
        private const val KEY_SNOOZE_MINUTES = "snoozeMinutes"
        private const val KEY_TIME_ZONE_ID = "timeZoneId"
        private const val KEY_CALIB_ELAPSED = "calibElapsed"
        private const val KEY_CALIB_WALL = "calibWall"
        const val DEFAULT_SNOOZE_MINUTES = 5
    }
}
