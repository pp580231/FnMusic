package com.seasonyuu.fnmusic.feature.music

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContract

/** The native screen receives only protocol state and display colors, never a music session. */
internal data class FolderAuthorizationLaunch(
    val request: FolderAuthorizationRequest,
    val backgroundColor: Int = Color.WHITE,
    val foregroundColor: Int = Color.BLACK,
    val accentColor: Int = Color.rgb(246, 44, 85),
)

internal class FolderAuthorizationContract : ActivityResultContract<FolderAuthorizationLaunch, FolderAuthorizationResult>() {
    override fun createIntent(context: Context, input: FolderAuthorizationLaunch) =
        Intent(context, FolderAuthorizationActivity::class.java).putExtra(INPUT, Bundle().apply {
            putString("url", input.request.url)
            putString("origin", input.request.origin)
            putString("callbackPath", input.request.callbackPath)
            putString("state", input.request.state)
            putBoolean("relayMode", input.request.relayMode)
            putInt("background", input.backgroundColor)
            putInt("foreground", input.foregroundColor)
            putInt("accent", input.accentColor)
        })

    override fun parseResult(resultCode: Int, intent: Intent?): FolderAuthorizationResult {
        if (resultCode == Activity.RESULT_CANCELED) return FolderAuthorizationResult.Closed
        if (resultCode != Activity.RESULT_OK) return FolderAuthorizationResult.Invalid
        return when (intent?.getStringExtra(STATUS)) {
            "success" -> intent.getStringArrayListExtra(PATHS)
                ?.takeIf { it.isNotEmpty() && it.all { path -> path.startsWith("/") } }
                ?.let { FolderAuthorizationResult.Success(it.toList()) } ?: FolderAuthorizationResult.Invalid
            "cancel" -> FolderAuthorizationResult.Cancel
            "error" -> FolderAuthorizationResult.Error
            "closed" -> FolderAuthorizationResult.Closed
            else -> FolderAuthorizationResult.Invalid
        }
    }

    companion object {
        private const val INPUT = "com.seasonyuu.fnmusic.folderAuthorization.input"
        private const val STATUS = "com.seasonyuu.fnmusic.folderAuthorization.status"
        private const val PATHS = "com.seasonyuu.fnmusic.folderAuthorization.paths"

        fun readInput(intent: Intent): FolderAuthorizationLaunch? = runCatching {
            val data = requireNotNull(intent.getBundleExtra(INPUT))
            FolderAuthorizationLaunch(
                FolderAuthorizationRequest.restore(
                    requireNotNull(data.getString("url")), requireNotNull(data.getString("origin")),
                    requireNotNull(data.getString("callbackPath")), requireNotNull(data.getString("state")),
                    data.getBoolean("relayMode"),
                ),
                data.getInt("background"), data.getInt("foreground"), data.getInt("accent"),
            )
        }.getOrNull()

        fun resultIntent(result: FolderAuthorizationResult) = Intent().apply {
            putExtra(STATUS, when (result) {
                is FolderAuthorizationResult.Success -> "success"
                FolderAuthorizationResult.Cancel -> "cancel"
                FolderAuthorizationResult.Error -> "error"
                FolderAuthorizationResult.Invalid -> "invalid"
                FolderAuthorizationResult.Closed -> "closed"
            })
            if (result is FolderAuthorizationResult.Success) putStringArrayListExtra(PATHS, ArrayList(result.paths))
        }
    }
}
