package com.malarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        val store = AlarmStore(context)
        val scheduler = AlarmScheduler(context)
        for (alarm in store.all()) {
            scheduler.cancel(alarm)
            if (alarm.enabled) scheduler.schedule(alarm)
        }
        scheduler.schedulePeriodicReschedule()
        store.setClockCalibration(SystemClock.elapsedRealtime(), System.currentTimeMillis())
    }
}
