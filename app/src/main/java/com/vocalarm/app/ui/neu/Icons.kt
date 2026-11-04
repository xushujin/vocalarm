package com.vocalarm.app.ui.neu

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vocalarm.app.ui.theme.LocalPalette
import kotlin.math.min

/**
 * Hand-drawn icon set. Keeping the glyphs in code means no icon dependency and one visual language
 * across every screen.
 */
enum class VIcon {
    Plus,
    Minus,
    Close,
    Check,
    Play,
    Pause,
    Stop,
    Record,
    Trash,
    Mic,
    Clock,
    ChevronDown,
    ChevronRight,
    Refresh,
    Download,
    Info,
    Droplet,
    Bell,
    Volume,
    Loop,
    Speed,
    Snooze,
    Edit,
}

@Composable
fun VIconImage(
    icon: VIcon,
    modifier: Modifier = Modifier,
    tint: Color = LocalPalette.current.textPrimary,
    strokeWidth: Dp = 1.9.dp,
) {
    Canvas(modifier = modifier) {
        val box = 24f
        val scale = min(size.width, size.height) / box
        if (scale <= 0f) return@Canvas
        val offsetX = (size.width - box * scale) / 2f
        val offsetY = (size.height - box * scale) / 2f
        val stroke = strokeWidth.toPx() / scale
        withTransform({
            translate(offsetX, offsetY)
            scale(scale, scale, pivot = Offset.Zero)
        }) {
            drawVocalarmIcon(icon, tint, stroke)
        }
    }
}

