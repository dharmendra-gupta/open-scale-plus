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
import org.json.JSONObject
import org.junit.Test
import com.health.openscale.core.sync.webhook.ValidationErrorType

class WebhookPayloadBuilderTest {

    private val fixedTimestampMs = 1748246400000L

    private fun makeValueWithType(key: MeasurementTypeKey, floatValue: Float?): MeasurementValueWithType =
        MeasurementValueWithType(
            value = MeasurementValue(measurementId = 1, typeId = key.id, floatValue = floatValue),
            type = MeasurementType(
                id = key.id, key = key, name = key.name, color = 0,
                icon = MeasurementTypeIcon.IC_DEFAULT, unit = UnitType.NONE,
            ),
        )

    private val measurement = MeasurementWithValues(
        measurement = Measurement(id = 1, userId = 1, timestamp = fixedTimestampMs),
        values = listOf(
            makeValueWithType(MeasurementTypeKey.WEIGHT, 75.5f),
            makeValueWithType(MeasurementTypeKey.BODY_FAT, 18.2f),
            makeValueWithType(MeasurementTypeKey.WATER, null),
        ),
    )

    // --- buildPayload ---

    @Test
    fun buildPayload_flatSchema_resolvesTokens() {
        val schema = """{"weight": "WEIGHT", "body_fat": "FAT"}"""
        val json = JSONObject(WebhookPayloadBuilder.buildPayload(schema, measurement))
        assertThat(json.getDouble("weight")).isWithin(0.01).of(75.5)
        assertThat(json.getDouble("body_fat")).isWithin(0.01).of(18.2)
    }

    @Test
    fun buildPayload_missingValue_resolvesToNull() {
        val schema = """{"water": "WATER"}"""
        val json = JSONObject(WebhookPayloadBuilder.buildPayload(schema, measurement))
        assertThat(json.isNull("water")).isTrue()
    }

    @Test
    fun buildPayload_literalString_passedThrough() {
        val schema = """{"api_key": "shfiuggrw_my_auth", "weight": "WEIGHT"}"""
        val json = JSONObject(WebhookPayloadBuilder.buildPayload(schema, measurement))
        assertThat(json.getString("api_key")).isEqualTo("shfiuggrw_my_auth")
    }

    @Test
    fun buildPayload_nestedSchema_resolvesDeep() {
        val schema = """
            {
              "meta": {"source": "openscale"},
              "body": {"weight": "WEIGHT", "fat": "FAT"}
            }
        """.trimIndent()
        val json = JSONObject(WebhookPayloadBuilder.buildPayload(schema, measurement))
        assertThat(json.getJSONObject("meta").getString("source")).isEqualTo("openscale")
        assertThat(json.getJSONObject("body").getDouble("weight")).isWithin(0.01).of(75.5)
    }

    @Test
    fun buildPayload_timestampToken_resolvesToLong() {
        val schema = """{"ts": "TIMESTAMP"}"""
        val json = JSONObject(WebhookPayloadBuilder.buildPayload(schema, measurement))
        assertThat(json.getLong("ts")).isEqualTo(fixedTimestampMs)
    }

    // --- buildHeaders ---

    @Test
    fun buildHeaders_parsesJsonObject() {
        val raw = """{"Authorization": "Bearer tok", "x-api-key": "abc"}"""
        val headers = WebhookPayloadBuilder.buildHeaders(raw)
        assertThat(headers["Authorization"]).isEqualTo("Bearer tok")
        assertThat(headers["x-api-key"]).isEqualTo("abc")
    }

    @Test
    fun buildHeaders_blankInput_returnsEmpty() {
        assertThat(WebhookPayloadBuilder.buildHeaders("")).isEmpty()
        assertThat(WebhookPayloadBuilder.buildHeaders("   ")).isEmpty()
    }

    @Test
    fun buildHeaders_invalidJson_returnsEmpty() {
        assertThat(WebhookPayloadBuilder.buildHeaders("{not-json")).isEmpty()
    }

    // --- validateSchema ---

