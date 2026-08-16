package com.malarm

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
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

        ContextCompat.startForegroundService(this, RingtoneService.intent(this, alarm!!))
    }

    private fun snooze() {
        val alarm = alarm ?: return
        sendBroadcast(
            Intent(this, AlarmReceiver::class.java)
                .setAction(AlarmScheduler.ACTION_SNOOZE)
                .putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarm.id),
        )
    }

    private fun dismiss() {
        stopRinging()
    }

    private fun stopRinging() {
        stopService(Intent(this, RingtoneService::class.java))
        AlarmNotifier.cancel(this)
    }

    companion object {
        fun intent(context: Context, alarmId: Long): Intent =
            Intent(context, AlarmActivity::class.java)
                .putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
    }
}
