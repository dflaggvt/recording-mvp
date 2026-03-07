package com.memorystream.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.memorystream.ui.theme.CalmColors
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun GradientBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CalmColors.ScreenGradient)
    ) {
        content()
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    fillAlpha: Float = 0.08f,
    borderAlpha: Float = 0.12f,
    shimmer: Boolean = false,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)

    val shimmerModifier = if (shimmer) {
        val transition = rememberInfiniteTransition(label = "glass_shimmer")
        val shimmerProgress by transition.animateFloat(
            initialValue = -1f,
            targetValue = 2f,
            animationSpec = infiniteRepeatable(
                tween(3000, easing = LinearEasing),
                RepeatMode.Restart
            ),
            label = "shimmer_sweep"
        )
        Modifier.drawWithContent {
            drawContent()
            val shimmerBrush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.05f),
                    Color.White.copy(alpha = 0.08f),
                    Color.White.copy(alpha = 0.05f),
                    Color.Transparent
                ),
                start = Offset(size.width * shimmerProgress, 0f),
                end = Offset(size.width * (shimmerProgress + 0.5f), size.height)
            )
            drawRect(shimmerBrush)
        }
    } else Modifier

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White.copy(alpha = fillAlpha), shape)
            .border(
                BorderStroke(
                    1.dp,
                    Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = borderAlpha * 1.5f),
                            Color.White.copy(alpha = borderAlpha * 0.5f),
                            Color.White.copy(alpha = borderAlpha)
                        )
                    )
                ),
                shape
            )
            .then(shimmerModifier)
            .padding(20.dp)
    ) {
        content()
    }
}

@Composable
fun GlassCardNoPadding(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    fillAlpha: Float = 0.08f,
    borderAlpha: Float = 0.12f,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White.copy(alpha = fillAlpha), shape)
            .border(
                BorderStroke(
                    1.dp,
                    Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = borderAlpha * 1.5f),
                            Color.White.copy(alpha = borderAlpha * 0.5f),
                            Color.White.copy(alpha = borderAlpha)
                        )
                    )
                ),
                shape
            )
    ) {
        content()
    }
}

@Composable
fun WaveformVisualization(
    modifier: Modifier = Modifier,
    barCount: Int = 60,
    barColor: Color = CalmColors.Periwinkle.copy(alpha = 0.4f),
    barHighlightColor: Color = CalmColors.Periwinkle.copy(alpha = 0.7f),
    animate: Boolean = true,
    seed: Long = 0L
) {
    val rng = remember(seed) { Random(seed) }
    val amplitudes = remember(seed, barCount) {
        List(barCount) { i ->
            val base = sin(i * 0.15 + seed * 0.01).toFloat() * 0.3f + 0.3f
            val noise = rng.nextFloat() * 0.4f
            (base + noise).coerceIn(0.05f, 1f)
        }
    }

    val transition = rememberInfiniteTransition(label = "waveform_anim")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (animate) 6.28f else 0f,
        animationSpec = infiniteRepeatable(
            tween(4000, easing = LinearEasing), RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                drawWaveform(amplitudes, phase, barColor, barHighlightColor, animate)
            }
    )
}

private fun DrawScope.drawWaveform(
    amplitudes: List<Float>,
    phase: Float,
    barColor: Color,
    highlightColor: Color,
    animate: Boolean
) {
    val barCount = amplitudes.size
    val totalWidth = size.width
    val barSpacing = 2.dp.toPx()
    val barWidth = (totalWidth - barSpacing * (barCount - 1)) / barCount
    val maxHeight = size.height

    amplitudes.forEachIndexed { i, amp ->
        val animatedAmp = if (animate) {
            val wave = sin(phase + i * 0.3).toFloat() * 0.15f
            (amp + wave).coerceIn(0.05f, 1f)
        } else amp

        val barHeight = maxHeight * animatedAmp
        val x = i * (barWidth + barSpacing)
        val y = (maxHeight - barHeight) / 2f
        val color = if (animatedAmp > 0.6f) highlightColor else barColor
        val radius = CornerRadius(barWidth / 2f, barWidth / 2f)

        drawRoundRect(
            color = color,
            topLeft = Offset(x, y),
            size = Size(barWidth, barHeight),
            cornerRadius = radius
        )
    }
}

@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = true
) {
    if (isPrimary) {
        Button(
            onClick = onClick,
            modifier = modifier.height(48.dp),
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = CalmColors.GradientBottom
            )
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier.height(48.dp),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = Color.White
            )
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun GlassSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    placeholder: String = "Ask your memory anything...",
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(28.dp)
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)), shape),
        placeholder = {
            Text(
                placeholder,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.40f)
            )
        },
        leadingIcon = {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.40f)
            )
        },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Clear",
                        tint = Color.White.copy(alpha = 0.50f)
                    )
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        singleLine = true,
        shape = shape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.White.copy(alpha = 0.08f),
            unfocusedContainerColor = Color.White.copy(alpha = 0.06f),
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            focusedTextColor = Color.White.copy(alpha = 0.90f),
            unfocusedTextColor = Color.White.copy(alpha = 0.90f),
            cursorColor = CalmColors.Periwinkle
        )
    )
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    @Suppress("unused") onAction: () -> Unit = {}
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = Color.White.copy(alpha = 0.90f)
        )
        if (action != null) {
            Text(
                text = action,
                style = MaterialTheme.typography.labelMedium,
                color = CalmColors.Periwinkle.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun GlassChip(
    text: String,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false
) {
    val shape = RoundedCornerShape(16.dp)
    val bgAlpha = if (isSelected) 0.18f else 0.08f
    val borderAlpha = if (isSelected) 0.25f else 0.12f
    Box(
        modifier = modifier
            .background(Color.White.copy(alpha = bgAlpha), shape)
            .border(BorderStroke(1.dp, Color.White.copy(alpha = borderAlpha)), shape)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) Color.White.copy(alpha = 0.90f)
                    else Color.White.copy(alpha = 0.55f)
        )
    }
}