    @Test
    fun validateSchema_allKnownTokens_noErrors() {
        val schema = """{"weight": "WEIGHT", "fat": "FAT", "ts": "TIMESTAMP"}"""
        val errors = WebhookPayloadBuilder.validateSchema(schema)
        assertThat(errors).isEmpty()
    }

    @Test
    fun validateSchema_unknownToken_returnsError() {
        val schema = """{"temp": "BODYTEMP"}"""
        val errors = WebhookPayloadBuilder.validateSchema(schema)
        assertThat(errors).hasSize(1)
        assertThat(errors.first().unknownToken).isEqualTo("BODYTEMP")
        assertThat(errors.first().path).isEqualTo("temp")
    }

    @Test
    fun validateSchema_nestedUnknownToken_includesPath() {
        val schema = """{"composition": {"temp": "BODYTEMP"}}"""
        val errors = WebhookPayloadBuilder.validateSchema(schema)
        assertThat(errors).hasSize(1)
        assertThat(errors.first().path).isEqualTo("composition.temp")
    }

    @Test
    fun validateSchema_literalStringNotFlagged() {
        // Literal value with lowercase should not be flagged as unknown token
        val schema = """{"key": "shfiuggrw_my_auth", "source": "openscale"}"""
        val errors = WebhookPayloadBuilder.validateSchema(schema)
        assertThat(errors).isEmpty()
    }

    @Test
    fun validateSchema_invalidJson_returnsOneError() {
        val errors = WebhookPayloadBuilder.validateSchema("{invalid")
        assertThat(errors).hasSize(1)
        assertThat(errors.first().path).isEmpty()
    }

    @Test
    fun validateSchema_mixedKnownAndUnknown_onlyUnknownReported() {
        val schema = """{"weight": "WEIGHT", "unknown": "STEPS"}"""
        val errors = WebhookPayloadBuilder.validateSchema(schema)
        assertThat(errors).hasSize(1)
        assertThat(errors.first().unknownToken).isEqualTo("STEPS")
    }

    // --- JSONArray support ---

    @Test
    fun buildPayload_arrayWithTokens_resolvesItems() {
        val schema = """{"metrics": ["WEIGHT", "FAT"]}"""
        val json = JSONObject(WebhookPayloadBuilder.buildPayload(schema, measurement))
        val arr = json.getJSONArray("metrics")
        assertThat(arr.getDouble(0)).isWithin(0.01).of(75.5)
        assertThat(arr.getDouble(1)).isWithin(0.01).of(18.2)
    }

    @Test
    fun buildPayload_arrayWithLiterals_passedThrough() {
        val schema = """{"tags": ["openscale", "health"]}"""
        val json = JSONObject(WebhookPayloadBuilder.buildPayload(schema, measurement))
        val arr = json.getJSONArray("tags")
        assertThat(arr.getString(0)).isEqualTo("openscale")
        assertThat(arr.getString(1)).isEqualTo("health")
    }

    @Test
    fun validateSchema_arrayWithUnknownToken_reportsError() {
        val schema = """{"metrics": ["WEIGHT", "STEPS"]}"""
        val errors = WebhookPayloadBuilder.validateSchema(schema)
        assertThat(errors).hasSize(1)
        assertThat(errors.first().unknownToken).isEqualTo("STEPS")
        assertThat(errors.first().path).isEqualTo("metrics[1]")
    }

    @Test
    fun validateSchema_arrayWithAllKnownTokens_noErrors() {
        val schema = """{"metrics": ["WEIGHT", "FAT", "TIMESTAMP"]}"""
        val errors = WebhookPayloadBuilder.validateSchema(schema)
        assertThat(errors).isEmpty()
    }

    // --- Parameterized tokens ---

    @Test
    fun buildPayload_dateWithCustomFormat_returnsFormattedDate() {
        val schema = """{"date": "DATE_dd/MM/yyyy"}"""
        val json = JSONObject(WebhookPayloadBuilder.buildPayload(schema, measurement))
        assertThat(json.getString("date")).matches("\\d{2}/\\d{2}/\\d{4}")
    }

    @Test
    fun buildPayload_timeWithCustomFormat_returnsFormattedTime() {
        val schema = """{"time": "TIME_HH:mm"}"""
        val json = JSONObject(WebhookPayloadBuilder.buildPayload(schema, measurement))
        assertThat(json.getString("time")).matches("\\d{2}:\\d{2}")
    }

