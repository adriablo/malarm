package com.malarm

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.malarm.databinding.ActivityAlarmBinding

class AlarmActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlarmBinding
    private var alarm: Alarm? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAlarmBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
            window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }

        // Keep the Snooze/Dismiss buttons clear of the system navigation bar.
        val baseBottom = binding.root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bottomInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            view.setPadding(
                view.paddingLeft,
                view.paddingTop,
                view.paddingRight,
                baseBottom + bottomInset,
            )
            insets
        }

        val id = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1)
        alarm = AlarmStore(this).get(id)
        if (alarm == null) {
            finish()
            return
        }

        binding.label.text = alarm!!.label.ifBlank { getString(R.string.app_name) }
        binding.time.text = AlarmFormatter.time(alarm!!)
        binding.repeat.text = AlarmFormatter.repeat(this, alarm!!)

        binding.snooze.setOnClickListener {
            snooze()
            finish()
        }
        binding.custom.setOnClickListener {
            startActivity(SnoozePickerActivity.intent(this, alarm!!.id))
            finish()
        }
        binding.dismiss.setOnClickListener {
            dismiss()
            finish()
        }

        // Back must never orphan the ringtone: leaving the screen without an
        // explicit choice snoozes, like the Snooze button.
        onBackPressedDispatcher.addCallback(this) {
            snooze()
            finish()
        }

        ContextCompat.startForegroundService(this, RingtoneService.intent(this, alarm!!))
    }

    // In-process direct calls, not broadcasts: this activity is already in the
    // foreground, so round-tripping through the receiver queue would only add
    // latency (ringtone lingers) and a failure mode. Mirrors
    // AlarmReceiver.handleSnooze/handleDismiss, which stay for the
    // notification-action path (PendingIntents must go through the receiver).
    private fun snooze() {
        val alarm = alarm ?: return
        val minutes = AlarmStore(this).snoozeMinutes()
        AlarmScheduler(this).scheduleSnooze(alarm, minutes * 60_000L)
        EventLog.log(this, EventType.SNOOZED, alarm.id, alarm.label, "$minutes min")
        AlarmNotifier.stopRinging(this)
    }

    private fun dismiss() {
        val alarm = alarm ?: return
        val scheduler = AlarmScheduler(this)
        // Re-read the store: a one-shot is already disabled there by the
        // receiver, and the stale in-memory copy must not re-arm it.
        val current = AlarmStore(this).get(alarm.id)
        // Dismiss ends this instance, not the series: cancel() below also
        // clears tomorrow's main (armed at fire time). Compute up front
        // whether there is a next instance so the DISMISSED row records it.
        val reArms = current != null && current.enabled && scheduler.nextTrigger(current) != null
        scheduler.cancel(alarm, "Dismiss")
        EventLog.log(this, EventType.DISMISSED, alarm.id, alarm.label, "Re-armed".takeIf { reArms })
        AlarmNotifier.stopRinging(this)
        // No-op for disabled/deleted one-shots (nextTrigger null).
        if (reArms) scheduler.schedule(current!!)
    }

    companion object {
        fun intent(context: Context, alarmId: Long): Intent =
            Intent(context, AlarmActivity::class.java)
                .putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
    }
}
