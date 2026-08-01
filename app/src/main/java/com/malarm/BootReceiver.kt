package com.malarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != Intent.ACTION_TIME_CHANGED &&
            action != Intent.ACTION_TIMEZONE_CHANGED
        ) {
            return
        }
        val store = AlarmStore(context)
        val scheduler = AlarmScheduler(context)
        for (alarm in store.all()) {
            scheduler.cancel(alarm)
            if (alarm.enabled) scheduler.schedule(alarm)
        }
    }
}
