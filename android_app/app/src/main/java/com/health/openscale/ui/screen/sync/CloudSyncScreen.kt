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
package com.health.openscale.ui.screen.sync

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.health.openscale.BuildConfig
import com.health.openscale.R
import com.health.openscale.ui.navigation.Routes
import com.health.openscale.ui.shared.SharedViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun CloudSyncScreen(
    navController: NavController,
    sharedViewModel: SharedViewModel,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val screenTitle = stringResource(R.string.route_title_cloud_sync)
    LaunchedEffect(Unit) {
        sharedViewModel.setTopBarTitle(screenTitle)
        sharedViewModel.setTopBarActions(emptyList())
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                val logFile = File(sharedViewModel.syncDryRunLogPath)
                scope.launch(Dispatchers.IO) {
                    val ok = runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            logFile.inputStream().use { it.copyTo(out) }
                        } != null
                    }.getOrElse { false }
                    scope.launch {
                        sharedViewModel.showSnackbar(
                            context.getString(
                                if (ok) R.string.log_export_success else R.string.log_export_error
                            )
                        )
                    }
                }
            }
        } else {
            scope.launch {
                sharedViewModel.showSnackbar(context.getString(R.string.log_export_cancelled))
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = stringResource(R.string.cloud_sync_section_available),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp),
        )

        SyncProviderCard(
            icon = Icons.Filled.CloudUpload,
            title = stringResource(R.string.cloud_sync_webhook_title),
            description = stringResource(R.string.cloud_sync_webhook_desc),
            onClick = { navController.navigate(Routes.WEBHOOK_SETTINGS) },
        )

        SyncProviderCard(
            icon = Icons.Filled.FitnessCenter,
            title = stringResource(R.string.cloud_sync_hevy_title),
            description = stringResource(R.string.cloud_sync_hevy_desc),
            onClick = { navController.navigate(Routes.HEVY_SETTINGS) },
        )

        SyncProviderCard(
            icon = Icons.Filled.DirectionsRun,
            title = stringResource(R.string.cloud_sync_strava_title),
            description = stringResource(R.string.cloud_sync_strava_desc),
            onClick = { navController.navigate(Routes.STRAVA_SETTINGS) },
        )

        SyncProviderCard(
            icon = Icons.Filled.Favorite,
            title = stringResource(R.string.cloud_sync_health_connect_title),
            description = stringResource(R.string.cloud_sync_health_connect_desc),
            onClick = { navController.navigate(Routes.HEALTH_CONNECT_SETTINGS) },
        )

        Spacer(Modifier.height(16.dp))

        if (BuildConfig.DEBUG) {
            val dryRunEnabled by sharedViewModel.syncDryRunEnabled.collectAsState()

            Text(
                text = "Developer",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                ListItem(
                    headlineContent = { Text("Dry Run Mode") },
                    supportingContent = {
                        Column {
                            Text(
                                text = if (dryRunEnabled)
                                    "Requests intercepted — writing to log file"
                                else
                                    "Toggle to intercept all sync requests instead of sending",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            if (dryRunEnabled) {
                                Spacer(Modifier.height(6.dp))
                                Row {
                                    OutlinedButton(
                                        onClick = {
                                            val logFile = File(sharedViewModel.syncDryRunLogPath)
                                            if (logFile.exists()) {
                                                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                                    addCategory(Intent.CATEGORY_OPENABLE)
                                                    type = "text/plain"
                                                    putExtra(Intent.EXTRA_TITLE, logFile.name)
                                                }
                                                try {
                                                    exportLauncher.launch(intent)
                                                } catch (e: ActivityNotFoundException) {
                                                    scope.launch {
                                                        sharedViewModel.showSnackbar(
                                                            context.getString(R.string.log_export_no_app_error)
                                                        )
                                                    }
                                                }
                                            } else {
                                                scope.launch {
                                                    sharedViewModel.showSnackbar(
                                                        context.getString(R.string.log_export_no_file_to_export)
                                                    )
                                                }
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text(stringResource(R.string.export_log_file_button))
                                    }
                                    Spacer(Modifier.padding(horizontal = 4.dp))
                                    Button(
                                        onClick = { sharedViewModel.clearSyncDryRunLog() },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.error,
                                        ),
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text("Clear Log")
                                    }
                                }
                            }
                        }
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Filled.BugReport,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = dryRunEnabled,
                            onCheckedChange = { sharedViewModel.setSyncDryRunEnabled(it) },
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SyncProviderCard(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() },
        colors = CardDefaults.elevatedCardColors(),
        elevation = CardDefaults.elevatedCardElevation(),
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            },
            supportingContent = {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            leadingContent = {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            trailingContent = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}
