package com.voltagealert.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.voltagealert.databinding.ItemLogEntryBinding
import com.voltagealert.logging.VoltageLogEntry

/**
 * RecyclerView adapter for displaying voltage log entries.
 * Uses DiffUtil for efficient updates.
 */
class LogAdapter : ListAdapter<VoltageLogEntry, LogAdapter.LogViewHolder>(LogDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val binding = ItemLogEntryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return LogViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class LogViewHolder(
        private val binding: ItemLogEntryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(logEntry: VoltageLogEntry) {
            // Format: "1. 2025/12/23 08:45:25 220V"
            binding.tvLogEntry.text = logEntry.getFormattedDisplay()
        }
    }

    private class LogDiffCallback : DiffUtil.ItemCallback<VoltageLogEntry>() {
        override fun areItemsTheSame(oldItem: VoltageLogEntry, newItem: VoltageLogEntry): Boolean {
            // Items are the same if they have the same sequence number and timestamp
            return oldItem.sequenceNumber == newItem.sequenceNumber &&
                    oldItem.timestamp == newItem.timestamp
        }

        override fun areContentsTheSame(oldItem: VoltageLogEntry, newItem: VoltageLogEntry): Boolean {
            return oldItem == newItem
        }
    }
}
