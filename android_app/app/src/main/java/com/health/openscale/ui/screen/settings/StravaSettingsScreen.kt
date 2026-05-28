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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.health.openscale.R
import com.health.openscale.core.sync.strava.StravaApiClient
import com.health.openscale.ui.shared.SharedViewModel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

@Composable
fun StravaSettingsScreen(
    navController: NavController,
    sharedViewModel: SharedViewModel,
) {
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current
    val currentUserId by sharedViewModel.selectedUserId.collectAsState()

    val savedClientId by remember(currentUserId) {
        currentUserId?.let { sharedViewModel.stravaClientId(it) } ?: flowOf("")
    }.collectAsState(initial = "")

    val savedClientSecret by remember(currentUserId) {
        currentUserId?.let { sharedViewModel.stravaClientSecret(it) } ?: flowOf("")
    }.collectAsState(initial = "")

    val savedAccessToken by remember(currentUserId) {
        currentUserId?.let { sharedViewModel.stravaAccessToken(it) } ?: flowOf("")
    }.collectAsState(initial = "")

    val savedAthleteName by remember(currentUserId) {
        currentUserId?.let { sharedViewModel.stravaAthleteName(it) } ?: flowOf("")
    }.collectAsState(initial = "")

    var clientIdDraft by remember(savedClientId) { mutableStateOf(savedClientId) }
    var clientSecretDraft by remember(savedClientSecret) { mutableStateOf(savedClientSecret) }
    var isTesting by remember { mutableStateOf(false) }
    var isExchanging by remember { mutableStateOf(false) }

    val isConnected = savedAccessToken.isNotBlank()
    val canConnect = savedClientId.isNotBlank() && savedClientSecret.isNotBlank()

    val screenTitle = stringResource(R.string.strava_settings_title)
    LaunchedEffect(Unit) {
        sharedViewModel.setTopBarTitle(screenTitle)
        sharedViewModel.setTopBarActions(emptyList())
    }

    // Collect OAuth authorization code from deep-link callback.
    LaunchedEffect(currentUserId) {
        sharedViewModel.stravaOAuthPendingCode.collect { code ->
            val userId = currentUserId ?: return@collect
            isExchanging = true
            val result = sharedViewModel.stravaExchangeCode(userId, code)
            sharedViewModel.consumeStravaOAuthCode()
            isExchanging = false
            result.fold(
                onSuccess = {
                    sharedViewModel.showSnackbar(messageResId = R.string.strava_settings_connected)
                },
                onFailure = { e ->
                    sharedViewModel.showSnackbar(
                        messageResId = R.string.strava_settings_connect_failed,
                        formatArgs = listOf(e.message ?: "Unknown error"),
                    )
                },
            )
        }
    }

    // Collect OAuth errors returned by Strava in the redirect URL (?error=...).
    LaunchedEffect(Unit) {
        sharedViewModel.stravaOAuthPendingError.collect { error ->
            sharedViewModel.consumeStravaOAuthCode()
            sharedViewModel.showSnackbar(
                messageResId = R.string.strava_settings_connect_failed,
                formatArgs = listOf(error),
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .navigationBarsPadding()
            .imePadding()
    ) {
        // Info banner
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 2.dp, end = 8.dp),
            )
            Text(
                text = stringResource(R.string.strava_settings_info),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        HorizontalDivider(modifier = Modifier.padding(bottom = 16.dp))

        // Callback domain notice
        val callbackDomainValue = stringResource(R.string.strava_settings_callback_domain_value)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(top = 2.dp, end = 8.dp),
            )
            Text(
                text = stringResource(R.string.strava_settings_callback_domain_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                )
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.strava_settings_callback_domain_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.strava_settings_callback_domain_value),
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            IconButton(
                onClick = {
                    clipboard.setText(AnnotatedString(callbackDomainValue))
                    sharedViewModel.showSnackbar(messageResId = R.string.strava_settings_callback_domain_copied)
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = stringResource(R.string.strava_settings_callback_domain_label),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(bottom = 16.dp))

        // Client ID
        OutlinedTextField(
            value = clientIdDraft,
            onValueChange = { clientIdDraft = it },
            label = { Text(stringResource(R.string.strava_settings_client_id_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Next,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))

        // Client Secret
        OutlinedTextField(
            value = clientSecretDraft,
            onValueChange = { clientSecretDraft = it },
            label = { Text(stringResource(R.string.strava_settings_client_secret_label)) },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(16.dp))

        // Save credentials
        Button(
            onClick = {
                val userId = currentUserId ?: return@Button
                scope.launch {
                    sharedViewModel.setStravaClientId(userId, clientIdDraft.trim())
                    sharedViewModel.setStravaClientSecret(userId, clientSecretDraft.trim())
                    sharedViewModel.showSnackbar(messageResId = R.string.strava_settings_credentials_saved)
                }
            },
            enabled = currentUserId != null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.strava_settings_save_credentials))
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider(modifier = Modifier.padding(bottom = 16.dp))

        // Connection status
        if (isConnected) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Column {
                    Text(
                        text = stringResource(R.string.strava_settings_status_connected),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (savedAthleteName.isNotBlank()) {
                        Text(
                            text = stringResource(R.string.strava_settings_connected_as, savedAthleteName),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Test Connection
            OutlinedButton(
                onClick = {
                    isTesting = true
                    scope.launch {
                        val result = sharedViewModel.testStravaConnection(savedAccessToken)
                        isTesting = false
                        result.fold(
                            onSuccess = {
                                sharedViewModel.showSnackbar(messageResId = R.string.strava_settings_test_success)
                            },
                            onFailure = { e ->
                                sharedViewModel.showSnackbar(
                                    messageResId = R.string.strava_settings_test_failure,
                                    formatArgs = listOf(e.message ?: "Unknown error"),
                                )
                            },
                        )
                    }
                },
                enabled = !isTesting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.strava_settings_test_button))
            }

            Spacer(Modifier.height(8.dp))

            // Disconnect
            OutlinedButton(
                onClick = {
                    val userId = currentUserId ?: return@OutlinedButton
                    scope.launch {
                        sharedViewModel.stravaDisconnect(userId)
                    }
                },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
                enabled = currentUserId != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.strava_settings_disconnect))
            }
        } else {
            Text(
                text = stringResource(R.string.strava_settings_status_not_connected),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            // Connect with Strava
            Button(
                onClick = {
                    val userId = currentUserId ?: return@Button
                    val clientId = savedClientId.ifBlank { clientIdDraft.trim() }
                    if (clientId.isBlank()) return@Button
                    uriHandler.openUri(StravaApiClient.buildAuthUrl(clientId))
                },
                enabled = canConnect && !isExchanging && currentUserId != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (isExchanging) stringResource(R.string.strava_settings_connecting)
                    else stringResource(R.string.strava_settings_connect)
                )
            }
        }
    }
}
