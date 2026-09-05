package com.malarm

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class AlarmJsonTest {

    @Test
    fun weeklyAlarmRoundTrips() {
        val alarm = Alarm(1, 7, 30, "gg", repeatDays = setOf(Calendar.MONDAY, Calendar.WEDNESDAY))
        assertEquals(alarm, Alarm.fromJson(alarm.toJson()))
    }

    @Test
    fun dateAlarmRoundTrips() {
        val alarm = Alarm(2, 8, 0, dateMillis = 1786518000000L, enabled = false)
        assertEquals(alarm, Alarm.fromJson(alarm.toJson()))
    }

    @Test
    fun monthlyAlarmRoundTrips() {
        val alarm = Alarm(3, 8, 0, label = "rent", monthlyDay = 12)
        assertEquals(alarm, Alarm.fromJson(alarm.toJson()))
        assertTrue(alarm.isRepeating)
    }

    @Test
    fun nullFieldsAreOmittedFromJson() {
        val json = Alarm(4, 9, 5).toJson()
        assertFalse(json.has("date"))
        assertFalse(json.has("monthDay"))
        val parsed = Alarm.fromJson(json)
        assertNull(parsed.dateMillis)
        assertNull(parsed.monthlyDay)
        assertFalse(parsed.isRepeating)
    }

    @Test
    fun dateWithRepeatNormalizesToOneShotDate() {
        // Code-review §1.3: a combined payload (hostile import, dirty prefs)
        // must not become an immortal repeating alarm — date wins.
        val json = JSONObject()
            .put("id", 6L)
            .put("hour", 8)
            .put("minute", 0)
            .put("days", org.json.JSONArray().put(Calendar.MONDAY).put(Calendar.WEDNESDAY))
            .put("monthDay", 12)
            .put("date", 1786518000000L)
        val alarm = Alarm.fromJson(json)
        assertEquals(1786518000000L, alarm.dateMillis)
        assertTrue(alarm.repeatDays.isEmpty())
        assertNull(alarm.monthlyDay)
        assertFalse(alarm.isRepeating)
    }

    @Test
    fun missingFieldsGetDefaults() {
        val json = JSONObject().put("id", 5L).put("hour", 6).put("minute", 10)
        val alarm = Alarm.fromJson(json)
        assertEquals(5L, alarm.id)
        assertEquals("", alarm.label)
        assertTrue(alarm.enabled)
        assertEquals("", alarm.ringtone)
        assertNull(alarm.dateMillis)
        assertNull(alarm.monthlyDay)
    }
}
