package com.malarm

import android.os.Bundle
import android.widget.NumberPicker
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.malarm.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var store: AlarmStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        store = AlarmStore(this)

        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.snoozeRow.setOnClickListener { showSnoozeDialog() }
        updateSnoozeValue()

        binding.versionValue.text = getString(
            R.string.version_format,
            BuildConfig.VERSION_NAME,
            BuildConfig.VERSION_CODE,
        )
    }

    private fun updateSnoozeValue() {
        binding.snoozeValue.text = getString(R.string.minutes_format, store.snoozeMinutes())
    }

    private fun showSnoozeDialog() {
        val labels = (1..60).map { getString(R.string.minutes_format, it) }.toTypedArray()
        val picker = NumberPicker(this).apply {
            minValue = 1
            maxValue = 60
            wrapSelectorWheel = false
            value = store.snoozeMinutes().coerceIn(minValue, maxValue)
            displayedValues = labels
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.snooze_duration)
            .setView(picker)
            .setPositiveButton(R.string.save) { _, _ ->
                store.setSnoozeMinutes(picker.value)
                updateSnoozeValue()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
