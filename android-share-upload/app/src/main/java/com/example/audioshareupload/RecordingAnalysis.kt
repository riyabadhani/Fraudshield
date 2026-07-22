package com.example.audioshareupload

data class RecordingAnalysis(
    val prediction: String,
    val confidence: Double,
    val riskLevel: String,
    val attackType: String,
    val redFlags: List<String>,
    val explanation: String,
    val safeAction: String
)
