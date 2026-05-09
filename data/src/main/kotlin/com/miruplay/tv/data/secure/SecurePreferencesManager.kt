package com.miruplay.tv.data.secure

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecurePreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val securePrefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "miruplay_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    var bangumiAccessToken: String?
        get() = securePrefs.getString(KEY_BANGUMI_TOKEN, null)
        set(value) {
            securePrefs.edit().putString(KEY_BANGUMI_TOKEN, value).apply()
        }

    fun clearBangumiToken() {
        securePrefs.edit().remove(KEY_BANGUMI_TOKEN).apply()
    }

    companion object {
        private const val KEY_BANGUMI_TOKEN = "bangumi_access_token"
    }
}
