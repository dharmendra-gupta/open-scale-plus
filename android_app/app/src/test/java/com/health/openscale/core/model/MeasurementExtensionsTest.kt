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
package com.health.openscale.core.model

import com.google.common.truth.Truth.assertThat
import com.health.openscale.core.data.Measurement
import com.health.openscale.core.data.MeasurementType
import com.health.openscale.core.data.MeasurementTypeKey
import com.health.openscale.core.data.MeasurementValue
import com.health.openscale.core.data.UnitType
import org.junit.Test

class MeasurementExtensionsTest {

    private fun entry(key: MeasurementTypeKey, value: Float, unit: UnitType) = MeasurementValueWithType(
        value = MeasurementValue(measurementId = 1, typeId = key.id, floatValue = value),
        type  = MeasurementType(id = key.id, key = key, unit = unit),
    )

    private fun measurementOf(vararg values: MeasurementValueWithType) = MeasurementWithValues(
        measurement = Measurement(id = 1, userId = 1, timestamp = 0L),
        values = values.toList(),
    )

    // -------------------------------------------------------------------------
    // extractWeightKg
    // -------------------------------------------------------------------------

    @Test
    fun extractWeightKg_kg_returnsSameValue() {
        val m = measurementOf(entry(MeasurementTypeKey.WEIGHT, 80f, UnitType.KG))
        assertThat(m.extractWeightKg()).isWithin(0.001f).of(80f)
    }

    @Test
    fun extractWeightKg_lb_convertsToKg() {
        // 176.37 lb ≈ 80 kg
        val m = measurementOf(entry(MeasurementTypeKey.WEIGHT, 176.37f, UnitType.LB))
        assertThat(m.extractWeightKg()!!).isWithin(0.1f).of(80f)
    }

    @Test
    fun extractWeightKg_st_convertsToKg() {
        // 12.594 st ≈ 80 kg
        val m = measurementOf(entry(MeasurementTypeKey.WEIGHT, 12.594f, UnitType.ST))
        assertThat(m.extractWeightKg()!!).isWithin(0.1f).of(80f)
    }

    @Test
    fun extractWeightKg_noWeightField_returnsNull() {
        val m = measurementOf(entry(MeasurementTypeKey.BODY_FAT, 20f, UnitType.PERCENT))
        assertThat(m.extractWeightKg()).isNull()
    }

    @Test
    fun extractWeightKg_nullFloatValue_returnsNull() {
        val entry = MeasurementValueWithType(
            value = MeasurementValue(measurementId = 1, typeId = MeasurementTypeKey.WEIGHT.id, floatValue = null),
            type  = MeasurementType(id = MeasurementTypeKey.WEIGHT.id, key = MeasurementTypeKey.WEIGHT, unit = UnitType.KG),
        )
        assertThat(measurementOf(entry).extractWeightKg()).isNull()
    }

    // -------------------------------------------------------------------------
    // extractBodyFatPct
    // -------------------------------------------------------------------------

    @Test
    fun extractBodyFatPct_percent_returnsValue() {
        val m = measurementOf(entry(MeasurementTypeKey.BODY_FAT, 22.5f, UnitType.PERCENT))
        assertThat(m.extractBodyFatPct()).isWithin(0.001f).of(22.5f)
    }

    @Test
    fun extractBodyFatPct_nonPercentUnit_returnsNull() {
        val m = measurementOf(entry(MeasurementTypeKey.BODY_FAT, 15f, UnitType.KG))
        assertThat(m.extractBodyFatPct()).isNull()
    }

    @Test
    fun extractBodyFatPct_noField_returnsNull() {
        val m = measurementOf(entry(MeasurementTypeKey.WEIGHT, 75f, UnitType.KG))
        assertThat(m.extractBodyFatPct()).isNull()
    }

    // -------------------------------------------------------------------------
    // extractLbmKg
    // -------------------------------------------------------------------------

    @Test
    fun extractLbmKg_kg_returnsSameValue() {
        val m = measurementOf(entry(MeasurementTypeKey.LBM, 60f, UnitType.KG))
        assertThat(m.extractLbmKg()).isWithin(0.001f).of(60f)
    }

    @Test
    fun extractLbmKg_lb_convertsToKg() {
        // 132.277 lb ≈ 60 kg
        val m = measurementOf(entry(MeasurementTypeKey.LBM, 132.277f, UnitType.LB))
        assertThat(m.extractLbmKg()!!).isWithin(0.1f).of(60f)
    }

    @Test
    fun extractLbmKg_st_convertsToKg() {
        // 9.449 st ≈ 60 kg
        val m = measurementOf(entry(MeasurementTypeKey.LBM, 9.449f, UnitType.ST))
        assertThat(m.extractLbmKg()!!).isWithin(0.1f).of(60f)
    }

    @Test
    fun extractLbmKg_noField_returnsNull() {
        val m = measurementOf(entry(MeasurementTypeKey.WEIGHT, 75f, UnitType.KG))
        assertThat(m.extractLbmKg()).isNull()
    }

    // -------------------------------------------------------------------------
    // extractBoneKg
    // -------------------------------------------------------------------------

    @Test
    fun extractBoneKg_kg_returnsSameValue() {
        val m = measurementOf(entry(MeasurementTypeKey.BONE, 3.5f, UnitType.KG))
        assertThat(m.extractBoneKg()).isWithin(0.001f).of(3.5f)
    }

    @Test
    fun extractBoneKg_lb_convertsToKg() {
        // 7.716 lb ≈ 3.5 kg
        val m = measurementOf(entry(MeasurementTypeKey.BONE, 7.716f, UnitType.LB))
        assertThat(m.extractBoneKg()!!).isWithin(0.05f).of(3.5f)
    }

    @Test
    fun extractBoneKg_noField_returnsNull() {
        val m = measurementOf(entry(MeasurementTypeKey.WEIGHT, 75f, UnitType.KG))
        assertThat(m.extractBoneKg()).isNull()
    }

    @Test
    fun extractBoneKg_nullFloatValue_returnsNull() {
        val entry = MeasurementValueWithType(
            value = MeasurementValue(measurementId = 1, typeId = MeasurementTypeKey.BONE.id, floatValue = null),
            type  = MeasurementType(id = MeasurementTypeKey.BONE.id, key = MeasurementTypeKey.BONE, unit = UnitType.KG),
        )
        assertThat(measurementOf(entry).extractBoneKg()).isNull()
    }

    // -------------------------------------------------------------------------
    // Empty measurement
    // -------------------------------------------------------------------------

    @Test
    fun emptyMeasurement_allExtractorsReturnNull() {
        val m = measurementOf()
        assertThat(m.extractWeightKg()).isNull()
        assertThat(m.extractBodyFatPct()).isNull()
        assertThat(m.extractLbmKg()).isNull()
        assertThat(m.extractBoneKg()).isNull()
    }
}
