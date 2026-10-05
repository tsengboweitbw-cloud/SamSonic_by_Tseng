package com.example.samsonic.ui.library

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.samsonic.LocalAppContainer
import com.example.samsonic.R
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** How far the photo can be zoomed in past the point where it just fills the square. */
private const val MAX_ZOOM = 8f

/**
 * Crops the photo at [uri] to a square: drag to move it, pinch to zoom, with the photo always
 * covering the square. Done hands back [onCropped] a file holding what is inside it; Cancel, or
 * a photo that can't be read, calls [onDismiss].
 */
@Composable
internal fun CoverCropDialog(uri: Uri, onCropped: (Uri) -> Unit, onDismiss: () -> Unit) {
    val covers = LocalAppContainer.current.playlistCovers
    val scope = rememberCoroutineScope()
    var bitmap by remember(uri) { mutableStateOf<ImageBitmap?>(null) }
    var working by remember(uri) { mutableStateOf(false) }
    LaunchedEffect(uri) {
        val loaded = covers.loadForCrop(uri)
        if (loaded == null) onDismiss() else bitmap = loaded.asImageBitmap()
    }
    // The photo's size on the screen is its pixels times [scale]; [offset] is how far its centre is from the square's.
    var scale by remember(uri) { mutableFloatStateOf(0f) }
    var offset by remember(uri) { mutableStateOf(Offset.Zero) }
    var area by remember { mutableStateOf(IntSize.Zero) }
    val square = minOf(area.width, area.height) * 0.86f
    val image = bitmap

    fun minScale(image: ImageBitmap) = square / minOf(image.width, image.height)

    fun clamp(image: ImageBitmap, scale: Float, offset: Offset): Offset {
        val roomX = ((image.width * scale - square) / 2f).coerceAtLeast(0f)
        val roomY = ((image.height * scale - square) / 2f).coerceAtLeast(0f)
        return Offset(offset.x.coerceIn(-roomX, roomX), offset.y.coerceIn(-roomY, roomY))
    }

    // Starts with the photo just filling the square, once both it and the screen's size are known.
    LaunchedEffect(image, square) {
        if (image != null && square > 0f && scale <= 0f) scale = minScale(image)
    }

    Dialog(
        onDismissRequest = { if (!working) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(bottom = 72.dp)
                    .pointerInput(image, square) {
                        if (image == null || square <= 0f) return@pointerInput
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            if (scale <= 0f) return@detectTransformGestures
                            val min = minScale(image)
                            val next = (scale * zoom).coerceIn(min, min * MAX_ZOOM)
                            // Zooming about the fingers: what is under them stays under them.
                            val p = centroid - Offset(size.width / 2f, size.height / 2f)
                            offset = clamp(image, next, p - (p - offset) * (next / scale) + pan)
                            scale = next
                        }
                    }
            ) {
                Canvas(Modifier.fillMaxSize().onSizeChanged { area = it }) {
                    if (image == null || scale <= 0f) return@Canvas
                    val centre = Offset(size.width / 2f, size.height / 2f)
                    val shown = Size(image.width * scale, image.height * scale)
                    val topLeft = centre + offset - Offset(shown.width / 2f, shown.height / 2f)
                    drawImage(
                        image,
                        dstOffset = IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()),
                        dstSize = IntSize(shown.width.roundToInt(), shown.height.roundToInt()),
                    )
                    // Dim all but the square, and outline it.
                    val left = centre.x - square / 2f
                    val top = centre.y - square / 2f
                    val dim = Color.Black.copy(alpha = 0.6f)
                    drawRect(dim, Offset.Zero, Size(size.width, top))
                    drawRect(dim, Offset(0f, top + square), Size(size.width, size.height - top - square))
                    drawRect(dim, Offset(0f, top), Size(left, square))
                    drawRect(dim, Offset(left + square, top), Size(size.width - left - square, square))
                    drawRect(Color.White, Offset(left, top), Size(square, square), style = Stroke(width = 2.dp.toPx()))
                }
                if (image == null) CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
            }
            Row(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss, enabled = !working) { Text(stringResource(R.string.library_cancel), color = Color.White) }
                Button(
                    enabled = image != null && scale > 0f && !working,
                    onClick = {
                        if (image == null) return@Button
                        working = true
                        // What of the photo is inside the square, in its own pixels.
                        val side = square / scale
                        val left = image.width / 2f - (square / 2f + offset.x) / scale
                        val top = image.height / 2f - (square / 2f + offset.y) / scale
                        scope.launch {
                            val region = IntRect(left.roundToInt(), top.roundToInt(), (left + side).roundToInt(), (top + side).roundToInt())
                            val file = covers.cropToFile(uri, region)
                            if (file != null) onCropped(file) else onDismiss()
                        }
                    },
                ) { Text(stringResource(R.string.library_crop_done)) }
            }
        }
    }
}
