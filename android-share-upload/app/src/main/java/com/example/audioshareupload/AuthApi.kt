package com.example.audioshareupload

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class AuthApi {
    private val client = OkHttpClient()

    suspend fun login(phoneNumber: String, password: String): Result<Unit> = withContext(Dispatchers.IO) {
        val jsonBody = JSONObject()
            .put("phone_number", phoneNumber)
            .put("password", password)
            .toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url("${BuildConfig.API_BASE_URL}/login")
            .post(jsonBody)
            .build()

        runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Login failed with HTTP ${response.code}")
                }
            }
        }
    }
}
