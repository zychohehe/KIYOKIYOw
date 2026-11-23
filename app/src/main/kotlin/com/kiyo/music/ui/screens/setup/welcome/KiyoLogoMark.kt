// the kiyo logo drawn live: the sound wave breathes and a faint ripple leaves it, like the mark is playing

package com.kiyo.music.ui.screens.setup.welcome

import androidx.compose.foundation.Canvas
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.runtime.Composable
import kotlin.math.sin

@Composable
fun KiyoLogoMark(modifier: Modifier = Modifier, seconds: () -> Float) {
    Canvas(modifier) {
        val t = seconds()
        val f = size.minDimension / 115f
        fun p(x: Float, y: Float) = Offset(size.width / 2f + (x - 116f) * f, size.height / 2f + (y - 118f) * f)

        drawRoundRect(Color(0xFF212121), topLeft = p(58.5f, 60.5f), size = Size(115f * f, 115f * f), cornerRadius = CornerRadius(37.5f * f))
        drawRoundRect(Color.White, topLeft = p(77.5f, 79.5f), size = Size(77f * f, 77f * f), cornerRadius = CornerRadius(18.5f * f))

        val brush = Brush.linearGradient(listOf(Color(0xFF1B1F3B), Color(0xFF4F5BD5)), start = p(96f, 100f), end = p(140f, 136f))
        val tri = Path().apply {
            moveTo(p(96f, 100f).x, p(96f, 100f).y)
            lineTo(p(96f, 136f).x, p(96f, 136f).y)
            lineTo(p(124f, 118f).x, p(124f, 118f).y)
            close()
        }
        drawPath(tri, brush)
        drawPath(tri, brush, style = Stroke(11f * f, cap = StrokeCap.Round, join = StrokeJoin.Round))

        fun arc() = Path().apply {
            moveTo(p(138f, 104f).x, p(138f, 104f).y)
            quadraticTo(p(147f, 118f).x, p(147f, 118f).y, p(138f, 132f).x, p(138f, 132f).y)
        }
        // the wave breathes
        val breath = 0.5f + 0.5f * sin(t * 4.2f)
        drawPath(arc(), brush, alpha = 0.7f + 0.3f * breath, style = Stroke(11f * f, cap = StrokeCap.Round))
        // and a thin ripple leaves it
        val phase = (t * 0.9f) % 1f
        scale(1f + 0.5f * phase, p(130f, 118f)) {
            drawPath(arc(), brush, alpha = (1f - phase) * 0.55f, style = Stroke(5f * f / (1f + 0.5f * phase), cap = StrokeCap.Round))
        }
    }
}
