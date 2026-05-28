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

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BoneMassRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.LeanBodyMassRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import com.health.openscale.R
import com.health.openscale.ui.shared.SharedViewModel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

private val HC_PERMISSIONS = setOf(
    HealthPermission.getWritePermission(WeightRecord::class),
    HealthPermission.getWritePermission(BodyFatRecord::class),
    HealthPermission.getWritePermission(LeanBodyMassRecord::class),
    HealthPermission.getWritePermission(BoneMassRecord::class),
)

private suspend fun checkHcPermissions(context: android.content.Context): Boolean =
    runCatching {
        val client = HealthConnectClient.getOrCreate(context)
        val granted = client.permissionController.getGrantedPermissions()
        HC_PERMISSIONS.all { it in granted }
    }.getOrElse { false }

@Composable
fun HealthConnectSettingsScreen(
    navController: NavController,
    sharedViewModel: SharedViewModel,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentUserId by sharedViewModel.selectedUserId.collectAsState()

    val isEnabled by remember(currentUserId) {
        currentUserId?.let { sharedViewModel.healthConnectEnabled(it) } ?: flowOf(false)
    }.collectAsState(initial = false)

    val sdkStatus = remember { HealthConnectClient.getSdkStatus(context) }
    val isAvailable = sdkStatus == HealthConnectClient.SDK_AVAILABLE

    var hasPermissions by remember { mutableStateOf(false) }

    // Re-check permissions every time the screen is resumed (handles external revocation).
    LaunchedEffect(isAvailable) {
        if (!isAvailable) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val granted = checkHcPermissions(context)
            hasPermissions = granted
            if (isEnabled && !granted) {
                currentUserId?.let { sharedViewModel.setHealthConnectEnabled(it, false) }
                sharedViewModel.showSnackbar(messageResId = R.string.health_connect_permissions_revoked)
            }
        }
    }

    val requestPermissions = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract(),
    ) { granted: Set<String> ->
        val allGranted = HC_PERMISSIONS.all { it in granted }
        hasPermissions = allGranted
        val userId = currentUserId ?: return@rememberLauncherForActivityResult
        scope.launch {
            sharedViewModel.setHealthConnectEnabled(userId, allGranted)
            if (!allGranted) {
                sharedViewModel.showSnackbar(messageResId = R.string.health_connect_permissions_denied)
            }
        }
    }

    val screenTitle = stringResource(R.string.health_connect_settings_title)
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
                text = stringResource(R.string.health_connect_settings_info),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        HorizontalDivider(modifier = Modifier.padding(bottom = 16.dp))

        if (!isAvailable) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 2.dp, end = 8.dp),
                )
                Text(
                    text = stringResource(R.string.health_connect_not_installed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                onClick = {
                    uriHandler.openUri("https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata")
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.health_connect_install_button))
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 12.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.health_connect_settings_enable_label),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(R.string.health_connect_settings_enable_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = isEnabled && hasPermissions,
                    onCheckedChange = { wantsEnabled ->
                        val userId = currentUserId ?: return@Switch
                        if (wantsEnabled) {
                            if (hasPermissions) {
                                scope.launch { sharedViewModel.setHealthConnectEnabled(userId, true) }
                            } else {
                                runCatching { requestPermissions.launch(HC_PERMISSIONS) }
                                    .onFailure {
                                        sharedViewModel.showSnackbar(
                                            messageResId = R.string.health_connect_permissions_denied
                                        )
                                    }
                            }
                        } else {
                            scope.launch { sharedViewModel.setHealthConnectEnabled(userId, false) }
                        }
                    },
                    enabled = currentUserId != null,
                )
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}
