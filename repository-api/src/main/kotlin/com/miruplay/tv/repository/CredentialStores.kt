package com.miruplay.tv.repository

interface CloudDriveCredentialStore {
    var cloudDriveToken: String?
    var cloudDrivePassword: String?
    fun clearCloudDriveCredentials()
}

interface AppCredentialStore : CloudDriveCredentialStore {
    var bangumiAccessToken: String?
    var tmdbAccessToken: String?
    var tmdbApiBaseUrlOverride: String?
    var otlpAccessToken: String?
    fun clearBangumiToken()
    fun clearTmdbToken()
    fun clearOtlpAccessToken()
}
