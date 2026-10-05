package com.mrturingdev.ssclassify.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mrturingdev.ssclassify.data.ThumbnailLoader
import com.mrturingdev.ssclassify.model.ImageRecord

/**
 * Interactive fullscreen zoomable screenshot viewer.
 *
 * Supports pinch-to-zoom (clamped 1f..5f), 2D pan when zoomed, and double-tap
 * to toggle zoom between 1f and 2.5f. Clamps pan offsets to image bounds and resets
 * offset to zero when fully zoomed out.
 */
@Composable
fun FullscreenImageViewer(
    image: ImageRecord,
    thumbnailLoader: ThumbnailLoader,
    onDismiss: () -> Unit,
) {
    var bitmap by remember(image.id) { mutableStateOf<ImageBitmap?>(null) }
    var isHighResLoaded by remember(image.id) { mutableStateOf(false) }

    var scale by remember(image.id) { mutableFloatStateOf(1f) }
    var offset by remember(image.id) { mutableStateOf(Offset.Zero) }
    var showHud by remember(image.id) { mutableStateOf(true) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(image.id) {
        // First load 720px preview (or fallback to 320px) to display immediately from cache
        val preview = thumbnailLoader.load(image.id, 720) ?: thumbnailLoader.load(image.id, 320)
        if (preview != null && !isHighResLoaded) {
            bitmap = preview
        }
        // Then load full high-res 2048px bitmap to upgrade clarity seamlessly
        val highRes = thumbnailLoader.load(image.id, 2048)
        if (highRes != null) {
            bitmap = highRes
            isHighResLoaded = true
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            // Interactive gesture layer for image viewing
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { containerSize = it }
                    .pointerInput(image.id) {
                        detectTapGestures(
                            onDoubleTap = { tapOffset ->
                                if (scale > 1.05f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    val targetScale = 2.5f
                                    scale = targetScale
                                    val center = Offset(containerSize.width / 2f, containerSize.height / 2f)
                                    val targetOffset = (center - tapOffset) * (targetScale - 1f)
                                    val (maxX, maxY) = calculateMaxPan(containerSize, image, bitmap, targetScale)
                                    offset = Offset(
                                        targetOffset.x.coerceIn(-maxX, maxX),
                                        targetOffset.y.coerceIn(-maxY, maxY),
                                    )
                                }
                            },
                            onTap = {
                                showHud = !showHud
                            },
                        )
                    }
                    .pointerInput(image.id) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (scale * zoom).coerceIn(1f, 5f)
                            scale = newScale
                            if (newScale <= 1f) {
                                offset = Offset.Zero
                            } else {
                                val (maxX, maxY) = calculateMaxPan(containerSize, image, bitmap, newScale)
                                val newX = (offset.x + pan.x).coerceIn(-maxX, maxX)
                                val newY = (offset.y + pan.y).coerceIn(-maxY, maxY)
                                offset = Offset(newX, newY)
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                val currentBitmap = bitmap
                if (currentBitmap != null) {
                    Image(
                        bitmap = currentBitmap,
                        contentDescription = image.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offset.x
                                translationY = offset.y
                            },
                    )
                } else {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(44.dp),
                    )
                }
            }

            // Translucent Top App Bar / HUD
            AnimatedVisibility(
                visible = showHud,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.statusBars),
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.Black.copy(alpha = 0.65f),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = image.name,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val subtitle = buildString {
                                if (image.width > 0 && image.height > 0) {
                                    append("${image.width} × ${image.height} px")
                                }
                                if (isHighResLoaded) {
                                    if (isNotEmpty()) append(" • ")
                                    append("HD")
                                }
                            }
                            if (subtitle.isNotEmpty()) {
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.7f),
                                )
                            }
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Close",
                                tint = Color.White,
                            )
                        }
                    }
                }
            }

            // Subtle Bottom Hint Pill
            AnimatedVisibility(
                visible = showHud,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 24.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.65f),
                    contentColor = Color.White.copy(alpha = 0.9f),
                    shadowElevation = 4.dp,
                ) {
                    Text(
                        text = if (scale > 1.05f) {
                            "Pinch to zoom • Double-tap to reset"
                        } else {
                            "Pinch to zoom • Double-tap to expand"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

/**
 * Calculates the maximum horizontal and vertical pan offsets allowed based on
 * the container size, the image's fitted dimensions under [ContentScale.Fit], and [scale].
 */
internal fun calculateMaxPan(
    containerSize: IntSize,
    image: ImageRecord,
    bitmap: ImageBitmap?,
    scale: Float,
): Pair<Float, Float> {
    if (containerSize.width <= 0 || containerSize.height <= 0 || scale <= 1f) {
        return 0f to 0f
    }

    val imgW = if (image.width > 0) {
        image.width.toFloat()
    } else {
        bitmap?.width?.toFloat() ?: containerSize.width.toFloat()
    }
    val imgH = if (image.height > 0) {
        image.height.toFloat()
    } else {
        bitmap?.height?.toFloat() ?: containerSize.height.toFloat()
    }

    if (imgW <= 0f || imgH <= 0f) {
        val maxX = ((containerSize.width * (scale - 1f)) / 2f).coerceAtLeast(0f)
        val maxY = ((containerSize.height * (scale - 1f)) / 2f).coerceAtLeast(0f)
        return maxX to maxY
    }

    val containerRatio = containerSize.width.toFloat() / containerSize.height.toFloat()
    val imageRatio = imgW / imgH

    val (fittedW, fittedH) = if (imageRatio > containerRatio) {
        val w = containerSize.width.toFloat()
        val h = w / imageRatio
        w to h
    } else {
        val h = containerSize.height.toFloat()
        val w = h * imageRatio
        w to h
    }

    val scaledW = fittedW * scale
    val scaledH = fittedH * scale

    val maxX = if (scaledW > containerSize.width) (scaledW - containerSize.width) / 2f else 0f
    val maxY = if (scaledH > containerSize.height) (scaledH - containerSize.height) / 2f else 0f

    return maxX to maxY
}
