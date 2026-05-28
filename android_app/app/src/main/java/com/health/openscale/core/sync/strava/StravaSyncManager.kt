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

import com.health.openscale.core.model.MeasurementWithValues
import com.health.openscale.core.model.extractWeightKg
import com.health.openscale.core.utils.LogManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StravaSyncManager @Inject constructor(
    private val client: StravaApiClient,
    private val settings: StravaSettings,
) {
    private val TAG = "StravaSyncManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshMutex = Mutex()

    // -------------------------------------------------------------------------
    // Fire-and-forget sync (called after each measurement save)
    // -------------------------------------------------------------------------

    fun fireAndForget(measurement: MeasurementWithValues) {
        scope.launch {
            try {
                val userId = measurement.measurement.userId
                val accessToken = settings.stravaAccessToken(userId).first().trim()
                if (accessToken.isBlank()) return@launch

                val weightKg = measurement.extractWeightKg() ?: return@launch

                val tokenToUse = if (isTokenExpired(userId)) {
                    refreshOrNull(userId) ?: return@launch
                } else {
                    accessToken
                }

                client.syncWeight(tokenToUse, weightKg).onFailure { e ->
                    LogManager.e(TAG, "Strava sync failed: ${e.message}", e)
                }
            } catch (e: Exception) {
                LogManager.e(TAG, "Strava sync unexpected error", e)
            }
        }
    }

    // -------------------------------------------------------------------------
    // OAuth token exchange (called from settings screen after callback)
    // -------------------------------------------------------------------------

    suspend fun exchangeAndSaveTokens(userId: Int, code: String): Result<Unit> {
        val clientId = settings.stravaClientId(userId).first().trim()
        val clientSecret = settings.stravaClientSecret(userId).first().trim()

        return client.exchangeCode(clientId, clientSecret, code).mapCatching { token ->
            settings.setStravaAccessToken(userId, token.accessToken)
            settings.setStravaRefreshToken(userId, token.refreshToken)
            settings.setStravaTokenExpiresAt(userId, token.expiresAt)
            settings.setStravaAthleteName(userId, token.athleteDisplayName)
        }
    }

    suspend fun disconnectUser(userId: Int) = settings.clearStravaAuth(userId)

    suspend fun testConnection(accessToken: String): Result<Unit> =
        client.testConnection(accessToken)

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private suspend fun isTokenExpired(userId: Int): Boolean {
        val expiresAt = settings.stravaTokenExpiresAt(userId).first()
        return expiresAt > 0L && System.currentTimeMillis() / 1000 >= expiresAt - 60
    }

    private suspend fun refreshOrNull(userId: Int): String? = refreshMutex.withLock {
        // Re-check inside the lock: a parallel coroutine may have already refreshed.
        if (!isTokenExpired(userId)) {
            return@withLock settings.stravaAccessToken(userId).first().trim().ifBlank { null }
        }

        val clientId = settings.stravaClientId(userId).first().trim()
        val clientSecret = settings.stravaClientSecret(userId).first().trim()
        val refreshToken = settings.stravaRefreshToken(userId).first().trim()

        if (clientId.isBlank() || clientSecret.isBlank() || refreshToken.isBlank()) {
            LogManager.e(TAG, "Strava token expired but refresh credentials missing")
            return@withLock null
        }

        return@withLock client.refreshToken(clientId, clientSecret, refreshToken).getOrElse { e ->
            LogManager.e(TAG, "Strava token refresh failed: ${e.message}", e)
            null
        }?.also { token ->
            settings.setStravaAccessToken(userId, token.accessToken)
            settings.setStravaRefreshToken(userId, token.refreshToken)
            settings.setStravaTokenExpiresAt(userId, token.expiresAt)
        }?.accessToken
    }

}
