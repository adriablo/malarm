package com.malarm

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import java.util.TimeZone

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            AlarmScheduler.ACTION_SNOOZE -> handleSnooze(context, intent)
            AlarmScheduler.ACTION_DISMISS -> handleDismiss(context)
            AlarmScheduler.ACTION_ALARM -> handleAlarm(context, intent)
            AlarmScheduler.ACTION_RESCHEDULE_ALL -> handleRescheduleAll(context)
        }
    }

    private fun handleRescheduleAll(context: Context) {
        EventLog.log(context, EventType.RESCHEDULE_ALL)
        val store = AlarmStore(context)
        val current = TimeZone.getDefault().id
        val timezoneChanged = current != store.timeZoneId()
        val clockJumped = clockJumped(store)
        if (!timezoneChanged && !clockJumped) return
        val scheduler = AlarmScheduler(context)
        for (alarm in store.all()) {
            scheduler.cancel(alarm)
            if (alarm.enabled) scheduler.schedule(alarm)
        }
        store.setTimeZoneId(current)
        store.setClockCalibration(SystemClock.elapsedRealtime(), System.currentTimeMillis())
    }

    private fun clockJumped(store: AlarmStore): Boolean {
        val calib = store.clockCalibration() ?: return true
        return AlarmScheduler.clockJumped(
            calibElapsed = calib.first,
            calibWall = calib.second,
            nowElapsed = SystemClock.elapsedRealtime(),
            nowWall = System.currentTimeMillis(),
        )
    }

    private fun handleSnooze(context: Context, intent: Intent) {
        val id = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1)
        if (id < 0) return
        val store = AlarmStore(context)
        val alarm = store.get(id) ?: return
        AlarmScheduler(context).scheduleSnooze(alarm, store.snoozeMinutes() * 60_000L)
        EventLog.log(context, EventType.SNOOZED, id, alarm.label)
        stopRinging(context)
    }

    private fun handleDismiss(context: Context) {
        EventLog.log(context, EventType.DISMISSED)
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

        EventLog.log(context, EventType.FIRED, id, alarm.label, if (isSnooze) "Snooze" else "Alarm")

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

        if (scheduler.canScheduleExact()) {
            ContextCompat.startForegroundService(context, RingtoneService.intent(context, alarm))
        } else {
            val manager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(AlarmNotifier.NOTIFICATION_ID, AlarmNotifier.build(context, alarm))
        }
    }

    companion object {
        private const val BRIDGE_LOCK_TIMEOUT_MS = 30_000L
    }
}
