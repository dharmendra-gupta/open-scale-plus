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
package com.health.openscale.core.sync.strava

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton bus that carries the OAuth result emitted by [MainActivity]
 * when the Strava deep-link callback (`openscale://localhost?code=…` or `?error=…`) arrives.
 */
@Singleton
class StravaOAuthBus @Inject constructor() {

    private val _pendingCode = MutableSharedFlow<String>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val pendingCode: SharedFlow<String> = _pendingCode.asSharedFlow()

    private val _pendingError = MutableSharedFlow<String>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val pendingError: SharedFlow<String> = _pendingError.asSharedFlow()

    fun emit(code: String) { _pendingCode.tryEmit(code) }

    fun emitError(error: String) { _pendingError.tryEmit(error) }

    fun clear() {
        _pendingCode.resetReplayCache()
        _pendingError.resetReplayCache()
    }
}
