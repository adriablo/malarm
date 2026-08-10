package com.malarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

class AlarmScheduler(private val context: Context) {

    private val alarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun schedule(alarm: Alarm) {
        val trigger = nextTrigger(alarm) ?: return
        setExact(trigger, alarmPendingIntent(alarm.id, ROLE_MAIN, isSnooze = false))
    }

    fun cancel(alarm: Alarm) {
        alarmManager.cancel(alarmPendingIntent(alarm.id, ROLE_MAIN, isSnooze = false))
        alarmManager.cancel(alarmPendingIntent(alarm.id, ROLE_SNOOZE, isSnooze = true))
    }

    fun scheduleSnooze(alarm: Alarm, delayMillis: Long) {
        setExact(
            System.currentTimeMillis() + delayMillis,
            alarmPendingIntent(alarm.id, ROLE_SNOOZE, isSnooze = true),
        )
    }

    fun cancelSnooze(alarm: Alarm) {
        alarmManager.cancel(alarmPendingIntent(alarm.id, ROLE_SNOOZE, isSnooze = true))
    }

    private fun setExact(triggerAtMillis: Long, pi: PendingIntent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            // Exact alarms not granted: degrade to an inexact 1-minute window so the
            // alarm still fires, and point the user to the exact-alarm settings.
            alarmManager.setWindow(AlarmManager.RTC_WAKEUP, triggerAtMillis, 60_000L, pi)
            return
        }
        alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(triggerAtMillis, null),
            pi,
        )
    }

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    fun nextTrigger(alarm: Alarm): Long? = nextTrigger(alarm, Calendar.getInstance())

    companion object {
        const val ACTION_ALARM = "com.malarm.ACTION_ALARM"
        const val EXTRA_ALARM_ID = "com.malarm.EXTRA_ALARM_ID"
        const val EXTRA_IS_SNOOZE = "com.malarm.EXTRA_IS_SNOOZE"

        // PendingIntent request codes must be unique per (alarm, role): identity
        // ignores extras, so adjacent alarm ids would otherwise collide.
        const val ROLE_MAIN = 0
        const val ROLE_SNOOZE = 1
        const val ROLE_FULL_SCREEN = 2
        const val ROLE_ACTION_SNOOZE = 3
        const val ROLE_ACTION_DISMISS = 4
        private const val ROLE_STRIDE = 5

        fun requestCode(alarmId: Long, role: Int): Int {
            val hash = (alarmId xor (alarmId ushr 32)).toInt()
            return hash * ROLE_STRIDE + role
        }

        internal fun nextTrigger(alarm: Alarm, now: Calendar): Long? {
            if (alarm.dateMillis != null) {
                val cal = now.clone() as Calendar
                cal.timeInMillis = alarm.dateMillis
                cal.set(Calendar.HOUR_OF_DAY, alarm.hour)
                cal.set(Calendar.MINUTE, alarm.minute)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                return cal.timeInMillis.takeIf { it > now.timeInMillis }
            }
            if (alarm.monthlyDay != null) {
                val cal = now.clone() as Calendar
                cal.set(Calendar.HOUR_OF_DAY, alarm.hour)
                cal.set(Calendar.MINUTE, alarm.minute)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                for (i in 0..370) {
                    if (cal.get(Calendar.DAY_OF_MONTH) == alarm.monthlyDay &&
                        cal.timeInMillis > now.timeInMillis
                    ) {
                        return cal.timeInMillis
                    }
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                }
                return null
            }
            val cal = now.clone() as Calendar
            cal.set(Calendar.HOUR_OF_DAY, alarm.hour)
            cal.set(Calendar.MINUTE, alarm.minute)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            if (!alarm.isRepeating) {
                return if (cal.timeInMillis > now.timeInMillis) cal.timeInMillis else null
            }
            val days = alarm.repeatDays
            for (i in 0..7) {
                if (days.contains(cal.get(Calendar.DAY_OF_WEEK)) && cal.timeInMillis > now.timeInMillis) {
                    return cal.timeInMillis
                }
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
            return null
        }
    }

    private fun alarmPendingIntent(alarmId: Long, role: Int, isSnooze: Boolean): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_ALARM
            putExtra(EXTRA_ALARM_ID, alarmId)
            putExtra(EXTRA_IS_SNOOZE, isSnooze)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode(alarmId, role),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