private fun DrawScope.drawVocalarmIcon(icon: VIcon, color: Color, sw: Float) {
    when (icon) {
        VIcon.Plus -> {
            line(color, 12f, 5f, 12f, 19f, sw)
            line(color, 5f, 12f, 19f, 12f, sw)
        }

        VIcon.Minus -> line(color, 5f, 12f, 19f, 12f, sw)

        VIcon.Close -> {
            line(color, 6.5f, 6.5f, 17.5f, 17.5f, sw)
            line(color, 17.5f, 6.5f, 6.5f, 17.5f, sw)
        }

        VIcon.Check -> polyline(color, listOf(5f to 12.5f, 10f to 17.5f, 19f to 6.5f), sw)

        VIcon.Play -> {
            val path = Path().apply {
                moveTo(8f, 5f)
                lineTo(19f, 12f)
                lineTo(8f, 19f)
                close()
            }
            drawPath(path, color)
        }

        VIcon.Pause -> {
            drawRoundRect(color, Offset(7.4f, 5f), Size(3.6f, 14f), CornerRadius(1.8f))
            drawRoundRect(color, Offset(13f, 5f), Size(3.6f, 14f), CornerRadius(1.8f))
        }

        VIcon.Stop -> drawRoundRect(
            color = color,
            topLeft = Offset(6.5f, 6.5f),
            size = Size(11f, 11f),
            cornerRadius = CornerRadius(2.6f),
        )

        VIcon.Record -> drawCircle(color, radius = 5.4f, center = Offset(12f, 12f))

        VIcon.Trash -> {
            line(color, 4.4f, 6.8f, 19.6f, 6.8f, sw)
            polyline(
                color,
                listOf(9.2f to 4.4f, 14.8f to 4.4f),
                sw,
            )
            line(color, 9.6f, 4.4f, 9.6f, 6.8f, sw)
            line(color, 14.4f, 4.4f, 14.4f, 6.8f, sw)
            val body = Path().apply {
                moveTo(6.9f, 8.2f)
                lineTo(7.6f, 18.4f)
                cubicTo(7.7f, 19.5f, 8.5f, 20.2f, 9.6f, 20.2f)
                lineTo(14.4f, 20.2f)
                cubicTo(15.5f, 20.2f, 16.3f, 19.5f, 16.4f, 18.4f)
                lineTo(17.1f, 8.2f)
            }
            drawPath(body, color, style = strokeStyle(sw))
            line(color, 10.4f, 10.6f, 10.7f, 17f, sw)
            line(color, 13.6f, 10.6f, 13.3f, 17f, sw)
        }

        VIcon.Mic -> {
            drawRoundRect(
                color = color,
                topLeft = Offset(9f, 3.2f),
                size = Size(6f, 10.6f),
                cornerRadius = CornerRadius(3f),
                style = strokeStyle(sw),
            )
            val cradle = Path().apply {
                moveTo(5.9f, 11.4f)
                cubicTo(5.9f, 15.4f, 8.6f, 18.2f, 12f, 18.2f)
                cubicTo(15.4f, 18.2f, 18.1f, 15.4f, 18.1f, 11.4f)
            }
            drawPath(cradle, color, style = strokeStyle(sw))
            line(color, 12f, 18.2f, 12f, 20.8f, sw)
            line(color, 8.6f, 20.8f, 15.4f, 20.8f, sw)
        }

        VIcon.Clock -> {
            drawCircle(color, radius = 8.1f, center = Offset(12f, 12f), style = strokeStyle(sw))
            polyline(color, listOf(12f to 7.2f, 12f to 12f, 15.8f to 13.9f), sw)
            drawCircle(color, radius = 1.1f, center = Offset(12f, 12f))
        }

        VIcon.ChevronDown -> polyline(color, listOf(6.5f to 9.5f, 12f to 15f, 17.5f to 9.5f), sw)

        VIcon.ChevronRight -> polyline(color, listOf(9.5f to 6.5f, 15f to 12f, 9.5f to 17.5f), sw)

        VIcon.Refresh -> {
            drawArc(
                color = color,
                startAngle = 40f,
                sweepAngle = 280f,
                useCenter = false,
                topLeft = Offset(4.2f, 4.2f),
                size = Size(15.6f, 15.6f),
                style = strokeStyle(sw),
            )
            polyline(color, listOf(13.6f to 2.8f, 17.2f to 3.4f, 16.6f to 7f), sw)
        }

        VIcon.Download -> {
            line(color, 12f, 3.8f, 12f, 14.6f, sw)
            polyline(color, listOf(7.4f to 10.2f, 12f to 14.8f, 16.6f to 10.2f), sw)
            line(color, 5f, 19.6f, 19f, 19.6f, sw)
        }

        VIcon.Info -> {
            drawCircle(color, radius = 8.5f, center = Offset(12f, 12f), style = strokeStyle(sw))
            drawCircle(color, radius = 1.15f, center = Offset(12f, 7.9f))
            line(color, 12f, 11f, 12f, 16.6f, sw)
        }

        VIcon.Droplet -> {
            val drop = Path().apply {
                moveTo(12f, 3.4f)
                cubicTo(12f, 3.4f, 5.6f, 10.6f, 5.6f, 14.4f)
                cubicTo(5.6f, 18.2f, 8.5f, 20.6f, 12f, 20.6f)
                cubicTo(15.5f, 20.6f, 18.4f, 18.2f, 18.4f, 14.4f)
                cubicTo(18.4f, 10.6f, 12f, 3.4f, 12f, 3.4f)
                close()
            }
            drawPath(drop, color, style = strokeStyle(sw))
        }

        VIcon.Bell -> {
            val bell = Path().apply {
                moveTo(8.2f, 17.3f)
                lineTo(8.2f, 12.6f)
                cubicTo(8.2f, 8.9f, 9.9f, 6.9f, 12f, 6.9f)
                cubicTo(14.1f, 6.9f, 15.8f, 8.9f, 15.8f, 12.6f)
                lineTo(15.8f, 17.3f)
            }
            drawPath(bell, color, style = strokeStyle(sw))
            line(color, 6.8f, 17.4f, 17.2f, 17.4f, sw)
            line(color, 12f, 4.4f, 12f, 6.6f, sw)
            drawCircle(color, radius = 1.05f, center = Offset(12f, 19.6f))
        }

        VIcon.Volume -> {
            val speaker = Path().apply {
                moveTo(4f, 9.7f)
                lineTo(7.4f, 9.7f)
                lineTo(11.4f, 5.8f)
                lineTo(11.4f, 18.2f)
                lineTo(7.4f, 14.3f)
                lineTo(4f, 14.3f)
                close()
            }
            drawPath(speaker, color, style = strokeStyle(sw))
            drawArc(
                color = color,
                startAngle = -54f,
                sweepAngle = 108f,
                useCenter = false,
                topLeft = Offset(10.6f, 7.4f),
                size = Size(8.2f, 9.2f),
                style = strokeStyle(sw),
            )
            drawArc(
                color = color,
                startAngle = -54f,
                sweepAngle = 108f,
                useCenter = false,
                topLeft = Offset(10.6f, 4.4f),
                size = Size(12.4f, 15.2f),
                style = strokeStyle(sw),
            )
        }

        VIcon.Loop -> {
            line(color, 4.6f, 8.4f, 15.6f, 8.4f, sw)
            polyline(color, listOf(13.2f to 5.6f, 16.2f to 8.4f, 13.2f to 11.2f), sw)
            line(color, 4.6f, 8.4f, 4.6f, 12f, sw)
            line(color, 19.4f, 15.6f, 8.4f, 15.6f, sw)
            polyline(color, listOf(10.8f to 12.8f, 7.8f to 15.6f, 10.8f to 18.4f), sw)
            line(color, 19.4f, 12f, 19.4f, 15.6f, sw)
        }

        VIcon.Speed -> {
            drawArc(
                color = color,
                startAngle = 155f,
                sweepAngle = 230f,
                useCenter = false,
                topLeft = Offset(4.2f, 4.2f),
                size = Size(15.6f, 15.6f),
                style = strokeStyle(sw),
            )
            line(color, 12f, 12f, 16.2f, 8f, sw)
            drawCircle(color, radius = 1.5f, center = Offset(12f, 12f))
        }

        VIcon.Snooze -> {
            val outer = Path().apply { addOval(Rect(4.6f, 3.6f, 19.4f, 18.4f)) }
            val cut = Path().apply { addOval(Rect(9.6f, 1.2f, 22.4f, 14.2f)) }
            val moon = Path.combine(PathOperation.Difference, outer, cut)
            drawPath(moon, color)
        }

        VIcon.Edit -> {
            val pencil = Path().apply {
                moveTo(4.6f, 19.4f)
                lineTo(5.6f, 15.1f)
                lineTo(15.6f, 5.1f)
                cubicTo(16.5f, 4.2f, 17.9f, 4.2f, 18.8f, 5.1f)
                cubicTo(19.7f, 6f, 19.7f, 7.4f, 18.8f, 8.3f)
                lineTo(8.9f, 18.4f)
                close()
            }
            drawPath(pencil, color, style = strokeStyle(sw))
            line(color, 13.6f, 7.1f, 16.9f, 10.4f, sw)
        }
    }
}

private fun strokeStyle(width: Float): Stroke =
    Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)

private fun DrawScope.line(color: Color, x1: Float, y1: Float, x2: Float, y2: Float, sw: Float) {
    drawLine(color, Offset(x1, y1), Offset(x2, y2), strokeWidth = sw, cap = StrokeCap.Round)
}

private fun DrawScope.polyline(color: Color, points: List<Pair<Float, Float>>, sw: Float) {
    if (points.size < 2) return
    val path = Path().apply {
        moveTo(points[0].first, points[0].second)
        for (index in 1 until points.size) {
            lineTo(points[index].first, points[index].second)
        }
    }
    drawPath(path, color, style = strokeStyle(sw))
}
