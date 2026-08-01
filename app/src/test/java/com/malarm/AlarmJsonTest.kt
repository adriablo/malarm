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