    @Test
    fun buildPayload_timezoneToken_returnsNonEmptyString() {
        val schema = """{"tz": "TIMEZONE"}"""
        val json = JSONObject(WebhookPayloadBuilder.buildPayload(schema, measurement))
        assertThat(json.getString("tz")).isNotEmpty()
    }

    @Test
    fun buildPayload_timezoneWithOffsetPattern_returnsOffset() {
        val schema = """{"tz": "TIMEZONE_xxx"}"""
        val json = JSONObject(WebhookPayloadBuilder.buildPayload(schema, measurement))
        assertThat(json.getString("tz")).matches("[+-]\\d{2}:\\d{2}")
    }

    @Test
    fun buildPayload_timezoneWithGmtPrefixPattern_startsWithGmt() {
        // OOOO is the "localized GMT offset" pattern — Java outputs "GMT+05:30" style
        val schema = """{"tz": "TIMEZONE_OOOO"}"""
        val json = JSONObject(WebhookPayloadBuilder.buildPayload(schema, measurement))
        assertThat(json.getString("tz")).startsWith("GMT")
    }

    @Test
    fun validateSchema_parameterizedDateToken_noError() {
        val schema = """{"date": "DATE_yyyy-MM-dd", "time": "TIME_HH:mm:ss"}"""
        val errors = WebhookPayloadBuilder.validateSchema(schema)
        assertThat(errors).isEmpty()
    }

    @Test
    fun validateSchema_timezoneTokens_noError() {
        val schema = """{"tz": "TIMEZONE", "tzOffset": "TIMEZONE_xxx", "tzLong": "TIMEZONE_OOOO"}"""
        val errors = WebhookPayloadBuilder.validateSchema(schema)
        assertThat(errors).isEmpty()
    }

    @Test
    fun validateSchema_invalidDatePattern_reportsInvalidPatternError() {
        // '#' is a reserved character that DateTimeFormatter.ofPattern() rejects
        val schema = """{"date": "DATE_#"}"""
        val errors = WebhookPayloadBuilder.validateSchema(schema)
        assertThat(errors).hasSize(1)
        assertThat(errors.first().type).isEqualTo(ValidationErrorType.INVALID_PATTERN)
        assertThat(errors.first().unknownToken).isEqualTo("DATE_#")
        assertThat(errors.first().path).isEqualTo("date")
    }

    @Test
    fun validateSchema_invalidTimezonePattern_reportsInvalidPatternError() {
        val schema = """{"tz": "TIMEZONE_#"}"""
        val errors = WebhookPayloadBuilder.validateSchema(schema)
        assertThat(errors).hasSize(1)
        assertThat(errors.first().type).isEqualTo(ValidationErrorType.INVALID_PATTERN)
    }

    @Test
    fun validateSchema_parameterizedTokenInArray_validPattern_noError() {
        val schema = """{"dates": ["DATE_yyyy-MM-dd", "TIMEZONE_xxx"]}"""
        val errors = WebhookPayloadBuilder.validateSchema(schema)
        assertThat(errors).isEmpty()
    }

    @Test
    fun validateSchema_parameterizedTokenInArray_invalidPattern_reportsError() {
        // '#' is a reserved character that DateTimeFormatter.ofPattern() rejects
        val schema = """{"dates": ["DATE_#"]}"""
        val errors = WebhookPayloadBuilder.validateSchema(schema)
        assertThat(errors).hasSize(1)
        assertThat(errors.first().type).isEqualTo(ValidationErrorType.INVALID_PATTERN)
        assertThat(errors.first().path).isEqualTo("dates[0]")
    }

    // --- Hardcoded number warnings ---

    @Test
    fun validateSchema_hardcodedInteger_reportsWarning() {
        val schema = """{"weight": 53}"""
        val errors = WebhookPayloadBuilder.validateSchema(schema)
        assertThat(errors).hasSize(1)
        assertThat(errors.first().type).isEqualTo(ValidationErrorType.HARDCODED_NUMBER)
        assertThat(errors.first().path).isEqualTo("weight")
        assertThat(errors.first().unknownToken).isEqualTo("53")
    }

