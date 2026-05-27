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

import com.health.openscale.core.model.MeasurementWithValues
import org.json.JSONArray
import org.json.JSONObject
import java.time.format.DateTimeFormatter

enum class ValidationErrorType { UNKNOWN_TOKEN, INVALID_PATTERN, HARDCODED_NUMBER, LOOKS_LIKE_FORMAT }

data class SchemaValidationError(
    val path: String,
    val unknownToken: String,
    val type: ValidationErrorType = ValidationErrorType.UNKNOWN_TOKEN,
)

object WebhookPayloadBuilder {

    fun buildPayload(schemaJson: String, measurement: MeasurementWithValues): String =
        resolveObject(JSONObject(schemaJson), measurement).toString()

    fun buildHeaders(rawHeadersJson: String): Map<String, String> {
        if (rawHeadersJson.isBlank()) return emptyMap()
        return runCatching {
            val obj = JSONObject(rawHeadersJson)
            obj.keys().asSequence().associateWith { obj.getString(it) }
        }.getOrDefault(emptyMap())
    }

    fun validateSchema(schemaJson: String): List<SchemaValidationError> = runCatching {
        collectErrors(JSONObject(schemaJson), "")
    }.getOrElse { e ->
        listOf(SchemaValidationError("", "Invalid JSON: ${e.message}"))
    }

    private fun resolveObject(obj: JSONObject, measurement: MeasurementWithValues): JSONObject {
        val result = JSONObject()
        for (key in obj.keys()) {
            when (val raw = obj.get(key)) {
                is String -> {
                    val token = WebhookToken.fromKey(raw)
                    when {
                        token != null -> {
                            val resolved = WebhookTokenResolver.resolve(token, measurement)
                            if (resolved == null) result.put(key, JSONObject.NULL)
                            else result.put(key, resolved)
                        }
                        WebhookTokenResolver.isParameterizedToken(raw) -> {
                            val resolved = WebhookTokenResolver.resolveParameterized(raw, measurement)
                            if (resolved == null) result.put(key, JSONObject.NULL)
                            else result.put(key, resolved)
                        }
                        else -> result.put(key, raw)
                    }
                }
                is JSONObject -> result.put(key, resolveObject(raw, measurement))
                is JSONArray -> result.put(key, resolveArray(raw, measurement))
                else -> result.put(key, raw)
            }
        }
        return result
    }

    private fun resolveArray(arr: JSONArray, measurement: MeasurementWithValues): JSONArray {
        val result = JSONArray()
        for (i in 0 until arr.length()) {
            when (val item = arr.get(i)) {
                is String -> {
                    val token = WebhookToken.fromKey(item)
                    when {
                        token != null -> {
                            val resolved = WebhookTokenResolver.resolve(token, measurement)
                            if (resolved == null) result.put(JSONObject.NULL)
                            else result.put(resolved)
                        }
                        WebhookTokenResolver.isParameterizedToken(item) -> {
                            val resolved = WebhookTokenResolver.resolveParameterized(item, measurement)
                            if (resolved == null) result.put(JSONObject.NULL)
                            else result.put(resolved)
                        }
                        else -> result.put(item)
                    }
                }
                is JSONObject -> result.put(resolveObject(item, measurement))
                is JSONArray -> result.put(resolveArray(item, measurement))
                else -> result.put(item)
            }
        }
        return result
    }

    private fun collectErrors(obj: JSONObject, path: String): List<SchemaValidationError> {
        val errors = mutableListOf<SchemaValidationError>()
        for (key in obj.keys()) {
            val currentPath = if (path.isEmpty()) key else "$path.$key"
            when (val raw = obj.get(key)) {
                is String -> errors.addAll(checkStringValue(raw, currentPath))
                is Number ->
                    errors.add(SchemaValidationError(currentPath, raw.toString(), ValidationErrorType.HARDCODED_NUMBER))
                is JSONObject -> errors.addAll(collectErrors(raw, currentPath))
                is JSONArray -> errors.addAll(collectArrayErrors(raw, currentPath))
            }
        }
        return errors
    }

    private fun collectArrayErrors(arr: JSONArray, path: String): List<SchemaValidationError> {
        val errors = mutableListOf<SchemaValidationError>()
        for (i in 0 until arr.length()) {
            val itemPath = "$path[$i]"
            when (val item = arr.get(i)) {
                is String -> errors.addAll(checkStringValue(item, itemPath))
                is Number ->
                    errors.add(SchemaValidationError(itemPath, item.toString(), ValidationErrorType.HARDCODED_NUMBER))
                is JSONObject -> errors.addAll(collectErrors(item, itemPath))
                is JSONArray -> errors.addAll(collectArrayErrors(item, itemPath))
            }
        }
        return errors
    }

    private fun checkStringValue(raw: String, path: String): List<SchemaValidationError> {
        if (looksLikeToken(raw)) {
            if (WebhookToken.fromKey(raw) != null) return emptyList()
            return if (WebhookTokenResolver.isParameterizedToken(raw)) {
                if (!WebhookTokenResolver.isValidPattern(raw))
                    listOf(SchemaValidationError(path, raw, ValidationErrorType.INVALID_PATTERN))
                else emptyList()
            } else {
                listOf(SchemaValidationError(path, raw))
            }
        }
        if (looksLikeFormatPattern(raw))
            return listOf(SchemaValidationError(path, raw, ValidationErrorType.LOOKS_LIKE_FORMAT))
        return emptyList()
    }

    // Token-like: all-uppercase+underscore fixed tokens, or DATE_/TIME_/TIMEZONE_ parameterized tokens
    private fun looksLikeToken(value: String): Boolean {
        if (value.isBlank()) return false
        if (value.all { it.isUpperCase() || it == '_' }) return true
        return value.startsWith("DATE_") || value.startsWith("TIME_") || value.startsWith("TIMEZONE_")
    }

    // Catches strings like "HH:mm:ss" or "dd/MM/yyyy" that look like format patterns but are missing their token prefix
    private fun looksLikeFormatPattern(value: String): Boolean {
        if (!value.any { it == ':' || it == '/' || it == '-' }) return false
        if (!value.any { it.isLetter() }) return false
        return runCatching { DateTimeFormatter.ofPattern(value) }.isSuccess
    }
}
