package com.malarm

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class SnoozePickerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val alarmId = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1)
        val alarm = AlarmStore(this).get(alarmId)
        if (alarm == null) {
            finish()
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.snooze)
            .setItems(SNOOZE_OPTIONS.map { snoozeLabel(it) }.toTypedArray()) { _, which ->
                snooze(alarm, SNOOZE_OPTIONS[which])
            }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun snooze(alarm: Alarm, minutes: Int) {
        AlarmScheduler(this).scheduleSnooze(alarm, minutes * 60_000L)
        EventLog.log(this, EventType.SNOOZED, alarm.id, alarm.label, "$minutes min")
        AlarmNotifier.stopRinging(this)
        finish()
    }

    private fun snoozeLabel(minutes: Int): String =
        if (minutes % 60 == 0) getString(R.string.hours_only, minutes / 60)
        else getString(R.string.minutes_only, minutes)

    companion object {
        private val SNOOZE_OPTIONS = intArrayOf(5, 10, 15, 30, 60, 120, 240, 480)

        fun intent(context: Context, alarmId: Long): Intent =
            Intent(context, SnoozePickerActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                .putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
    }
}
