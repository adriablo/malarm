package com.malarm

import android.content.Context
import java.util.Calendar
import java.util.Date

object AlarmFormatter {

    val DAY_NAMES = intArrayOf(
        Calendar.MONDAY,
        Calendar.TUESDAY,
        Calendar.WEDNESDAY,
        Calendar.THURSDAY,
        Calendar.FRIDAY,
        Calendar.SATURDAY,
        Calendar.SUNDAY,
    )
    val DAY_SHORT = arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    fun time(context: Context, alarm: Alarm): String {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, alarm.hour)
            set(Calendar.MINUTE, alarm.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT)
            .format(Date(cal.timeInMillis))
    }

    fun repeat(alarm: Alarm): String {
        if (alarm.dateMillis != null) {
            return java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM)
                .format(Date(alarm.dateMillis))
        }
        alarm.monthlyDay?.let { return "Monthly on ${ordinal(it)}" }
        if (!alarm.isRepeating) return "Once"
        if (alarm.repeatDays.containsAll(DAY_NAMES.toList())) return "Daily"
        if (alarm.repeatDays == DAY_NAMES.take(5).toSet()) return "Weekdays"
        if (alarm.repeatDays == setOf(Calendar.SATURDAY, Calendar.SUNDAY)) return "Weekends"
        return DAY_SHORT
            .filterIndexed { index, _ -> DAY_NAMES[index] in alarm.repeatDays }
            .joinToString(", ")
    }

    private fun ordinal(n: Int): String = when {
        n % 100 in 11..13 -> "${n}th"
        n % 10 == 1 -> "${n}st"
        n % 10 == 2 -> "${n}nd"
        n % 10 == 3 -> "${n}rd"
        else -> "${n}th"
    }
}
