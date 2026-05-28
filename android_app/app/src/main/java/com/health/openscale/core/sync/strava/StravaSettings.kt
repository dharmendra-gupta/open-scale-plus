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
package com.health.openscale.core.sync.strava

import kotlinx.coroutines.flow.Flow

interface StravaSettings {
    fun stravaClientId(userId: Int): Flow<String>
    fun stravaClientSecret(userId: Int): Flow<String>
    fun stravaAccessToken(userId: Int): Flow<String>
    fun stravaRefreshToken(userId: Int): Flow<String>
    fun stravaTokenExpiresAt(userId: Int): Flow<Long>
    fun stravaAthleteName(userId: Int): Flow<String>

    suspend fun setStravaClientId(userId: Int, id: String)
    suspend fun setStravaClientSecret(userId: Int, secret: String)
    suspend fun setStravaAccessToken(userId: Int, token: String)
    suspend fun setStravaRefreshToken(userId: Int, token: String)
    suspend fun setStravaTokenExpiresAt(userId: Int, expiresAt: Long)
    suspend fun setStravaAthleteName(userId: Int, name: String)
    suspend fun clearStravaAuth(userId: Int)
}
