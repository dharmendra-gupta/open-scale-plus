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

import com.google.common.truth.Truth.assertThat
import com.health.openscale.core.data.Measurement
import com.health.openscale.core.data.MeasurementType
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
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class StravaSyncManagerTest {

    private val server = MockWebServer()
    private lateinit var apiClient: StravaApiClient

    @Before
    fun setUp() {
        server.start()
        apiClient = StravaApiClient(OkHttpClient()).also {
            it.baseUrl = server.url("").toString().trimEnd('/')
        }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun makeSettings(
        accessToken: String = "valid-access-token",
        refreshToken: String = "valid-refresh-token",
        clientId: String = "client-id",
        clientSecret: String = "client-secret",
        expiresAt: Long = Long.MAX_VALUE,  // far future = not expired
        athleteName: String = "Test Athlete",
    ): StravaSettings = object : StravaSettings {
        override fun stravaClientId(userId: Int): Flow<String> = flowOf(clientId)
        override fun stravaClientSecret(userId: Int): Flow<String> = flowOf(clientSecret)
        override fun stravaAccessToken(userId: Int): Flow<String> = flowOf(accessToken)
        override fun stravaRefreshToken(userId: Int): Flow<String> = flowOf(refreshToken)
        override fun stravaTokenExpiresAt(userId: Int): Flow<Long> = flowOf(expiresAt)
        override fun stravaAthleteName(userId: Int): Flow<String> = flowOf(athleteName)

        override suspend fun setStravaClientId(userId: Int, id: String) {}
        override suspend fun setStravaClientSecret(userId: Int, secret: String) {}
        override suspend fun setStravaAccessToken(userId: Int, token: String) {}
        override suspend fun setStravaRefreshToken(userId: Int, token: String) {}
        override suspend fun setStravaTokenExpiresAt(userId: Int, expiresAt: Long) {}
        override suspend fun setStravaAthleteName(userId: Int, name: String) {}
        override suspend fun clearStravaAuth(userId: Int) {}
    }

    private fun makeManager(settings: StravaSettings = makeSettings()): StravaSyncManager =
        StravaSyncManager(apiClient, settings)

    private fun measurementOf(vararg values: MeasurementValueWithType): MeasurementWithValues =
        MeasurementWithValues(
            measurement = Measurement(id = 1, userId = 1, timestamp = 1748246400000L),
            values = values.toList(),
        )

    private fun weightEntry(value: Float, unit: UnitType = UnitType.KG) = MeasurementValueWithType(
        value = MeasurementValue(measurementId = 1, typeId = MeasurementTypeKey.WEIGHT.id, floatValue = value),
        type  = MeasurementType(id = MeasurementTypeKey.WEIGHT.id, key = MeasurementTypeKey.WEIGHT, unit = unit),
    )

    private fun fatEntry(value: Float) = MeasurementValueWithType(
        value = MeasurementValue(measurementId = 1, typeId = MeasurementTypeKey.BODY_FAT.id, floatValue = value),
        type  = MeasurementType(id = MeasurementTypeKey.BODY_FAT.id, key = MeasurementTypeKey.BODY_FAT, unit = UnitType.PERCENT),
    )

    private val tokenJson = """
        {"access_token":"new-access","refresh_token":"new-refresh","expires_at":9999999999}
    """.trimIndent()

    // -------------------------------------------------------------------------
    // Guard: blank access token
    // -------------------------------------------------------------------------

    @Test
    fun fireAndForget_blankAccessToken_doesNotSendRequest() = runTest {
        val manager = makeManager(makeSettings(accessToken = ""))
        manager.fireAndForget(measurementOf(weightEntry(70f)))
        delay(300)
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test
    fun fireAndForget_whitespaceAccessToken_doesNotSendRequest() = runTest {
        val manager = makeManager(makeSettings(accessToken = "   "))
        manager.fireAndForget(measurementOf(weightEntry(70f)))
        delay(300)
        assertThat(server.requestCount).isEqualTo(0)
    }

    // -------------------------------------------------------------------------
    // Guard: no weight type
    // -------------------------------------------------------------------------

    @Test
    fun fireAndForget_noWeightType_doesNotSendRequest() = runTest {
        val manager = makeManager()
        manager.fireAndForget(measurementOf(fatEntry(20f)))
        delay(300)
        assertThat(server.requestCount).isEqualTo(0)
    }

    // -------------------------------------------------------------------------
    // Sync — valid non-expired token
    // -------------------------------------------------------------------------

    @Test
    fun fireAndForget_validToken_sendsPutRequest() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val manager = makeManager()
        manager.fireAndForget(measurementOf(weightEntry(75f)))
        val request = withContext(Dispatchers.IO) { server.takeRequest(2, TimeUnit.SECONDS) }
        assertThat(request).isNotNull()
        assertThat(request!!.method).isEqualTo("PUT")
    }

    @Test
    fun fireAndForget_sendsBearerToken() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val manager = makeManager(makeSettings(accessToken = "my-token"))
        manager.fireAndForget(measurementOf(weightEntry(75f)))
        val request = withContext(Dispatchers.IO) { server.takeRequest(2, TimeUnit.SECONDS) }
        assertThat(request!!.getHeader("Authorization")).isEqualTo("Bearer my-token")
    }

    // -------------------------------------------------------------------------
    // Weight unit conversion
    // -------------------------------------------------------------------------

    @Test
    fun fireAndForget_weightKg_sentAsIs() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val manager = makeManager()
        manager.fireAndForget(measurementOf(weightEntry(80.0f, UnitType.KG)))
        val body = withContext(Dispatchers.IO) {
            server.takeRequest(2, TimeUnit.SECONDS)!!.body.readUtf8()
        }
        assertThat(body).contains("weight=")
        assertThat(body).contains("80.0")
    }

    @Test
    fun fireAndForget_weightLb_convertedToKg() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val manager = makeManager()
        manager.fireAndForget(measurementOf(weightEntry(176.37f, UnitType.LB)))
        val body = withContext(Dispatchers.IO) {
            server.takeRequest(2, TimeUnit.SECONDS)!!.body.readUtf8()
        }
        // 176.37 lb / 2.20462 ≈ 79.997 kg — expect body to contain value near 80
        assertThat(body).contains("weight=")
        val weightValue = body.substringAfter("weight=").toFloatOrNull() ?: 0f
        assertThat(weightValue).isWithin(0.2f).of(80.0f)
    }

    @Test
    fun fireAndForget_weightSt_convertedToKg() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val manager = makeManager()
        manager.fireAndForget(measurementOf(weightEntry(12.594f, UnitType.ST)))
        val body = withContext(Dispatchers.IO) {
            server.takeRequest(2, TimeUnit.SECONDS)!!.body.readUtf8()
        }
        // 12.594 st / 0.157473 ≈ 79.97 kg
        assertThat(body).contains("weight=")
        val weightValue = body.substringAfter("weight=").toFloatOrNull() ?: 0f
        assertThat(weightValue).isWithin(0.2f).of(80.0f)
    }

    // -------------------------------------------------------------------------
    // Token refresh — expired token
    // -------------------------------------------------------------------------

    @Test
    fun fireAndForget_expiredToken_refreshesThenSyncs() = runTest {
        // First request: token refresh → new token
        server.enqueue(MockResponse().setResponseCode(200).setBody(tokenJson))
        // Second request: PUT /athlete
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        val manager = makeManager(makeSettings(expiresAt = 1L)) // epoch 1 = always expired
        manager.fireAndForget(measurementOf(weightEntry(70f)))

        val first = withContext(Dispatchers.IO) { server.takeRequest(2, TimeUnit.SECONDS) }
        val second = withContext(Dispatchers.IO) { server.takeRequest(2, TimeUnit.SECONDS) }
        assertThat(first).isNotNull()
        assertThat(second).isNotNull()
        assertThat(first!!.path).contains("/oauth/token")
        assertThat(second!!.method).isEqualTo("PUT")
    }

    @Test
    fun fireAndForget_expiredToken_usesNewAccessToken() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(tokenJson))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        val manager = makeManager(makeSettings(expiresAt = 1L))
        manager.fireAndForget(measurementOf(weightEntry(70f)))

        withContext(Dispatchers.IO) { server.takeRequest(2, TimeUnit.SECONDS) } // refresh
        val syncRequest = withContext(Dispatchers.IO) { server.takeRequest(2, TimeUnit.SECONDS) }
        assertThat(syncRequest!!.getHeader("Authorization")).isEqualTo("Bearer new-access")
    }

    @Test
    fun fireAndForget_expiredToken_missingRefreshToken_doesNotSendSyncRequest() = runTest {
        val manager = makeManager(makeSettings(expiresAt = 1L, refreshToken = ""))
        manager.fireAndForget(measurementOf(weightEntry(70f)))
        delay(300)
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test
    fun fireAndForget_expiredToken_missingClientId_doesNotSendSyncRequest() = runTest {
        val manager = makeManager(makeSettings(expiresAt = 1L, clientId = ""))
        manager.fireAndForget(measurementOf(weightEntry(70f)))
        delay(300)
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test
    fun fireAndForget_expiredToken_refreshFails_doesNotSendSyncRequest() = runTest {
        server.enqueue(MockResponse().setResponseCode(401)) // refresh fails
        val manager = makeManager(makeSettings(expiresAt = 1L))
        manager.fireAndForget(measurementOf(weightEntry(70f)))
        val refreshRequest = withContext(Dispatchers.IO) { server.takeRequest(2, TimeUnit.SECONDS) }
        assertThat(refreshRequest).isNotNull()
        // No second request (sync) should be made
        val syncRequest = server.takeRequest(300, TimeUnit.MILLISECONDS)
        assertThat(syncRequest).isNull()
    }

    // -------------------------------------------------------------------------
    // Error resilience
    // -------------------------------------------------------------------------

    @Test
    fun fireAndForget_serverError_doesNotCrash() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        val manager = makeManager()
        manager.fireAndForget(measurementOf(weightEntry(75f)))
        val request = withContext(Dispatchers.IO) { server.takeRequest(2, TimeUnit.SECONDS) }
        assertThat(request).isNotNull()
        // If we get here without an exception, the test passes
    }

    // -------------------------------------------------------------------------
    // exchangeAndSaveTokens
    // -------------------------------------------------------------------------

    @Test
    fun exchangeAndSaveTokens_success_returnsSuccess() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(tokenJson))
        val manager = makeManager()
        val result = manager.exchangeAndSaveTokens(1, "auth-code")
        assertThat(result.isSuccess).isTrue()
    }

    @Test
    fun exchangeAndSaveTokens_apiError_returnsFailure() = runTest {
        server.enqueue(MockResponse().setResponseCode(400))
        val manager = makeManager()
        val result = manager.exchangeAndSaveTokens(1, "bad-code")
        assertThat(result.isFailure).isTrue()
    }

    // -------------------------------------------------------------------------
    // testConnection
    // -------------------------------------------------------------------------

    @Test
    fun testConnection_200_returnsSuccess() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val result = makeManager().testConnection("valid-token")
        assertThat(result.isSuccess).isTrue()
    }

    @Test
    fun testConnection_401_returnsFailure() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        val result = makeManager().testConnection("bad-token")
        assertThat(result.isFailure).isTrue()
    }
}
