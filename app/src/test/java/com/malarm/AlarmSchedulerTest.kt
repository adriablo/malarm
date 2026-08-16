package com.malarm

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    private fun expect(expected: Calendar, alarm: Alarm) =
        expect(now, expected, alarm)

    private fun expect(now: Calendar, expected: Calendar, alarm: Alarm) {
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
    fun monthlyDay31SkipsShortMonth() {
        expect(
            at(30, Calendar.APRIL, 2026, 2, 0),
            at(31, Calendar.MAY, 2026, 8, 0),
            Alarm(1, 8, 0, monthlyDay = 31),
        )
    }

    @Test
    fun monthlyDay29SkipsNonLeapFebruary() {
        expect(
            at(28, Calendar.FEBRUARY, 2027, 2, 0),
            at(29, Calendar.MARCH, 2027, 8, 0),
            Alarm(1, 8, 0, monthlyDay = 29),
        )
    }

    @Test
    fun monthlyDay29FiresOnLeapYearFebruary() {
        expect(
            at(28, Calendar.FEBRUARY, 2028, 2, 0),
            at(29, Calendar.FEBRUARY, 2028, 8, 0),
            Alarm(1, 8, 0, monthlyDay = 29),
        )
    }

    @Test
    fun monthlyInvalidDayReturnsNull() {
        assertNull(AlarmScheduler.nextTrigger(Alarm(1, 8, 0, monthlyDay = 0), now))
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
    fun dateAlarmOnSpringForwardDayUsesWallClockTime() {
        withTimeZone("America/New_York") {
            val now = at(7, Calendar.MARCH, 2026, 12, 0)
            val alarm = Alarm(1, 8, 0, dateMillis = at(8, Calendar.MARCH, 2026, 0, 0).timeInMillis)
            expect(now, at(8, Calendar.MARCH, 2026, 8, 0), alarm)
        }
    }

    @Test
    fun dateAlarmOnFallBackDayUsesWallClockTime() {
        withTimeZone("America/New_York") {
            val now = at(31, Calendar.OCTOBER, 2026, 12, 0)
            val alarm = Alarm(1, 8, 0, dateMillis = at(1, Calendar.NOVEMBER, 2026, 0, 0).timeInMillis)
            expect(now, at(1, Calendar.NOVEMBER, 2026, 8, 0), alarm)
        }
    }

    private fun withTimeZone(id: String, block: () -> Unit) {
        val original = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(id))
        try {
            block()
        } finally {
            TimeZone.setDefault(original)
        }
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

    @Test
    fun alarmRequestCodesAreNonNegative() {
        val roles = intArrayOf(
            AlarmScheduler.ROLE_MAIN,
            AlarmScheduler.ROLE_SNOOZE,
            AlarmScheduler.ROLE_FULL_SCREEN,
            AlarmScheduler.ROLE_ACTION_SNOOZE,
            AlarmScheduler.ROLE_ACTION_DISMISS,
        )
        // Alarm ids are positive, so their request codes must never enter the
        // negative namespace reserved for app-wide PendingIntents.
        for (id in 0L..1_000_000L) {
            for (role in roles) {
                assertTrue(
                    "alarm $id role $role produced a negative request code",
                    AlarmScheduler.requestCode(id, role) >= 0,
                )
            }
        }
    }

    @Test
    fun clockJumpedFalseWhenWallClockTracksElapsed() {
        // calibration at t=0; after 4h elapsed, wall also moved 4h -> no jump
        assertFalse(AlarmScheduler.clockJumped(0L, 1_000_000_000_000L, 4 * 3600_000L, 1_000_000_000_000L + 4 * 3600_000L))
    }

    @Test
    fun clockJumpedTrueWhenWallClockMovesAhead() {
        // wall moved 6h while only 4h elapsed -> 2h manual forward jump
        assertTrue(AlarmScheduler.clockJumped(0L, 1_000_000_000_000L, 4 * 3600_000L, 1_000_000_000_000L + 6 * 3600_000L))
    }

    @Test
    fun clockJumpedTrueWhenWallClockMovesBack() {
        // wall moved 2h while 4h elapsed -> 2h manual backward jump
        assertTrue(AlarmScheduler.clockJumped(0L, 1_000_000_000_000L, 4 * 3600_000L, 1_000_000_000_000L + 2 * 3600_000L))
    }

    @Test
    fun clockJumpedFalseForSubToleranceDrift() {
        // 1 minute drift is within the 2-minute tolerance
        assertFalse(AlarmScheduler.clockJumped(0L, 1_000_000_000_000L, 3600_000L, 1_000_000_000_000L + 3600_000L + 60_000L))
    }
}
