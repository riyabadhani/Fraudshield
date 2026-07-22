package com.example.audioshareupload

data class Recording(
    val id: String,
    val fileName: String,
    val storedPath: String,
    val mimeType: String,
    val status: UploadStatus,
    val receivedAt: Long,
    val durationSeconds: Int = 0,
    val progressPercent: Int = 0,
    val analysis: RecordingAnalysis? = null,
    val analysisCompletedAt: Long? = null,
    val analysisError: String? = null
)
