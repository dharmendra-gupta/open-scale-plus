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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.health.openscale.R
import com.health.openscale.core.sync.webhook.WebhookPayloadBuilder
import com.health.openscale.core.sync.webhook.SchemaValidationError
import com.health.openscale.core.sync.webhook.ValidationErrorType
import com.health.openscale.ui.shared.SharedViewModel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

@Composable
fun CloudSyncSettingsScreen(
    navController: NavController,
    sharedViewModel: SharedViewModel,
) {
    val scope = rememberCoroutineScope()

    val currentUserId by sharedViewModel.selectedUserId.collectAsState()

    val webhookUrl by remember(currentUserId) {
        currentUserId?.let { sharedViewModel.webhookUrl(it) } ?: flowOf("")
    }.collectAsState(initial = "")
    val webhookAuthHeaders by remember(currentUserId) {
        currentUserId?.let { sharedViewModel.webhookAuthHeaders(it) } ?: flowOf("")
    }.collectAsState(initial = "")
    val webhookPayloadSchema by remember(currentUserId) {
        currentUserId?.let { sharedViewModel.webhookPayloadSchema(it) } ?: flowOf("")
    }.collectAsState(initial = "")

    var urlDraft by remember(webhookUrl) { mutableStateOf(webhookUrl) }
    var headersDraft by remember(webhookAuthHeaders) { mutableStateOf(webhookAuthHeaders) }
    var schemaDraft by remember(webhookPayloadSchema) { mutableStateOf(webhookPayloadSchema) }

    var urlError by remember { mutableStateOf<String?>(null) }
    var headersError by remember { mutableStateOf<String?>(null) }
    var schemaError by remember { mutableStateOf<String?>(null) }
    var schemaValidationErrors by remember { mutableStateOf<List<SchemaValidationError>>(emptyList()) }
    var schemaValidated by remember { mutableStateOf(false) }

    val screenTitle = stringResource(R.string.settings_cloud_sync_title)
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
            .imePadding()
    ) {
        // --- Section header ---
        Text(
            text = stringResource(R.string.settings_cloud_sync_webhook_section),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        // --- Webhook URL ---
        OutlinedTextField(
            value = urlDraft,
            onValueChange = {
                urlDraft = it
                urlError = null
                schemaValidated = false
            },
            label = { Text(stringResource(R.string.settings_cloud_sync_url_label)) },
            placeholder = { Text("https://example.com/webhook") },
            isError = urlError != null,
            supportingText = urlError?.let { { Text(it) } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Next,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))

        // --- Auth Headers ---
        OutlinedTextField(
            value = headersDraft,
            onValueChange = {
                headersDraft = it
                headersError = null
            },
            label = { Text(stringResource(R.string.settings_cloud_sync_auth_headers_label)) },
            placeholder = { Text("{\n  \"Authorization\": \"Bearer token\"\n}") },
            isError = headersError != null,
            supportingText = headersError?.let { { Text(it) } },
            minLines = 3,
            maxLines = 6,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))

        // --- Payload Schema ---
        OutlinedTextField(
            value = schemaDraft,
            onValueChange = {
                schemaDraft = it
                schemaValidated = false
                schemaValidationErrors = emptyList()
                schemaError = null
            },
            label = { Text(stringResource(R.string.settings_cloud_sync_schema_label)) },
            placeholder = { Text("{\n  \"weight\": \"WEIGHT\",\n  \"body_fat\": \"FAT\"\n}") },
            isError = schemaError != null,
            supportingText = schemaError?.let { { Text(it) } },
            minLines = 4,
            maxLines = 12,
            modifier = Modifier.fillMaxWidth(),
        )

        // Schema validation results
        if (schemaValidated) {
            Spacer(Modifier.height(8.dp))
            if (schemaValidationErrors.isEmpty()) {
                Text(
                    text = stringResource(R.string.settings_cloud_sync_schema_valid),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                schemaValidationErrors.forEach { err ->
                    val isWarning = err.type == ValidationErrorType.HARDCODED_NUMBER ||
                            err.type == ValidationErrorType.LOOKS_LIKE_FORMAT
                    val errorText = when {
                        err.path.isEmpty() -> err.unknownToken
                        err.type == ValidationErrorType.INVALID_PATTERN ->
                            stringResource(R.string.settings_cloud_sync_schema_invalid_pattern, err.unknownToken, err.path)
                        err.type == ValidationErrorType.HARDCODED_NUMBER ->
                            stringResource(R.string.settings_cloud_sync_schema_hardcoded_number, err.unknownToken, err.path)
                        err.type == ValidationErrorType.LOOKS_LIKE_FORMAT ->
                            stringResource(R.string.settings_cloud_sync_schema_looks_like_format, err.unknownToken, err.path)
                        else ->
                            stringResource(R.string.settings_cloud_sync_schema_error, err.unknownToken, err.path)
                    }
                    Text(
                        text = errorText,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isWarning) MaterialTheme.colorScheme.secondary
                                else MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Validate button
        Button(
            onClick = {
                schemaValidationErrors = WebhookPayloadBuilder.validateSchema(schemaDraft)
                schemaValidated = true
            },
            enabled = schemaDraft.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = if (schemaValidated && schemaValidationErrors.isEmpty())
                    Icons.Filled.Check else Icons.Filled.Cloud,
                contentDescription = null,
                modifier = Modifier.padding(end = 8.dp),
            )
            Text(stringResource(R.string.settings_cloud_sync_validate_button))
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        // Save button
        Button(
            onClick = {
                var valid = true

                if (urlDraft.isNotBlank() && !urlDraft.startsWith("http://") && !urlDraft.startsWith("https://")) {
                    urlError = "URL must start with http:// or https://"
                    valid = false
                }

                if (headersDraft.isNotBlank()) {
                    try {
                        org.json.JSONObject(headersDraft)
                    } catch (e: Exception) {
                        headersError = "Invalid JSON: ${e.message}"
                        valid = false
                    }
                }

                if (schemaDraft.isNotBlank()) {
                    try {
                        org.json.JSONObject(schemaDraft)
                    } catch (e: Exception) {
                        schemaError = "Invalid JSON: ${e.message}"
                        valid = false
                    }
                }

                if (valid) {
                    scope.launch {
                        val uid = currentUserId ?: return@launch
                        sharedViewModel.setWebhookUrl(uid, urlDraft.trim())
                        sharedViewModel.setWebhookAuthHeaders(uid, headersDraft.trim())
                        sharedViewModel.setWebhookPayloadSchema(uid, schemaDraft.trim())
                        navController.popBackStack()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.settings_cloud_sync_save_button))
        }

        Spacer(Modifier.height(16.dp))

        // Token reference
        Text(
            text = stringResource(R.string.settings_cloud_sync_tokens_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.settings_cloud_sync_tokens_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
