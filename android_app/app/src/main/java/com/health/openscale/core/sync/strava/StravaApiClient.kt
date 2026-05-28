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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class StravaApiClient @Inject constructor(
    @Named("strava") private val httpClient: OkHttpClient,
) {
    companion object {
        private val FORM_MEDIA = "application/x-www-form-urlencoded".toMediaType()
        const val REDIRECT_URI = "openscale://localhost"
        private const val SCOPE = "profile:write"

        fun buildAuthUrl(clientId: String): String =
            "https://www.strava.com/oauth/authorize" +
                "?client_id=$clientId" +
                "&redirect_uri=$REDIRECT_URI" +
                "&response_type=code" +
                "&scope=$SCOPE"
    }

    internal var baseUrl: String = "https://www.strava.com"

    // -------------------------------------------------------------------------
    // OAuth
    // -------------------------------------------------------------------------

    suspend fun exchangeCode(
        clientId: String,
        clientSecret: String,
        code: String,
    ): Result<StravaTokenResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val body = "client_id=$clientId&client_secret=$clientSecret&code=$code&grant_type=authorization_code"
                .toRequestBody(FORM_MEDIA)
            val request = Request.Builder()
                .url("$baseUrl/oauth/token")
                .post(body)
                .build()
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: throw IOException("Empty token response")
                if (!response.isSuccessful) throw IOException(stravaErrorMessage(response.code, body))
                parseTokenResponse(body)
            }
        }
    }

    suspend fun refreshToken(
        clientId: String,
        clientSecret: String,
        refreshToken: String,
    ): Result<StravaTokenResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val body = "client_id=$clientId&client_secret=$clientSecret&refresh_token=$refreshToken&grant_type=refresh_token"
                .toRequestBody(FORM_MEDIA)
            val request = Request.Builder()
                .url("$baseUrl/oauth/token")
                .post(body)
                .build()
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: throw IOException("Empty refresh response")
                if (!response.isSuccessful) throw IOException(stravaErrorMessage(response.code, body))
                parseTokenResponse(body)
            }
        }
    }

    // -------------------------------------------------------------------------
    // Athlete
    // -------------------------------------------------------------------------

    /** Updates the authenticated athlete's weight (kg). */
    suspend fun syncWeight(accessToken: String, weightKg: Float): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val body = "weight=$weightKg".toRequestBody(FORM_MEDIA)
            val request = Request.Builder()
                .url("$baseUrl/api/v3/athlete")
                .addHeader("Authorization", "Bearer $accessToken")
                .put(body)
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Strava HTTP ${response.code}")
            }
        }
    }

    /** Verifies the token by fetching the authenticated athlete's profile. */
    suspend fun testConnection(accessToken: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("$baseUrl/api/v3/athlete")
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Strava HTTP ${response.code}")
            }
        }
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private fun stravaErrorMessage(code: Int, body: String): String {
        val message = runCatching { JSONObject(body).getString("message") }.getOrNull()
        return if (message != null) "Strava $code: $message" else "Strava HTTP $code"
    }

    private fun parseTokenResponse(json: String): StravaTokenResponse {
        val obj = JSONObject(json)
        val athlete = obj.optJSONObject("athlete")
        return StravaTokenResponse(
            accessToken   = obj.getString("access_token"),
            refreshToken  = obj.getString("refresh_token"),
            expiresAt     = obj.getLong("expires_at"),
            athleteFirstName = athlete?.optString("firstname", "") ?: "",
            athleteLastName  = athlete?.optString("lastname", "")  ?: "",
        )
    }
}
