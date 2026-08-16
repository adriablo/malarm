package com.malarm

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.malarm.databinding.ItemEventBinding

class EventLogAdapter(
    private var events: List<AlarmEvent>
) : RecyclerView.Adapter<EventLogAdapter.Holder>() {

    fun submit(list: List<AlarmEvent>) {
        events = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemEventBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun getItemCount(): Int = events.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(events[position])
    }

    inner class Holder(private val binding: ItemEventBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(event: AlarmEvent) {
            binding.tvTimestamp.text = EventLog.formatTimestamp(event.timestamp)
            val labelPart = event.label?.takeIf { it.isNotBlank() }?.let { "$it " } ?: ""
            val idPart = event.alarmId?.let { "($it)" } ?: ""
            binding.tvType.text = "${event.type.name.replace("_", " ")} $labelPart$idPart".trim()
            binding.tvDetails.text = event.details ?: ""
        }
    }
}
