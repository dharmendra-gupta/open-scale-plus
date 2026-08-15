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

import com.health.openscale.core.model.MeasurementWithValues
import com.health.openscale.core.utils.LogManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebhookSyncManager @Inject constructor(
    private val client: WebhookSyncClient,
    private val settings: WebhookSettings,
) {
    private val TAG = "WebhookSyncManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun fireAndForget(
        measurement: MeasurementWithValues,
        onError: ((String) -> Unit)? = null,
    ) {
        scope.launch {
            try {
                val userId = measurement.measurement.userId
                val url = settings.webhookUrl(userId).first().trim()
                if (url.isBlank()) {
                    LogManager.d(TAG, "Skipped: no URL configured for user $userId")
                    return@launch
                }

                val schemaJson = settings.webhookPayloadSchema(userId).first()
                if (schemaJson.isBlank()) {
                    LogManager.w(TAG, "Skipped: no payload schema configured for user $userId")
                    return@launch
                }

                val headersJson = settings.webhookAuthHeaders(userId).first()
                val headers = WebhookPayloadBuilder.buildHeaders(headersJson)
                val payload = WebhookPayloadBuilder.buildPayload(schemaJson, measurement)

                LogManager.i(TAG, "Sending to $url — payload: $payload")
                client.send(url, headers, payload)
                    .onSuccess { LogManager.i(TAG, "Webhook sent successfully") }
                    .onFailure { e ->
                        LogManager.e(TAG, "Webhook send failed: ${e.message}", e)
                        onError?.invoke(e.message ?: "Unknown error")
                    }
            } catch (e: Exception) {
                LogManager.e(TAG, "Webhook unexpected error", e)
                onError?.invoke(e.message ?: "Unknown error")
            }
        }
    }

    suspend fun sendTest(url: String, headers: Map<String, String>, payloadJson: String): Result<WebhookTestResponse> =
        client.sendTest(url, headers, payloadJson)
}
