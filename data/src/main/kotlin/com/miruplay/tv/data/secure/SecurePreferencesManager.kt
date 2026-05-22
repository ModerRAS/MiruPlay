package com.miruplay.tv.data.secure

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.miruplay.tv.repository.AppCredentialStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

interface MediaSourceSecretStore {
    fun getMediaSourcePassword(sourceId: Long): String?
    fun setMediaSourcePassword(sourceId: Long, password: String?)
    fun clearMediaSourcePassword(sourceId: Long)
}

@Singleton
class SecurePreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) : MediaSourceSecretStore, AppCredentialStore {
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

    override var bangumiAccessToken: String?
        get() = securePrefs.getString(KEY_BANGUMI_TOKEN, null)
        set(value) {
            securePrefs.edit().putString(KEY_BANGUMI_TOKEN, value).apply()
        }

    override var cloudDriveToken: String?
        get() = securePrefs.getString(KEY_CLOUD_DRIVE_TOKEN, null)
        set(value) {
            securePrefs.edit().putString(KEY_CLOUD_DRIVE_TOKEN, value).apply()
        }

    override var cloudDrivePassword: String?
        get() = securePrefs.getString(KEY_CLOUD_DRIVE_PASSWORD, null)
        set(value) {
            securePrefs.edit().putString(KEY_CLOUD_DRIVE_PASSWORD, value).apply()
        }

    override var otlpAccessToken: String?
        get() = securePrefs.getString(KEY_OTLP_ACCESS_TOKEN, null)
        set(value) {
            securePrefs.edit().putString(KEY_OTLP_ACCESS_TOKEN, value).apply()
        }

    var webControlAccessToken: String?
        get() = securePrefs.getString(KEY_WEB_CONTROL_ACCESS_TOKEN, null)
        set(value) {
            securePrefs.edit().putString(KEY_WEB_CONTROL_ACCESS_TOKEN, value).apply()
        }

    fun ensureWebControlAccessToken(): String {
        val current = webControlAccessToken
        if (!current.isNullOrBlank()) return current
        return rotateWebControlAccessToken()
    }

    fun rotateWebControlAccessToken(): String {
        val bytes = ByteArray(WEB_CONTROL_TOKEN_BYTES)
        SecureRandom().nextBytes(bytes)
        val token = Base64.encodeToString(
            bytes,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
        webControlAccessToken = token
        return token
    }

    override fun getMediaSourcePassword(sourceId: Long): String? =
        securePrefs.getString(mediaSourcePasswordKey(sourceId), null)

    override fun setMediaSourcePassword(sourceId: Long, password: String?) {
        val editor = securePrefs.edit()
        if (password.isNullOrBlank()) {
            editor.remove(mediaSourcePasswordKey(sourceId))
        } else {
            editor.putString(mediaSourcePasswordKey(sourceId), password)
        }
        editor.apply()
    }

    override fun clearMediaSourcePassword(sourceId: Long) {
        securePrefs.edit().remove(mediaSourcePasswordKey(sourceId)).apply()
    }

    override fun clearBangumiToken() {
        securePrefs.edit().remove(KEY_BANGUMI_TOKEN).apply()
    }

    override fun clearOtlpAccessToken() {
        securePrefs.edit().remove(KEY_OTLP_ACCESS_TOKEN).apply()
    }

    override fun clearCloudDriveCredentials() {
        securePrefs.edit()
            .remove(KEY_CLOUD_DRIVE_TOKEN)
            .remove(KEY_CLOUD_DRIVE_PASSWORD)
            .apply()
    }

    private fun mediaSourcePasswordKey(sourceId: Long): String =
        "$KEY_MEDIA_SOURCE_PASSWORD_PREFIX$sourceId"

    companion object {
        private const val KEY_BANGUMI_TOKEN = "bangumi_access_token"
        private const val KEY_CLOUD_DRIVE_TOKEN = "cloud_drive_token"
        private const val KEY_CLOUD_DRIVE_PASSWORD = "cloud_drive_password"
        private const val KEY_OTLP_ACCESS_TOKEN = "otlp_access_token"
        private const val KEY_WEB_CONTROL_ACCESS_TOKEN = "web_control_access_token"
        private const val KEY_MEDIA_SOURCE_PASSWORD_PREFIX = "media_source_password_"
        private const val WEB_CONTROL_TOKEN_BYTES = 24
    }
}
