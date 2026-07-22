package com.example.audioshareupload

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class Uploader {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .callTimeout(120, TimeUnit.SECONDS)
        .build()

    suspend fun upload(
        recording: Recording,
        phoneNumber: String,
        onProgress: (Int) -> Unit
    ): Result<UploadResponse> = withContext(Dispatchers.IO) {
        val file = File(recording.storedPath)
        if (!file.exists()) {
            return@withContext Result.failure(IOException("Saved file missing"))
        }

        val fileBody = ProgressRequestBody(
            file = file,
            mediaType = recording.mimeType.toMediaTypeOrNull(),
            onProgress = onProgress
        )

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("phone_number", phoneNumber)
            .addFormDataPart("file", recording.fileName, fileBody)
            .build()

        val request = Request.Builder()
            .url("${BuildConfig.UPLOAD_BASE_URL}/upload")
            .post(requestBody)
            .build()

        runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Upload failed with HTTP ${response.code}")
                }
                val responseText = response.body?.string().orEmpty()
                parseUploadResponse(responseText)
            }
        }
    }

    private fun parseUploadResponse(json: String): UploadResponse {
        val payload = JSONObject(json)
        val fraudshieldError = payload.optString("fraudshield_error").takeIf { it.isNotBlank() && it != "null" }
        val fraudshieldResult = payload.optJSONObject("fraudshield_result")

        return UploadResponse(
            analysis = fraudshieldResult?.toRecordingAnalysis(),
            fraudshieldError = fraudshieldError,
            transcript = payload.optString("transcript").takeIf { it.isNotBlank() },
            detectedLanguage = payload.optString("detected_language").takeIf { it.isNotBlank() }
        )
    }

    private fun JSONObject.toRecordingAnalysis(): RecordingAnalysis {
        val redFlagsJson = optJSONArray("red_flags") ?: JSONArray()
        val redFlags = buildList {
            for (index in 0 until redFlagsJson.length()) {
                add(redFlagsJson.optString(index))
            }
        }

        return RecordingAnalysis(
            prediction = optString("prediction", "UNKNOWN"),
            confidence = optDouble("confidence", 0.0),
            riskLevel = optString("risk_level", "LOW"),
            attackType = optString("attack_type", "Unknown"),
            redFlags = redFlags,
            explanation = optString("explanation", "No explanation available."),
            safeAction = optString("safe_action", "No safe action available.")
        )
    }
}
