package com.malarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> EventLog.log(context, EventType.BOOT_COMPLETED)
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            -> EventLog.log(context, EventType.TIMEZONE_CHANGED)
            else -> return
        }
        val store = AlarmStore(context)
        val scheduler = AlarmScheduler(context)
        val reason = when (intent.action) {
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> "Time change"
            else -> "Boot"
        }
        for (alarm in store.all()) {
            if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
                intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
            ) {
                // Boot wipes AlarmManager (and the elapsed clock resets), so drop everything.
                scheduler.cancel(alarm, reason)
            } else {
                // Clock jump: re-anchor wall-clock mains only; an active snooze is
                // elapsed-based and survives untouched (§6.8).
                scheduler.cancelMain(alarm, reason)
            }
            if (scheduler.isExpiredDateAlarm(alarm)) {
                store.save(alarm.copy(enabled = false))
                EventLog.log(context, EventType.DISABLED, alarm.id, alarm.label, "Will never ring")
            } else if (alarm.enabled) {
                scheduler.schedule(alarm)
            }
        }
        scheduler.schedulePeriodicReschedule()
        store.setClockCalibration(SystemClock.elapsedRealtime(), System.currentTimeMillis())
    }
}
