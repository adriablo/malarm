package com.malarm

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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

        adapter = EventLogAdapter(emptyList())
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        loadEvents()

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

    private fun exportLog() {
        lifecycleScope.launch {
            val events = EventLog.getAll(this@EventLogActivity)
            val sb = StringBuilder()
            sb.append("Malarm Event Log\n\n")
            for (event in events) {
                val labelPart = event.label?.takeIf { it.isNotBlank() }?.let { "$it " } ?: ""
                val idPart = event.alarmId?.let { "($it)" } ?: ""
                sb.append("${EventLog.formatTimestamp(event.timestamp)} | ${event.type.name} $labelPart$idPart | ${event.details ?: ""}\n".trimEnd() + "\n")
            }
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, sb.toString())
            }
            startActivity(Intent.createChooser(intent, "Export log"))
        }
    }

    private fun clearLog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.clear)
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
