package com.example.audioshareupload

data class UploadResponse(
    val analysis: RecordingAnalysis?,
    val fraudshieldError: String?,
    val transcript: String?,
    val detectedLanguage: String?
)
