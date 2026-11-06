package com.vocalarm.app.ui.neu

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.vocalarm.app.ui.theme.LocalNeuMetrics
import com.vocalarm.app.ui.theme.LocalPalette
import com.vocalarm.app.ui.theme.VocalarmPalette
import kotlin.math.min
import kotlin.math.roundToInt

enum class NeuTone { Raised, Inset }

/**
 * Soft shadow built from expanding rounded rects. The falloff is quadratic and the offset is
 * biased downwards, which reads as a real light source instead of an even glow around the card.
 */
internal fun DrawScope.drawNeuShadow(
    color: Color,
    rect: Rect,
    cornerRadius: Float,
    offset: Offset,
    blur: Float,
    layers: Int,
    peakAlpha: Float,
) {
    if (blur <= 0f || peakAlpha <= 0f) return
    val count = layers.coerceAtLeast(1)
    for (index in count downTo 1) {
        val t = index.toFloat() / count
        val grow = blur * t
        val alpha = (peakAlpha * 2.7f * (1f - t) * (1f - t) / count).coerceIn(0f, 1f)
        drawRoundRect(
            color = color.copy(alpha = alpha),
            topLeft = Offset(rect.left - grow + offset.x, rect.top - grow + offset.y),
            size = Size(rect.width + grow * 2f, rect.height + grow * 2f),
            cornerRadius = CornerRadius(cornerRadius + grow),
        )
    }
}

fun Modifier.neuSurface(
    palette: VocalarmPalette,
    fill: Color = palette.surface,
    radius: Dp = 24.dp,
    tone: NeuTone = NeuTone.Raised,
    depth: Dp = 7.dp,
    blur: Dp = 18.dp,
    circle: Boolean = false,
    layers: Int = 12,
    glowColor: Color? = null,
): Modifier = this.drawWithCache {
    val corner = if (circle) min(size.width, size.height) / 2f else radius.toPx()
    val depthPx = depth.toPx()
    val blurPx = blur.toPx()
    val rect = Rect(0f, 0f, size.width, size.height)
    val shapePath = Path().apply { addRoundRect(RoundRect(rect, CornerRadius(corner))) }
    val dark = palette.darkShadow
    val light = palette.lightShadow
    onDrawBehind {
        when (tone) {
            NeuTone.Raised -> {
                if (glowColor != null) {
                    drawNeuShadow(
                        color = glowColor,
                        rect = rect,
                        cornerRadius = corner,
                        offset = Offset(0f, depthPx * 1.35f),
                        blur = blurPx * 1.15f,
                        layers = layers,
                        peakAlpha = 0.26f,
                    )
                }
                drawNeuShadow(
                    color = dark,
                    rect = rect,
                    cornerRadius = corner,
                    offset = Offset(depthPx * 0.18f, depthPx * 0.88f),
                    blur = blurPx * 0.92f,
                    layers = layers,
                    peakAlpha = palette.darkShadowAlpha * 0.82f,
                )
                drawNeuShadow(
                    color = light,
                    rect = rect,
                    cornerRadius = corner,
                    offset = Offset(-depthPx * 0.32f, -depthPx * 0.38f),
                    blur = blurPx * 0.52f,
                    layers = layers,
                    peakAlpha = palette.lightShadowAlpha * 0.30f,
                )
                drawRoundRect(
                    color = fill,
                    topLeft = Offset.Zero,
                    size = size,
                    cornerRadius = CornerRadius(corner),
                )
                // Light from above as a soft fill, never a stroke: a 1px rim reads as a stray
                // hairline at the corners once the arc radius is small relative to the card.
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to light.copy(alpha = 0.16f),
                            0.35f to light.copy(alpha = 0.04f),
                            0.55f to Color.Transparent,
                        ),
                        startY = 0f,
                        endY = size.height,
                    ),
                    topLeft = Offset.Zero,
                    size = size,
                    cornerRadius = CornerRadius(corner),
                )
            }

            NeuTone.Inset -> {
                drawRoundRect(
                    color = fill,
                    topLeft = Offset.Zero,
                    size = size,
                    cornerRadius = CornerRadius(corner),
                )
                clipPath(shapePath) {
                    val spread = blurPx * 1.15f
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                dark.copy(alpha = palette.darkShadowAlpha * 0.72f),
                                Color.Transparent,
                            ),
                            start = Offset(0f, 0f),
                            end = Offset(spread, spread),
                        ),
                    )
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.Transparent,
                                light.copy(alpha = palette.lightShadowAlpha * 0.62f),
                            ),
                            start = Offset(size.width - spread, size.height - spread),
                            end = Offset(size.width, size.height),
                        ),
                    )
                }
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            dark.copy(alpha = 0.12f),
                            Color.Transparent,
                            light.copy(alpha = 0.20f),
                        ),
                    ),
                    topLeft = Offset(0.75f, 0.75f),
                    size = Size(size.width - 1.5f, size.height - 1.5f),
                    cornerRadius = CornerRadius(corner - 0.75f),
                    style = Stroke(width = 1f),
                )
            }
        }
    }
}

