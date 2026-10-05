package com.mrturingdev.ssclassify.ui

import androidx.compose.animation.core.animate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Velocity
import kotlin.math.roundToInt

/**
 * Collapses the top app bar and the [CollapsingHeader] below it as one block:
 * scrolling down hides the bar, then the header; any scroll up brings them back
 * before the content moves. Attach [connection] with `Modifier.nestedScroll`
 * on a parent of both and of the scrolling content.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Stable
internal class CollapsingHeaderState(val bar: TopAppBarState) {
    /** Measured header height; plain field because only scroll callbacks read it. */
    internal var headerHeightPx = 0

    /** How far the header is pushed up, from -[headerHeightPx] (hidden) to 0 (shown). */
    var headerOffsetPx by mutableFloatStateOf(0f)
        private set

    // The bar reports its limit after its first layout; until then it cannot collapse.
    private val barHeightPx get() = if (bar.heightOffsetLimit == -Float.MAX_VALUE) 0f else -bar.heightOffsetLimit
    private val rangePx get() = barHeightPx + headerHeightPx

    /** Combined collapse, from -[rangePx] (all hidden) to 0 (all shown). */
    private var collapsedPx: Float
        get() = bar.heightOffset + headerOffsetPx.coerceAtLeast(-headerHeightPx.toFloat())
        set(value) {
            val total = value.coerceIn(-rangePx, 0f)
            bar.heightOffset = total.coerceAtLeast(-barHeightPx)
            headerOffsetPx = (total + barHeightPx).coerceAtMost(0f)
        }

    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            val before = collapsedPx
            collapsedPx = before + available.y
            return Offset(0f, collapsedPx - before)
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            // Never leave the block half-shown once the gesture settles.
            settleTo(if (collapsedPx < -rangePx / 2f) -rangePx else 0f)
            return Velocity.Zero
        }
    }

    suspend fun expand() = settleTo(0f)

    private suspend fun settleTo(target: Float) {
        if (collapsedPx == target) return
        animate(collapsedPx, target) { value, _ -> collapsedPx = value }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun rememberCollapsingHeaderState(): CollapsingHeaderState {
    val bar = rememberTopAppBarState()
    return remember(bar) { CollapsingHeaderState(bar) }
}

/**
 * Stacks [top], [pinned] and [bottom]. As [state] collapses, [top] slides up out
 * of view first, then [bottom] slides up underneath [pinned], which always stays.
 */
@Composable
internal fun CollapsingHeader(
    state: CollapsingHeaderState,
    modifier: Modifier = Modifier,
    top: @Composable () -> Unit,
    pinned: @Composable () -> Unit,
    bottom: @Composable () -> Unit = {},
) {
    Layout(listOf(top, pinned, bottom), modifier.clipToBounds()) { (tops, pins, bottoms), constraints ->
        val free = constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
        val topPlaceables = tops.map { it.measure(free) }
        val pinnedPlaceables = pins.map { it.measure(free) }
        val bottomPlaceables = bottoms.map { it.measure(free) }
        val topHeight = topPlaceables.sumOf { it.height }
        val pinnedHeight = pinnedPlaceables.sumOf { it.height }
        val bottomHeight = bottomPlaceables.sumOf { it.height }
        state.headerHeightPx = topHeight + bottomHeight
        // Read in the layout phase so scrolling relayouts without recomposing.
        val slots = headerSlots(-state.headerOffsetPx.roundToInt(), topHeight, bottomHeight)

        layout(constraints.maxWidth, slots.topShown + pinnedHeight + slots.bottomShown) {
            var y = slots.topShown - topHeight
            topPlaceables.forEach { it.place(0, y); y += it.height }
            y = slots.topShown + pinnedHeight - (bottomHeight - slots.bottomShown)
            bottomPlaceables.forEach { it.place(0, y); y += it.height }
            // Placed last so [bottom] slides underneath it.
            y = slots.topShown
            pinnedPlaceables.forEach { it.place(0, y, zIndex = 1f); y += it.height }
        }
    }
}

internal data class HeaderSlots(val topShown: Int, val bottomShown: Int)

/** Splits [collapsedPx] of collapse: [top] gives way first, then [bottom]. */
internal fun headerSlots(collapsedPx: Int, topHeight: Int, bottomHeight: Int): HeaderSlots {
    val collapsed = collapsedPx.coerceIn(0, topHeight + bottomHeight)
    val topHidden = collapsed.coerceAtMost(topHeight)
    return HeaderSlots(topShown = topHeight - topHidden, bottomShown = bottomHeight - (collapsed - topHidden))
}
