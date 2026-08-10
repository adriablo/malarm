package com.malarm

import android.content.Context
import java.text.DateFormatSymbols
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
    val DAY_SHORT: Array<String>
        get() {
            val symbols = DateFormatSymbols.getInstance()
            return DAY_NAMES.map { symbols.shortWeekdays[it] }.toTypedArray()
        }

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

    fun repeat(context: Context, alarm: Alarm): String {
        if (alarm.dateMillis != null) {
            return java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM)
                .format(Date(alarm.dateMillis))
        }
        alarm.monthlyDay?.let { return context.getString(R.string.repeat_monthly_on_day, it) }
        if (!alarm.isRepeating) return context.getString(R.string.once)
        if (alarm.repeatDays.containsAll(DAY_NAMES.toList())) return context.getString(R.string.daily)
        if (alarm.repeatDays == DAY_NAMES.take(5).toSet()) return context.getString(R.string.weekdays)
        if (alarm.repeatDays == setOf(Calendar.SATURDAY, Calendar.SUNDAY)) return context.getString(R.string.weekends)
        return DAY_SHORT
            .filterIndexed { index, _ -> DAY_NAMES[index] in alarm.repeatDays }
            .joinToString(", ")
    }
}