@Composable
fun NeuCard(
    modifier: Modifier = Modifier,
    radius: Dp = 26.dp,
    depth: Dp = 7.dp,
    tone: NeuTone = NeuTone.Raised,
    fill: Color = LocalPalette.current.surface,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    onClick: (() -> Unit)? = null,
    glow: Color? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val palette = LocalPalette.current
    val metrics = LocalNeuMetrics.current
    val shape = remember(radius) { RoundedCornerShape(radius) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val animatedDepth by animateDpAsState(
        targetValue = if (pressed && onClick != null && tone == NeuTone.Raised) depth * 0.45f else depth,
        animationSpec = spring(stiffness = 900f),
        label = "cardDepth",
    )
    val clickable = if (onClick == null) {
        Modifier
    } else {
        Modifier.clickable(
            interactionSource = interaction,
            indication = null,
            role = Role.Button,
            onClick = onClick,
        )
    }
    Box(
        modifier = modifier
            .neuSurface(
                palette = palette,
                fill = fill,
                radius = radius,
                tone = tone,
                depth = animatedDepth,
                blur = metrics.blur,
                glowColor = glow,
            )
            .clip(shape)
            .then(clickable)
            .padding(contentPadding),
        content = content,
    )
}

@Composable
fun NeuButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: VIcon? = null,
    accent: Boolean = false,
    enabled: Boolean = true,
    height: Dp = 52.dp,
    fillColor: Color? = null,
    contentTint: Color? = null,
) {
    val palette = LocalPalette.current
    val metrics = LocalNeuMetrics.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val depth by animateDpAsState(
        targetValue = if (pressed) 2.dp else 6.dp,
        animationSpec = spring(stiffness = 900f),
        label = "buttonDepth",
    )
    val fill = fillColor ?: if (accent) palette.accent else palette.surface
    val contentColor = when {
        !enabled -> palette.muted
        contentTint != null -> contentTint
        accent -> palette.onAccent
        else -> palette.textPrimary
    }
    val shape = RoundedCornerShape(height / 2)
    Row(
        modifier = modifier
            .height(height)
            .defaultMinSize(minWidth = 48.dp)
            .neuSurface(
                palette = palette,
                fill = fill,
                radius = height / 2,
                tone = NeuTone.Raised,
                depth = depth,
                blur = metrics.blur,
                glowColor = if (accent && enabled) palette.accent.copy(alpha = 0.55f) else null,
            )
            .clip(shape)
            .then(
                if (accent && enabled && fillColor == null) {
                    Modifier
                        .background(palette.accentGradient, shape)
                        .border(1.dp, Color.White.copy(alpha = 0.18f), shape)
                } else {
                    Modifier
                },
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 22.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            VIconImage(icon = icon, tint = contentColor, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun NeuIconButton(
    icon: VIcon,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    accent: Boolean = false,
    enabled: Boolean = true,
    tint: Color? = null,
) {
    val palette = LocalPalette.current
    val metrics = LocalNeuMetrics.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val depth by animateDpAsState(
        targetValue = if (pressed) 2.dp else 5.dp,
        animationSpec = spring(stiffness = 900f),
        label = "iconButtonDepth",
    )
    val iconTint = when {
        !enabled -> palette.muted
        tint != null -> tint
        accent -> palette.onAccent
        else -> palette.textPrimary
    }
    Box(
        modifier = modifier
            .size(size)
            .neuSurface(
                palette = palette,
                fill = if (accent) palette.accent else palette.surface,
                radius = size / 2,
                tone = NeuTone.Raised,
                depth = depth,
                blur = metrics.blur,
                circle = true,
                glowColor = if (accent && enabled) palette.accent.copy(alpha = 0.55f) else null,
            )
            .clip(CircleShape)
            .then(
                if (accent && enabled) {
                    Modifier
                        .background(palette.accentGradient, CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                } else {
                    Modifier
                },
            )
            .semantics { this.contentDescription = contentDescription }
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        VIconImage(icon = icon, tint = iconTint, modifier = Modifier.size(size * 0.42f))
    }
}

@Composable
fun NeuSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val palette = LocalPalette.current
    val knobOffset by animateDpAsState(
        targetValue = if (checked) 25.dp else 3.dp,
        animationSpec = spring(stiffness = 700f, dampingRatio = 0.82f),
        label = "switchKnob",
    )
    val trackFill by animateColorAsState(
        targetValue = when {
            !enabled -> palette.track
            checked -> palette.accent
            else -> palette.background
        },
        label = "switchTrack",
    )
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(54.dp, 31.dp)
            .neuSurface(
                palette = palette,
                fill = trackFill,
                radius = 16.dp,
                tone = NeuTone.Inset,
                depth = 4.dp,
            )
            .clip(CircleShape)
            .then(
                if (checked && enabled) {
                    Modifier.background(palette.accentGradient, CircleShape)
                } else {
                    Modifier
                },
            )
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(knobOffset.roundToPx(), 3.dp.roundToPx()) }
                .size(25.dp)
                .neuSurface(
                    palette = palette,
                    fill = palette.surfaceHigh,
                    radius = 13.dp,
                    tone = NeuTone.Raised,
                    depth = 3.dp,
                    blur = 9.dp,
                    circle = true,
                )
                .clip(CircleShape),
        )
    }
}

@Composable
fun NeuSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    label: String? = null,
    enabled: Boolean = true,
) {
    val palette = LocalPalette.current
    var widthPx by remember { mutableFloatStateOf(1f) }
    val start = valueRange.start
    val end = valueRange.endInclusive
    val span = (end - start).takeIf { it != 0f } ?: 1f
    val fraction = ((value - start) / span).coerceIn(0f, 1f)

    fun updateFromX(x: Float) {
        val p = (x / widthPx).coerceIn(0f, 1f)
        onValueChange(start + p * span)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .onSizeChanged { widthPx = it.width.toFloat() }
            .pointerInput(enabled, valueRange) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    widthPx = size.width.toFloat()
                    updateFromX(down.position.x)
                    down.consume()
                    var event = awaitPointerEvent()
                    while (event.changes.any { it.pressed }) {
                        val change = event.changes.firstOrNull { it.pressed }
                        if (change != null) {
                            updateFromX(change.position.x)
                            change.consume()
                        }
                        event = awaitPointerEvent()
                    }
                }
            }
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                setProgress { target ->
                    onValueChange(start + target.coerceIn(0f, 1f) * span)
                    true
                }
                if (label != null) contentDescription = label
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val trackHeight = 12.dp.toPx()
            val centerY = size.height / 2f
            val thumbRadius = 13.dp.toPx()
            val inset = thumbRadius
            val trackWidth = size.width - thumbRadius * 2f
            val trackRect = Rect(
                left = inset,
                top = centerY - trackHeight / 2f,
                right = inset + trackWidth,
                bottom = centerY + trackHeight / 2f,
            )
            drawRoundRect(
                color = palette.track,
                topLeft = trackRect.topLeft,
                size = trackRect.size,
                cornerRadius = CornerRadius(trackHeight / 2f),
            )
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        palette.darkShadow.copy(alpha = palette.darkShadowAlpha * 0.45f),
                        Color.Transparent,
                    ),
                    startY = trackRect.top,
                    endY = trackRect.top + trackHeight / 2f,
                ),
                topLeft = trackRect.topLeft,
                size = Size(trackRect.width, trackHeight / 2f),
            )
            val activeWidth = trackWidth * fraction
            if (activeWidth > 0f && enabled) {
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(palette.accent, palette.accentDeep),
                        startX = trackRect.left,
                        endX = trackRect.left + activeWidth.coerceAtLeast(1f),
                    ),
                    topLeft = trackRect.topLeft,
                    size = Size(activeWidth, trackHeight),
                    cornerRadius = CornerRadius(trackHeight / 2f),
                )
            }
            val thumbCenter = Offset(inset + activeWidth, centerY)
            drawNeuShadow(
                color = palette.darkShadow,
                rect = Rect(
                    thumbCenter.x - thumbRadius,
                    thumbCenter.y - thumbRadius,
                    thumbCenter.x + thumbRadius,
                    thumbCenter.y + thumbRadius,
                ),
                cornerRadius = thumbRadius,
                offset = Offset(2.5.dp.toPx(), 4.dp.toPx()),
                blur = 9.dp.toPx(),
                layers = 9,
                peakAlpha = palette.darkShadowAlpha * 0.8f,
            )
            drawCircle(
                color = if (enabled) palette.surfaceHigh else palette.track,
                radius = thumbRadius,
                center = thumbCenter,
            )
            drawCircle(
                color = palette.accent.copy(alpha = if (enabled) 0.95f else 0.35f),
                radius = 4.5.dp.toPx(),
                center = thumbCenter,
            )
        }
    }
}

