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
package com.health.openscale.core.sync.webhook

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class WebhookSyncClientTest {

    private val server = MockWebServer()
    private lateinit var client: WebhookSyncClient

    @Before
    fun setUp() {
        server.start()
        client = WebhookSyncClient(OkHttpClient())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun send_2xxResponse_returnsSuccess() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        val url = server.url("/webhook").toString()
        val result = client.send(url, emptyMap(), """{"weight": 75.5}""")
        assertThat(result.isSuccess).isTrue()
    }

    @Test
    fun send_customHeaders_areSentToServer() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        val url = server.url("/webhook").toString()
        client.send(url, mapOf("x-api-key" to "secret123"), """{}""")
        val request = server.takeRequest()
        assertThat(request.getHeader("x-api-key")).isEqualTo("secret123")
    }

    @Test
    fun send_contentTypeIsJson() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        val url = server.url("/webhook").toString()
        client.send(url, emptyMap(), """{"weight": 75.5}""")
        val request = server.takeRequest()
        assertThat(request.getHeader("Content-Type")).contains("application/json")
    }

    @Test
    fun send_postMethod_usedForAllRequests() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        val url = server.url("/webhook").toString()
        client.send(url, emptyMap(), """{}""")
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
    }

    @Test
    fun send_4xxResponse_returnsFailure() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        val url = server.url("/webhook").toString()
        val result = client.send(url, emptyMap(), """{}""")
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("401")
    }

    @Test
    fun send_5xxResponse_returnsFailure() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        val url = server.url("/webhook").toString()
        val result = client.send(url, emptyMap(), """{}""")
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("500")
    }

    @Test
    fun send_bodyIsPostedAsIs() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        val url = server.url("/webhook").toString()
        val payload = """{"weight":75.5,"fat":18.2}"""
        client.send(url, emptyMap(), payload)
        val request = server.takeRequest()
        assertThat(request.body.readUtf8()).isEqualTo(payload)
    }

    // -------------------------------------------------------------------------
    // sendTest — sandbox/test console: non-2xx is captured as data, not failure
    // -------------------------------------------------------------------------

    @Test
    fun sendTest_200Response_returnsSuccessWithFields() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true}"""))
        val url = server.url("/webhook").toString()
        val result = client.sendTest(url, emptyMap(), """{"weight": 75.5}""")
        assertThat(result.isSuccess).isTrue()
        val response = result.getOrThrow()
        assertThat(response.statusCode).isEqualTo(200)
        assertThat(response.body).isEqualTo("""{"ok":true}""")
    }

    @Test
    fun sendTest_404Response_returnsSuccessNotFailure() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("not found"))
        val url = server.url("/webhook").toString()
        val result = client.sendTest(url, emptyMap(), """{}""")
        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrThrow().statusCode).isEqualTo(404)
        assertThat(result.getOrThrow().body).isEqualTo("not found")
    }

    @Test
    fun sendTest_500Response_returnsSuccessNotFailure() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))
        val url = server.url("/webhook").toString()
        val result = client.sendTest(url, emptyMap(), """{}""")
        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrThrow().statusCode).isEqualTo(500)
    }

    @Test
    fun sendTest_responseHeaders_areCaptured() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setHeader("X-Custom", "abc123"))
        val url = server.url("/webhook").toString()
        val result = client.sendTest(url, emptyMap(), """{}""")
        assertThat(result.getOrThrow().headers["X-Custom"]).isEqualTo("abc123")
    }

    @Test
    fun sendTest_customRequestHeaders_areSentToServer() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        val url = server.url("/webhook").toString()
        client.sendTest(url, mapOf("x-api-key" to "secret123"), """{}""")
        val request = server.takeRequest()
        assertThat(request.getHeader("x-api-key")).isEqualTo("secret123")
    }

    @Test
    fun sendTest_connectionFailure_returnsFailure() = runTest {
        // Port 1 is a reserved/privileged port — nothing listens there, so this
        // triggers a real connection-refused without any DNS or network dependency.
        val result = client.sendTest("http://127.0.0.1:1/webhook", emptyMap(), """{}""")
        assertThat(result.isFailure).isTrue()
    }
}
