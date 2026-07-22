package com.example.audioshareupload

import android.content.Context

class SessionStore(context: Context) {
    private val preferences = context.getSharedPreferences("session", Context.MODE_PRIVATE)

    fun isLoggedIn(): Boolean = preferences.getBoolean(KEY_IS_LOGGED_IN, false)

    fun phoneNumber(): String? = preferences.getString(KEY_PHONE_NUMBER, null)

    fun saveLogin(phoneNumber: String) {
        preferences.edit()
            .putBoolean(KEY_IS_LOGGED_IN, true)
            .putString(KEY_PHONE_NUMBER, phoneNumber)
            .apply()
    }

    companion object {
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_PHONE_NUMBER = "phone_number"
    }
}
