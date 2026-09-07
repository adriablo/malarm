package com.malarm

import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.malarm.databinding.ActivityEventLogBinding
import kotlinx.coroutines.launch

class EventLogActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEventLogBinding
    private lateinit var adapter: EventLogAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEventLogBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        // Keep the Export/Clear buttons clear of the system navigation bar.
        val baseBottom = binding.buttonRow.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.buttonRow) { view, insets ->
            val bottomInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            view.setPadding(
                view.paddingLeft,
                view.paddingTop,
                view.paddingRight,
                baseBottom + bottomInset,
            )
            insets
        }

        adapter = EventLogAdapter(emptyList())
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        binding.btnExport.setOnClickListener { exportLog() }
        binding.btnClear.setOnClickListener { clearLog() }
    }

    override fun onResume() {
        super.onResume()
        loadEvents()
    }

    private fun loadEvents() {
        lifecycleScope.launch {
            val events = EventLog.getAll(this@EventLogActivity)
            adapter.submit(events)
        }
    }

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            val csv = EventLogExport.toCsv(EventLog.getAll(this@EventLogActivity))
            runCatching {
                contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(csv) }
            }.onFailure {
                Toast.makeText(this@EventLogActivity, R.string.export_error, Toast.LENGTH_SHORT).show()
                return@launch
            }
            Toast.makeText(this@EventLogActivity, R.string.export_done, Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportLog() {
        exportLauncher.launch(EventLogExport.fileName())
    }

    private fun clearLog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.clear_log_title)
            .setMessage(R.string.clear_log_confirm)
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch {
                    EventLog.clear(this@EventLogActivity)
                    loadEvents()
                    Toast.makeText(this@EventLogActivity, R.string.log_cleared, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
