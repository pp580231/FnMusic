package com.seasonyuu.fnmusic.feature.music

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicInteger

class FolderAuthorizationPageTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var server: MockWebServer

    @Before fun setUp() { server = MockWebServer().apply { start() } }
    @After fun tearDown() { server.shutdown() }

    @Test fun fnOsLoginRedirectAnd404DocumentReachValidatedCallback() {
        val logins = AtomicInteger()
        val result = AtomicReference<FolderAuthorizationResult>()
        val request = FolderAuthorizationRequest.create(
            FolderAuthorizationTarget(server.url("/music/").toString()), emptyList(), "test-state")
        val callback = "/music/app-auth-pick-file?method=music-app-auth-pick-file&appName=trim.music&state=test-state&status=success&path=%5B%22%2Fvol1%2F1000%2FMusic%22%5D"
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.requestUrl?.encodedPath) {
                "/app-auth/pick-shared-file" -> html(404, "<script>location.replace('/login?redirect_uri=' + encodeURIComponent(location.pathname + location.search))</script>")
                "/login" -> { logins.incrementAndGet(); html(200, """
                    <style>#root { height: 0 !important } .login-form { height: 0 !important; max-height: 100% !important }</style>
                    <div id="root"><div class="login-form">系统登录</div></div>
                    <script>setInterval(function() {
                        if (document.querySelector('.login-form').getBoundingClientRect().height > 200) location.replace('$callback');
                    }, 50)</script>
                """.trimIndent()) }
                else -> MockResponse().setResponseCode(404)
            }
        }
        compose.setContent { FolderAuthorizationPage(request, { result.set(it) }, {}) }
        compose.waitUntil(20_000) { result.get() != null }
        compose.onNodeWithText("授权文件夹").assertIsDisplayed()
        compose.onNodeWithContentDescription("飞牛 fnOS").assertIsDisplayed()
        assertEquals(FolderAuthorizationResult.Success(listOf("/vol1/1000/Music")), result.get())
        assertEquals(1, logins.get())
        assertTrue(server.requestCount >= 2)
    }

    private fun html(code: Int, body: String) = MockResponse().setResponseCode(code)
        .setHeader("Content-Type", "text/html; charset=utf-8")
        .setBody("<!doctype html><html><head></head><body>$body</body></html>")
}