@Composable
fun NeuChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val palette = LocalPalette.current
    val metrics = LocalNeuMetrics.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val fill = when {
        !enabled -> palette.track
        selected -> palette.accent
        else -> palette.surface
    }
    val textColor = when {
        !enabled -> palette.muted
        selected -> palette.onAccent
        else -> palette.textSecondary
    }
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier = modifier
            .height(44.dp)
            .defaultMinSize(minWidth = 64.dp)
            .neuSurface(
                palette = palette,
                fill = fill,
                radius = 22.dp,
                tone = NeuTone.Raised,
                depth = if (pressed) 2.dp else 4.dp,
                blur = metrics.blur,
                glowColor = if (selected) palette.accent.copy(alpha = 0.40f) else null,
            )
            .clip(shape)
            .then(
                if (selected && enabled) {
                    Modifier
                        .background(palette.accentGradient, shape)
                        .border(1.dp, Color.White.copy(alpha = 0.18f), shape)
                } else {
                    Modifier.border(1.dp, palette.hairline, shape)
                },
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = textColor,
            maxLines = 1,
        )
    }
}

@Composable
fun NeuTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    minLines: Int = 1,
    maxLines: Int = 5,
    singleLine: Boolean = minLines == 1 && maxLines == 1,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    textStyle: TextStyle? = null,
    enabled: Boolean = true,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val palette = LocalPalette.current
    val metrics = LocalNeuMetrics.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val baseStyle = textStyle ?: MaterialTheme.typography.bodyLarge
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .neuSurface(
                palette = palette,
                fill = palette.background,
                radius = 18.dp,
                tone = NeuTone.Inset,
                depth = 4.dp,
                blur = metrics.blur,
            )
            .clip(shape)
            .border(
                width = 1.dp,
                color = if (focused) palette.accent.copy(alpha = 0.55f) else Color.Transparent,
                shape = shape,
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    style = baseStyle.copy(color = palette.muted),
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                interactionSource = interaction,
                textStyle = baseStyle.copy(color = palette.textPrimary),
                cursorBrush = SolidColor(palette.accent),
                singleLine = singleLine,
                minLines = minLines,
                maxLines = maxLines,
                keyboardOptions = keyboardOptions,
                visualTransformation = visualTransformation,
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

/** A tinted, soft-cornered tile used to anchor section headers. */
@Composable
fun NeuIconTile(
    icon: VIcon,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    tint: Color = LocalPalette.current.accent,
) {
    val palette = LocalPalette.current
    val shape = RoundedCornerShape(size * 0.34f)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(palette.accentTint, palette.accentTint.copy(alpha = 0.55f)),
                ),
            )
            .border(1.dp, tint.copy(alpha = 0.10f), shape),
        contentAlignment = Alignment.Center,
    ) {
        VIconImage(icon = icon, tint = tint, modifier = Modifier.size(size * 0.46f))
    }
}

