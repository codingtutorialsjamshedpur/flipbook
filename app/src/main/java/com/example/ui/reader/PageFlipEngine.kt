package com.example.ui.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.input.pointer.pointerInput
import com.example.data.model.PageCurlAnchor
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sin

/**
 * State object driving both touch-gesture curls and programmatic button turns.
 */
class PageCurlState(
    val onPageTurn: (forward: Boolean) -> Unit
) {
    var isFlipping by mutableStateOf(false)
    var curlProgress by mutableFloatStateOf(0f) // 0.0 to 1.0
    var isForward by mutableStateOf(true)
    var activeAnchor by mutableStateOf(PageCurlAnchor.MIDDLE_RIGHT)

    suspend fun animateTurn(
        forward: Boolean,
        anchor: PageCurlAnchor = if (forward) PageCurlAnchor.MIDDLE_RIGHT else PageCurlAnchor.MIDDLE_LEFT
    ) {
        if (isFlipping) return
        isFlipping = true
        isForward = forward
        activeAnchor = anchor

        val anim = Animatable(0f)
        anim.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing)
        ) {
            curlProgress = this.value
        }

        onPageTurn(forward)
        curlProgress = 0f
        isFlipping = false
    }

    suspend fun settleOrCancel(commit: Boolean) {
        val anim = Animatable(curlProgress)
        val target = if (commit) 1f else 0f
        anim.animateTo(
            targetValue = target,
            animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing)
        ) {
            curlProgress = this.value
        }

        if (commit) {
            onPageTurn(isForward)
        }
        curlProgress = 0f
        isFlipping = false
    }
}

@Composable
fun rememberPageCurlState(onPageTurn: (forward: Boolean) -> Unit): PageCurlState {
    return remember { PageCurlState(onPageTurn) }
}

/**
 * Container that equips the book surface with interactive 6-zone touch-curl physics
 * and renders realistic 3D page curls across single-page and double-page layouts.
 */
@Composable
fun PageFlipContainer(
    state: PageCurlState,
    modifier: Modifier = Modifier,
    isLandscape: Boolean = false,
    canTurnForward: Boolean = true,
    canTurnBackward: Boolean = true,
    content: @Composable () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var startDragOffset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(isLandscape, canTurnForward, canTurnBackward) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val width = size.width
                        val height = size.height
                        val isRightEdge = offset.x > width * 0.70f
                        val isLeftEdge = offset.x < width * 0.30f

                        if (isRightEdge && canTurnForward) {
                            startDragOffset = offset
                            state.isFlipping = true
                            state.isForward = true
                            state.activeAnchor = when {
                                offset.y < height * 0.33f -> PageCurlAnchor.TOP_RIGHT
                                offset.y > height * 0.66f -> PageCurlAnchor.BOTTOM_RIGHT
                                else -> PageCurlAnchor.MIDDLE_RIGHT
                            }
                        } else if (isLeftEdge && canTurnBackward) {
                            startDragOffset = offset
                            state.isFlipping = true
                            state.isForward = false
                            state.activeAnchor = when {
                                offset.y < height * 0.33f -> PageCurlAnchor.TOP_LEFT
                                offset.y > height * 0.66f -> PageCurlAnchor.BOTTOM_LEFT
                                else -> PageCurlAnchor.MIDDLE_LEFT
                            }
                        }
                    },
                    onDrag = { change, dragAmount ->
                        if (!state.isFlipping) return@detectDragGestures
                        change.consume()

                        val width = size.width
                        val totalDx = if (state.isForward) {
                            (startDragOffset.x - change.position.x).coerceAtLeast(0f)
                        } else {
                            (change.position.x - startDragOffset.x).coerceAtLeast(0f)
                        }

                        val progress = (totalDx / (width * 0.62f)).coerceIn(0f, 1f)
                        state.curlProgress = progress
                    },
                    onDragEnd = {
                        if (state.isFlipping) {
                            val commit = state.curlProgress > 0.32f
                            coroutineScope.launch {
                                state.settleOrCancel(commit)
                            }
                        }
                    },
                    onDragCancel = {
                        if (state.isFlipping) {
                            coroutineScope.launch {
                                state.settleOrCancel(false)
                            }
                        }
                    }
                )
            }
    ) {
        // Underlying pages
        content()

        // 3D Realistic Curl Canvas Overlay
        if (state.isFlipping && state.curlProgress > 0.01f) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawAdvancedPageCurlPhysics(
                    progress = state.curlProgress,
                    isForward = state.isForward,
                    anchor = state.activeAnchor,
                    isLandscape = isLandscape
                )
            }
        }
    }
}

