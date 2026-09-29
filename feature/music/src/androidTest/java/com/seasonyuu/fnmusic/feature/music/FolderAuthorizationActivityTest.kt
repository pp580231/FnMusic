package com.seasonyuu.fnmusic.feature.music

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.webkit.WebView
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBackUnconditionally
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class FolderAuthorizationActivityTest {
    private lateinit var server: MockWebServer
    private val contract = FolderAuthorizationContract()
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val request get() = FolderAuthorizationRequest.create(
        FolderAuthorizationTarget(server.url("/music/").toString()), emptyList(), "test-state")
    private val callback = "/music/app-auth-pick-file?method=music-app-auth-pick-file&appName=trim.music&state=test-state&status=success&path=%5B%22%2Fvol1%2F1000%2FMusic%22%5D"

    @Before fun setUp() { server = MockWebServer().apply { start() } }
    @After fun tearDown() { server.shutdown() }

    @Test fun fnOsLoginRedirectAnd404DocumentReachValidatedCallback() {
        val logins = AtomicInteger()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.requestUrl?.encodedPath) {
                "/app-auth/pick-shared-file" -> html(404, "<script>location.replace('/login')</script>")
                "/login" -> {
                    logins.incrementAndGet()
                    html(200, """
                        <style>#root { height: 0 !important } .login-form { height: 0 !important; max-height: 100% !important }</style>
                        <div id="root"><div class="login-form">系统登录</div></div>
                        <script>setInterval(function() {
                            if (document.querySelector('.login-form').getBoundingClientRect().height > 200) location.replace('$callback');
                        }, 50)</script>
                    """.trimIndent())
                }
                else -> MockResponse().setResponseCode(404)
            }
        }
        launch().use { scenario ->
            val result = scenario.result
            assertEquals(FolderAuthorizationResult.Success(listOf("/vol1/1000/Music")),
                contract.parseResult(result.resultCode, result.resultData))
        }
        assertEquals(1, logins.get())
    }

    @Test fun appBarBackCanCloseAndReopenLoadedNativePage() {
        repeat(3) {
            val ready = serveStablePage()
            launch().use { scenario ->
                assertTrue(ready.await(10, TimeUnit.SECONDS))
                // The fixture can load while ActivityScenario's bootstrap splash is exiting.
                // Wait for window accessibility events to settle before injecting a physical tap.
                InstrumentationRegistry.getInstrumentation().uiAutomation.waitForIdle(500, 5_000)
                onView(withId(R.id.authorization_title)).check(matches(withText("授权文件夹")))
                onView(withId(R.id.authorization_web_view)).check(matches(isDisplayed()))
                onView(withId(R.id.authorization_back)).perform(click())
                val result = scenario.result
                assertEquals(FolderAuthorizationResult.Closed, contract.parseResult(result.resultCode, result.resultData))
            }
        }
    }

    @Test fun systemBackClosesTheAuthorizationActivity() {
        val ready = serveStablePage()
        launch().use { scenario ->
            assertTrue(ready.await(10, TimeUnit.SECONDS))
            pressBackUnconditionally()
            assertEquals(Activity.RESULT_CANCELED, scenario.result.resultCode)
        }
    }

    @Test fun recreationPreservesRequestCallbackAndAppearance() {
        val ready = serveStablePage()
        val input = FolderAuthorizationLaunch(request, Color.BLACK, Color.WHITE, Color.RED)
        launch(input).use { scenario ->
            assertTrue(ready.await(10, TimeUnit.SECONDS))
            var oldView: WebView? = null
            scenario.onActivity { oldView = it.findViewById(R.id.authorization_web_view) }
            val restoredReady = serveStablePage()
            scenario.recreate()
            assertTrue(restoredReady.await(10, TimeUnit.SECONDS))
            scenario.onActivity { activity ->
                val view = activity.findViewById<WebView>(R.id.authorization_web_view)
                assertNotSame(oldView, view)
                assertNull(oldView!!.parent)
                assertEquals(Color.WHITE, activity.findViewById<TextView>(R.id.authorization_title).currentTextColor)
                view.evaluateJavascript("location.href = '$callback'", null)
            }
            val result = scenario.result
            assertEquals(FolderAuthorizationResult.Success(listOf("/vol1/1000/Music")),
                contract.parseResult(result.resultCode, result.resultData))
        }
    }

    @Test fun callbackWithWrongStateReturnsInvalid() {
        server.enqueue(html(200, "<script>location.replace('${callback.replace("test-state", "wrong-state")}')</script>"))
        launch().use { scenario ->
            val result = scenario.result
            assertEquals(FolderAuthorizationResult.Invalid, contract.parseResult(result.resultCode, result.resultData))
        }
    }

    @Test fun missingRequestFinishesWithInvalidResult() {
        ActivityScenario.launchActivityForResult<FolderAuthorizationActivity>(Intent(context, FolderAuthorizationActivity::class.java)).use { scenario ->
            val result = scenario.result
            assertEquals(FolderAuthorizationResult.Invalid, contract.parseResult(result.resultCode, result.resultData))
        }
    }

    @Test fun contractUsesExplicitPrivateActivityAndPrimitiveResultData() {
        val input = FolderAuthorizationLaunch(request, Color.BLACK, Color.WHITE, Color.RED)
        val intent = contract.createIntent(context, input)
        val info = context.packageManager.getActivityInfo(requireNotNull(intent.component), 0)
        assertFalse(info.exported)
        val restored = requireNotNull(FolderAuthorizationContract.readInput(intent))
        assertEquals(input.request.url, restored.request.url)
        assertEquals(input.request.state, restored.request.state)
        assertEquals(input.backgroundColor, restored.backgroundColor)
        listOf(FolderAuthorizationResult.Success(listOf("/vol1/1000/Music")), FolderAuthorizationResult.Cancel,
            FolderAuthorizationResult.Error, FolderAuthorizationResult.Invalid).forEach { result ->
            assertEquals(result, contract.parseResult(Activity.RESULT_OK, FolderAuthorizationContract.resultIntent(result)))
        }
        assertEquals(FolderAuthorizationResult.Closed, contract.parseResult(Activity.RESULT_CANCELED, null))
        assertEquals(FolderAuthorizationResult.Invalid, contract.parseResult(Activity.RESULT_OK, Intent()))
    }

    private fun launch(input: FolderAuthorizationLaunch = FolderAuthorizationLaunch(request)) =
        ActivityScenario.launchActivityForResult<FolderAuthorizationActivity>(contract.createIntent(context, input))

    private fun serveStablePage(): CountDownLatch {
        val ready = CountDownLatch(1)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.requestUrl?.encodedPath) {
                "/app-auth/pick-shared-file" -> html(200, "授权页面<script>fetch('/ready')</script>")
                "/ready" -> { ready.countDown(); MockResponse().setBody("ready") }
                else -> MockResponse().setResponseCode(404)
            }
        }
        return ready
    }

    private fun html(code: Int, body: String) = MockResponse().setResponseCode(code)
        .setHeader("Content-Type", "text/html; charset=utf-8")
        .setBody("<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'></head><body>$body</body></html>")
}
