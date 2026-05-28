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
    fun sync_409_overrideEnabled_sendsGetThenPut() = runTest {
        val listBody = """{"body_measurements":[{"id":"abc123","date":"2026-05-27"}]}"""
        server.enqueue(MockResponse().setResponseCode(409))
        server.enqueue(MockResponse().setResponseCode(200).setBody(listBody))
        server.enqueue(MockResponse().setResponseCode(200))

        client.sync("key", "2026-05-27", 75.0f, null, null, true)

        val post = server.takeRequest()
        val get = server.takeRequest()
        val put = server.takeRequest()
        assertThat(post.method).isEqualTo("POST")
        assertThat(get.method).isEqualTo("GET")
        assertThat(put.method).isEqualTo("PUT")
    }

    @Test
    fun sync_409_overrideEnabled_putUsesCorrectId() = runTest {
        val listBody = """{"body_measurements":[{"id":"abc123","date":"2026-05-27"}]}"""
        server.enqueue(MockResponse().setResponseCode(409))
        server.enqueue(MockResponse().setResponseCode(200).setBody(listBody))
        server.enqueue(MockResponse().setResponseCode(200))

        client.sync("key", "2026-05-27", 75.0f, null, null, true)

        server.takeRequest() // POST
        server.takeRequest() // GET
        val put = server.takeRequest()
        assertThat(put.path).contains("abc123")
    }

    @Test
    fun sync_409_overrideEnabled_entryNotInList_returnsFailure() = runTest {
        val listBody = """{"body_measurements":[{"id":"xyz","date":"2026-05-26"}]}"""
        server.enqueue(MockResponse().setResponseCode(409))
        server.enqueue(MockResponse().setResponseCode(200).setBody(listBody))

        val result = client.sync("key", "2026-05-27", 75.0f, null, null, true)

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("No existing entry")
    }

    @Test
    fun sync_409_overrideEnabled_arrayListBody_parsedCorrectly() = runTest {
        val listBody = """[{"id":"direct123","date":"2026-05-27"}]"""
        server.enqueue(MockResponse().setResponseCode(409))
        server.enqueue(MockResponse().setResponseCode(200).setBody(listBody))
        server.enqueue(MockResponse().setResponseCode(200))

        val result = client.sync("key", "2026-05-27", 75.0f, null, null, true)

        assertThat(result.isSuccess).isTrue()
        server.takeRequest() // POST
        server.takeRequest() // GET
        val put = server.takeRequest()
        assertThat(put.path).contains("direct123")
    }

    @Test
    fun sync_409_overrideEnabled_putReturnsSuccess() = runTest {
        val listBody = """{"body_measurements":[{"id":"abc123","date":"2026-05-27"}]}"""
        server.enqueue(MockResponse().setResponseCode(409))
        server.enqueue(MockResponse().setResponseCode(200).setBody(listBody))
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
