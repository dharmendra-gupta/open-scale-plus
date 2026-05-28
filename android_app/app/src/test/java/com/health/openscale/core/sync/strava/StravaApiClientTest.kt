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

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class StravaApiClientTest {

    private val server = MockWebServer()
    private lateinit var client: StravaApiClient

    @Before
    fun setUp() {
        server.start()
        client = StravaApiClient(OkHttpClient()).also {
            it.baseUrl = server.url("").toString().trimEnd('/')
        }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    // -------------------------------------------------------------------------
    // buildAuthUrl
    // -------------------------------------------------------------------------

    @Test
    fun buildAuthUrl_containsClientId() {
        val url = StravaApiClient.buildAuthUrl("12345")
        assertThat(url).contains("client_id=12345")
    }

    @Test
    fun buildAuthUrl_containsRedirectUri() {
        val url = StravaApiClient.buildAuthUrl("id")
        assertThat(url).contains("redirect_uri=openscale://localhost")
    }

    @Test
    fun buildAuthUrl_containsProfileWriteScope() {
        val url = StravaApiClient.buildAuthUrl("id")
        assertThat(url).contains("scope=profile:write")
    }

    @Test
    fun buildAuthUrl_containsResponseTypeCode() {
        val url = StravaApiClient.buildAuthUrl("id")
        assertThat(url).contains("response_type=code")
    }

    // -------------------------------------------------------------------------
    // exchangeCode
    // -------------------------------------------------------------------------

    @Test
    fun exchangeCode_200_returnsSuccess() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(tokenJson()))
        val result = client.exchangeCode("id", "secret", "auth-code")
        assertThat(result.isSuccess).isTrue()
    }

    @Test
    fun exchangeCode_parsesAccessToken() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(tokenJson(access = "abc123")))
        val token = client.exchangeCode("id", "secret", "code").getOrThrow()
        assertThat(token.accessToken).isEqualTo("abc123")
    }

    @Test
    fun exchangeCode_parsesRefreshToken() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(tokenJson(refresh = "ref456")))
        val token = client.exchangeCode("id", "secret", "code").getOrThrow()
        assertThat(token.refreshToken).isEqualTo("ref456")
    }

    @Test
    fun exchangeCode_parsesExpiresAt() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(tokenJson(expiresAt = 9999999L)))
        val token = client.exchangeCode("id", "secret", "code").getOrThrow()
        assertThat(token.expiresAt).isEqualTo(9999999L)
    }

    @Test
    fun exchangeCode_parsesAthleteFirstName() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(tokenJson(firstName = "John")))
        val token = client.exchangeCode("id", "secret", "code").getOrThrow()
        assertThat(token.athleteFirstName).isEqualTo("John")
    }

    @Test
    fun exchangeCode_parsesAthleteLastName() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(tokenJson(lastName = "Doe")))
        val token = client.exchangeCode("id", "secret", "code").getOrThrow()
        assertThat(token.athleteLastName).isEqualTo("Doe")
    }

    @Test
    fun exchangeCode_noAthlete_returnsEmptyNames() = runTest {
        val json = """{"access_token":"a","refresh_token":"r","expires_at":1000}"""
        server.enqueue(MockResponse().setResponseCode(200).setBody(json))
        val token = client.exchangeCode("id", "secret", "code").getOrThrow()
        assertThat(token.athleteFirstName).isEmpty()
        assertThat(token.athleteLastName).isEmpty()
    }

    @Test
    fun exchangeCode_400_returnsFailure() = runTest {
        server.enqueue(MockResponse().setResponseCode(400))
        val result = client.exchangeCode("id", "secret", "bad-code")
        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun exchangeCode_sendsPostToTokenEndpoint() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(tokenJson()))
        client.exchangeCode("id", "secret", "code")
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).contains("/oauth/token")
    }

    @Test
    fun exchangeCode_bodyContainsGrantType() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(tokenJson()))
        client.exchangeCode("id", "secret", "mycode")
        val body = server.takeRequest().body.readUtf8()
        assertThat(body).contains("grant_type=authorization_code")
    }

    @Test
    fun exchangeCode_bodyContainsCode() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(tokenJson()))
        client.exchangeCode("id", "secret", "mycode")
        val body = server.takeRequest().body.readUtf8()
        assertThat(body).contains("code=mycode")
    }

    // -------------------------------------------------------------------------
    // refreshToken
    // -------------------------------------------------------------------------

    @Test
    fun refreshToken_200_returnsSuccess() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(tokenJson()))
        val result = client.refreshToken("id", "secret", "old-refresh")
        assertThat(result.isSuccess).isTrue()
    }

    @Test
    fun refreshToken_parsesNewAccessToken() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(tokenJson(access = "new-access")))
        val token = client.refreshToken("id", "secret", "old-refresh").getOrThrow()
        assertThat(token.accessToken).isEqualTo("new-access")
    }

    @Test
    fun refreshToken_parsesNewRefreshToken() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(tokenJson(refresh = "new-refresh")))
        val token = client.refreshToken("id", "secret", "old-refresh").getOrThrow()
        assertThat(token.refreshToken).isEqualTo("new-refresh")
    }

    @Test
    fun refreshToken_401_returnsFailure() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        val result = client.refreshToken("id", "secret", "bad-token")
        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun refreshToken_sendsPostWithRefreshGrantType() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(tokenJson()))
        client.refreshToken("id", "secret", "old-refresh")
        val body = server.takeRequest().body.readUtf8()
        assertThat(body).contains("grant_type=refresh_token")
    }

    @Test
    fun refreshToken_bodyContainsRefreshToken() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(tokenJson()))
        client.refreshToken("id", "secret", "my-refresh-token")
        val body = server.takeRequest().body.readUtf8()
        assertThat(body).contains("refresh_token=my-refresh-token")
    }

    // -------------------------------------------------------------------------
    // syncWeight
    // -------------------------------------------------------------------------

    @Test
    fun syncWeight_200_returnsSuccess() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val result = client.syncWeight("token", 75.5f)
        assertThat(result.isSuccess).isTrue()
    }

    @Test
    fun syncWeight_usesPutMethod() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        client.syncWeight("token", 75.0f)
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("PUT")
    }

    @Test
    fun syncWeight_sendsToAthleteEndpoint() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        client.syncWeight("token", 75.0f)
        val request = server.takeRequest()
        assertThat(request.path).contains("/api/v3/athlete")
    }

    @Test
    fun syncWeight_sendsBearerAuthHeader() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        client.syncWeight("my-access-token", 75.0f)
        val request = server.takeRequest()
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer my-access-token")
    }

    @Test
    fun syncWeight_bodyContainsWeight() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        client.syncWeight("token", 80.25f)
        val body = server.takeRequest().body.readUtf8()
        assertThat(body).contains("weight=")
        assertThat(body).contains("80.25")
    }

    @Test
    fun syncWeight_401_returnsFailure() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        val result = client.syncWeight("bad-token", 75.0f)
        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun syncWeight_500_returnsFailure() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        val result = client.syncWeight("token", 75.0f)
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("500")
    }

    // -------------------------------------------------------------------------
    // testConnection
    // -------------------------------------------------------------------------

    @Test
    fun testConnection_200_returnsSuccess() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val result = client.testConnection("token")
        assertThat(result.isSuccess).isTrue()
    }

    @Test
    fun testConnection_sendsGetRequest() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        client.testConnection("token")
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("GET")
    }

    @Test
    fun testConnection_sendsToAthleteEndpoint() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        client.testConnection("token")
        val request = server.takeRequest()
        assertThat(request.path).contains("/api/v3/athlete")
    }

    @Test
    fun testConnection_sendsBearerAuthHeader() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        client.testConnection("my-token")
        val request = server.takeRequest()
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer my-token")
    }

    @Test
    fun testConnection_401_returnsFailure() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        val result = client.testConnection("bad-token")
        assertThat(result.isFailure).isTrue()
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun tokenJson(
        access: String = "access-token",
        refresh: String = "refresh-token",
        expiresAt: Long = 9999999999L,
        firstName: String = "John",
        lastName: String = "Doe",
    ) = """
        {
            "access_token": "$access",
            "refresh_token": "$refresh",
            "expires_at": $expiresAt,
            "athlete": { "firstname": "$firstName", "lastname": "$lastName" }
        }
    """.trimIndent()
}
