package com.malarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat

object AlarmNotifier {

    const val CHANNEL_ID = "alarm_ringing"
    const val NOTIFICATION_ID = 1001

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.alarm_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ),
        )
    }

    fun build(context: Context, alarm: Alarm): android.app.Notification {
        val fullScreen = PendingIntent.getActivity(
            context,
            AlarmScheduler.requestCode(alarm.id, AlarmScheduler.ROLE_FULL_SCREEN),
            AlarmActivity.intent(context, alarm.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val snooze = PendingIntent.getActivity(
            context,
            AlarmScheduler.requestCode(alarm.id, AlarmScheduler.ROLE_ACTION_SNOOZE),
            AlarmActivity.intent(context, alarm.id, AlarmActivity.Action.SNOOZE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val dismiss = PendingIntent.getActivity(
            context,
            AlarmScheduler.requestCode(alarm.id, AlarmScheduler.ROLE_ACTION_DISMISS),
            AlarmActivity.intent(context, alarm.id, AlarmActivity.Action.DISMISS),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alarm_small)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(
                if (alarm.label.isBlank()) AlarmFormatter.time(context, alarm) else alarm.label,
            )
            .setContentIntent(fullScreen)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setAutoCancel(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(fullScreen, true)
            .addAction(0, context.getString(R.string.snooze), snooze)
            .addAction(0, context.getString(R.string.dismiss), dismiss)
            .build()
    }

    fun cancel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(NOTIFICATION_ID)
    }
}
