package com.malarm

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.malarm.databinding.ItemAlarmBinding

class AlarmAdapter(
    private val listener: Listener,
) : ListAdapter<Alarm, AlarmAdapter.Holder>(DIFF) {

    interface Listener {
        fun onToggle(alarm: Alarm, enabled: Boolean)
        fun onClick(alarm: Alarm)
        fun onDelete(alarm: Alarm)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemAlarmBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(position)
    }

    inner class Holder(private val binding: ItemAlarmBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(position: Int) {
            val context = binding.root.context
            binding.enabled.setOnCheckedChangeListener(null)
            val alarm = getItem(position)
            binding.time.text = AlarmFormatter.time(alarm)
            binding.label.text = alarm.label.ifBlank { context.getString(R.string.app_name) }
            binding.repeat.text = AlarmFormatter.repeat(context, alarm)
            binding.enabled.isChecked = alarm.enabled

            binding.enabled.setOnCheckedChangeListener { _, checked ->
                current()?.let { listener.onToggle(it, checked) }
            }
            binding.root.setOnClickListener { current()?.let(listener::onClick) }
            binding.delete.setOnClickListener { current()?.let(listener::onDelete) }
        }

        /** Resolves the current item at click time so handlers never see a stale bind-time copy. */
        private fun current(): Alarm? {
            val position = bindingAdapterPosition
            if (position == RecyclerView.NO_POSITION) return null
            return getItem(position)
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Alarm>() {
            override fun areItemsTheSame(oldItem: Alarm, newItem: Alarm): Boolean =
                oldItem.id == newItem.id

            // Alarm is a data class, so structural equality covers every field.
            override fun areContentsTheSame(oldItem: Alarm, newItem: Alarm): Boolean =
                oldItem == newItem
        }
    }
}
