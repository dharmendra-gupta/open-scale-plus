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

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.BoneMassRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.LeanBodyMassRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Percentage
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HealthConnectApiClient @Inject constructor(
    @ApplicationContext private val context: Context,
) : HealthConnectApi {
    private val client: HealthConnectClient by lazy {
        HealthConnectClient.getOrCreate(context)
    }

    override suspend fun insertWeight(weightKg: Double, timestamp: Instant): Result<Unit> = runCatching {
        client.insertRecords(listOf(
            WeightRecord(
                weight = Mass.kilograms(weightKg),
                time = timestamp,
                zoneOffset = ZoneOffset.UTC,
            )
        ))
    }

    override suspend fun insertBodyFat(percentage: Double, timestamp: Instant): Result<Unit> = runCatching {
        client.insertRecords(listOf(
            BodyFatRecord(
                percentage = Percentage(percentage),
                time = timestamp,
                zoneOffset = ZoneOffset.UTC,
            )
        ))
    }

    override suspend fun insertLeanBodyMass(lbmKg: Double, timestamp: Instant): Result<Unit> = runCatching {
        client.insertRecords(listOf(
            LeanBodyMassRecord(
                mass = Mass.kilograms(lbmKg),
                time = timestamp,
                zoneOffset = ZoneOffset.UTC,
            )
        ))
    }

    override suspend fun insertBoneMass(boneKg: Double, timestamp: Instant): Result<Unit> = runCatching {
        client.insertRecords(listOf(
            BoneMassRecord(
                mass = Mass.kilograms(boneKg),
                time = timestamp,
                zoneOffset = ZoneOffset.UTC,
            )
        ))
    }

    companion object {
        fun isAvailable(context: Context): Boolean =
            HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
    }
}
