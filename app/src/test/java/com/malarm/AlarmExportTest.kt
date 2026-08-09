package com.malarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

class AlarmExportTest {

    @Test
    fun exportImportRoundTrips() {
        val alarms = listOf(
            Alarm(1, 7, 30, "gg", repeatDays = setOf(Calendar.MONDAY, Calendar.WEDNESDAY)),
            Alarm(2, 8, 0, "rent", monthlyDay = 12),
            Alarm(3, 22, 45, dateMillis = 1786518000000L, enabled = false),
        )
        val json = AlarmExport.export(alarms).toString()
        assertEquals(alarms, AlarmExport.import(json))
    }

    @Test
    fun importAcceptsRawArray() {
        val json = """[{"id":1,"hour":7,"minute":30}]"""
        assertEquals(listOf(Alarm(1, 7, 30)), AlarmExport.import(json))
    }

    @Test
    fun importRejectsGarbage() {
        assertNull(AlarmExport.import("not json at all"))
    }

    @Test
    fun importRejectsWrongFormat() {
        val json = """{"format":"other","alarms":[]}"""
        assertNull(AlarmExport.import(json))
    }

    @Test
    fun exportIncludesVersionInfo() {
        val json = AlarmExport.export(emptyList())
        assertNotNull(json.optString("format"))
        assertEquals(BuildConfig.VERSION_NAME, json.getString("appVersion"))
        assertEquals(BuildConfig.VERSION_CODE, json.getInt("appVersionCode"))
    }
}
