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

import java.time.Instant

interface HealthConnectApi {
    suspend fun insertWeight(weightKg: Double, timestamp: Instant): Result<Unit>
    suspend fun insertBodyFat(percentage: Double, timestamp: Instant): Result<Unit>
    suspend fun insertLeanBodyMass(lbmKg: Double, timestamp: Instant): Result<Unit>
    suspend fun insertBoneMass(boneKg: Double, timestamp: Instant): Result<Unit>
}
