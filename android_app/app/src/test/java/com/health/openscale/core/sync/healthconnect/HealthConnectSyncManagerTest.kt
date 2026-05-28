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
package com.health.openscale.core.sync.healthconnect

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
import org.junit.Test
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class HealthConnectSyncManagerTest {

    // -------------------------------------------------------------------------
    // Fake API
    // -------------------------------------------------------------------------

    data class InsertCall(val method: String, val value: Double, val timestamp: Instant)

    class FakeHealthConnectApi(
        private val failMethods: Set<String> = emptySet(),
    ) : HealthConnectApi {
        val calls = CopyOnWriteArrayList<InsertCall>()
        private val queue = LinkedBlockingQueue<InsertCall>()

        /** Block the calling thread until [n] calls are recorded, or [timeoutMs] elapses. */
        fun awaitCalls(n: Int = 1, timeoutMs: Long = 2000): List<InsertCall> {
            val deadline = System.currentTimeMillis() + timeoutMs
            while (calls.size < n && System.currentTimeMillis() < deadline) Thread.sleep(10)
            return calls.toList()
        }

        override suspend fun insertWeight(weightKg: Double, timestamp: Instant) =
            record("weight", weightKg, timestamp)

        override suspend fun insertBodyFat(percentage: Double, timestamp: Instant) =
            record("bodyFat", percentage, timestamp)

        override suspend fun insertLeanBodyMass(lbmKg: Double, timestamp: Instant) =
            record("lbm", lbmKg, timestamp)

        override suspend fun insertBoneMass(boneKg: Double, timestamp: Instant) =
            record("bone", boneKg, timestamp)

        private fun record(method: String, value: Double, ts: Instant): Result<Unit> {
            val call = InsertCall(method, value, ts)
            calls.add(call)
            queue.put(call)
            return if (method in failMethods) Result.failure(RuntimeException("Fake $method failure"))
            else Result.success(Unit)
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun makeSettings(enabled: Boolean = true): HealthConnectSettings =
        object : HealthConnectSettings {
            override fun healthConnectEnabled(userId: Int): Flow<Boolean> = flowOf(enabled)
            override suspend fun setHealthConnectEnabled(userId: Int, enabled: Boolean) {}
        }

    private fun makeManager(
        enabled: Boolean = true,
        failMethods: Set<String> = emptySet(),
    ): Pair<HealthConnectSyncManager, FakeHealthConnectApi> {
        val api = FakeHealthConnectApi(failMethods)
        val mgr = HealthConnectSyncManager(api, makeSettings(enabled))
        return mgr to api
    }

    private val baseTimestamp = 1748246400000L

    private fun measurementOf(vararg values: MeasurementValueWithType) = MeasurementWithValues(
        measurement = Measurement(id = 1, userId = 1, timestamp = baseTimestamp),
        values = values.toList(),
    )

    private fun entry(key: MeasurementTypeKey, value: Float, unit: UnitType) = MeasurementValueWithType(
        value = MeasurementValue(measurementId = 1, typeId = key.id, floatValue = value),
        type  = MeasurementType(id = key.id, key = key, unit = unit),
    )

    // -------------------------------------------------------------------------
    // Disabled guard
    // -------------------------------------------------------------------------

    @Test
    fun fireAndForget_healthConnectDisabled_noClientCalls() = runTest {
        val (manager, api) = makeManager(enabled = false)
        manager.fireAndForget(measurementOf(entry(MeasurementTypeKey.WEIGHT, 70f, UnitType.KG)))
        delay(300)
        assertThat(api.calls).isEmpty()
    }

    // -------------------------------------------------------------------------
    // Field routing
    // -------------------------------------------------------------------------

    @Test
    fun fireAndForget_weightOnly_callsInsertWeight() = runTest {
        val (manager, api) = makeManager()
        manager.fireAndForget(measurementOf(entry(MeasurementTypeKey.WEIGHT, 75f, UnitType.KG)))
        val calls = withContext(Dispatchers.IO) { api.awaitCalls(1) }
        assertThat(calls.map { it.method }).containsExactly("weight")
    }

    @Test
    fun fireAndForget_bodyFatOnly_callsInsertBodyFat() = runTest {
        val (manager, api) = makeManager()
        manager.fireAndForget(measurementOf(entry(MeasurementTypeKey.BODY_FAT, 20f, UnitType.PERCENT)))
        val calls = withContext(Dispatchers.IO) { api.awaitCalls(1) }
        assertThat(calls.map { it.method }).containsExactly("bodyFat")
    }

    @Test
    fun fireAndForget_lbmOnly_callsInsertLeanBodyMass() = runTest {
        val (manager, api) = makeManager()
        manager.fireAndForget(measurementOf(entry(MeasurementTypeKey.LBM, 60f, UnitType.KG)))
        val calls = withContext(Dispatchers.IO) { api.awaitCalls(1) }
        assertThat(calls.map { it.method }).containsExactly("lbm")
    }

    @Test
    fun fireAndForget_boneOnly_callsInsertBoneMass() = runTest {
        val (manager, api) = makeManager()
        manager.fireAndForget(measurementOf(entry(MeasurementTypeKey.BONE, 3.5f, UnitType.KG)))
        val calls = withContext(Dispatchers.IO) { api.awaitCalls(1) }
        assertThat(calls.map { it.method }).containsExactly("bone")
    }

    @Test
    fun fireAndForget_allFourFields_callsAllFourMethods() = runTest {
        val (manager, api) = makeManager()
        manager.fireAndForget(measurementOf(
            entry(MeasurementTypeKey.WEIGHT,   75f,  UnitType.KG),
            entry(MeasurementTypeKey.BODY_FAT, 20f,  UnitType.PERCENT),
            entry(MeasurementTypeKey.LBM,      60f,  UnitType.KG),
            entry(MeasurementTypeKey.BONE,     3.5f, UnitType.KG),
        ))
        val calls = withContext(Dispatchers.IO) { api.awaitCalls(4) }
        assertThat(calls.map { it.method }).containsExactlyElementsIn(
            listOf("weight", "bodyFat", "lbm", "bone")
        )
    }

    @Test
    fun fireAndForget_noMatchingFields_noClientCalls() = runTest {
        val (manager, api) = makeManager()
        manager.fireAndForget(measurementOf(entry(MeasurementTypeKey.WATER, 55f, UnitType.PERCENT)))
        delay(300)
        assertThat(api.calls).isEmpty()
    }

    // -------------------------------------------------------------------------
    // Unit conversion
    // -------------------------------------------------------------------------

    @Test
    fun fireAndForget_weightKg_sentAsIs() = runTest {
        val (manager, api) = makeManager()
        manager.fireAndForget(measurementOf(entry(MeasurementTypeKey.WEIGHT, 80f, UnitType.KG)))
        val calls = withContext(Dispatchers.IO) { api.awaitCalls(1) }
        assertThat(calls.first { it.method == "weight" }.value).isWithin(0.001).of(80.0)
    }

    @Test
    fun fireAndForget_weightLb_convertedToKg() = runTest {
        val (manager, api) = makeManager()
        // 176.37 lb ≈ 80 kg
        manager.fireAndForget(measurementOf(entry(MeasurementTypeKey.WEIGHT, 176.37f, UnitType.LB)))
        val calls = withContext(Dispatchers.IO) { api.awaitCalls(1) }
        assertThat(calls.first { it.method == "weight" }.value).isWithin(0.2).of(80.0)
    }

    @Test
    fun fireAndForget_weightSt_convertedToKg() = runTest {
        val (manager, api) = makeManager()
        // 12.594 st ≈ 80 kg
        manager.fireAndForget(measurementOf(entry(MeasurementTypeKey.WEIGHT, 12.594f, UnitType.ST)))
        val calls = withContext(Dispatchers.IO) { api.awaitCalls(1) }
        assertThat(calls.first { it.method == "weight" }.value).isWithin(0.2).of(80.0)
    }

    @Test
    fun fireAndForget_boneLb_convertedToKg() = runTest {
        val (manager, api) = makeManager()
        // 7.716 lb ≈ 3.5 kg
        manager.fireAndForget(measurementOf(entry(MeasurementTypeKey.BONE, 7.716f, UnitType.LB)))
        val calls = withContext(Dispatchers.IO) { api.awaitCalls(1) }
        assertThat(calls.first { it.method == "bone" }.value).isWithin(0.05).of(3.5)
    }

    // -------------------------------------------------------------------------
    // Timestamp
    // -------------------------------------------------------------------------

    @Test
    fun fireAndForget_timestampMatchesMeasurementMillis() = runTest {
        val (manager, api) = makeManager()
        manager.fireAndForget(measurementOf(entry(MeasurementTypeKey.WEIGHT, 70f, UnitType.KG)))
        val calls = withContext(Dispatchers.IO) { api.awaitCalls(1) }
        assertThat(calls.first().timestamp).isEqualTo(Instant.ofEpochMilli(baseTimestamp))
    }

    // -------------------------------------------------------------------------
    // Error resilience
    // -------------------------------------------------------------------------

    @Test
    fun fireAndForget_insertFails_doesNotCrash() = runTest {
        val (manager, api) = makeManager(failMethods = setOf("weight"))
        manager.fireAndForget(measurementOf(entry(MeasurementTypeKey.WEIGHT, 70f, UnitType.KG)))
        val calls = withContext(Dispatchers.IO) { api.awaitCalls(1) }
        assertThat(calls).hasSize(1)
    }

    @Test
    fun fireAndForget_oneFieldFails_otherFieldsStillInserted() = runTest {
        val (manager, api) = makeManager(failMethods = setOf("weight"))
        manager.fireAndForget(measurementOf(
            entry(MeasurementTypeKey.WEIGHT,   75f, UnitType.KG),
            entry(MeasurementTypeKey.BODY_FAT, 20f, UnitType.PERCENT),
        ))
        val calls = withContext(Dispatchers.IO) { api.awaitCalls(2) }
        assertThat(calls.map { it.method }).contains("bodyFat")
    }
}
