package com.malarm

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.malarm.BuildConfig
import com.malarm.databinding.ActivityMainBinding
import com.malarm.databinding.DialogAlarmBinding
import java.util.Calendar
import java.util.TimeZone

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var store: AlarmStore
    private lateinit var scheduler: AlarmScheduler
    private lateinit var adapter: AlarmAdapter

    private val timeChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            store.all().forEach { alarm ->
                scheduler.cancel(alarm)
                if (alarm.enabled) scheduler.schedule(alarm)
            }
            store.setTimeZoneId(TimeZone.getDefault().id)
            scheduler.schedulePeriodicReschedule()
        }
    }

    private var dialog: androidx.appcompat.app.AlertDialog? = null
    private var dialogBinding: DialogAlarmBinding? = null
    private var editing: Alarm? = null
    private var isNewAlarm: Boolean = false

    private val ringtoneLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                result.data?.getParcelableExtra(
                    RingtoneManager.EXTRA_RINGTONE_PICKED_URI,
                    Uri::class.java,
                )
            } else {
                @Suppress("DEPRECATION")
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            editing = editing?.copy(
                ringtone = uri?.toString() ?: RingtoneService.RINGTONE_SILENT,
            )
            dialogBinding?.dialogRingtone?.text = ringtoneLabel()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        requestFullScreenIntentPermissionIfNeeded()
        updatePermissionGate()
        if (!granted) {
            Snackbar.make(binding.root, R.string.notifications_permission_denied, Snackbar.LENGTH_LONG).show()
        }
    }

    private val fullScreenPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) {
            Snackbar.make(binding.root, R.string.full_screen_permission_denied, Snackbar.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        store = AlarmStore(this)
        scheduler = AlarmScheduler(this)

        adapter = AlarmAdapter(store.all(), object : AlarmAdapter.Listener {
            override fun onToggle(alarm: Alarm, enabled: Boolean) {
                val updated = alarm.copy(enabled = enabled)
                store.save(updated)
                if (enabled) scheduler.schedule(updated) else scheduler.cancel(updated)
                adapter.submit(store.all())
            }

            override fun onClick(alarm: Alarm) = showAlarmDialog(alarm)

            override fun onDelete(alarm: Alarm) {
                scheduler.cancel(alarm)
                store.delete(alarm.id)
                adapter.submit(store.all())
            }
        })

        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter
        binding.fab.setOnClickListener { showAlarmDialog(null) }
        binding.settings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        binding.permissionAllow.setOnClickListener {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        binding.permissionSettings.setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
            )
        }

        handleDebugIntent(intent)
        warnIfExactAlarmsUnavailable()

        requestNotificationPermissionIfNeeded()
        updatePermissionGate()

        registerReceiver(
            timeChangeReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            },
        )
        scheduler.schedulePeriodicReschedule()
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { unregisterReceiver(timeChangeReceiver) }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionGate()
        adapter.submit(store.all())
    }

    private fun updatePermissionGate() {
        val required = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS,
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        binding.permissionGate.visibility =
            if (required) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun warnIfExactAlarmsUnavailable() {
        if (scheduler.canScheduleExact()) return
        Snackbar.make(
            binding.root,
            R.string.exact_alarm_permission_required,
            Snackbar.LENGTH_LONG,
        ).setAction(R.string.allow) {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:$packageName"),
                ),
            )
        }.show()
    }

    private fun handleDebugIntent(intent: Intent) {
        if (!BuildConfig.DEBUG) return
        if (!intent.getBooleanExtra("debug_schedule", false)) return
        val cal = Calendar.getInstance().apply { add(Calendar.MINUTE, 1) }
        val alarm = Alarm(
            id = store.nextId(),
            hour = cal.get(Calendar.HOUR_OF_DAY),
            minute = cal.get(Calendar.MINUTE),
            label = "Debug alarm",
            enabled = true,
        )
        store.save(alarm)
        scheduler.schedule(alarm)
        Toast.makeText(this, "Debug alarm scheduled in 1 min", Toast.LENGTH_LONG).show()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        requestFullScreenIntentPermissionIfNeeded()
    }

    private fun requestFullScreenIntentPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.USE_FULL_SCREEN_INTENT,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) {
            fullScreenPermissionLauncher.launch(Manifest.permission.USE_FULL_SCREEN_INTENT)
        }
    }

    private fun showAlarmDialog(alarm: Alarm?) {
        isNewAlarm = alarm == null
        val initial = alarm ?: Alarm(id = 0, hour = 8, minute = 0)
        editing = initial
        val db = DialogAlarmBinding.inflate(layoutInflater)
        dialogBinding = db

        db.dialogTime.text = AlarmFormatter.time(this, initial)
        db.dialogLabel.setText(initial.label)
        db.dialogDate.text = dateLabel(initial)
        db.dialogRepeat.text = AlarmFormatter.repeat(this, initial)
        db.dialogRingtone.text = getString(R.string.ringtone) + ": " + ringtoneTitle(initial.ringtone)
        db.dialogEnabled.isChecked = initial.enabled
        db.dialogDelete.visibility = if (alarm == null) android.view.View.GONE else android.view.View.VISIBLE

        db.dialogTime.setOnClickListener {
            val current = editing ?: return@setOnClickListener
            TimePickerDialog(
                this,
                { _, hour, minute ->
                    editing = current.copy(hour = hour, minute = minute)
                    db.dialogTime.text = AlarmFormatter.time(this, editing!!)
                },
                current.hour,
                current.minute,
                DateFormat.is24HourFormat(this),
            ).show()
        }

        db.dialogDate.setOnClickListener {
            val current = editing ?: return@setOnClickListener
            val cal = Calendar.getInstance().apply {
                current.dateMillis?.let { timeInMillis = it }
            }
            DatePickerDialog(
                this,
                { _, year, month, day ->
                    val midnight = Calendar.getInstance().apply {
                        set(year, month, day, 0, 0, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    editing = current.copy(dateMillis = midnight, repeatDays = emptySet())
                    db.dialogDate.text = dateLabel(editing!!)
                    db.dialogRepeat.text = AlarmFormatter.repeat(this, editing!!)
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH),
            ).apply {
                setButton(DialogInterface.BUTTON_NEGATIVE, getString(R.string.clear)) { _, _ ->
                    editing = current.copy(dateMillis = null)
                    db.dialogDate.text = dateLabel(editing!!)
                    db.dialogRepeat.text = AlarmFormatter.repeat(this@MainActivity, editing!!)
                }
            }.show()
        }

        db.dialogRepeat.setOnClickListener {
            val base = editing ?: return@setOnClickListener
            val options = arrayOf(
                getString(R.string.once),
                getString(R.string.daily),
                getString(R.string.weekdays),
                getString(R.string.weekends),
                getString(R.string.monthly),
                getString(R.string.choose_days),
            )
            var pending: Alarm? = null
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.repeat)
                .setSingleChoiceItems(options, repeatModeIndex(base)) { dialog, which ->
                    when (which) {
                        0 -> pending = base.copy(
                            repeatDays = emptySet(),
                            monthlyDay = null,
                            dateMillis = null,
                        )
                        1 -> pending = base.copy(
                            repeatDays = AlarmFormatter.DAY_NAMES.toSet(),
                            monthlyDay = null,
                            dateMillis = null,
                        )
                        2 -> pending = base.copy(
                            repeatDays = AlarmFormatter.DAY_NAMES.take(5).toSet(),
                            monthlyDay = null,
                            dateMillis = null,
                        )
                        3 -> pending = base.copy(
                            repeatDays = setOf(Calendar.SATURDAY, Calendar.SUNDAY),
                            monthlyDay = null,
                            dateMillis = null,
                        )
                        4 -> {
                            dialog.dismiss()
                            showMonthlyDayPicker(base)
                        }
                        5 -> {
                            dialog.dismiss()
                            showWeeklyDaysPicker(base)
                        }
                    }
                }
                .setPositiveButton(R.string.save) { _, _ ->
                    pending?.let { editing = it }
                }
                .setNegativeButton(R.string.cancel, null)
                .setOnDismissListener {
                    val result = editing ?: base
                    db.dialogRepeat.text = AlarmFormatter.repeat(this, result)
                    db.dialogDate.text = dateLabel(result)
                }
                .show()
        }

        db.dialogRingtone.setOnClickListener {
            val current = editing ?: return@setOnClickListener
            val uri = current.ringtone
                .takeIf { it.isNotBlank() && it != RingtoneService.RINGTONE_SILENT }
                ?.let { Uri.parse(it) }
            val picker = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, uri)
            }
            ringtoneLauncher.launch(picker)
        }

        db.dialogDelete.setOnClickListener {
            val current = editing ?: return@setOnClickListener
            scheduler.cancel(current)
            store.delete(current.id)
            adapter.submit(store.all())
            dialog?.dismiss()
        }

        dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.app_name)
            .setView(db.root)
            .setPositiveButton(R.string.save) { _, _ -> saveDialog() }
            .setNegativeButton(R.string.cancel, null)
            .setOnDismissListener {
                dialogBinding = null
                editing = null
                isNewAlarm = false
            }
            .show()
    }

    private fun repeatModeIndex(alarm: Alarm): Int = when {
        alarm.monthlyDay != null -> 4
        alarm.repeatDays.isEmpty() -> 0
        alarm.repeatDays.containsAll(AlarmFormatter.DAY_NAMES.toList()) -> 1
        alarm.repeatDays == AlarmFormatter.DAY_NAMES.take(5).toSet() -> 2
        alarm.repeatDays == setOf(Calendar.SATURDAY, Calendar.SUNDAY) -> 3
        else -> 5
    }

    private fun showWeeklyDaysPicker(alarm: Alarm) {
        val initial = editing ?: alarm
        val checked = BooleanArray(AlarmFormatter.DAY_NAMES.size) { i ->
            AlarmFormatter.DAY_NAMES[i] in initial.repeatDays
        }
        var pendingDays: Set<Int>? = null
        var pendingDate: Long? = initial.dateMillis
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.repeat)
            .setMultiChoiceItems(AlarmFormatter.DAY_SHORT, checked) { _, which, isChecked ->
                val newDays = (pendingDays ?: initial.repeatDays).toMutableSet()
                if (isChecked) newDays.add(AlarmFormatter.DAY_NAMES[which])
                else newDays.remove(AlarmFormatter.DAY_NAMES[which])
                pendingDays = newDays
                pendingDate = if (newDays.isEmpty()) initial.dateMillis else null
            }
            .setPositiveButton(R.string.save) { _, _ ->
                editing = initial.copy(
                    repeatDays = pendingDays ?: initial.repeatDays,
                    monthlyDay = null,
                    dateMillis = pendingDate,
                )
            }
            .setNegativeButton(R.string.cancel, null)
            .setOnDismissListener {
                val result = editing ?: alarm
                dialogBinding?.dialogRepeat?.text = AlarmFormatter.repeat(this, result)
                dialogBinding?.dialogDate?.text = dateLabel(result)
            }
            .show()
    }

    private fun showMonthlyDayPicker(alarm: Alarm) {
        val pad = (16 * resources.displayMetrics.density).toInt()
        val input = com.google.android.material.textfield.TextInputEditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText((alarm.monthlyDay
                ?: Calendar.getInstance().get(Calendar.DAY_OF_MONTH)).toString())
            selectAll()
        }
        val layout = com.google.android.material.textfield.TextInputLayout(this).apply {
            setHint(R.string.monthly_day)
            addView(input)
            setPadding(pad, pad, pad, pad)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.monthly_day)
            .setView(layout)
            .setPositiveButton(R.string.save) { _, _ ->
                val day = input.text.toString().toIntOrNull()?.coerceIn(1, 31) ?: 1
                editing = (editing ?: alarm).copy(
                    repeatDays = emptySet(),
                    monthlyDay = day,
                    dateMillis = null,
                )
                dialogBinding?.dialogRepeat?.text = AlarmFormatter.repeat(this, editing!!)
                dialogBinding?.dialogDate?.text = dateLabel(editing!!)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun saveDialog() {
        val db = dialogBinding ?: return
        val current = editing ?: return
        val updated = current.copy(
            id = if (isNewAlarm) store.nextId() else current.id,
            label = db.dialogLabel.text?.toString()?.trim().orEmpty(),
            enabled = db.dialogEnabled.isChecked,
        )
        scheduler.cancel(updated)
        store.save(updated)
        if (updated.enabled) scheduler.schedule(updated)
        adapter.submit(store.all())
        Toast.makeText(this, R.string.alarm_saved, Toast.LENGTH_SHORT).show()
    }

    private fun dateLabel(alarm: Alarm): String =
        getString(R.string.date) + ": " + (alarm.dateMillis?.let {
            java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(it)
        } ?: getString(R.string.date_none))

    private fun ringtoneLabel(): String {
        val current = editing ?: return getString(R.string.ringtone)
        return getString(R.string.ringtone) + ": " + ringtoneTitle(current.ringtone)
    }

    private fun ringtoneTitle(uriString: String): String {
        if (uriString == RingtoneService.RINGTONE_SILENT) return getString(R.string.silent)
        if (uriString.isBlank()) return getString(R.string.ringtone_default)
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return getString(R.string.ringtone_default)
        val ringtone: Ringtone? = RingtoneManager.getRingtone(this, uri)
        return ringtone?.getTitle(this) ?: getString(R.string.ringtone_default)
    }
}
