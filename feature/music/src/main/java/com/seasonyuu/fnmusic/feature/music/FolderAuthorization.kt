package com.seasonyuu.fnmusic.feature.music

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import java.util.UUID

/** Only the resolved music origin and FN Connect routing state cross into the WebView. */
data class FolderAuthorizationTarget(val musicBaseUrl: String, val relayMode: Boolean = false)

internal sealed interface FolderAuthorizationResult {
    data class Success(val paths: List<String>) : FolderAuthorizationResult
    data object Cancel : FolderAuthorizationResult
    data object Error : FolderAuthorizationResult
    data object Invalid : FolderAuthorizationResult
    data object Closed : FolderAuthorizationResult
}

internal fun relayRoutingCookie(relayMode: Boolean): String? =
    if (relayMode) "mode=relay; Path=/; Secure; HttpOnly" else null

internal class FolderAuthorizationRequest private constructor(
    val url: String,
    val origin: String,
    val callbackPath: String,
    val state: String,
    val relayMode: Boolean,
) {
    fun callback(url: String): FolderAuthorizationResult {
        val parsed = url.toHttpUrlOrNull() ?: return FolderAuthorizationResult.Invalid
        val expectedOrigin = origin.toHttpUrlOrNull() ?: return FolderAuthorizationResult.Invalid
        if (parsed.scheme != expectedOrigin.scheme || parsed.host != expectedOrigin.host || parsed.port != expectedOrigin.port ||
            parsed.encodedPath != callbackPath || parsed.fragment != null ||
            parsed.queryParameterValues("method") != listOf(CALLBACK_METHOD) ||
            parsed.queryParameterValues("appName") != listOf(APP_NAME) ||
            parsed.queryParameterValues("state") != listOf(state) ||
            parsed.queryParameterValues("status").size != 1
        ) return FolderAuthorizationResult.Invalid
        return when (parsed.queryParameter("status")) {
            "success" -> {
                if (parsed.queryParameterValues("path").size != 1) return FolderAuthorizationResult.Invalid
                val raw = parsed.queryParameter("path") ?: return FolderAuthorizationResult.Invalid
                val paths = runCatching { Json.parseToJsonElement(raw) }.getOrNull() as? JsonArray
                    ?: return FolderAuthorizationResult.Invalid
                val values = paths.map { (it as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.content
                    ?: return FolderAuthorizationResult.Invalid }
                if (values.isEmpty() || values.any { !it.startsWith("/") }) FolderAuthorizationResult.Invalid
                else FolderAuthorizationResult.Success(values)
            }
            "cancel" -> FolderAuthorizationResult.Cancel
            "error" -> FolderAuthorizationResult.Error
            else -> FolderAuthorizationResult.Invalid
        }
    }

    companion object {
        private const val APP_NAME = "trim.music"
        private const val CALLBACK_METHOD = "music-app-auth-pick-file"
        private const val SIDEBAR_GROUP = "myFiles,team,external,remote,storage"

        internal fun restore(url: String, origin: String, callbackPath: String, state: String,
            relayMode: Boolean): FolderAuthorizationRequest =
            FolderAuthorizationRequest(url, origin, callbackPath, state, relayMode)

        fun create(target: FolderAuthorizationTarget, authorizedPaths: List<String>, state: String = UUID.randomUUID().toString()): FolderAuthorizationRequest {
            require(state.isNotBlank())
            val music = target.musicBaseUrl.toHttpUrlOrNull() ?: error("无效的音乐服务地址")
            val callbackPath = music.encodedPath.trimEnd('/') + "/app-auth-pick-file"
            val origin = music.newBuilder().encodedPath("/").query(null).fragment(null).build()
            val callback = origin.newBuilder().encodedPath(callbackPath).build()
            val url = origin.newBuilder().encodedPath("/app-auth/pick-shared-file")
                .addQueryParameter("appName", APP_NAME)
                .addQueryParameter("redirectUri", callback.toString())
                .addQueryParameter("state", state)
                .addQueryParameter("sidebarGroup", SIDEBAR_GROUP)
                .addQueryParameter("disabledPaths", authorizedPaths.joinToString(","))
                .build()
            return FolderAuthorizationRequest(url.toString(), origin.toString(), callbackPath, state, target.relayMode)
        }
    }
}
