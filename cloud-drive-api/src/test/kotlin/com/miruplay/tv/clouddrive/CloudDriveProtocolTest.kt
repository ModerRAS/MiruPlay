package com.miruplay.tv.clouddrive

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudDriveProtocolTest {
    @Test
    fun `parse endpoint address resolves default ports and tls`() {
        assertEquals(
            CloudDriveEndpointAddress(host = "127.0.0.1", port = 80, useTransportSecurity = false),
            CloudDriveProtocol.parseEndpointAddress("http://127.0.0.1")
        )
        assertEquals(
            CloudDriveEndpointAddress(host = "cloud.example.test", port = 443, useTransportSecurity = true),
            CloudDriveProtocol.parseEndpointAddress("https://cloud.example.test")
        )
        assertEquals(
            CloudDriveEndpointAddress(host = "cloud.example.test", port = 19798, useTransportSecurity = false),
            CloudDriveProtocol.parseEndpointAddress("http://cloud.example.test:19798")
        )
    }

    @Test
    fun `parse endpoint address rejects unsupported endpoints`() {
        assertThrows(IllegalArgumentException::class.java) {
            CloudDriveProtocol.parseEndpointAddress("ftp://cloud.example.test")
        }
        assertThrows(IllegalStateException::class.java) {
            CloudDriveProtocol.parseEndpointAddress("http:///missing-host")
        }
    }

    @Test
    fun `require token rejects blank tokens`() {
        assertTrue(CloudDriveProtocol.requireToken(CloudDriveEndpoint("http://127.0.0.1", "api-token")) is Result.Success)
        assertTrue(CloudDriveProtocol.requireToken(CloudDriveEndpoint("http://127.0.0.1", "")) is Result.Error)
        assertTrue(CloudDriveProtocol.requireToken(CloudDriveEndpoint("http://127.0.0.1", null)) is Result.Error)
    }

    @Test
    fun `authorization candidates try bearer before raw token`() {
        assertEquals(listOf("Bearer api-token", "api-token"), CloudDriveProtocol.authorizationCandidates("api-token"))
    }

    @Test
    fun `raw token retry detection recognizes auth failures only`() {
        assertTrue(
            CloudDriveProtocol.shouldRetryWithRawToken(
                Result.failure(AppError.NetworkError.ServerUnreachable("UNAUTHENTICATED: Invalid auth token"))
            )
        )
        assertTrue(
            CloudDriveProtocol.shouldRetryWithRawToken(
                Result.failure(AppError.MediaSourceError.AuthenticationFailed("Invalid auth token"))
            )
        )
        assertFalse(CloudDriveProtocol.shouldRetryWithRawToken(Result.success(Unit)))
        assertFalse(
            CloudDriveProtocol.shouldRetryWithRawToken(
                Result.failure(AppError.NetworkError.ServerUnreachable("connection refused"))
            )
        )
    }

    @Test
    fun `operation result maps failures with fallback message`() {
        assertTrue(CloudDriveProtocol.operationResult(success = true, errorMessage = "", fallback = "fallback") is Result.Success)

        val result = CloudDriveProtocol.operationResult(success = false, errorMessage = "", fallback = "fallback")
        assertTrue(result is Result.Error)
        val error = (result as Result.Error).error as AppError.SyncError.WriteFailed
        assertEquals("CloudDrive2", error.path)
        assertEquals("fallback", error.cause)
    }
}
