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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.health.openscale.R
import com.health.openscale.ui.navigation.Routes
import com.health.openscale.ui.shared.SharedViewModel

@Composable
fun CloudSyncScreen(
    navController: NavController,
    sharedViewModel: SharedViewModel,
) {
    val screenTitle = stringResource(R.string.route_title_cloud_sync)
    LaunchedEffect(Unit) {
        sharedViewModel.setTopBarTitle(screenTitle)
        sharedViewModel.setTopBarActions(emptyList())
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
            comingSoon = false,
            onClick = { navController.navigate(Routes.WEBHOOK_SETTINGS) },
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.cloud_sync_section_coming_soon),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
        )

        SyncProviderCard(
            icon = Icons.Filled.FitnessCenter,
            title = stringResource(R.string.cloud_sync_hevy_title),
            description = stringResource(R.string.cloud_sync_hevy_desc),
            comingSoon = true,
        )

        SyncProviderCard(
            icon = Icons.Filled.DirectionsRun,
            title = stringResource(R.string.cloud_sync_strava_title),
            description = stringResource(R.string.cloud_sync_strava_desc),
            comingSoon = true,
        )

        SyncProviderCard(
            icon = Icons.Filled.Favorite,
            title = stringResource(R.string.cloud_sync_health_connect_title),
            description = stringResource(R.string.cloud_sync_health_connect_desc),
            comingSoon = true,
        )

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SyncProviderCard(
    icon: ImageVector,
    title: String,
    description: String,
    comingSoon: Boolean,
    onClick: () -> Unit = {},
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(enabled = !comingSoon) { onClick() },
        colors = if (comingSoon)
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        else
            CardDefaults.elevatedCardColors(),
        elevation = if (comingSoon) CardDefaults.cardElevation(defaultElevation = 0.dp)
                    else CardDefaults.elevatedCardElevation(),
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (comingSoon) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface,
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
                    tint = if (comingSoon) MaterialTheme.colorScheme.onSurfaceVariant
                           else MaterialTheme.colorScheme.primary,
                )
            },
            trailingContent = {
                if (comingSoon) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Text(
                            text = stringResource(R.string.cloud_sync_coming_soon),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}
