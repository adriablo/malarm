package com.malarm

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.malarm.databinding.ActivityAlarmBinding

class AlarmActivity : AppCompatActivity() {

    enum class Action { SNOOZE, DISMISS }

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

        val id = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1)
        alarm = AlarmStore(this).get(id)
        if (alarm == null) {
            finish()
            return
        }

        binding.label.text = alarm!!.label.ifBlank { getString(R.string.app_name) }
        binding.time.text = AlarmFormatter.time(this, alarm!!)
        binding.repeat.text = AlarmFormatter.repeat(this, alarm!!)

        binding.snooze.setOnClickListener {
            snooze()
            finish()
        }
        binding.dismiss.setOnClickListener {
            dismiss()
            finish()
        }

        if (intent.getStringExtra(EXTRA_ACTION) == null) {
            ContextCompat.startForegroundService(this, RingtoneService.intent(this, alarm!!))
        }
        handleAction(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAction(intent)
    }

    private fun handleAction(intent: Intent?) {
        when (intent?.getStringExtra(EXTRA_ACTION)) {
            Action.SNOOZE.name -> {
                snooze()
                finish()
            }
            Action.DISMISS.name -> {
                dismiss()
                finish()
            }
        }
    }

    private fun snooze() {
        val alarm = alarm ?: return
        val minutes = AlarmStore(this).snoozeMinutes()
        AlarmScheduler(this).scheduleSnooze(alarm, minutes * 60_000L)
        stopRinging()
    }

    private fun dismiss() {
        stopRinging()
    }

    private fun stopRinging() {
        stopService(Intent(this, RingtoneService::class.java))
        AlarmNotifier.cancel(this)
    }

    companion object {
        const val EXTRA_ACTION = "com.malarm.EXTRA_ACTION"

        fun intent(context: Context, alarmId: Long, action: Action? = null): Intent =
            Intent(context, AlarmActivity::class.java).apply {
                putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
                putExtra(EXTRA_ACTION, action?.name)
            }
    }
}
