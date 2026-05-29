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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject
import com.health.openscale.core.utils.LogManager
import javax.inject.Singleton

@Singleton
class HevyApiClient @Inject constructor(
    private val httpClient: OkHttpClient,
) {
    private companion object {
        val JSON_MEDIA = "application/json".toMediaType()
        const val TAG = "HevyApiClient"
    }

    internal var baseUrl: String = "https://api.hevyapp.com/v1"

    suspend fun sync(
        apiKey: String,
        date: String,
        weightKg: Float?,
        bodyFatPct: Float?,
        lbmKg: Float?,
        overrideExisting: Boolean,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = buildPayload(date, weightKg, bodyFatPct, lbmKg)
            val url = "$baseUrl/body_measurements"
            LogManager.i(TAG, "POST $url body=$payload")
            val request = Request.Builder()
                .url(url)
                .addHeader("api-key", apiKey)
                .post(payload.toRequestBody(JSON_MEDIA))
                .build()

            val (code, body) = httpClient.newCall(request).execute().use { it.code to (it.body?.string() ?: "") }
            LogManager.i(TAG, "POST $url → $code${if (body.isNotBlank()) " body=$body" else ""}")
            when {
                code in 200..299 -> return@runCatching
                code == 409 && overrideExisting -> updateByDate(apiKey, date, weightKg, bodyFatPct, lbmKg)
                code == 409 -> throw IOException("Entry already exists for $date. Enable Override in Hevy settings to update.")
                else -> throw IOException("Hevy HTTP $code: $body")
            }
        }
    }

    suspend fun testConnection(apiKey: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("$baseUrl/body_measurements?page=1&pageSize=1")
                .addHeader("api-key", apiKey)
                .get()
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            }
        }
    }

    private fun Float.round2(): Double = String.format("%.2f", this).toDouble()

    private fun buildPayload(date: String, weightKg: Float?, bodyFatPct: Float?, lbmKg: Float?): String =
        JSONObject().apply {
            put("date", date)
            weightKg?.let { put("weight_kg", it.round2()) }
            bodyFatPct?.let { put("fat_percent", it.round2()) }
            lbmKg?.let { put("lean_mass_kg", it.round2()) }
        }.toString()

    private fun buildUpdatePayload(weightKg: Float?, bodyFatPct: Float?, lbmKg: Float?): String =
        JSONObject().apply {
            weightKg?.let { put("weight_kg", it.round2()) }
            bodyFatPct?.let { put("fat_percent", it.round2()) }
            lbmKg?.let { put("lean_mass_kg", it.round2()) }
        }.toString()

    private fun updateByDate(apiKey: String, date: String, weightKg: Float?, bodyFatPct: Float?, lbmKg: Float?) {
        val payload = buildUpdatePayload(weightKg, bodyFatPct, lbmKg)
        val putUrl = "$baseUrl/body_measurements/$date"
        LogManager.i(TAG, "PUT $putUrl body=$payload")
        val putRequest = Request.Builder()
            .url(putUrl)
            .addHeader("api-key", apiKey)
            .put(payload.toRequestBody(JSON_MEDIA))
            .build()
        httpClient.newCall(putRequest).execute().use { response ->
            val body = response.body?.string() ?: ""
            LogManager.i(TAG, "PUT $putUrl → ${response.code}${if (body.isNotBlank()) " body=$body" else ""}")
            if (!response.isSuccessful) throw IOException("PUT body_measurements HTTP ${response.code}: $body")
        }
    }

}
