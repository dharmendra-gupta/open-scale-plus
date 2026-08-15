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
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class WebhookTokenTest {

    private val fixedTimestampMs = 1748246400000L // 2025-05-26T08:00:00Z

    private fun makeType(key: MeasurementTypeKey, id: Int = key.id): MeasurementType =
        MeasurementType(
            id = id,
            key = key,
            name = key.name,
            color = 0,
            icon = MeasurementTypeIcon.IC_DEFAULT,
            unit = UnitType.NONE,
        )

    private fun makeValueWithType(key: MeasurementTypeKey, floatValue: Float?): MeasurementValueWithType =
        MeasurementValueWithType(
            value = MeasurementValue(
                measurementId = 1,
                typeId = key.id,
                floatValue = floatValue,
            ),
            type = makeType(key),
        )

    private fun makeMeasurement(vararg entries: Pair<MeasurementTypeKey, Float?>): MeasurementWithValues =
        MeasurementWithValues(
            measurement = Measurement(id = 1, userId = 1, timestamp = fixedTimestampMs),
            values = entries.map { (key, v) -> makeValueWithType(key, v) },
        )

    // --- fromKey ---

    @Test
    fun fromKey_returnsCorrectToken() {
        assertThat(WebhookToken.fromKey("WEIGHT")).isEqualTo(WebhookToken.WEIGHT)
        assertThat(WebhookToken.fromKey("VISCERAL_FAT")).isEqualTo(WebhookToken.VISCERAL_FAT)
        assertThat(WebhookToken.fromKey("TIMESTAMP")).isEqualTo(WebhookToken.TIMESTAMP)
    }

    @Test
    fun fromKey_returnsNullForUnknown() {
        assertThat(WebhookToken.fromKey("BODYTEMP")).isNull()
        assertThat(WebhookToken.fromKey("")).isNull()
        assertThat(WebhookToken.fromKey("weight")).isNull() // lowercase
    }

    // --- Resolve body composition tokens ---

    @Test
    fun resolve_weight_returnsDoubleValue() {
        val m = makeMeasurement(MeasurementTypeKey.WEIGHT to 75.5f)
        val result = WebhookTokenResolver.resolve(WebhookToken.WEIGHT, m)
        assertThat(result).isEqualTo(75.5)
    }

    @Test
    fun resolve_fat_returnsDoubleValue() {
        val m = makeMeasurement(MeasurementTypeKey.BODY_FAT to 18.2f)
        val result = WebhookTokenResolver.resolve(WebhookToken.FAT, m)
        assertThat(result).isEqualTo(18.2)
    }

    @Test
    fun resolve_missingValue_returnsNull() {
        val m = makeMeasurement(MeasurementTypeKey.WEIGHT to 75.5f) // no FAT
        val result = WebhookTokenResolver.resolve(WebhookToken.FAT, m)
        assertThat(result).isNull()
    }

    @Test
    fun resolve_nullFloatValue_returnsNull() {
        val m = makeMeasurement(MeasurementTypeKey.WATER to null)
        val result = WebhookTokenResolver.resolve(WebhookToken.WATER, m)
        assertThat(result).isNull()
    }

    // --- Resolve date/time tokens ---

    @Test
    fun resolve_timestamp_returnsLong() {
        val m = makeMeasurement()
        val result = WebhookTokenResolver.resolve(WebhookToken.TIMESTAMP, m)
        assertThat(result).isEqualTo(fixedTimestampMs)
    }

    @Test
    fun resolve_date_returnsIso8601String() {
        val m = makeMeasurement()
        val result = WebhookTokenResolver.resolve(WebhookToken.DATE, m) as String
        // Must be ISO-8601 date format yyyy-MM-dd
        assertThat(result).matches("\\d{4}-\\d{2}-\\d{2}")
    }

    @Test
    fun resolve_time_returnsHHmmssString() {
        val m = makeMeasurement()
        val result = WebhookTokenResolver.resolve(WebhookToken.TIME, m) as String
        assertThat(result).matches("\\d{2}:\\d{2}:\\d{2}")
    }

    @Test
    fun resolve_timezone_returnsNonEmptyString() {
        val m = makeMeasurement()
        val result = WebhookTokenResolver.resolve(WebhookToken.TIMEZONE, m) as String
        assertThat(result).isNotEmpty()
    }

    // --- Parameterized tokens ---

    @Test
    fun isParameterizedToken_recognizesPrefixes() {
        assertThat(WebhookTokenResolver.isParameterizedToken("DATE_yyyy-MM-dd")).isTrue()
        assertThat(WebhookTokenResolver.isParameterizedToken("TIME_HH:mm:ss")).isTrue()
        assertThat(WebhookTokenResolver.isParameterizedToken("TIMEZONE_xxx")).isTrue()
        assertThat(WebhookTokenResolver.isParameterizedToken("DATE")).isFalse()
        assertThat(WebhookTokenResolver.isParameterizedToken("WEIGHT")).isFalse()
        assertThat(WebhookTokenResolver.isParameterizedToken("TIMEZONE")).isFalse()
    }

    @Test
    fun isValidPattern_validPatterns_returnsTrue() {
        assertThat(WebhookTokenResolver.isValidPattern("DATE_yyyy-MM-dd")).isTrue()
        assertThat(WebhookTokenResolver.isValidPattern("TIME_HH:mm:ss")).isTrue()
        assertThat(WebhookTokenResolver.isValidPattern("TIMEZONE_xxx")).isTrue()
        assertThat(WebhookTokenResolver.isValidPattern("TIMEZONE_OOOO")).isTrue()
        assertThat(WebhookTokenResolver.isValidPattern("TIMEZONE_VV")).isTrue()
    }

    @Test
    fun isValidPattern_invalidPattern_returnsFalse() {
        // '#' is a reserved character that DateTimeFormatter.ofPattern() rejects
        assertThat(WebhookTokenResolver.isValidPattern("DATE_#")).isFalse()
        assertThat(WebhookTokenResolver.isValidPattern("TIME_#")).isFalse()
    }

    @Test
    fun resolveParameterized_dateCustomFormat_returnsFormattedDate() {
        val m = makeMeasurement()
        val result = WebhookTokenResolver.resolveParameterized("DATE_dd/MM/yyyy", m) as String
        assertThat(result).matches("\\d{2}/\\d{2}/\\d{4}")
    }

    @Test
    fun resolveParameterized_timeCustomFormat_returnsFormattedTime() {
        val m = makeMeasurement()
        val result = WebhookTokenResolver.resolveParameterized("TIME_HH:mm", m) as String
        assertThat(result).matches("\\d{2}:\\d{2}")
    }

    @Test
    fun resolveParameterized_timezoneOffsetPattern_returnsOffset() {
        val m = makeMeasurement()
        val result = WebhookTokenResolver.resolveParameterized("TIMEZONE_xxx", m) as String
        // e.g. "+05:30", "-04:00", "+00:00"
        assertThat(result).matches("[+-]\\d{2}:\\d{2}")
    }

    @Test
    fun resolveParameterized_timezoneGmtPrefixPattern_returnsGmtForm() {
        val m = makeMeasurement()
        // OOOO is the "localized GMT offset" pattern — Java outputs "GMT+05:30" style
        val result = WebhookTokenResolver.resolveParameterized("TIMEZONE_OOOO", m) as String
        assertThat(result).startsWith("GMT")
    }

    @Test
    fun resolveParameterized_invalidPattern_returnsNull() {
        val m = makeMeasurement()
        // '#' is a reserved character that DateTimeFormatter.ofPattern() rejects
        val result = WebhookTokenResolver.resolveParameterized("DATE_#", m)
        assertThat(result).isNull()
    }

    @Test
    fun resolve_allBodyTokens_correctTypeKeys() {
        val allBodyTokens = mapOf(
            WebhookToken.WEIGHT to MeasurementTypeKey.WEIGHT,
            WebhookToken.FAT to MeasurementTypeKey.BODY_FAT,
            WebhookToken.WATER to MeasurementTypeKey.WATER,
            WebhookToken.BONE to MeasurementTypeKey.BONE,
            WebhookToken.LBM to MeasurementTypeKey.LBM,
            WebhookToken.BMI to MeasurementTypeKey.BMI,
            WebhookToken.MUSCLE to MeasurementTypeKey.MUSCLE,
            WebhookToken.VISCERAL_FAT to MeasurementTypeKey.VISCERAL_FAT,
        )
        val measurement = makeMeasurement(
            *allBodyTokens.values.map { it to 1.0f }.toTypedArray()
        )
        allBodyTokens.forEach { (token, _) ->
            val result = WebhookTokenResolver.resolve(token, measurement)
            assertThat(result).isNotNull()
        }
    }
}
