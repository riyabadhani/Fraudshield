package com.example.audioshareupload

import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.File

class ProgressRequestBody(
    private val file: File,
    private val mediaType: MediaType?,
    private val onProgress: (Int) -> Unit
) : RequestBody() {

    override fun contentType(): MediaType? = mediaType

    override fun contentLength(): Long = file.length()

    override fun writeTo(sink: BufferedSink) {
        val totalBytes = contentLength()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var uploadedBytes = 0L

        file.inputStream().use { input ->
            var read = input.read(buffer)
            while (read >= 0) {
                sink.write(buffer, 0, read)
                uploadedBytes += read
                val progress = if (totalBytes == 0L) 100 else ((uploadedBytes * 100) / totalBytes).toInt()
                onProgress(progress.coerceIn(0, 100))
                read = input.read(buffer)
            }
        }
    }
}
