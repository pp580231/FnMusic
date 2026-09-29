package com.seasonyuu.fnmusic.feature.music

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.seasonyuu.fnmusic.core.designsystem.FnTextSecondary

@Composable
internal fun ArtistSkeleton(modifier: Modifier = Modifier) {
    val pulse = rememberInfiniteTransition(label = "artist-loading")
    val alpha by pulse.animateFloat(.06f, .12f,
        infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "artist-loading-opacity")
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(FnTextSecondary.copy(alpha = alpha)))
}

@Composable
internal fun ArtistListSkeleton(album: Boolean) {
    if (album) Column(Modifier.fillMaxWidth().padding(bottom = 28.dp).semantics { contentDescription = "正在加载专辑" }) {
        ArtistSkeleton(Modifier.fillMaxWidth().aspectRatio(1f))
        Spacer(Modifier.height(12.dp))
        ArtistSkeleton(Modifier.fillMaxWidth(.7f).height(18.dp))
        Spacer(Modifier.height(8.dp))
        ArtistSkeleton(Modifier.fillMaxWidth(.4f).height(14.dp))
    } else Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp)
        .semantics { contentDescription = "正在加载歌曲" }, verticalAlignment = Alignment.CenterVertically) {
        ArtistSkeleton(Modifier.size(48.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            ArtistSkeleton(Modifier.fillMaxWidth(.55f).height(18.dp))
            Spacer(Modifier.height(8.dp))
            ArtistSkeleton(Modifier.fillMaxWidth(.8f).height(14.dp))
        }
        Spacer(Modifier.width(48.dp))
    }
}

@Composable
internal fun ArtistPageLoading() {
    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
    }
}

internal fun artistVisibleCount(reported: Int, detailsReady: Boolean, loaded: Int, listEmpty: Boolean): Int? = when {
    listEmpty -> 0
    reported > 0 -> reported
    detailsReady && loaded == 0 -> reported
    else -> null
}
