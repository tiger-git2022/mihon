package eu.kanade.presentation.components

import androidx.compose.animation.core.animate
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Velocity
import kotlin.math.roundToInt

/**
 * Fork: part of a top bar (the filter chips) that slides away while the page scrolls down and comes back as soon as
 * it scrolls up, keeping the pinned bar small. Give [connection] to the page's scroll container (the scaffold's
 * modifier) and [Modifier.collapsingHeader] to the part that collapses.
 */
@Stable
class CollapsingHeaderState {
    /** 0 when shown in full, down to -[height] when hidden. */
    var offset by mutableFloatStateOf(0f)
        private set

    internal var height by mutableFloatStateOf(0f)

    val connection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            val old = offset
            offset = (offset + available.y).coerceIn(-height, 0f)
            return Offset(0f, offset - old)
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            // Never left half shown
            if (offset < 0f && offset > -height) {
                val target = if (offset < -height / 2) -height else 0f
                animate(offset, target) { value, _ -> offset = value.coerceIn(-height, 0f) }
            }
            return Velocity.Zero
        }
    }

    fun show() {
        offset = 0f
    }
}

@Composable
fun rememberCollapsingHeaderState() = remember { CollapsingHeaderState() }

/** Clips this to the part of it [state] shows, sliding up under what is above it; unchanged when [state] is null. */
fun Modifier.collapsingHeader(state: CollapsingHeaderState?): Modifier {
    if (state == null) return this
    return clipToBounds().layout { measurable, constraints ->
        val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
        state.height = placeable.height.toFloat()
        val shown = (placeable.height + state.offset.roundToInt()).coerceIn(0, placeable.height)
        layout(placeable.width, shown) { placeable.place(0, shown - placeable.height) }
    }
}
