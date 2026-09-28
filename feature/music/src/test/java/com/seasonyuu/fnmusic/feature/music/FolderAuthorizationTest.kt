package com.seasonyuu.fnmusic.feature.music

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test

class FolderAuthorizationTest {
    private val request = FolderAuthorizationRequest.create(
        FolderAuthorizationTarget("https://relay.fnos.net/music/", relayMode = true),
        listOf("/vol1/1000/音乐 精选", "/vol02/remote"), "random-state",
    )

    @Test fun authorizationUrlEncodesParametersAndUsesResolvedOrigin() {
        val url = request.url.toHttpUrl()
        assertEquals("/app-auth/pick-shared-file", url.encodedPath)
        assertEquals("trim.music", url.queryParameter("appName"))
        assertEquals("https://relay.fnos.net/music/app-auth-pick-file", url.queryParameter("redirectUri"))
        assertEquals("random-state", url.queryParameter("state"))
        assertEquals("myFiles,team,external,remote,storage", url.queryParameter("sidebarGroup"))
        assertEquals("/vol1/1000/音乐 精选,/vol02/remote", url.queryParameter("disabledPaths"))
        assertTrue(request.relayMode)
    }

    @Test fun webViewReceivesOnlyRelayRoutingCookie() {
        assertEquals("mode=relay; Path=/; Secure; HttpOnly", relayRoutingCookie(true))
        assertNull(relayRoutingCookie(false))
        assertFalse(relayRoutingCookie(true)!!.contains("music-token"))
    }

    @Test fun callbackRequiresExactOriginPathStateMethodAndApp() {
        val callback = "https://relay.fnos.net/music/app-auth-pick-file?method=music-app-auth-pick-file&appName=trim.music&state=random-state&status=success&path=%5B%22%2Fvol1%2F1000%2FNew%22%5D"
        assertEquals(FolderAuthorizationResult.Success(listOf("/vol1/1000/New")), request.callback(callback))
        assertEquals(FolderAuthorizationResult.Invalid, request.callback(callback.replace("random-state", "wrong")))
        assertEquals(FolderAuthorizationResult.Invalid, request.callback(callback.replace("relay.fnos.net", "evil.example")))
        assertEquals(FolderAuthorizationResult.Invalid, request.callback(callback.replace("app-auth-pick-file?", "app-auth-pick-file/extra?")))
        assertEquals(FolderAuthorizationResult.Invalid, request.callback(callback.replace("music-app-auth-pick-file&", "other&")))
        assertEquals(FolderAuthorizationResult.Invalid, request.callback(callback.replace("trim.music&", "other.app&")))
        assertEquals(FolderAuthorizationResult.Invalid, request.callback(callback.replace("status=success", "status=success&status=cancel")))
        assertEquals(FolderAuthorizationResult.Invalid, request.callback(callback.replace("%5B%22%2Fvol1%2F1000%2FNew%22%5D", "%5B%22relative%22%5D")))
    }

    @Test fun cancellationAndFailureAreDistinctFromSuccess() {
        val callback = "https://relay.fnos.net/music/app-auth-pick-file?method=music-app-auth-pick-file&appName=trim.music&state=random-state&status="
        assertEquals(FolderAuthorizationResult.Cancel, request.callback(callback + "cancel"))
        assertEquals(FolderAuthorizationResult.Error, request.callback(callback + "error"))
        assertEquals(FolderAuthorizationResult.Invalid, request.callback(callback + "unknown"))
    }
}