    @Test
    fun validateSchema_hardcodedDouble_reportsWarning() {
        val schema = """{"body_fat": 21.9}"""
        val errors = WebhookPayloadBuilder.validateSchema(schema)
        assertThat(errors).hasSize(1)
        assertThat(errors.first().type).isEqualTo(ValidationErrorType.HARDCODED_NUMBER)
        assertThat(errors.first().path).isEqualTo("body_fat")
    }

    @Test
    fun validateSchema_hardcodedNumberInArray_reportsWarning() {
        val schema = """{"values": [78, 21.9]}"""
        val errors = WebhookPayloadBuilder.validateSchema(schema)
        assertThat(errors).hasSize(2)
        assertThat(errors.all { it.type == ValidationErrorType.HARDCODED_NUMBER }).isTrue()
    }

    @Test
    fun validateSchema_rawDataPayload_reportsNumberWarnings() {
        val schema = """{"weight": 78, "body_fat": 21.78, "water": 75, "date": "2026-05-27"}"""
        val errors = WebhookPayloadBuilder.validateSchema(schema)
        // Three hardcoded numbers; date literal has no letters so no LOOKS_LIKE_FORMAT
        assertThat(errors).hasSize(3)
        assertThat(errors.all { it.type == ValidationErrorType.HARDCODED_NUMBER }).isTrue()
    }

    // --- LOOKS_LIKE_FORMAT warnings ---

    @Test
    fun validateSchema_timeStringWithoutPrefix_reportsFormatWarning() {
        val schema = """{"time": "HH:mm:ss"}"""
        val errors = WebhookPayloadBuilder.validateSchema(schema)
        assertThat(errors).hasSize(1)
        assertThat(errors.first().type).isEqualTo(ValidationErrorType.LOOKS_LIKE_FORMAT)
        assertThat(errors.first().unknownToken).isEqualTo("HH:mm:ss")
        assertThat(errors.first().path).isEqualTo("time")
    }

    @Test
    fun validateSchema_dateStringWithoutPrefix_reportsFormatWarning() {
        val schema = """{"date": "yyyy-MM-dd"}"""
        val errors = WebhookPayloadBuilder.validateSchema(schema)
        assertThat(errors).hasSize(1)
        assertThat(errors.first().type).isEqualTo(ValidationErrorType.LOOKS_LIKE_FORMAT)
        assertThat(errors.first().unknownToken).isEqualTo("yyyy-MM-dd")
    }

    @Test
    fun validateSchema_literalDateValue_noWarning() {
        // "2026-05-27" has dashes but no letters — not a format pattern
        val schema = """{"date": "2026-05-27"}"""
        val errors = WebhookPayloadBuilder.validateSchema(schema)
        assertThat(errors).isEmpty()
    }

    @Test
    fun validateSchema_literalTimeValue_noWarning() {
        // "10:40:00" has colons but no letters
        val schema = """{"time": "10:40:00"}"""
        val errors = WebhookPayloadBuilder.validateSchema(schema)
        assertThat(errors).isEmpty()
    }

    @Test
    fun validateSchema_literalTimezoneOffset_noWarning() {
        // "+05:30" has colon but no letters
        val schema = """{"tz": "+05:30"}"""
        val errors = WebhookPayloadBuilder.validateSchema(schema)
        assertThat(errors).isEmpty()
    }

    @Test
    fun validateSchema_plainLiteralString_noWarning() {
        val schema = """{"source": "openscale", "label": "health"}"""
        val errors = WebhookPayloadBuilder.validateSchema(schema)
        assertThat(errors).isEmpty()
    }

    @Test
    fun validateSchema_formatStringInArray_reportsFormatWarning() {
        val schema = """{"formats": ["HH:mm:ss", "WEIGHT"]}"""
        val errors = WebhookPayloadBuilder.validateSchema(schema)
        assertThat(errors).hasSize(1)
        assertThat(errors.first().type).isEqualTo(ValidationErrorType.LOOKS_LIKE_FORMAT)
        assertThat(errors.first().path).isEqualTo("formats[0]")
    }
}
