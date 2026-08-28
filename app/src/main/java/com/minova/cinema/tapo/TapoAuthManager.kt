package com.minova.cinema.tapo

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Stores the Tapo account secret encrypted by a key held in Android Keystore. */
class TapoAuthManager(context: Context) {
    private val preferences = EncryptedSharedPreferences.create(
        context.applicationContext,
        FILE_NAME,
        MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun read(): TapoCredentials? {
        val email = preferences.getString(KEY_EMAIL, null)?.trim().orEmpty()
        val password = preferences.getString(KEY_PASSWORD, null).orEmpty()
        return if (email.isBlank() || password.isBlank()) null else TapoCredentials(email, password)
    }

    fun save(email: String, password: String) {
        require(email.isNotBlank()) { "Enter the email used by the Tapo app." }
        require(password.isNotBlank()) { "Enter the Tapo account password." }
        preferences.edit()
            .putString(KEY_EMAIL, email.trim())
            .putString(KEY_PASSWORD, password)
            .apply()
    }

    fun clear() {
        preferences.edit().clear().apply()
    }

    private companion object {
        const val FILE_NAME = "minova_cinema_tapo_credentials"
        const val KEY_EMAIL = "email"
        const val KEY_PASSWORD = "password"
    }
}

