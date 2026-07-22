package com.example.audioshareupload

import android.content.Intent
import android.database.Cursor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.audioshareupload.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var store: RecordingStore
    private lateinit var uploader: Uploader
    private lateinit var adapter: RecordingAdapter
    private lateinit var sessionStore: SessionStore
    private lateinit var authApi: AuthApi

    private val recordings = mutableListOf<Recording>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        store = RecordingStore(this)
        uploader = Uploader()
        adapter = RecordingAdapter(::openRecordingDetail)
        sessionStore = SessionStore(this)
        authApi = AuthApi()

        binding.recordingsList.layoutManager = LinearLayoutManager(this)
        binding.recordingsList.adapter = adapter
        binding.loginButton.setOnClickListener { login() }
        binding.telegramBotButton.setOnClickListener { openTelegramBot() }

        recordings += store.load()
        render()
        updateScreenState()

        if (sessionStore.isLoggedIn()) {
            handleShareIntent(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        recordings.clear()
        recordings += store.load()
        render()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (sessionStore.isLoggedIn()) {
            handleShareIntent(intent)
        }
    }

    private fun updateScreenState() {
        val loggedIn = sessionStore.isLoggedIn()
        binding.loginContainer.visibility = if (loggedIn) android.view.View.GONE else android.view.View.VISIBLE
        binding.recordingsContainer.visibility = if (loggedIn) android.view.View.VISIBLE else android.view.View.GONE
        if (loggedIn) {
            render()
        }
    }

    private fun login() {
        val phoneNumber = binding.phoneInput.text.toString().trim()
        val password = binding.passwordInput.text.toString()

        if (phoneNumber.isBlank() || password.isBlank()) {
            binding.loginErrorText.text = getString(R.string.enter_phone_and_password)
            binding.loginErrorText.visibility = android.view.View.VISIBLE
            return
        }

        binding.loginButton.isEnabled = false
        binding.loginErrorText.visibility = android.view.View.GONE

        lifecycleScope.launch {
            val result = authApi.login(phoneNumber, password)
            binding.loginButton.isEnabled = true

            if (result.isSuccess) {
                sessionStore.saveLogin(phoneNumber)
                binding.passwordInput.text?.clear()
                updateScreenState()
                handleShareIntent(intent)
            } else {
                binding.loginErrorText.text = getString(R.string.login_failed)
                binding.loginErrorText.visibility = android.view.View.VISIBLE
            }
        }
    }

    private fun openTelegramBot() {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(TELEGRAM_BOT_URL)))
    }

    private fun openRecordingDetail(recording: Recording) {
        startActivity(
            Intent(this, RecordingDetailActivity::class.java)
                .putExtra(RecordingDetailActivity.EXTRA_RECORDING_ID, recording.id)
        )
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return

        val sharedUri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java) ?: return
        lifecycleScope.launch {
            val recording = importSharedFile(sharedUri)
            if (recording == null) {
                Toast.makeText(this@MainActivity, "Could not save shared file", Toast.LENGTH_SHORT).show()
                return@launch
            }
            recordings.add(0, recording)
            persistAndRender()
            uploadRecording(recording.id)
            setIntent(Intent(Intent.ACTION_MAIN))
        }
    }

    private suspend fun importSharedFile(uri: Uri): Recording? = withContext(Dispatchers.IO) {
        runCatching {
            contentResolver.openInputStream(uri)?.use { input ->
                val originalName = sanitizeFileName(resolveFileName(uri))
                val generatedId = store.newId()
                val targetFile = File(store.recordingsDirectory(), "${generatedId}_$originalName")
                targetFile.outputStream().use { output -> input.copyTo(output) }

                Recording(
                    id = generatedId,
                    fileName = originalName,
                    storedPath = targetFile.absolutePath,
                    mimeType = contentResolver.getType(uri) ?: inferMimeTypeFromName(originalName),
                    status = UploadStatus.PENDING,
                    receivedAt = System.currentTimeMillis(),
                    durationSeconds = readDurationSeconds(targetFile)
                )
            }
        }.getOrNull()
    }

    private fun uploadRecording(recordingId: String) {
        if (recordings.none { it.id == recordingId }) return
        val phoneNumber = sessionStore.phoneNumber() ?: return

        lifecycleScope.launch {
            updateRecording(recordingId) { it.copy(status = UploadStatus.UPLOADING, progressPercent = 0, analysisError = null) }

            val current = recordings.first { it.id == recordingId }
            val result = uploader.upload(current, phoneNumber) { progress ->
                runOnUiThread {
                    updateRecording(recordingId) {
                        it.copy(status = UploadStatus.UPLOADING, progressPercent = progress, analysisError = null)
                    }
                }
            }

            if (result.isSuccess) {
                val uploadResponse = result.getOrThrow()
                if (uploadResponse.analysis != null) {
                    updateRecording(recordingId) {
                        it.copy(
                            status = UploadStatus.SUCCESS,
                            progressPercent = 100,
                            analysis = uploadResponse.analysis,
                            analysisCompletedAt = System.currentTimeMillis(),
                            analysisError = null
                        )
                    }
                } else {
                    updateRecording(recordingId) {
                        it.copy(
                            status = UploadStatus.FAILED,
                            progressPercent = 0,
                            analysis = null,
                            analysisCompletedAt = null,
                            analysisError = uploadResponse.fraudshieldError ?: "Analysis service returned no result."
                        )
                    }
                }
            } else {
                updateRecording(recordingId) {
                    it.copy(
                        status = UploadStatus.FAILED,
                        progressPercent = 0,
                        analysis = null,
                        analysisCompletedAt = null,
                        analysisError = result.exceptionOrNull()?.message ?: "Upload failed."
                    )
                }
            }
        }
    }

    private fun updateRecording(recordingId: String, transform: (Recording) -> Recording) {
        val index = recordings.indexOfFirst { it.id == recordingId }
        if (index == -1) return
        recordings[index] = transform(recordings[index])
        persistAndRender()
    }

    private fun persistAndRender() {
        store.save(recordings)
        render()
    }

    private fun render() {
        adapter.submitList(recordings.toList())
    }

    private fun resolveFileName(uri: Uri): String {
        val cursor: Cursor? = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && it.moveToFirst()) {
                return it.getString(nameIndex)
            }
        }
        return uri.lastPathSegment ?: "recording"
    }

    private fun readDurationSeconds(file: File): Int {
        val retriever = MediaMetadataRetriever()
        return runCatching {
            retriever.setDataSource(file.absolutePath)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            (durationMs / 1000L).toInt()
        }.getOrDefault(0)
            .also { retriever.release() }
    }

    private fun sanitizeFileName(fileName: String): String {
        return fileName.replace(Regex("[^A-Za-z0-9._-]"), "_")
    }

    private fun inferMimeTypeFromName(fileName: String): String {
        return when {
            fileName.endsWith(".mp3", ignoreCase = true) -> "audio/mpeg"
            fileName.endsWith(".wav", ignoreCase = true) -> "audio/wav"
            else -> "application/octet-stream"
        }
    }

    companion object {
        private const val TELEGRAM_BOT_URL = "https://t.me/fraudsheild_ai_09_bot"
    }
}
