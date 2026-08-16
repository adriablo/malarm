package com.malarm

import android.content.Intent
import android.os.Bundle
import android.widget.NumberPicker
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.malarm.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var store: AlarmStore

    private val exportLauncher = registerForActivityResult(        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        val json = AlarmExport.export(store.all()).toString(2)
        runCatching {
            contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(json) }
        }.onFailure {
            Toast.makeText(this, R.string.export_error, Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }
        Toast.makeText(this, R.string.export_done, Toast.LENGTH_SHORT).show()
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        val content = runCatching {
            contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()
        val alarms = content?.let { AlarmExport.import(it) }
        if (alarms == null) {
            Toast.makeText(this, R.string.import_error, Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.import_title)
            .setMessage(resources.getQuantityString(R.plurals.import_confirm, alarms.size, alarms.size))
            .setPositiveButton(R.string.save) { _, _ -> applyImport(alarms) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        store = AlarmStore(this)

        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.snoozeRow.setOnClickListener { showSnoozeDialog() }
        updateSnoozeValue()

        binding.exportRow.setOnClickListener { exportLauncher.launch("malarm-alarms.json") }
        binding.importRow.setOnClickListener {
            importLauncher.launch(arrayOf("*/*"))
        }
        binding.removeInactiveRow.setOnClickListener {
            val inactive = store.all().filter {
                !it.enabled || AlarmScheduler(this).nextTrigger(it) == null
            }
            if (inactive.isEmpty()) {
                Toast.makeText(this, R.string.no_inactive_alarms, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.remove_inactive_alarms)
                .setMessage(resources.getQuantityString(R.plurals.remove_inactive_confirm, inactive.size, inactive.size))
                .setPositiveButton(R.string.delete) { _, _ -> removeInactive(inactive) }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
        binding.githubRow.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, GITHUB_URL.toUri()))
        }

        binding.versionValue.text = getString(
            R.string.version_format,
            BuildConfig.VERSION_NAME,
            BuildConfig.VERSION_CODE,
        )
    }

    private fun applyImport(alarms: List<Alarm>) {
        val scheduler = AlarmScheduler(this)
        store.all().forEach { scheduler.cancel(it) }
        val imported = store.importAll(alarms)
        imported.filter { it.enabled }.forEach { scheduler.schedule(it) }
        Toast.makeText(this, resources.getQuantityString(R.plurals.import_done, imported.size, imported.size), Toast.LENGTH_SHORT).show()
    }

    private fun removeInactive(inactive: List<Alarm>) {
        val scheduler = AlarmScheduler(this)
        inactive.forEach { scheduler.cancel(it) }
        store.deleteAll(inactive.map { it.id }.toSet())
        Toast.makeText(this, resources.getQuantityString(R.plurals.remove_inactive_done, inactive.size, inactive.size), Toast.LENGTH_SHORT).show()
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

    companion object {
        private const val GITHUB_URL = "https://github.com/adriablo/malarm"
    }
}
