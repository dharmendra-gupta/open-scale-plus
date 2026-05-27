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
import com.health.openscale.core.data.Measurement
import com.health.openscale.core.data.MeasurementType
import com.health.openscale.core.data.MeasurementTypeIcon
import com.health.openscale.core.data.MeasurementTypeKey
import com.health.openscale.core.data.MeasurementValue
import com.health.openscale.core.data.UnitType
import com.health.openscale.core.model.MeasurementValueWithType
import com.health.openscale.core.model.MeasurementWithValues
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class WebhookSyncManagerTest {

    private val server = MockWebServer()

    private fun makeMeasurement(): MeasurementWithValues = MeasurementWithValues(
        measurement = Measurement(id = 1, userId = 1, timestamp = 1748246400000L),
        values = listOf(
            MeasurementValueWithType(
                value = MeasurementValue(measurementId = 1, typeId = MeasurementTypeKey.WEIGHT.id, floatValue = 75.5f),
                type = MeasurementType(
                    id = MeasurementTypeKey.WEIGHT.id,
                    key = MeasurementTypeKey.WEIGHT,
                    name = "Weight",
                    color = 0,
                    icon = MeasurementTypeIcon.IC_DEFAULT,
                    unit = UnitType.NONE,
                ),
            )
        ),
    )

    private fun makeSettings(
        url: String,
        schema: String = """{"weight": "WEIGHT"}""",
        headers: String = "",
    ): WebhookSettings = object : WebhookSettings {
        override fun webhookUrl(userId: Int): Flow<String> = flowOf(url)
        override fun webhookPayloadSchema(userId: Int): Flow<String> = flowOf(schema)
        override fun webhookAuthHeaders(userId: Int): Flow<String> = flowOf(headers)
        override suspend fun setWebhookUrl(userId: Int, url: String) {}
        override suspend fun setWebhookAuthHeaders(userId: Int, json: String) {}
        override suspend fun setWebhookPayloadSchema(userId: Int, json: String) {}
    }

    @Before
    fun setUp() {
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun fireAndForget_blankUrl_doesNotSendRequest() = runTest {
        val settings = makeSettings(url = "")
        val client = WebhookSyncClient(OkHttpClient())
        val manager = WebhookSyncManager(client, settings)

        manager.fireAndForget(makeMeasurement())
        delay(200)

        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test
    fun fireAndForget_blankSchema_doesNotSendRequest() = runTest {
        val url = server.url("/webhook").toString()
        val settings = makeSettings(url = url, schema = "")
        val client = WebhookSyncClient(OkHttpClient())
        val manager = WebhookSyncManager(client, settings)

        manager.fireAndForget(makeMeasurement())
        delay(200)

        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test
    fun fireAndForget_validConfig_sendsPostRequest() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        val url = server.url("/webhook").toString()
        val settings = makeSettings(url = url)
        val client = WebhookSyncClient(OkHttpClient())
        val manager = WebhookSyncManager(client, settings)

        manager.fireAndForget(makeMeasurement())
        val request = withContext(Dispatchers.IO) { server.takeRequest() }

        assertThat(server.requestCount).isEqualTo(1)
        assertThat(request.method).isEqualTo("POST")
    }

    @Test
    fun fireAndForget_validConfig_payloadContainsResolvedWeight() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        val url = server.url("/webhook").toString()
        val settings = makeSettings(url = url, schema = """{"weight": "WEIGHT"}""")
        val client = WebhookSyncClient(OkHttpClient())
        val manager = WebhookSyncManager(client, settings)

        manager.fireAndForget(makeMeasurement())
        delay(500)

        val body = server.takeRequest().body.readUtf8()
        assertThat(body).contains("75.5")
    }

    @Test
    fun fireAndForget_customHeaders_areSent() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        val url = server.url("/webhook").toString()
        val settings = makeSettings(
            url = url,
            headers = """{"x-api-key": "secret"}""",
        )
        val client = WebhookSyncClient(OkHttpClient())
        val manager = WebhookSyncManager(client, settings)

        manager.fireAndForget(makeMeasurement())
        delay(500)

        val request = server.takeRequest()
        assertThat(request.getHeader("x-api-key")).isEqualTo("secret")
    }

    @Test
    fun fireAndForget_serverError_invokesOnError() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        val url = server.url("/webhook").toString()
        val settings = makeSettings(url = url)
        val client = WebhookSyncClient(OkHttpClient())
        val manager = WebhookSyncManager(client, settings)

        val latch = CountDownLatch(1)
        var errorMessage: String? = null
        manager.fireAndForget(makeMeasurement()) { msg ->
            errorMessage = msg
            latch.countDown()
        }
        withContext(Dispatchers.IO) { latch.await(5, TimeUnit.SECONDS) }

        assertThat(errorMessage).isNotNull()
        assertThat(errorMessage).contains("500")
    }

    @Test
    fun fireAndForget_successResponse_doesNotInvokeOnError() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        val url = server.url("/webhook").toString()
        val settings = makeSettings(url = url)
        val client = WebhookSyncClient(OkHttpClient())
        val manager = WebhookSyncManager(client, settings)

        var errorCalled = false
        manager.fireAndForget(makeMeasurement()) { errorCalled = true }
        delay(500)

        assertThat(errorCalled).isFalse()
    }
}
