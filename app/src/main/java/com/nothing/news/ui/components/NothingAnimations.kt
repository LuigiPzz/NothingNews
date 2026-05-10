package com.nothing.news.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlin.random.Random

@Composable
fun PixelDissolveContainer(
    isDissolving: Boolean,
    onAnimationEnd: () -> Unit,
    modifier: Modifier = Modifier,
    fadeContent: Boolean = true,
    isResolving: Boolean = false,
    content: @Composable () -> Unit
) {
    val progress = remember { Animatable(0f) }
    val particles = remember(isDissolving || isResolving) {
        if (isDissolving || isResolving) List(800) { NewsParticle() }
        else emptyList()
    }

    LaunchedEffect(isDissolving, isResolving) {
        if (isDissolving) {
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 1000, easing = LinearEasing)
            )
            onAnimationEnd()
        } else if (isResolving) {
            progress.snapTo(1f)
            progress.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 1000, easing = LinearEasing)
            )
            onAnimationEnd()
        } else {
            progress.snapTo(0f)
        }
    }

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .graphicsLayer {
                    if (fadeContent) {
                        alpha = 1f - progress.value
                    }
                }
        ) {
            content()
        }

        if (progress.value > 0.01f) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val p = progress.value
                val baseSize = 0.8.dp.toPx()
                
                particles.forEach { particle ->
                    val startX = (particle.relX) * size.width
                    val startY = (particle.relY) * size.height
                    
                    // Explosive movement in all directions
                    val movementScale = 112.5f
                    val currentX = startX + (particle.dirX * p * movementScale)
                    val currentY = startY + (particle.dirY * p * movementScale)
                    
                    val particleAlpha = (1f - (p / particle.lifeSpan)).coerceIn(0f, 1f)
                    
                    if (particleAlpha > 0f) {
                        val color = when (particle.colorType) {
                            0 -> Color.White
                            1 -> Color.LightGray
                            else -> Color(0xFFFF0000)
                        }
                        
                        val finalSize = baseSize * particle.sizeMultiplier
                        
                        drawRect(
                            color = color.copy(alpha = particleAlpha),
                            topLeft = Offset(currentX, currentY),
                            size = Size(finalSize, finalSize)
                        )
                    }
                }
            }
        }
    }
}

private class NewsParticle {
    val relX = Random.nextFloat()
    val relY = Random.nextFloat()
    
    // 360 degree random direction
    private val angle = Random.nextFloat() * 2f * Math.PI.toFloat()
    private val speed = Random.nextFloat() * 3f + 1f
    val dirX = Math.cos(angle.toDouble()).toFloat() * speed
    val dirY = Math.sin(angle.toDouble()).toFloat() * speed
    
    val lifeSpan = Random.nextFloat() * 0.4f + 0.6f
    val sizeMultiplier = Random.nextFloat() * 0.5f + 0.75f
    val colorType = when (Random.nextFloat()) {
        in 0f..0.7f -> 0 // White
        in 0.7f..0.95f -> 1 // Gray
        else -> 2 // Red
    }
}
