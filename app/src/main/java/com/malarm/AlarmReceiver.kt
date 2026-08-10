package com.malarm

import android.app.ForegroundServiceStartNotAllowedException
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.core.content.ContextCompat

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            AlarmScheduler.ACTION_SNOOZE -> handleSnooze(context, intent)
            AlarmScheduler.ACTION_DISMISS -> handleDismiss(context)
            AlarmScheduler.ACTION_ALARM -> handleAlarm(context, intent)
        }
    }

    private fun handleSnooze(context: Context, intent: Intent) {
        val id = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1)
        if (id < 0) return
        val store = AlarmStore(context)
        val alarm = store.get(id) ?: return
        AlarmScheduler(context).scheduleSnooze(alarm, store.snoozeMinutes() * 60_000L)
        stopRinging(context)
    }

    private fun handleDismiss(context: Context) {
        stopRinging(context)
    }

    private fun stopRinging(context: Context) {
        context.stopService(Intent(context, RingtoneService::class.java))
        AlarmNotifier.cancel(context)
    }

    private fun handleAlarm(context: Context, intent: Intent) {
        if (intent.action != AlarmScheduler.ACTION_ALARM) return
        val id = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1)
        if (id < 0) return

        val isSnooze = intent.getBooleanExtra(AlarmScheduler.EXTRA_IS_SNOOZE, false)
        val store = AlarmStore(context)
        val alarm = store.get(id) ?: return
        if (!alarm.enabled && !isSnooze) return

        val scheduler = AlarmScheduler(context)
        if (!isSnooze) {
            if (alarm.isRepeating) {
                scheduler.schedule(alarm)
            } else {
                store.save(alarm.copy(enabled = false))
            }
        }

        AlarmNotifier.ensureChannel(context)

        // Keep the CPU awake while the full-screen intent is being shown.
        val bridgeLock = (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "${context.packageName}:bridge")
        bridgeLock.acquire(BRIDGE_LOCK_TIMEOUT_MS)

        try {
            ContextCompat.startForegroundService(context, RingtoneService.intent(context, alarm))
        } catch (e: ForegroundServiceStartNotAllowedException) {
            // AlarmManager grants a temp allowlist for real alarm fires; when that
            // is unavailable, fall back to a full-screen intent notification.
            val manager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(AlarmNotifier.NOTIFICATION_ID, AlarmNotifier.build(context, alarm))
        }
    }

    companion object {
        private const val BRIDGE_LOCK_TIMEOUT_MS = 30_000L
    }
}
