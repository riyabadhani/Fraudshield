package com.example.audioshareupload

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.audioshareupload.databinding.ItemRecordingBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecordingAdapter(
    private val onRecordingClicked: (Recording) -> Unit
) : RecyclerView.Adapter<RecordingAdapter.RecordingViewHolder>() {

    private val items = mutableListOf<Recording>()

    fun submitList(recordings: List<Recording>) {
        items.clear()
        items.addAll(recordings)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecordingViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return RecordingViewHolder(ItemRecordingBinding.inflate(inflater, parent, false))
    }

    override fun onBindViewHolder(holder: RecordingViewHolder, position: Int) {
        holder.bind(items[position], onRecordingClicked)
    }

    override fun getItemCount(): Int = items.size

    class RecordingViewHolder(
        private val binding: ItemRecordingBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        private val formatter = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())

        fun bind(recording: Recording, onRecordingClicked: (Recording) -> Unit) {
            val context = binding.root.context
            val (dotColor, statusTextColor, statusLabel) = when (recording.status) {
                UploadStatus.SUCCESS -> Triple(R.color.status_green, R.color.status_green, "Sent")
                UploadStatus.FAILED -> Triple(R.color.status_red, R.color.status_red, "Error")
                UploadStatus.UPLOADING -> Triple(R.color.accent_blue, R.color.accent_blue, "Analyzing")
                UploadStatus.PENDING -> Triple(R.color.text_tertiary, R.color.text_tertiary, "Queued")
            }

            binding.fileNameText.text = recording.fileName
            binding.timeText.text = formatter.format(Date(recording.receivedAt))
            binding.statusDot.background.setTint(ContextCompat.getColor(context, dotColor))
            binding.statusText.text = statusLabel
            binding.statusText.setTextColor(ContextCompat.getColor(context, statusTextColor))
            binding.root.isClickable = true
            binding.root.setOnClickListener { onRecordingClicked(recording) }
        }
    }
}
