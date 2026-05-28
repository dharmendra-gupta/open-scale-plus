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

import android.content.SharedPreferences
import com.google.common.truth.Truth.assertThat
import com.health.openscale.core.sync.security.EncryptedPrefsManagerTest.FakeSharedPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class EncryptedPrefsManagerTest {

    // -------------------------------------------------------------------------
    // Minimal in-memory SharedPreferences for tests (no Android Keystore needed)
    // -------------------------------------------------------------------------

    class FakeSharedPreferences : SharedPreferences {
        private val store = mutableMapOf<String, Any?>()

        override fun getAll(): Map<String, *> = store.toMap()
        override fun contains(key: String) = store.containsKey(key)
        override fun getString(key: String, defValue: String?) = store[key] as? String ?: defValue
        override fun getStringSet(key: String, defValues: MutableSet<String>?) =
            (store[key] as? Set<*>)?.filterIsInstance<String>()?.toMutableSet() ?: defValues
        override fun getInt(key: String, defValue: Int) = store[key] as? Int ?: defValue
        override fun getLong(key: String, defValue: Long) = store[key] as? Long ?: defValue
        override fun getFloat(key: String, defValue: Float) = store[key] as? Float ?: defValue
        override fun getBoolean(key: String, defValue: Boolean) = store[key] as? Boolean ?: defValue
        override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val puts = mutableMapOf<String, Any?>()
            private val removes = mutableSetOf<String>()
            private var clearAll = false

            override fun putString(k: String, v: String?) = apply { puts[k] = v }
            override fun putStringSet(k: String, v: MutableSet<String>?) = apply { puts[k] = v }
            override fun putInt(k: String, v: Int) = apply { puts[k] = v }
            override fun putLong(k: String, v: Long) = apply { puts[k] = v }
            override fun putFloat(k: String, v: Float) = apply { puts[k] = v }
            override fun putBoolean(k: String, v: Boolean) = apply { puts[k] = v }
            override fun remove(k: String) = apply { removes.add(k) }
            override fun clear() = apply { clearAll = true }
            override fun commit(): Boolean { flush(); return true }
            override fun apply() { flush() }
            private fun flush() {
                if (clearAll) store.clear()
                removes.forEach { store.remove(it) }
                puts.forEach { (k, v) -> if (v != null) store[k] = v else store.remove(k) }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Setup
    // -------------------------------------------------------------------------

    private lateinit var manager: EncryptedPrefsManager

    @Before
    fun setUp() {
        val fakePrefs = FakeSharedPreferences()
        manager = EncryptedPrefsManager { fakePrefs }
    }

    // -------------------------------------------------------------------------
    // getString / setString
    // -------------------------------------------------------------------------

    @Test
    fun getString_unknownKey_returnsDefault() {
        assertThat(manager.getString("missing", "default")).isEqualTo("default")
    }

    @Test
    fun setString_updatesGetString() {
        manager.setString("token", "abc123")
        assertThat(manager.getString("token", "")).isEqualTo("abc123")
    }

    @Test
    fun setString_emptyValue_clearsKey() {
        manager.setString("token", "abc")
        manager.setString("token", "")
        assertThat(manager.getString("token", "default")).isEqualTo("")
    }

    // -------------------------------------------------------------------------
    // observeString flow
    // -------------------------------------------------------------------------

    @Test
    fun observeString_emitsDefaultForUnknownKey() = runTest {
        val value = manager.observeString("missing", "fallback").first()
        assertThat(value).isEqualTo("fallback")
    }

    @Test
    fun observeString_emitsStoredValueAfterSet() = runTest {
        manager.setString("key", "stored")
        val value = manager.observeString("key", "fallback").first()
        assertThat(value).isEqualTo("stored")
    }

    @Test
    fun observeString_flowUpdatesOnSubsequentSet() = runTest {
        val flow = manager.observeString("key", "")
        assertThat(flow.first()).isEqualTo("")

        manager.setString("key", "updated")
        assertThat(flow.first()).isEqualTo("updated")
    }

    @Test
    fun observeString_multipleKeys_independentFlows() = runTest {
        manager.setString("a", "alpha")
        manager.setString("b", "beta")
        assertThat(manager.observeString("a", "").first()).isEqualTo("alpha")
        assertThat(manager.observeString("b", "").first()).isEqualTo("beta")
    }

    // -------------------------------------------------------------------------
    // getLong / setLong
    // -------------------------------------------------------------------------

    @Test
    fun getLong_unknownKey_returnsDefault() {
        assertThat(manager.getLong("expiry", 0L)).isEqualTo(0L)
    }

    @Test
    fun setLong_updatesGetLong() {
        manager.setLong("expiry", 9999999L)
        assertThat(manager.getLong("expiry", 0L)).isEqualTo(9999999L)
    }

    // -------------------------------------------------------------------------
    // observeLong flow
    // -------------------------------------------------------------------------

    @Test
    fun observeLong_emitsDefaultForUnknownKey() = runTest {
        val value = manager.observeLong("expiry", 0L).first()
        assertThat(value).isEqualTo(0L)
    }

    @Test
    fun observeLong_flowUpdatesOnSetLong() = runTest {
        val flow = manager.observeLong("expiry", 0L)
        assertThat(flow.first()).isEqualTo(0L)

        manager.setLong("expiry", 1234567890L)
        assertThat(flow.first()).isEqualTo(1234567890L)
    }

    // -------------------------------------------------------------------------
    // remove
    // -------------------------------------------------------------------------

    @Test
    fun remove_clearsStringValue() {
        manager.setString("tok", "value")
        manager.remove("tok")
        assertThat(manager.getString("tok", "default")).isEqualTo("default")
    }

    @Test
    fun remove_updatesStringFlow() = runTest {
        val flow = manager.observeString("tok", "default")
        manager.setString("tok", "set")
        assertThat(flow.first()).isEqualTo("set")

        manager.remove("tok")
        assertThat(flow.first()).isEqualTo("")
    }

    @Test
    fun remove_clearsLongValue() {
        manager.setLong("exp", 999L)
        manager.remove("exp")
        assertThat(manager.getLong("exp", 0L)).isEqualTo(0L)
    }
}
