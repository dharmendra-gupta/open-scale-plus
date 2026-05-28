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
package com.health.openscale.core.sync.dryrun

import com.health.openscale.core.utils.LogManager
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OkHttp interceptor that, when [SyncDryRunMode] is enabled, captures every outgoing
 * request and writes it to [SyncDryRunMode.logFile] instead of sending it over the
 * network. A synthetic HTTP 200 response is returned so callers behave normally.
 *
 * Only intended for debug builds — the toggle in the UI is hidden in release.
 */
@Singleton
class DryRunInterceptor @Inject constructor(
    private val dryRunMode: SyncDryRunMode,
) : Interceptor {

    private val TAG = "DryRunInterceptor"
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val fileLock = Any()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!dryRunMode.enabled.value) return chain.proceed(request)

        val bodyStr = request.body?.let {
            val buffer = Buffer()
            it.writeTo(buffer)
            buffer.readUtf8()
        } ?: "<no body>"

        val entry = buildString {
            appendLine()
            appendLine("=== ${dateFmt.format(Date())} ===")
            appendLine("${request.method} ${request.url}")
            request.headers.forEach { (name, value) -> appendLine("$name: $value") }
            appendLine()
            appendLine(bodyStr)
        }

        runCatching {
            synchronized(fileLock) { dryRunMode.logFile().appendText(entry) }
        }.onFailure { e ->
            LogManager.e(TAG, "Failed to write dry-run log: ${e.message}", e)
        }

        LogManager.d(TAG, "DryRun intercepted: ${request.method} ${request.url}")

        return Response.Builder()
            .code(200)
            .message("DryRun OK")
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .body("{}".toResponseBody("application/json".toMediaType()))
            .build()
    }
}
