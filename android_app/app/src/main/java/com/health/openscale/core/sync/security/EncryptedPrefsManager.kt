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
package com.health.openscale.core.sync.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.health.openscale.core.utils.LogManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps Android's hardware-backed EncryptedSharedPreferences for storing sensitive credentials.
 * Values are encrypted with AES-256-GCM (values) and AES-256-SIV (keys).
 *
 * Reactive reads are backed by per-key MutableStateFlows that are updated on every write,
 * so callers can either collect the Flow or call .first() — both work correctly.
 *
 * The secondary constructor accepting a [SharedPreferences] factory is used in tests to inject
 * a plain in-memory implementation without requiring Android Keystore.
 */
@Singleton
class EncryptedPrefsManager(
    prefsFactory: () -> SharedPreferences,
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this({
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            "secure_prefs",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    })

    private val TAG = "EncryptedPrefsManager"
    private val prefs: SharedPreferences by lazy { prefsFactory() }

    private val stringFlows = ConcurrentHashMap<String, MutableStateFlow<String>>()
    private val longFlows   = ConcurrentHashMap<String, MutableStateFlow<Long>>()

    // -------------------------------------------------------------------------
    // String
    // -------------------------------------------------------------------------

    fun observeString(key: String, default: String = ""): Flow<String> =
        stringFlows.getOrPut(key) { MutableStateFlow(getString(key, default)) }.asStateFlow()

    fun getString(key: String, default: String = ""): String =
        runCatching { prefs.getString(key, default) ?: default }.getOrElse { e ->
            LogManager.e(TAG, "getString failed for key=$key", e)
            default
        }

    fun setString(key: String, value: String) {
        runCatching { prefs.edit().putString(key, value).apply() }.onFailure { e ->
            LogManager.e(TAG, "setString failed for key=$key", e)
        }
        stringFlows[key]?.value = value
    }

    // -------------------------------------------------------------------------
    // Long
    // -------------------------------------------------------------------------

    fun observeLong(key: String, default: Long = 0L): Flow<Long> =
        longFlows.getOrPut(key) { MutableStateFlow(getLong(key, default)) }.asStateFlow()

    fun getLong(key: String, default: Long = 0L): Long =
        runCatching { prefs.getLong(key, default) }.getOrElse { e ->
            LogManager.e(TAG, "getLong failed for key=$key", e)
            default
        }

    fun setLong(key: String, value: Long) {
        runCatching { prefs.edit().putLong(key, value).apply() }.onFailure { e ->
            LogManager.e(TAG, "setLong failed for key=$key", e)
        }
        longFlows[key]?.value = value
    }

    // -------------------------------------------------------------------------
    // Removal
    // -------------------------------------------------------------------------

    fun remove(key: String) {
        runCatching { prefs.edit().remove(key).apply() }
        stringFlows[key]?.value = ""
        longFlows[key]?.value = 0L
    }
}
