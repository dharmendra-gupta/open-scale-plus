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

import com.health.openscale.core.data.MeasurementTypeKey
import com.health.openscale.core.data.UnitType

fun MeasurementWithValues.extractWeightKg(): Float? {
    val entry = values.firstOrNull { it.type.key == MeasurementTypeKey.WEIGHT } ?: return null
    val raw = entry.value.floatValue ?: return null
    return when (entry.type.unit) {
        UnitType.KG -> raw
        UnitType.LB -> raw / 2.20462f
        UnitType.ST -> raw / 0.157473f
        else        -> raw
    }
}

fun MeasurementWithValues.extractBodyFatPct(): Float? {
    val entry = values.firstOrNull { it.type.key == MeasurementTypeKey.BODY_FAT } ?: return null
    if (entry.type.unit != UnitType.PERCENT) return null
    return entry.value.floatValue
}

fun MeasurementWithValues.extractLbmKg(): Float? {
    val entry = values.firstOrNull { it.type.key == MeasurementTypeKey.LBM } ?: return null
    val raw = entry.value.floatValue ?: return null
    return when (entry.type.unit) {
        UnitType.KG -> raw
        UnitType.LB -> raw / 2.20462f
        UnitType.ST -> raw / 0.157473f
        else        -> raw
    }
}

fun MeasurementWithValues.extractBoneKg(): Float? {
    val entry = values.firstOrNull { it.type.key == MeasurementTypeKey.BONE } ?: return null
    val raw = entry.value.floatValue ?: return null
    return when (entry.type.unit) {
        UnitType.KG -> raw
        UnitType.LB -> raw / 2.20462f
        else        -> raw
    }
}
