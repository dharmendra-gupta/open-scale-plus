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
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class HevySyncManagerTest {

    private val server = MockWebServer()
    private lateinit var apiClient: HevyApiClient

    @Before
    fun setUp() {
        server.start()
        apiClient = HevyApiClient(OkHttpClient()).also {
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
        apiKey: String = "test-api-key",
        overrideEnabled: Boolean = false,
    ): HevySettings = object : HevySettings {
        override fun hevyApiKey(userId: Int): Flow<String> = flowOf(apiKey)
        override fun hevyOverrideEnabled(userId: Int): Flow<Boolean> = flowOf(overrideEnabled)
        override suspend fun setHevyApiKey(userId: Int, apiKey: String) {}
        override suspend fun setHevyOverrideEnabled(userId: Int, enabled: Boolean) {}
    }

    private fun makeManager(settings: HevySettings = makeSettings()): HevySyncManager =
        HevySyncManager(apiClient, settings)

    private fun measurementOf(vararg values: MeasurementValueWithType): MeasurementWithValues =
        MeasurementWithValues(
            measurement = Measurement(id = 1, userId = 1, timestamp = 1748246400000L),
            values = values.toList(),
        )

    private fun weightEntry(value: Float, unit: UnitType) = MeasurementValueWithType(
        value = MeasurementValue(measurementId = 1, typeId = MeasurementTypeKey.WEIGHT.id, floatValue = value),
        type = MeasurementType(id = MeasurementTypeKey.WEIGHT.id, key = MeasurementTypeKey.WEIGHT, unit = unit),
    )

    private fun fatEntry(value: Float, unit: UnitType) = MeasurementValueWithType(
        value = MeasurementValue(measurementId = 1, typeId = MeasurementTypeKey.BODY_FAT.id, floatValue = value),
        type = MeasurementType(id = MeasurementTypeKey.BODY_FAT.id, key = MeasurementTypeKey.BODY_FAT, unit = unit),
    )

    private fun lbmEntry(value: Float, unit: UnitType) = MeasurementValueWithType(
        value = MeasurementValue(measurementId = 1, typeId = MeasurementTypeKey.LBM.id, floatValue = value),
        type = MeasurementType(id = MeasurementTypeKey.LBM.id, key = MeasurementTypeKey.LBM, unit = unit),
    )

    private fun waterEntry(value: Float) = MeasurementValueWithType(
        value = MeasurementValue(measurementId = 1, typeId = MeasurementTypeKey.WATER.id, floatValue = value),
        type = MeasurementType(id = MeasurementTypeKey.WATER.id, key = MeasurementTypeKey.WATER, unit = UnitType.PERCENT),
    )

    // -------------------------------------------------------------------------
    // Guard: blank API key / no relevant types
    // -------------------------------------------------------------------------

    @Test
    fun fireAndForget_blankApiKey_doesNotSendRequest() = runTest {
        val manager = makeManager(makeSettings(apiKey = ""))
        manager.fireAndForget(measurementOf(weightEntry(75f, UnitType.KG)))
        delay(300)
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test
    fun fireAndForget_whitespaceApiKey_doesNotSendRequest() = runTest {
        val manager = makeManager(makeSettings(apiKey = "   "))
        manager.fireAndForget(measurementOf(weightEntry(75f, UnitType.KG)))
        delay(300)
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test
    fun fireAndForget_noRelevantTypes_doesNotSendRequest() = runTest {
        val manager = makeManager()
        manager.fireAndForget(measurementOf(waterEntry(55f)))
        delay(300)
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test
    fun fireAndForget_bodyFatInKgNotPercent_skippedSoNoRequest() = runTest {
        val manager = makeManager()
        // body fat stored in KG unit is meaningless to Hevy, treated as null
        manager.fireAndForget(measurementOf(fatEntry(15f, UnitType.KG)))
        delay(300)
        assertThat(server.requestCount).isEqualTo(0)
    }

    // -------------------------------------------------------------------------
    // Request is sent: api-key header and POST method
    // -------------------------------------------------------------------------

    @Test
    fun fireAndForget_validConfig_sendsPostRequest() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        val manager = makeManager()
        manager.fireAndForget(measurementOf(weightEntry(75f, UnitType.KG)))
        val request = withContext(Dispatchers.IO) { server.takeRequest(2, TimeUnit.SECONDS) }
        assertThat(request).isNotNull()
        assertThat(request!!.method).isEqualTo("POST")
    }

    @Test
    fun fireAndForget_sendsApiKeyHeader() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        val manager = makeManager(makeSettings(apiKey = "my-hevy-key"))
        manager.fireAndForget(measurementOf(weightEntry(75f, UnitType.KG)))
        val request = withContext(Dispatchers.IO) { server.takeRequest(2, TimeUnit.SECONDS) }
        assertThat(request!!.getHeader("api-key")).isEqualTo("my-hevy-key")
    }

    // -------------------------------------------------------------------------
    // Weight unit conversion
    // -------------------------------------------------------------------------

    @Test
    fun fireAndForget_weightKg_sentAsIs() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        val manager = makeManager()
        manager.fireAndForget(measurementOf(weightEntry(80.0f, UnitType.KG)))
        val body = withContext(Dispatchers.IO) {
            JSONObject(server.takeRequest(2, TimeUnit.SECONDS)!!.body.readUtf8())
        }
        assertThat(body.getDouble("weight_kg")).isWithin(0.01).of(80.0)
    }

    @Test
    fun fireAndForget_weightLb_convertedToKg() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        val manager = makeManager()
        manager.fireAndForget(measurementOf(weightEntry(176.37f, UnitType.LB)))
        val body = withContext(Dispatchers.IO) {
            JSONObject(server.takeRequest(2, TimeUnit.SECONDS)!!.body.readUtf8())
        }
        // 176.37 lb / 2.20462 ≈ 79.997 kg
        assertThat(body.getDouble("weight_kg")).isWithin(0.1).of(80.0)
    }

    @Test
    fun fireAndForget_weightSt_convertedToKg() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        val manager = makeManager()
        manager.fireAndForget(measurementOf(weightEntry(12.594f, UnitType.ST)))
        val body = withContext(Dispatchers.IO) {
            JSONObject(server.takeRequest(2, TimeUnit.SECONDS)!!.body.readUtf8())
        }
        // 12.594 st / 0.157473 ≈ 79.974 kg
        assertThat(body.getDouble("weight_kg")).isWithin(0.1).of(80.0)
    }

    // -------------------------------------------------------------------------
    // Body fat
    // -------------------------------------------------------------------------

    @Test
    fun fireAndForget_bodyFatPercent_includedInPayload() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        val manager = makeManager()
        manager.fireAndForget(measurementOf(weightEntry(75f, UnitType.KG), fatEntry(18.5f, UnitType.PERCENT)))
        val body = withContext(Dispatchers.IO) {
            JSONObject(server.takeRequest(2, TimeUnit.SECONDS)!!.body.readUtf8())
        }
        assertThat(body.getDouble("fat_percent")).isWithin(0.01).of(18.5)
    }

    @Test
    fun fireAndForget_bodyFatPercent_withoutWeight_sendsRequest() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        val manager = makeManager()
        manager.fireAndForget(measurementOf(fatEntry(18.5f, UnitType.PERCENT)))
        val request = withContext(Dispatchers.IO) { server.takeRequest(2, TimeUnit.SECONDS) }
        assertThat(request).isNotNull()
    }

    // -------------------------------------------------------------------------
    // LBM unit conversion
    // -------------------------------------------------------------------------

    @Test
    fun fireAndForget_lbmKg_includedInPayload() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        val manager = makeManager()
        manager.fireAndForget(measurementOf(lbmEntry(62.5f, UnitType.KG)))
        val body = withContext(Dispatchers.IO) {
            JSONObject(server.takeRequest(2, TimeUnit.SECONDS)!!.body.readUtf8())
        }
        assertThat(body.getDouble("lean_mass_kg")).isWithin(0.01).of(62.5)
    }

    @Test
    fun fireAndForget_lbmLb_convertedToKg() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        val manager = makeManager()
        manager.fireAndForget(measurementOf(lbmEntry(137.79f, UnitType.LB)))
        val body = withContext(Dispatchers.IO) {
            JSONObject(server.takeRequest(2, TimeUnit.SECONDS)!!.body.readUtf8())
        }
        // 137.79 lb / 2.20462 ≈ 62.5 kg
        assertThat(body.getDouble("lean_mass_kg")).isWithin(0.1).of(62.5)
    }

    @Test
    fun fireAndForget_lbmSt_convertedToKg() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        val manager = makeManager()
        manager.fireAndForget(measurementOf(lbmEntry(9.843f, UnitType.ST)))
        val body = withContext(Dispatchers.IO) {
            JSONObject(server.takeRequest(2, TimeUnit.SECONDS)!!.body.readUtf8())
        }
        // 9.843 st / 0.157473 ≈ 62.5 kg
        assertThat(body.getDouble("lean_mass_kg")).isWithin(0.1).of(62.5)
    }

    // -------------------------------------------------------------------------
    // Partial measurements: only present fields appear in payload
    // -------------------------------------------------------------------------

    @Test
    fun fireAndForget_weightOnly_payloadOmitsBodyFatAndLbm() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        val manager = makeManager()
        manager.fireAndForget(measurementOf(weightEntry(75f, UnitType.KG)))
        val body = withContext(Dispatchers.IO) {
            JSONObject(server.takeRequest(2, TimeUnit.SECONDS)!!.body.readUtf8())
        }
        assertThat(body.has("weight_kg")).isTrue()
        assertThat(body.has("fat_percent")).isFalse()
        assertThat(body.has("lean_mass_kg")).isFalse()
    }

    @Test
    fun fireAndForget_fatOnly_payloadOmitsWeightAndLbm() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        val manager = makeManager()
        manager.fireAndForget(measurementOf(fatEntry(20f, UnitType.PERCENT)))
        val body = withContext(Dispatchers.IO) {
            JSONObject(server.takeRequest(2, TimeUnit.SECONDS)!!.body.readUtf8())
        }
        assertThat(body.has("fat_percent")).isTrue()
        assertThat(body.has("weight_kg")).isFalse()
        assertThat(body.has("lean_mass_kg")).isFalse()
    }

    @Test
    fun fireAndForget_allThreeFields_allIncludedInPayload() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        val manager = makeManager()
        manager.fireAndForget(measurementOf(
            weightEntry(80f, UnitType.KG),
            fatEntry(18f, UnitType.PERCENT),
            lbmEntry(65.6f, UnitType.KG),
        ))
        val body = withContext(Dispatchers.IO) {
            JSONObject(server.takeRequest(2, TimeUnit.SECONDS)!!.body.readUtf8())
        }
        assertThat(body.has("weight_kg")).isTrue()
        assertThat(body.has("fat_percent")).isTrue()
        assertThat(body.has("lean_mass_kg")).isTrue()
    }

    // -------------------------------------------------------------------------
    // Error resilience: server errors don't crash the app
    // -------------------------------------------------------------------------

    @Test
    fun fireAndForget_serverError_doesNotCrash() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        val manager = makeManager()
        manager.fireAndForget(measurementOf(weightEntry(75f, UnitType.KG)))
        // just verify the request was sent and no exception propagates
        val request = withContext(Dispatchers.IO) { server.takeRequest(2, TimeUnit.SECONDS) }
        assertThat(request).isNotNull()
    }
}
