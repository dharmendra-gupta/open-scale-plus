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
package com.health.openscale.core.sync.hevy

import com.health.openscale.core.model.MeasurementWithValues
import com.health.openscale.core.model.extractBodyFatPct
import com.health.openscale.core.model.extractLbmKg
import com.health.openscale.core.model.extractWeightKg
import com.health.openscale.core.utils.LogManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HevySyncManager @Inject constructor(
    private val client: HevyApiClient,
    private val settings: HevySettings,
) {
    private val TAG = "HevySyncManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun fireAndForget(measurement: MeasurementWithValues) {
        scope.launch {
            try {
                val userId = measurement.measurement.userId
                val apiKey = settings.hevyApiKey(userId).first().trim()
                if (apiKey.isBlank()) {
                    LogManager.d(TAG, "Skipped: no API key configured for user $userId")
                    return@launch
                }

                val override = settings.hevyOverrideEnabled(userId).first()
                val date = dateFmt.format(Date(measurement.measurement.timestamp))
                val weightKg = measurement.extractWeightKg()
                val bodyFatPct = measurement.extractBodyFatPct()
                val lbmKg = measurement.extractLbmKg()

                if (weightKg == null && bodyFatPct == null && lbmKg == null) {
                    LogManager.w(TAG, "Skipped: no syncable values in measurement for user $userId")
                    return@launch
                }

                LogManager.i(TAG, "Syncing date=$date weight=$weightKg fat=$bodyFatPct lbm=$lbmKg override=$override")
                client.sync(apiKey, date, weightKg, bodyFatPct, lbmKg, override)
                    .onSuccess { LogManager.i(TAG, "Hevy sync successful for $date") }
                    .onFailure { e -> LogManager.e(TAG, "Hevy sync failed: ${e.message}", e) }
            } catch (e: Exception) {
                LogManager.e(TAG, "Hevy sync unexpected error", e)
            }
        }
    }

    suspend fun testConnection(apiKey: String): Result<Unit> =
        client.testConnection(apiKey)
}