/**
 * Draws the physical page curl with diagonal origin, cylindrical curvature,
 * dual-sided sheet lighting, moving soft cast shadow, and spine depth.
 */
private fun DrawScope.drawAdvancedPageCurlPhysics(
    progress: Float,
    isForward: Boolean,
    anchor: PageCurlAnchor,
    isLandscape: Boolean
) {
    val w = size.width
    val h = size.height

    val activeWidth = if (isLandscape) w / 2f else w
    val spineX = if (isLandscape) w / 2f else 0f

    val curlExtent = activeWidth * progress

    // Compute curl fold coordinate along the active sheet
    val foldX = if (isForward) {
        w - curlExtent
    } else {
        if (isLandscape) spineX + curlExtent else curlExtent
    }

    // Dynamic diagonal skew based on the 6 grab zones
    val diagonalOffset = when (anchor) {
        PageCurlAnchor.TOP_LEFT, PageCurlAnchor.TOP_RIGHT -> h * 0.28f * sin(progress * Math.PI.toFloat())
        PageCurlAnchor.BOTTOM_LEFT, PageCurlAnchor.BOTTOM_RIGHT -> -h * 0.28f * sin(progress * Math.PI.toFloat())
        else -> 0f
    }

    // 1. Cast Soft Moving Shadow onto the page revealed beneath
    val shadowSpread = (80f * (1f - abs(progress - 0.5f) * 1.5f)).coerceIn(15f, 90f)
    val shadowStart = if (isForward) (foldX - shadowSpread).coerceAtLeast(0f) else foldX
    val shadowEnd = if (isForward) foldX else (foldX + shadowSpread).coerceAtMost(w)

    val castShadowBrush = Brush.horizontalGradient(
        colors = if (isForward) {
            listOf(Color.Transparent, Color(0x22000000), Color(0x66000000))
        } else {
            listOf(Color(0x66000000), Color(0x22000000), Color.Transparent)
        },
        startX = shadowStart,
        endX = shadowEnd
    )

    drawRect(
        brush = castShadowBrush,
        topLeft = Offset(shadowStart, 0f),
        size = Size(abs(shadowEnd - shadowStart), h)
    )

    // 2. Turning Page Curling Mesh Path
    val curlPath = Path()
    if (isForward) {
        curlPath.moveTo(foldX, 0f)
        curlPath.lineTo(w, 0f)
        curlPath.lineTo(w, h)
        curlPath.lineTo(foldX + diagonalOffset, h)
        curlPath.close()
    } else {
        val startX = if (isLandscape) spineX else 0f
        curlPath.moveTo(startX, 0f)
        curlPath.lineTo(foldX, 0f)
        curlPath.lineTo(foldX + diagonalOffset, h)
        curlPath.lineTo(startX, h)
        curlPath.close()
    }

    // Paper Texture Shading: Backside of turning page with natural gradient
    val paperSpine = Color(0xFFDCD6C8)
    val paperHighlight = Color(0xFFFAF7EF)
    val paperShadow = Color(0xFFE8E2D4)

    val sheetGradient = Brush.horizontalGradient(
        colors = if (isForward) {
            listOf(paperSpine, paperHighlight, paperShadow)
        } else {
            listOf(paperShadow, paperHighlight, paperSpine)
        },
        startX = foldX - 30f,
        endX = foldX + 70f
    )

    drawPath(path = curlPath, brush = sheetGradient, style = Fill)

    // 3. Crisp Cylinder Fold Highlight Line (reflects ambient reading light)
    drawLine(
        brush = Brush.verticalGradient(
            colors = listOf(Color(0xCCFFFFFF), Color(0x66FFFFFF), Color(0xCCFFFFFF))
        ),
        start = Offset(foldX, 0f),
        end = Offset(foldX + diagonalOffset, h),
        strokeWidth = 3f
    )

    // 4. Subtle Inner Crease Shadow
    val creaseX = if (isForward) foldX + 4f else foldX - 4f
    drawLine(
        color = Color(0x33000000),
        start = Offset(creaseX, 0f),
        end = Offset(creaseX + diagonalOffset, h),
        strokeWidth = 2f
    )
}
