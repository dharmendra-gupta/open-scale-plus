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
package com.health.openscale.core.sync.healthconnect

import com.health.openscale.core.model.MeasurementWithValues
import com.health.openscale.core.model.extractBodyFatPct
import com.health.openscale.core.model.extractBoneKg
import com.health.openscale.core.model.extractLbmKg
import com.health.openscale.core.model.extractWeightKg
import com.health.openscale.core.utils.LogManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HealthConnectSyncManager @Inject constructor(
    private val client: HealthConnectApi,
    private val settings: HealthConnectSettings,
) {
    private val TAG = "HealthConnectSyncManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun fireAndForget(measurement: MeasurementWithValues) {
        scope.launch {
            try {
                val userId = measurement.measurement.userId
                if (!settings.healthConnectEnabled(userId).first()) {
                    LogManager.d(TAG, "Skipped: not enabled for user $userId")
                    return@launch
                }

                val weight = measurement.extractWeightKg()
                val fat = measurement.extractBodyFatPct()
                val lbm = measurement.extractLbmKg()
                val bone = measurement.extractBoneKg()
                LogManager.i(TAG, "Syncing user=$userId weight=$weight fat=$fat lbm=$lbm bone=$bone")

                val timestamp = Instant.ofEpochMilli(measurement.measurement.timestamp)

                weight?.let { kg ->
                    client.insertWeight(kg.toDouble(), timestamp)
                        .onSuccess { LogManager.i(TAG, "Weight inserted: ${"%.2f".format(kg)} kg") }
                        .onFailure { e -> LogManager.e(TAG, "Weight insert failed: ${e.message}", e) }
                }
                fat?.let { pct ->
                    client.insertBodyFat(pct.toDouble(), timestamp)
                        .onSuccess { LogManager.i(TAG, "Body fat inserted: ${"%.2f".format(pct)} %") }
                        .onFailure { e -> LogManager.e(TAG, "Body fat insert failed: ${e.message}", e) }
                }
                lbm?.let { kg ->
                    client.insertLeanBodyMass(kg.toDouble(), timestamp)
                        .onSuccess { LogManager.i(TAG, "LBM inserted: ${"%.2f".format(kg)} kg") }
                        .onFailure { e -> LogManager.e(TAG, "LBM insert failed: ${e.message}", e) }
                }
                bone?.let { kg ->
                    client.insertBoneMass(kg.toDouble(), timestamp)
                        .onSuccess { LogManager.i(TAG, "Bone mass inserted: ${"%.2f".format(kg)} kg") }
                        .onFailure { e -> LogManager.e(TAG, "Bone mass insert failed: ${e.message}", e) }
                }
            } catch (e: Exception) {
                LogManager.e(TAG, "HC sync unexpected error", e)
            }
        }
    }
}
