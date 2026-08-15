/*
 * openScale
 * Copyright (C) 2026 openScale+ Dharmendra Gupta
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.health.openscale.core.sync.webhook

import com.health.openscale.core.utils.LogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

data class WebhookTestResponse(
    val statusCode: Int,
    val statusMessage: String,
    val headers: Map<String, String>,
    val body: String,
    val durationMs: Long,
)

@Singleton
class WebhookSyncClient @Inject constructor(
    private val httpClient: OkHttpClient,
) {
    private companion object {
        const val TAG = "WebhookSyncClient"
    }

    /**
     * Sends a webhook test request and returns the raw response as data, for the
     * sandbox/test console. Unlike [send], a non-2xx status is NOT a failure here —
     * seeing e.g. a 404/500 is exactly what a test console exists to show. Only a
     * genuine I/O failure (timeout, DNS, connection refused) becomes [Result.failure].
     */
    suspend fun sendTest(
        url: String,
        headers: Map<String, String>,
        payloadJson: String,
    ): Result<WebhookTestResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val body = payloadJson.toRequestBody("application/json".toMediaType())
            val requestBuilder = Request.Builder()
                .url(url)
                .post(body)
            headers.forEach { (k, v) -> requestBuilder.addHeader(k, v) }
            val startMs = System.currentTimeMillis()
            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                val durationMs = System.currentTimeMillis() - startMs
                val responseBody = response.body?.string() ?: ""
                val responseHeaders = response.headers.names()
                    .associateWith { name -> response.headers.values(name).joinToString(", ") }
                LogManager.i(TAG, "POST $url (test) → ${response.code} in ${durationMs}ms")
                WebhookTestResponse(
                    statusCode = response.code,
                    statusMessage = response.message,
                    headers = responseHeaders,
                    body = responseBody,
                    durationMs = durationMs,
                )
            }
        }
    }

    suspend fun send(
        url: String,
        headers: Map<String, String>,
        payloadJson: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val headerKeys = headers.keys.joinToString()
            LogManager.i(TAG, "POST $url headers=[$headerKeys] body=$payloadJson")
            val body = payloadJson.toRequestBody("application/json".toMediaType())
            val requestBuilder = Request.Builder()
                .url(url)
                .post(body)
            headers.forEach { (k, v) -> requestBuilder.addHeader(k, v) }
            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                LogManager.i(TAG, "POST $url → ${response.code}${if (responseBody.isNotBlank()) " body=$responseBody" else ""}")
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}: $responseBody")
            }
        }
    }
}
