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
package com.health.openscale.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.health.openscale.R
import com.health.openscale.core.model.extractWeightKg
import com.health.openscale.core.sync.webhook.WebhookPayloadBuilder
import com.health.openscale.core.sync.webhook.WebhookTestResponse
import com.health.openscale.ui.shared.SharedViewModel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WebhookSandboxScreen(
    navController: NavController,
    sharedViewModel: SharedViewModel,
) {
    val scope = rememberCoroutineScope()

    val currentUserId by sharedViewModel.selectedUserId.collectAsState()

    // Sandbox operates on the SAVED config only (not any unsaved draft in the
    // settings screen) — CloudSyncSettingsScreen warns the user separately if
    // their draft differs from what's saved.
    val webhookUrl by remember(currentUserId) {
        currentUserId?.let { sharedViewModel.webhookUrl(it) } ?: flowOf("")
    }.collectAsState(initial = "")
    val webhookAuthHeaders by remember(currentUserId) {
        currentUserId?.let { sharedViewModel.webhookAuthHeaders(it) } ?: flowOf("")
    }.collectAsState(initial = "")
    val webhookPayloadSchema by remember(currentUserId) {
        currentUserId?.let { sharedViewModel.webhookPayloadSchema(it) } ?: flowOf("")
    }.collectAsState(initial = "")

    val measurements by remember(currentUserId) {
        currentUserId?.let { sharedViewModel.getMeasurementsForUser(it) } ?: flowOf(emptyList())
    }.collectAsState(initial = emptyList())
    var selectedMeasurement by remember(measurements) { mutableStateOf(measurements.firstOrNull()) }

    var previewPayload by remember { mutableStateOf<String?>(null) }
    var previewError by remember { mutableStateOf<String?>(null) }

    var isSending by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<WebhookTestResponse?>(null) }
    var testError by remember { mutableStateOf<String?>(null) }

    val dryRunEnabled by sharedViewModel.syncDryRunEnabled.collectAsState()

    val screenTitle = stringResource(R.string.settings_cloud_sync_sandbox_title)
    LaunchedEffect(Unit) {
        sharedViewModel.setTopBarTitle(screenTitle)
        sharedViewModel.setTopBarActions(emptyList())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .navigationBarsPadding()
    ) {
        if (dryRunEnabled) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.settings_cloud_sync_sandbox_dry_run_banner),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(12.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        if (webhookUrl.isBlank() || webhookPayloadSchema.isBlank()) {
            Text(
                text = stringResource(R.string.settings_cloud_sync_sandbox_not_configured),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        // --- Measurement picker ---
        Text(
            text = stringResource(R.string.settings_cloud_sync_sandbox_pick_measurement),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        if (measurements.isEmpty()) {
            Text(
                text = stringResource(R.string.settings_cloud_sync_sandbox_no_measurements),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val dateFormatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
            Card(modifier = Modifier.fillMaxWidth()) {
                LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                    items(measurements, key = { it.measurement.id }) { measurement ->
                        val isSelected = measurement.measurement.id == selectedMeasurement?.measurement?.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surface
                                )
                                .clickable {
                                    selectedMeasurement = measurement
                                    previewPayload = null
                                    previewError = null
                                    testResult = null
                                    testError = null
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                        ) {
                            val weightLabel = measurement.extractWeightKg()?.let { " · %.1f kg".format(it) } ?: ""
                            Text(dateFormatter.format(Date(measurement.measurement.timestamp)) + weightLabel)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // --- Preview Payload ---
        Button(
            onClick = {
                val measurement = selectedMeasurement ?: return@Button
                runCatching { WebhookPayloadBuilder.buildPayload(webhookPayloadSchema, measurement) }
                    .onSuccess { previewPayload = it; previewError = null }
                    .onFailure { previewError = it.message; previewPayload = null }
            },
            enabled = selectedMeasurement != null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.settings_cloud_sync_sandbox_preview_button))
        }

        previewPayload?.let { payload ->
            Spacer(Modifier.height(8.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = payload,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
        previewError?.let { err ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.settings_cloud_sync_sandbox_preview_error, err),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        // --- Send Test ---
        Button(
            onClick = {
                val measurement = selectedMeasurement ?: return@Button
                scope.launch {
                    isSending = true
                    testError = null
                    testResult = null
                    val payload = previewPayload
                        ?: runCatching { WebhookPayloadBuilder.buildPayload(webhookPayloadSchema, measurement) }
                            .getOrElse {
                                testError = it.message
                                isSending = false
                                return@launch
                            }
                    val headers = WebhookPayloadBuilder.buildHeaders(webhookAuthHeaders)
                    sharedViewModel.sendWebhookTest(webhookUrl, headers, payload)
                        .onSuccess { testResult = it }
                        .onFailure { testError = it.message }
                    isSending = false
                }
            },
            enabled = !isSending && selectedMeasurement != null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (isSending) stringResource(R.string.settings_cloud_sync_sandbox_sending)
                else stringResource(R.string.settings_cloud_sync_sandbox_send_button)
            )
        }

        testResult?.let { response ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.settings_cloud_sync_sandbox_response_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(4.dp))
            Card(
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = stringResource(
                            R.string.settings_cloud_sync_sandbox_status_line,
                            response.statusCode,
                            response.statusMessage,
                            response.durationMs,
                        ),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    response.headers.forEach { (name, value) ->
                        Text(
                            text = "$name: $value",
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (response.body.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = response.body,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
        testError?.let { err ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.settings_cloud_sync_sandbox_send_failed, err),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