@Composable
fun NeuSectionTitle(text: String, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    Text(
        text = text,
        modifier = modifier.padding(start = 8.dp, bottom = 10.dp),
        style = MaterialTheme.typography.labelMedium,
        color = palette.muted,
    )
}

@Composable
fun NeuScreenBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val palette = LocalPalette.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    Brush.verticalGradient(
                        colors = listOf(
                            palette.background,
                            palette.background,
                            palette.background,
                        ),
                    ),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            palette.accent.copy(alpha = if (palette.isDark) 0.14f else 0.08f),
                            Color.Transparent,
                        ),
                        center = Offset(size.width * 0.86f, size.height * 0.02f),
                        radius = size.width * 0.72f,
                    ),
                    radius = size.width * 0.72f,
                    center = Offset(size.width * 0.86f, size.height * 0.02f),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            palette.lightShadow.copy(alpha = if (palette.isDark) 0.05f else 0.30f),
                            Color.Transparent,
                        ),
                        center = Offset(size.width * 0.08f, size.height * 0.92f),
                        radius = size.width * 0.7f,
                    ),
                    radius = size.width * 0.7f,
                    center = Offset(size.width * 0.08f, size.height * 0.92f),
                )
            },
        content = content,
    )
}

/** Keeps a single implementation of body text so every screen uses identical metrics. */
@Composable
fun NeuBodyText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LocalPalette.current.textSecondary,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    textAlign: TextAlign? = null,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        style = style,
        textAlign = textAlign,
    )
}

@Composable
fun NeuDivider(modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color.Transparent,
                        palette.hairline,
                        Color.Transparent,
                    ),
                ),
            ),
    )
}

@Composable
fun NeuLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        color = LocalPalette.current.textSecondary,
    )
}
