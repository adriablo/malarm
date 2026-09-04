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
            scheduler.cancel(alarm, reason)
            if (alarm.enabled) scheduler.schedule(alarm)
        }
        scheduler.schedulePeriodicReschedule()
        store.setClockCalibration(SystemClock.elapsedRealtime(), System.currentTimeMillis())
    }
}
