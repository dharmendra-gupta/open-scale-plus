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
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.health.openscale.R
import com.health.openscale.ui.shared.SharedViewModel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

@Composable
fun HevySettingsScreen(
    navController: NavController,
    sharedViewModel: SharedViewModel,
) {
    val scope = rememberCoroutineScope()
    val currentUserId by sharedViewModel.selectedUserId.collectAsState()

    val savedApiKey by remember(currentUserId) {
        currentUserId?.let { sharedViewModel.hevyApiKey(it) } ?: flowOf("")
    }.collectAsState(initial = "")

    val savedOverride by remember(currentUserId) {
        currentUserId?.let { sharedViewModel.hevyOverrideEnabled(it) } ?: flowOf(false)
    }.collectAsState(initial = false)

    var apiKeyDraft by remember(savedApiKey) { mutableStateOf(savedApiKey) }
    var overrideDraft by remember(savedOverride) { mutableStateOf(savedOverride) }

    var isTesting by remember { mutableStateOf(false) }

    val screenTitle = stringResource(R.string.hevy_settings_title)
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
        // Pro info banner
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
                text = stringResource(R.string.hevy_settings_pro_info),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        HorizontalDivider(modifier = Modifier.padding(bottom = 16.dp))

        // API Key field
        OutlinedTextField(
            value = apiKeyDraft,
            onValueChange = { apiKeyDraft = it },
            label = { Text(stringResource(R.string.hevy_settings_api_key_label)) },
            placeholder = { Text(stringResource(R.string.hevy_settings_api_key_placeholder)) },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(16.dp))

        // Override toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.hevy_settings_override_label),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.hevy_settings_override_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = overrideDraft,
                onCheckedChange = { overrideDraft = it },
            )
        }

        Spacer(Modifier.height(24.dp))

        // Test Connection
        OutlinedButton(
            onClick = {
                if (currentUserId == null) return@OutlinedButton
                isTesting = true
                scope.launch {
                    val result = sharedViewModel.testHevyConnection(apiKeyDraft.trim())
                    isTesting = false
                    result.fold(
                        onSuccess = {
                            sharedViewModel.showSnackbar(messageResId = R.string.hevy_settings_test_success)
                        },
                        onFailure = { e ->
                            sharedViewModel.showSnackbar(
                                messageResId = R.string.hevy_settings_test_failure,
                                formatArgs = listOf(e.message ?: "Unknown error"),
                            )
                        }
                    )
                }
            },
            enabled = apiKeyDraft.isNotBlank() && !isTesting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.hevy_settings_test_button))
        }

        Spacer(Modifier.height(8.dp))

        // Save
        Button(
            onClick = {
                val userId = currentUserId ?: return@Button
                scope.launch {
                    sharedViewModel.setHevyApiKey(userId, apiKeyDraft.trim())
                    sharedViewModel.setHevyOverrideEnabled(userId, overrideDraft)
                    sharedViewModel.showSnackbar(messageResId = R.string.hevy_settings_saved)
                }
            },
            enabled = currentUserId != null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.hevy_settings_save_button))
        }
    }
}
