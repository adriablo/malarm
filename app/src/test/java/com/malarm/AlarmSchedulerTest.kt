package com.malarm

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmSchedulerTest {

    private fun at(
        day: Int,
        month: Int,
        year: Int,
        hour: Int,
        minute: Int = 0,
    ): Calendar = Calendar.getInstance().apply {
        clear()
        set(year, month, day, hour, minute, 0)
        set(Calendar.MILLISECOND, 0)
    }

    // Thursday 2026-08-06 02:00
    private val now: Calendar = at(6, Calendar.AUGUST, 2026, 2, 0)

    private fun expect(expected: Calendar, alarm: Alarm) {
        assertEquals(expected.timeInMillis, AlarmScheduler.nextTrigger(alarm, now))
    }

    @Test
    fun oneShotLaterTodaySchedulesForToday() {
        expect(at(6, Calendar.AUGUST, 2026, 8, 0), Alarm(1, 8, 0))
    }

    @Test
    fun oneShotEarlierTodayReturnsNull() {
        assertNull(AlarmScheduler.nextTrigger(Alarm(1, 1, 0), now))
    }

    @Test
    fun weeklyNextSaturday() {
        val alarm = Alarm(1, 2, 15, repeatDays = setOf(Calendar.SATURDAY))
        expect(at(8, Calendar.AUGUST, 2026, 2, 15), alarm)
    }

    @Test
    fun weeklyTomorrow() {
        val alarm = Alarm(1, 9, 0, repeatDays = setOf(Calendar.FRIDAY))
        expect(at(7, Calendar.AUGUST, 2026, 9, 0), alarm)
    }

    @Test
    fun weeklyTodayLaterTimeFiresToday() {
        val alarm = Alarm(1, 8, 0, repeatDays = setOf(Calendar.THURSDAY))
        expect(at(6, Calendar.AUGUST, 2026, 8, 0), alarm)
    }

    @Test
    fun weeklyTodayEarlierTimeRollsToNextWeek() {
        val alarm = Alarm(1, 1, 0, repeatDays = setOf(Calendar.THURSDAY))
        expect(at(13, Calendar.AUGUST, 2026, 1, 0), alarm)
    }

    @Test
    fun monthlyNextTwelfth() {
        expect(at(12, Calendar.AUGUST, 2026, 8, 0), Alarm(1, 8, 0, monthlyDay = 12))
    }

    @Test
    fun monthlyDayFallsTodayLaterTime() {
        expect(at(6, Calendar.AUGUST, 2026, 8, 0), Alarm(1, 8, 0, monthlyDay = 6))
    }

    @Test
    fun monthlyDayFallsTodayEarlierTimeSkipsToNextMonth() {
        expect(at(6, Calendar.SEPTEMBER, 2026, 1, 0), Alarm(1, 1, 0, monthlyDay = 6))
    }

    @Test
    fun dateAlarmAddsHourAndMinute() {
        val alarm = Alarm(1, 8, 0, dateMillis = at(12, Calendar.AUGUST, 2026, 0, 0).timeInMillis)
        expect(at(12, Calendar.AUGUST, 2026, 8, 0), alarm)
    }

    @Test
    fun dateAlarmInPastReturnsNull() {
        val alarm = Alarm(1, 8, 0, dateMillis = at(1, Calendar.AUGUST, 2026, 0, 0).timeInMillis)
        assertNull(AlarmScheduler.nextTrigger(alarm, now))
    }

    @Test
    fun dateAlarmEarlierTodayReturnsNull() {
        val alarm = Alarm(1, 1, 0, dateMillis = at(6, Calendar.AUGUST, 2026, 0, 0).timeInMillis)
        assertNull(AlarmScheduler.nextTrigger(alarm, now))
    }

    @Test
    fun pendingIntentRequestCodesAreUniquePerAlarmAndRole() {
        val roles = intArrayOf(
            AlarmScheduler.ROLE_MAIN,
            AlarmScheduler.ROLE_SNOOZE,
            AlarmScheduler.ROLE_FULL_SCREEN,
            AlarmScheduler.ROLE_ACTION_SNOOZE,
            AlarmScheduler.ROLE_ACTION_DISMISS,
        )
        val codes = mutableSetOf<Int>()
        for (id in 0L..50L) {
            for (role in roles) {
                assertTrue(
                    "duplicate request code for alarm $id role $role",
                    codes.add(AlarmScheduler.requestCode(id, role)),
                )
            }
        }
        assertTrue(codes.size >= 51 * roles.size)
    }
}
