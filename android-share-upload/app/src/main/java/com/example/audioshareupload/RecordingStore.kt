package com.example.audioshareupload

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class RecordingStore(private val context: Context) {
    private val recordingsDir = File(context.filesDir, "recordings").apply { mkdirs() }
    private val metadataFile = File(context.filesDir, "recordings_index.json")

    fun recordingsDirectory(): File = recordingsDir

    fun load(): List<Recording> {
        if (!metadataFile.exists()) return emptyList()
        val json = metadataFile.readText()
        if (json.isBlank()) return emptyList()

        val array = JSONArray(json)
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    Recording(
                        id = item.getString("id"),
                        fileName = item.getString("fileName"),
                        storedPath = item.getString("storedPath"),
                        mimeType = item.getString("mimeType"),
                        status = UploadStatus.valueOf(item.getString("status")),
                        receivedAt = item.optLong("receivedAt", 0L),
                        durationSeconds = item.optInt("durationSeconds", 0),
                        progressPercent = item.optInt("progressPercent", 0),
                        analysis = item.optJSONObject("analysis")?.toRecordingAnalysis(),
                        analysisCompletedAt = item.optLong("analysisCompletedAt").takeIf { item.has("analysisCompletedAt") },
                        analysisError = item.optString("analysisError").takeIf { item.has("analysisError") && it.isNotBlank() }
                    )
                )
            }
        }
    }

    fun save(recordings: List<Recording>) {
        val array = JSONArray()
        recordings.forEach { recording ->
            array.put(
                JSONObject().apply {
                    put("id", recording.id)
                    put("fileName", recording.fileName)
                    put("storedPath", recording.storedPath)
                    put("mimeType", recording.mimeType)
                    put("status", recording.status.name)
                    put("receivedAt", recording.receivedAt)
                    put("durationSeconds", recording.durationSeconds)
                    put("progressPercent", recording.progressPercent)
                    recording.analysis?.let { put("analysis", it.toJson()) }
                    recording.analysisCompletedAt?.let { put("analysisCompletedAt", it) }
                    recording.analysisError?.let { put("analysisError", it) }
                }
            )
        }
        metadataFile.writeText(array.toString())
    }

    fun findById(recordingId: String): Recording? = load().firstOrNull { it.id == recordingId }

    fun newId(): String = UUID.randomUUID().toString()

    private fun JSONObject.toRecordingAnalysis(): RecordingAnalysis {
        val redFlagsArray = optJSONArray("redFlags") ?: JSONArray()
        val redFlags = buildList {
            for (i in 0 until redFlagsArray.length()) {
                add(redFlagsArray.optString(i))
            }
        }
        return RecordingAnalysis(
            prediction = getString("prediction"),
            confidence = optDouble("confidence", 0.0),
            riskLevel = getString("riskLevel"),
            attackType = getString("attackType"),
            redFlags = redFlags,
            explanation = getString("explanation"),
            safeAction = getString("safeAction")
        )
    }

    private fun RecordingAnalysis.toJson(): JSONObject {
        val redFlagsArray = JSONArray()
        redFlags.forEach { redFlagsArray.put(it) }
        return JSONObject().apply {
            put("prediction", prediction)
            put("confidence", confidence)
            put("riskLevel", riskLevel)
            put("attackType", attackType)
            put("redFlags", redFlagsArray)
            put("explanation", explanation)
            put("safeAction", safeAction)
        }
    }
}
