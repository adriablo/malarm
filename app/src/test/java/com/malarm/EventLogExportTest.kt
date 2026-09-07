package com.malarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class EventLogExportTest {

    @Test
    fun fileNameUsesIsoTimestamp() {
        val original = java.util.TimeZone.getDefault()
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"))
        try {
            val millis = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
                set(2026, Calendar.SEPTEMBER, 3, 19, 20, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            assertEquals("malarm-log-2026-09-03T19-20-00.csv", EventLogExport.fileName(millis))
        } finally {
            java.util.TimeZone.setDefault(original)
        }
    }

    @Test
    fun toCsvHasHeaderAndOneRowPerEvent() {
        val events = listOf(
            AlarmEvent(
                id = 1, timestamp = 1788792327593L, type = EventType.FIRED,
                alarmId = 7, label = "standup", details = "Alarm",
            ),
            AlarmEvent(
                id = 2, timestamp = 1788792327593L, type = EventType.PERIODIC_CHECK,
                alarmId = null, label = null, details = "No change",
            ),
        )
        val lines = EventLogExport.toCsv(events).trimEnd().split("\n")
        assertEquals("timestamp,type,alarmId,label,details", lines[0])
        assertEquals(3, lines.size)
        assertTrue(lines[1].contains("FIRED,7,standup,Alarm"))
        assertTrue(lines[2].endsWith(",PERIODIC_CHECK,,,No change"))
    }

    @Test
    fun toCsvEscapesCommasQuotesAndNewlines() {
        val events = listOf(
            AlarmEvent(
                id = 1, timestamp = 1788792327593L, type = EventType.SCHEDULED,
                alarmId = 1, label = "a,b\"c", details = "line1\nline2",
            ),
        )
        val csv = EventLogExport.toCsv(events)
        assertTrue(csv.contains("\"a,b\"\"c\""))
        assertTrue(csv.contains("\"line1\nline2\""))
    }

    @Test
    fun toCsvEmptyLogIsHeaderOnly() {
        assertEquals("timestamp,type,alarmId,label,details\n", EventLogExport.toCsv(emptyList()))
    }
}
