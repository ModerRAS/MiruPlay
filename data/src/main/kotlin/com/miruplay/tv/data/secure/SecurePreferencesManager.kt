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

    var cloudDriveToken: String?
        get() = securePrefs.getString(KEY_CLOUD_DRIVE_TOKEN, null)
        set(value) {
            securePrefs.edit().putString(KEY_CLOUD_DRIVE_TOKEN, value).apply()
        }

    var cloudDrivePassword: String?
        get() = securePrefs.getString(KEY_CLOUD_DRIVE_PASSWORD, null)
        set(value) {
            securePrefs.edit().putString(KEY_CLOUD_DRIVE_PASSWORD, value).apply()
        }

    fun clearBangumiToken() {
        securePrefs.edit().remove(KEY_BANGUMI_TOKEN).apply()
    }

    fun clearCloudDriveCredentials() {
        securePrefs.edit()
            .remove(KEY_CLOUD_DRIVE_TOKEN)
            .remove(KEY_CLOUD_DRIVE_PASSWORD)
            .apply()
    }

    companion object {
        private const val KEY_BANGUMI_TOKEN = "bangumi_access_token"
        private const val KEY_CLOUD_DRIVE_TOKEN = "cloud_drive_token"
        private const val KEY_CLOUD_DRIVE_PASSWORD = "cloud_drive_password"
    }
}
