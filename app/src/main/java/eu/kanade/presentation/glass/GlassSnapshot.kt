package eu.kanade.presentation.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import kotlinx.coroutines.CancellationException

/**
 * Fork: a still picture of the home page taken right before the global search opens over it, so the empty search
 * shows the page through its glass, like the expanded search of EhViewer. Only while liquid glass is on: without it no
 * page is recorded and nothing is taken. One picture at a time, released when the search screen leaves.
 */
object GlassSnapshot {
    private var layer: GraphicsLayer? = null

    var image by mutableStateOf<ImageBitmap?>(null)
        private set

    /** Takes the picture of the home page as it is now; nothing when no page is recorded. */
    suspend fun capture() {
        image = try {
            layer?.toImageBitmap()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    fun clear() {
        image = null
    }

    /** Records the home page into a layer the picture can be taken from. */
    @Composable
    internal fun Modifier.recordSnapshot(): Modifier {
        val recorded = rememberGraphicsLayer()
        DisposableEffect(recorded) {
            layer = recorded
            onDispose { if (layer === recorded) layer = null }
        }
        return drawWithContent {
            recorded.record { this@drawWithContent.drawContent() }
            drawLayer(recorded)
        }
    }
}
