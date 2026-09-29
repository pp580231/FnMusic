package com.seasonyuu.fnmusic.feature.music

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.seasonyuu.fnmusic.core.designsystem.BlurRadiusStop
import com.seasonyuu.fnmusic.core.designsystem.FnBackgroundBottom
import com.seasonyuu.fnmusic.core.designsystem.FnBackgroundTop
import com.seasonyuu.fnmusic.core.designsystem.ProgressiveBarBlur
import kotlin.math.roundToInt

internal val ArtistControlsHeight = 48.dp
internal val ArtistListBlurTransitionHeight = 32.dp
internal val ArtistListTopPadding = ArtistControlsHeight + ArtistListBlurTransitionHeight

/** The header's collapse belongs to the page, independently of either list's scroll. */
@Stable
internal class ArtistHeaderScrollState(initialFraction: Float = 0f) {
    var fraction by mutableFloatStateOf(initialFraction)
        private set
    var rangePx: Float = 0f
    val collapsedPx: Float get() = fraction * rangePx

    fun consume(deltaY: Float): Float {
        if (rangePx <= 0f) return 0f
        val before = collapsedPx
        val after = (before - deltaY).coerceIn(0f, rangePx)
        fraction = after / rangePx
        return before - after
    }

    companion object {
        val Saver = Saver<ArtistHeaderScrollState, Float>(
            save = { it.fraction }, restore = { ArtistHeaderScrollState(it) },
        )
    }
}

/**
 * The list and background are recorded into the scene. The fading header is drawn above
 * the list blur so its metadata is never cut by the moving blur boundary. AppBar sampling
 * still sees the complete page through the parent backdrop.
 */
@Composable
internal fun ArtistDetailLayout(
    scrollState: ArtistHeaderScrollState,
    pinnedTop: Dp,
    scrollListBy: (Float) -> Float,
    header: @Composable () -> Unit,
    controls: @Composable (Backdrop) -> Unit,
    content: @Composable () -> Unit,
) {
    val scene = rememberLayerBackdrop()
    val currentScrollListBy by rememberUpdatedState(scrollListBy)
    val nestedScroll = remember(scrollState) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
                if (available.y < 0f) Offset(0f, scrollState.consume(available.y)) else Offset.Zero

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
                if (available.y > 0f) Offset(0f, scrollState.consume(available.y)) else Offset.Zero
        }
    }
    val headerGestures = rememberScrollableState { delta ->
        // Swipes starting on the artwork/controls continue into the active list at the boundary.
        if (delta < 0f) {
            val headerConsumed = scrollState.consume(delta)
            headerConsumed - currentScrollListBy(-(delta - headerConsumed))
        } else {
            val listConsumed = -currentScrollListBy(-delta)
            listConsumed + scrollState.consume(delta - listConsumed)
        }
    }
    val controlsHeight = ArtistControlsHeight
    val appBarBlurOverlap = pinnedTop * ((scrollState.fraction - .75f) / .25f).coerceIn(0f, 1f)
    val blurHeight = appBarBlurOverlap + ArtistListTopPadding
    val background = Brush.verticalGradient(listOf(FnBackgroundTop, FnBackgroundBottom))
    var measuredHeaderHeight = 0
    Layout(
        modifier = Modifier.fillMaxSize().clipToBounds(),
        content = {
            Box(Modifier.fillMaxSize().layerBackdrop(scene).background(background)) {
                Layout(
                    modifier = Modifier.fillMaxSize(),
                    content = {
                        Box(Modifier.fillMaxSize().clipToBounds().nestedScroll(nestedScroll)) { content() }
                    },
                ) { measurables, constraints ->
                    // Extend the viewport through the AppBar when pinned. Matching scrollable
                    // padding keeps the initial content below the controls.
                    val bodyTop = measuredHeaderHeight - scrollState.collapsedPx.roundToInt() - pinnedTop.roundToPx()
                    val bodyPlaceable = measurables[0].measure(Constraints.fixed(
                        constraints.maxWidth, (constraints.maxHeight - bodyTop).coerceAtLeast(1),
                    ))
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        bodyPlaceable.place(0, bodyTop)
                    }
                }
            }
            Box(Modifier.testTag("artist-list-transition-blur")) {
                ProgressiveBarBlur(
                    backdrop = scene, top = true, modifier = Modifier,
                    transitionHeight = blurHeight, includeSystemInset = false,
                    fadeStartFraction = 0f,
                    radiusStops = listOf(
                        BlurRadiusStop(0.dp, 20.dp),
                        BlurRadiusStop(appBarBlurOverlap + controlsHeight, 16.dp),
                        BlurRadiusStop(blurHeight, 0.dp),
                    ),
                )
            }
            Box(Modifier.fillMaxWidth()
                .graphicsLayer {
                    // Fade only near the pinned position, following the drag in both directions.
                    val progress = ((scrollState.fraction - .75f) / .25f).coerceIn(0f, 1f)
                    alpha = 1f - progress * progress * (3f - 2f * progress)
                }
                .scrollable(headerGestures, Orientation.Vertical)) { header() }
            Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                .scrollable(headerGestures, Orientation.Vertical)) { controls(scene) }
        },
    ) { measurables, constraints ->
        val headerPlaceable = measurables[2].measure(Constraints.fixedWidth(constraints.maxWidth))
        measuredHeaderHeight = headerPlaceable.height
        scrollState.rangePx = (measuredHeaderHeight - pinnedTop.roundToPx()).coerceAtLeast(0).toFloat()
        val scenePlaceable = measurables[0].measure(Constraints.fixed(constraints.maxWidth, constraints.maxHeight))
        val blurPlaceable = measurables[1].measure(Constraints.fixed(constraints.maxWidth, blurHeight.roundToPx()))
        val controlsPlaceable = measurables[3].measure(Constraints.fixed(constraints.maxWidth, controlsHeight.roundToPx()))
        val controlsTop = pinnedTop.roundToPx() + (scrollState.rangePx - scrollState.collapsedPx).roundToInt()
        layout(constraints.maxWidth, constraints.maxHeight) {
            scenePlaceable.place(0, 0)
            blurPlaceable.place(0, controlsTop - appBarBlurOverlap.roundToPx())
            headerPlaceable.place(0, -scrollState.collapsedPx.roundToInt())
            controlsPlaceable.place(0, controlsTop)
        }
    }
}
