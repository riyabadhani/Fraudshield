package com.example.audioshareupload

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.audioshareupload.databinding.ActivityRecordingDetailBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecordingDetailActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRecordingDetailBinding
    private lateinit var store: RecordingStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecordingDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        store = RecordingStore(this)

        binding.backButton.setOnClickListener { finish() }
        renderRecording()
    }

    override fun onResume() {
        super.onResume()
        renderRecording()
    }

    private fun renderRecording() {
        val recordingId = intent.getStringExtra(EXTRA_RECORDING_ID) ?: return
        val recording = store.findById(recordingId) ?: return

        binding.fileNameText.text = recording.fileName
        binding.metaText.text = buildMetaLine(recording)

        when {
            recording.analysis != null -> renderAnalysis(recording)
            recording.status == UploadStatus.FAILED -> renderFallback(
                title = getString(R.string.analysis_failed_title),
                message = recording.analysisError ?: getString(R.string.analysis_failed_message),
                status = getString(R.string.analysis_failed_status),
                colorRes = R.color.status_red
            )
            else -> renderFallback(
                title = getString(R.string.analysis_pending_title),
                message = getString(R.string.analysis_pending_message),
                status = getString(R.string.analysis_pending_status),
                colorRes = R.color.accent_blue
            )
        }
    }

    private fun renderAnalysis(recording: Recording) {
        val analysis = recording.analysis ?: return
        val isScam = analysis.prediction.equals("SCAM", ignoreCase = true)
        val accentColor = if (isScam) R.color.status_red else R.color.status_green
        val titleColor = getColor(accentColor)

        binding.analysisHeaderLabel.text = getString(R.string.analysis_result_label)
        binding.predictionText.text = analysis.prediction
        binding.predictionText.setTextColor(titleColor)
        binding.riskPill.text = if (analysis.riskLevel.equals("HIGH", ignoreCase = true)) {
            getString(R.string.high_risk)
        } else {
            getString(R.string.risk_pill, analysis.riskLevel)
        }
        binding.riskPill.setTextColor(titleColor)
        binding.riskPill.backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(if (isScam) R.color.risk_pill_red else R.color.risk_pill_green))
        binding.summaryIcon.setImageResource(if (isScam) R.drawable.ic_warning else R.drawable.ic_check)
        binding.summaryIcon.imageTintList = android.content.res.ColorStateList.valueOf(titleColor)

        binding.confidenceValue.text = getString(R.string.confidence_value, analysis.confidence.toInt())
        binding.confidenceValue.setTextColor(getColor(R.color.status_green))
        binding.riskValue.text = analysis.riskLevel.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        binding.riskValue.setTextColor(titleColor)
        binding.attackTypeValue.text = analysis.attackType
        binding.redFlagsBody.text = analysis.redFlags.joinToString(separator = "\n") { "• $it" }
        binding.explanationBody.text = analysis.explanation
        binding.safeActionBody.text = analysis.safeAction
        binding.redFlagsTitle.setTextColor(titleColor)
        binding.explanationTitle.setTextColor(getColor(R.color.status_green))
        binding.safeActionTitle.setTextColor(getColor(R.color.status_green))
        binding.analysisStatusText.text = getString(R.string.analysis_completed)
        binding.analysisStatusTime.text = formatTimestamp(recording.analysisCompletedAt ?: recording.receivedAt)
    }

    private fun renderFallback(title: String, message: String, status: String, colorRes: Int) {
        val color = getColor(colorRes)
        binding.analysisHeaderLabel.text = getString(R.string.analysis_result_label)
        binding.predictionText.text = title
        binding.predictionText.setTextColor(color)
        binding.riskPill.text = status
        binding.riskPill.setTextColor(color)
        binding.riskPill.backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.surface_secondary))
        binding.summaryIcon.setImageResource(R.drawable.ic_warning)
        binding.summaryIcon.imageTintList = android.content.res.ColorStateList.valueOf(color)
        binding.confidenceValue.text = "--"
        binding.riskValue.text = "--"
        binding.attackTypeValue.text = "--"
        binding.redFlagsTitle.setTextColor(color)
        binding.redFlagsBody.text = message
        binding.explanationTitle.setTextColor(getColor(R.color.text_primary))
        binding.explanationBody.text = message
        binding.safeActionTitle.setTextColor(getColor(R.color.text_primary))
        binding.safeActionBody.text = getString(R.string.analysis_fallback_action)
        binding.analysisStatusText.text = status
        binding.analysisStatusTime.text = formatTimestamp(System.currentTimeMillis())
    }

    private fun buildMetaLine(recording: Recording): String {
        val timestamp = SimpleDateFormat("dd MMM yyyy • h:mm a", Locale.getDefault()).format(Date(recording.receivedAt))
        val duration = formatDuration(recording.durationSeconds)
        return if (duration.isBlank()) timestamp else "$timestamp • $duration"
    }

    private fun formatTimestamp(epochMillis: Long): String {
        return SimpleDateFormat("dd MMM yyyy • h:mm a", Locale.getDefault()).format(Date(epochMillis))
    }

    private fun formatDuration(totalSeconds: Int): String {
        if (totalSeconds <= 0) return ""
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    companion object {
        const val EXTRA_RECORDING_ID = "recording_id"
    }
}
