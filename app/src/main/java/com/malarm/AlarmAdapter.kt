package com.malarm

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.malarm.databinding.ItemAlarmBinding

class AlarmAdapter(
    private var alarms: List<Alarm>,
    private val listener: Listener,
) : RecyclerView.Adapter<AlarmAdapter.Holder>() {

    interface Listener {
        fun onToggle(alarm: Alarm, enabled: Boolean)
        fun onClick(alarm: Alarm)
        fun onDelete(alarm: Alarm)
    }

    fun submit(list: List<Alarm>) {
        alarms = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemAlarmBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun getItemCount(): Int = alarms.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(alarms[position])
    }

    inner class Holder(private val binding: ItemAlarmBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(alarm: Alarm) {
            val context = binding.root.context
            binding.enabled.setOnCheckedChangeListener(null)
            binding.time.text = AlarmFormatter.time(alarm)
            binding.label.text = alarm.label.ifBlank { context.getString(R.string.app_name) }
            binding.repeat.text = AlarmFormatter.repeat(context, alarm)
            binding.enabled.isChecked = alarm.enabled

            binding.enabled.setOnCheckedChangeListener { _, checked ->
                listener.onToggle(alarm, checked)
            }
            binding.root.setOnClickListener { listener.onClick(alarm) }
            binding.delete.setOnClickListener { listener.onDelete(alarm) }
        }
    }
}
