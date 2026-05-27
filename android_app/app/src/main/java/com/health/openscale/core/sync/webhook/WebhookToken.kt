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

import com.health.openscale.core.data.MeasurementTypeKey
import com.health.openscale.core.model.MeasurementWithValues
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class WebhookToken(val tokenKey: String) {
    WEIGHT("WEIGHT"),
    FAT("FAT"),
    WATER("WATER"),
    BONE("BONE"),
    LBM("LBM"),
    BMI("BMI"),
    MUSCLE("MUSCLE"),
    VISCERAL_FAT("VISCERAL_FAT"),
    DATE("DATE"),
    TIME("TIME"),
    TIMESTAMP("TIMESTAMP"),
    TIMEZONE("TIMEZONE");

    companion object {
        private val byKey = entries.associateBy { it.tokenKey }
        fun fromKey(key: String): WebhookToken? = byKey[key]
    }
}

object WebhookTokenResolver {
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    fun resolve(token: WebhookToken, measurement: MeasurementWithValues): Any? {
        val zdt = Instant.ofEpochMilli(measurement.measurement.timestamp)
            .atZone(ZoneId.systemDefault())
        return when (token) {
            WebhookToken.DATE -> zdt.toLocalDate().toString()
            WebhookToken.TIME -> zdt.toLocalTime().format(timeFormatter)
            WebhookToken.TIMESTAMP -> measurement.measurement.timestamp
            WebhookToken.TIMEZONE -> zdt.zone.id
            else -> {
                val typeKey = token.toMeasurementTypeKey()
                measurement.values.firstOrNull { it.type.key == typeKey }?.value?.floatValue
            }
        }
    }

    fun isParameterizedToken(key: String): Boolean =
        key.startsWith("DATE_") || key.startsWith("TIME_") || key.startsWith("TIMEZONE_")

    fun isValidPattern(key: String): Boolean {
        val pattern = parameterizedPattern(key) ?: return false
        return runCatching { DateTimeFormatter.ofPattern(pattern) }.isSuccess
    }

    fun resolveParameterized(key: String, measurement: MeasurementWithValues): Any? {
        val pattern = parameterizedPattern(key) ?: return null
        val zdt = Instant.ofEpochMilli(measurement.measurement.timestamp)
            .atZone(ZoneId.systemDefault())
        return runCatching { DateTimeFormatter.ofPattern(pattern).format(zdt) }.getOrNull()
    }

    private fun parameterizedPattern(key: String): String? = when {
        key.startsWith("DATE_") -> key.removePrefix("DATE_")
        key.startsWith("TIME_") -> key.removePrefix("TIME_")
        key.startsWith("TIMEZONE_") -> key.removePrefix("TIMEZONE_")
        else -> null
    }

    private fun WebhookToken.toMeasurementTypeKey(): MeasurementTypeKey = when (this) {
        WebhookToken.WEIGHT -> MeasurementTypeKey.WEIGHT
        WebhookToken.FAT -> MeasurementTypeKey.BODY_FAT
        WebhookToken.WATER -> MeasurementTypeKey.WATER
        WebhookToken.BONE -> MeasurementTypeKey.BONE
        WebhookToken.LBM -> MeasurementTypeKey.LBM
        WebhookToken.BMI -> MeasurementTypeKey.BMI
        WebhookToken.MUSCLE -> MeasurementTypeKey.MUSCLE
        WebhookToken.VISCERAL_FAT -> MeasurementTypeKey.VISCERAL_FAT
        else -> throw IllegalStateException("No MeasurementTypeKey for $this")
    }
}
