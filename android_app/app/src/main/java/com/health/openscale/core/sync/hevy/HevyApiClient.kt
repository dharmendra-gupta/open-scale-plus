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
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HevyApiClient @Inject constructor(
    private val httpClient: OkHttpClient,
) {
    private companion object {
        val JSON_MEDIA = "application/json".toMediaType()
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
            val request = Request.Builder()
                .url("$baseUrl/body_measurements")
                .addHeader("api-key", apiKey)
                .post(payload.toRequestBody(JSON_MEDIA))
                .build()

            val code = httpClient.newCall(request).execute().use { it.code }
            when {
                code in 200..299 -> return@runCatching
                code == 409 && overrideExisting -> updateByDate(apiKey, date, weightKg, bodyFatPct, lbmKg)
                code == 409 -> throw IOException("Entry already exists for $date. Enable Override in Hevy settings to update.")
                else -> throw IOException("Hevy HTTP $code")
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

    private fun buildPayload(date: String, weightKg: Float?, bodyFatPct: Float?, lbmKg: Float?): String =
        JSONObject().apply {
            put("date", date)
            weightKg?.let { put("weight_kg", it.toDouble()) }
            bodyFatPct?.let { put("fat_percent", it.toDouble()) }
            lbmKg?.let { put("lean_mass_kg", it.toDouble()) }
        }.toString()

    private fun updateByDate(apiKey: String, date: String, weightKg: Float?, bodyFatPct: Float?, lbmKg: Float?) {
        val getRequest = Request.Builder()
            .url("$baseUrl/body_measurements?page=1&pageSize=10")
            .addHeader("api-key", apiKey)
            .get()
            .build()

        val id = httpClient.newCall(getRequest).execute().use { response ->
            if (!response.isSuccessful) throw IOException("GET body_measurements HTTP ${response.code}")
            val body = response.body?.string() ?: throw IOException("Empty GET response")
            findIdByDate(body, date)
        } ?: throw IOException("No existing entry found for $date to update")

        val payload = buildPayload(date, weightKg, bodyFatPct, lbmKg)
        val putRequest = Request.Builder()
            .url("$baseUrl/body_measurements/$id")
            .addHeader("api-key", apiKey)
            .put(payload.toRequestBody(JSON_MEDIA))
            .build()
        httpClient.newCall(putRequest).execute().use { response ->
            if (!response.isSuccessful) throw IOException("PUT body_measurements HTTP ${response.code}")
        }
    }

    private fun findIdByDate(responseBody: String, date: String): String? {
        return try {
            val arr: JSONArray = try {
                JSONArray(responseBody)
            } catch (_: Exception) {
                JSONObject(responseBody).optJSONArray("body_measurements") ?: return null
            }
            for (i in 0 until arr.length()) {
                val entry = arr.getJSONObject(i)
                if (entry.optString("date") == date) {
                    return entry.optString("id").takeIf { it.isNotBlank() }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }
}
