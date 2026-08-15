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

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test

class HevyApiClientTest {

    private val server = MockWebServer()
    private lateinit var client: HevyApiClient

    @Before
    fun setUp() {
        server.start()
        client = HevyApiClient(OkHttpClient()).also {
            it.baseUrl = server.url("").toString().trimEnd('/')
        }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    // -------------------------------------------------------------------------
    // sync — happy paths
    // -------------------------------------------------------------------------

    @Test
    fun sync_200_returnsSuccess() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        val result = client.sync("key", "2026-05-27", 75.0f, null, null, false)
        assertThat(result.isSuccess).isTrue()
    }

    @Test
    fun sync_201_returnsSuccess() = runTest {
        server.enqueue(MockResponse().setResponseCode(201))
        val result = client.sync("key", "2026-05-27", 75.0f, null, null, false)
        assertThat(result.isSuccess).isTrue()
    }

    @Test
    fun sync_sendsApiKeyHeader() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        client.sync("my-secret-key", "2026-05-27", 75.0f, null, null, false)
        val request = server.takeRequest()
        assertThat(request.getHeader("api-key")).isEqualTo("my-secret-key")
    }

    @Test
    fun sync_usesPostMethod() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        client.sync("key", "2026-05-27", 75.0f, null, null, false)
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
    }

    @Test
    fun sync_contentTypeIsJson() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        client.sync("key", "2026-05-27", 75.0f, null, null, false)
        val request = server.takeRequest()
        assertThat(request.getHeader("Content-Type")).contains("application/json")
    }

    // -------------------------------------------------------------------------
    // sync — payload fields
    // -------------------------------------------------------------------------

    @Test
    fun sync_payloadContainsDate() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        client.sync("key", "2026-05-27", 75.0f, null, null, false)
        val body = JSONObject(server.takeRequest().body.readUtf8())
        assertThat(body.getString("date")).isEqualTo("2026-05-27")
    }

    @Test
    fun sync_payloadContainsWeightKg() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        client.sync("key", "2026-05-27", 80.5f, null, null, false)
        val body = JSONObject(server.takeRequest().body.readUtf8())
        assertThat(body.getDouble("weight_kg")).isWithin(0.001).of(80.5)
    }

    @Test
    fun sync_payloadContainsBodyFatPercentage() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        client.sync("key", "2026-05-27", null, 22.5f, null, false)
        val body = JSONObject(server.takeRequest().body.readUtf8())
        assertThat(body.getDouble("fat_percent")).isWithin(0.001).of(22.5)
    }

    @Test
    fun sync_payloadContainsLeanBodyMassKg() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        client.sync("key", "2026-05-27", null, null, 62.3f, false)
        val body = JSONObject(server.takeRequest().body.readUtf8())
        assertThat(body.getDouble("lean_mass_kg")).isWithin(0.001).of(62.3)
    }

    @Test
    fun sync_payloadContainsAllThreeFields() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        client.sync("key", "2026-05-27", 80.0f, 18.0f, 65.6f, false)
        val body = JSONObject(server.takeRequest().body.readUtf8())
        assertThat(body.has("weight_kg")).isTrue()
        assertThat(body.has("fat_percent")).isTrue()
        assertThat(body.has("lean_mass_kg")).isTrue()
    }

    @Test
    fun sync_nullWeight_payloadOmitsWeightKg() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        client.sync("key", "2026-05-27", null, 22.5f, null, false)
        val body = JSONObject(server.takeRequest().body.readUtf8())
        assertThat(body.has("weight_kg")).isFalse()
    }

    @Test
    fun sync_nullBodyFat_payloadOmitsBodyFatPercentage() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        client.sync("key", "2026-05-27", 75.0f, null, null, false)
        val body = JSONObject(server.takeRequest().body.readUtf8())
        assertThat(body.has("fat_percent")).isFalse()
    }

    @Test
    fun sync_nullLbm_payloadOmitsLeanBodyMassKg() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        client.sync("key", "2026-05-27", 75.0f, null, null, false)
        val body = JSONObject(server.takeRequest().body.readUtf8())
        assertThat(body.has("lean_mass_kg")).isFalse()
    }

    // -------------------------------------------------------------------------
    // sync — circumference fields must match Hevy's body_measurements schema keys
    // -------------------------------------------------------------------------

    @Test
    fun sync_payloadUsesExactHevyCircumferenceKeys() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        client.sync(
            "key", "2026-05-27", 80.0f, null, null, false,
            waistCm = 82.0f, hipsCm = 96.0f, chestCm = 100.0f,
            neckCm = 38.0f, bicepCm = 34.0f, thighCm = 56.0f,
        )
        val body = JSONObject(server.takeRequest().body.readUtf8())
        // Hevy uses suffix-less keys for waist / hips / thigh (values still cm)
        assertThat(body.getDouble("waist")).isWithin(0.001).of(82.0)
        assertThat(body.getDouble("hips")).isWithin(0.001).of(96.0)
        assertThat(body.getDouble("left_thigh")).isWithin(0.001).of(56.0)
        assertThat(body.getDouble("right_thigh")).isWithin(0.001).of(56.0)
        // …but _cm suffix for chest / neck / bicep
        assertThat(body.getDouble("chest_cm")).isWithin(0.001).of(100.0)
        assertThat(body.getDouble("neck_cm")).isWithin(0.001).of(38.0)
        assertThat(body.getDouble("left_bicep_cm")).isWithin(0.001).of(34.0)
        assertThat(body.getDouble("right_bicep_cm")).isWithin(0.001).of(34.0)
    }

    @Test
    fun sync_payloadNeverSendsUnsupportedKeys() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        client.sync(
            "key", "2026-05-27", 80.0f, null, null, false,
            waistCm = 82.0f, hipsCm = 96.0f, thighCm = 56.0f,
        )
        val body = JSONObject(server.takeRequest().body.readUtf8())
        // wrong names Hevy would silently drop, and bmi which Hevy has no field for
        assertThat(body.has("waist_cm")).isFalse()
        assertThat(body.has("hips_cm")).isFalse()
        assertThat(body.has("left_thigh_cm")).isFalse()
        assertThat(body.has("right_thigh_cm")).isFalse()
        assertThat(body.has("bmi")).isFalse()
    }

    @Test
    fun sync_nullCircumferences_payloadOmitsThem() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        client.sync("key", "2026-05-27", 80.0f, null, null, false)
        val body = JSONObject(server.takeRequest().body.readUtf8())
        assertThat(body.has("waist")).isFalse()
        assertThat(body.has("hips")).isFalse()
        assertThat(body.has("chest_cm")).isFalse()
        assertThat(body.has("neck_cm")).isFalse()
        assertThat(body.has("left_bicep_cm")).isFalse()
        assertThat(body.has("left_thigh")).isFalse()
    }

    @Test
    fun sync_409_overrideEnabled_putUsesExactHevyCircumferenceKeys() = runTest {
        server.enqueue(MockResponse().setResponseCode(409))
        server.enqueue(MockResponse().setResponseCode(200))
        client.sync(
            "key", "2026-05-27", 80.0f, null, null, true,
            waistCm = 82.0f, thighCm = 56.0f,
        )
        server.takeRequest() // POST
        val body = JSONObject(server.takeRequest().body.readUtf8())
        assertThat(body.getDouble("waist")).isWithin(0.001).of(82.0)
        assertThat(body.getDouble("left_thigh")).isWithin(0.001).of(56.0)
        assertThat(body.has("waist_cm")).isFalse()
    }

    // -------------------------------------------------------------------------
    // sync — error responses
    // -------------------------------------------------------------------------

    @Test
    fun sync_500_returnsFailure() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        val result = client.sync("key", "2026-05-27", 75.0f, null, null, false)
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("500")
    }

    @Test
    fun sync_401_returnsFailure() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        val result = client.sync("key", "2026-05-27", 75.0f, null, null, false)
        assertThat(result.isFailure).isTrue()
    }

    // -------------------------------------------------------------------------
    // sync — 409 conflict handling
    // -------------------------------------------------------------------------

    @Test
    fun sync_409_overrideDisabled_returnsFailure() = runTest {
        server.enqueue(MockResponse().setResponseCode(409))
        val result = client.sync("key", "2026-05-27", 75.0f, null, null, false)
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("already exists")
    }

    @Test
    fun sync_409_overrideDisabled_errorMentionsDate() = runTest {
        server.enqueue(MockResponse().setResponseCode(409))
        val result = client.sync("key", "2026-05-27", 75.0f, null, null, false)
        assertThat(result.exceptionOrNull()?.message).contains("2026-05-27")
    }

    @Test
    fun sync_409_overrideEnabled_sendsPostThenPut() = runTest {
        server.enqueue(MockResponse().setResponseCode(409))
        server.enqueue(MockResponse().setResponseCode(200))

        client.sync("key", "2026-05-27", 75.0f, null, null, true)

        val post = server.takeRequest()
        val put = server.takeRequest()
        assertThat(post.method).isEqualTo("POST")
        assertThat(put.method).isEqualTo("PUT")
    }

    @Test
    fun sync_409_overrideEnabled_putUsesDateInPath() = runTest {
        server.enqueue(MockResponse().setResponseCode(409))
        server.enqueue(MockResponse().setResponseCode(200))

        client.sync("key", "2026-05-27", 75.0f, null, null, true)

        server.takeRequest() // POST
        val put = server.takeRequest()
        assertThat(put.path).contains("2026-05-27")
    }

    @Test
    fun sync_409_overrideEnabled_putFailure_returnsFailure() = runTest {
        server.enqueue(MockResponse().setResponseCode(409))
        server.enqueue(MockResponse().setResponseCode(400).setBody("bad request"))

        val result = client.sync("key", "2026-05-27", 75.0f, null, null, true)

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("400")
    }

    @Test
    fun sync_409_overrideEnabled_putBodyHasNoDate() = runTest {
        server.enqueue(MockResponse().setResponseCode(409))
        server.enqueue(MockResponse().setResponseCode(200))

        client.sync("key", "2026-05-27", 75.0f, null, null, true)

        server.takeRequest() // POST
        val put = server.takeRequest()
        val body = JSONObject(put.body.readUtf8())
        assertThat(body.has("date")).isFalse()
    }

    @Test
    fun sync_409_overrideEnabled_putReturnsSuccess() = runTest {
        server.enqueue(MockResponse().setResponseCode(409))
        server.enqueue(MockResponse().setResponseCode(200))

        val result = client.sync("key", "2026-05-27", 75.0f, null, null, true)

        assertThat(result.isSuccess).isTrue()
    }

    // -------------------------------------------------------------------------
    // testConnection
    // -------------------------------------------------------------------------

    @Test
    fun testConnection_200_returnsSuccess() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
        val result = client.testConnection("my-key")
        assertThat(result.isSuccess).isTrue()
    }

    @Test
    fun testConnection_401_returnsFailure() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        val result = client.testConnection("bad-key")
        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun testConnection_sendsGetRequest() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
        client.testConnection("my-key")
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("GET")
    }

    @Test
    fun testConnection_sendsApiKeyHeader() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
        client.testConnection("test-key-123")
        val request = server.takeRequest()
        assertThat(request.getHeader("api-key")).isEqualTo("test-key-123")
    }
}
