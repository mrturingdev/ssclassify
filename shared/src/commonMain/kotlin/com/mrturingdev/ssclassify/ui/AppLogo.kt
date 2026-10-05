package com.mrturingdev.ssclassify.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Official App Logo for S.S. Classify (Variant 3: The Viewfinder Twin-S).
 * Features precision screenshot capture brackets enclosing an interlocking dual-S monogram.
 */
@Composable
fun AppLogo(
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    accentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Canvas(modifier = modifier.size(size)) {
        val scaleX = this.size.width / 100f
        val scaleY = this.size.height / 100f

        val bracketStroke = Stroke(
            width = 2.5f * scaleX,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )

        val monogramStroke = Stroke(
            width = 3.6f * scaleX,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )

        // 1. Viewfinder Brackets (Accent / onSurface color)
        // Top-Left
        val tlPath = Path().apply {
            moveTo(23f * scaleX, 33f * scaleY)
            lineTo(23f * scaleX, 25f * scaleY)
            cubicTo(23f * scaleX, 23f * scaleY, 25f * scaleX, 21f * scaleY, 27f * scaleX, 21f * scaleY)
            lineTo(35f * scaleX, 21f * scaleY)
        }
        drawPath(tlPath, accentColor.copy(alpha = 0.85f), style = bracketStroke)

        // Top-Right
        val trPath = Path().apply {
            moveTo(65f * scaleX, 21f * scaleY)
            lineTo(73f * scaleX, 21f * scaleY)
            cubicTo(75f * scaleX, 21f * scaleY, 77f * scaleX, 23f * scaleY, 77f * scaleX, 25f * scaleY)
            lineTo(77f * scaleX, 33f * scaleY)
        }
        drawPath(trPath, accentColor.copy(alpha = 0.85f), style = bracketStroke)

        // Bottom-Right
        val brPath = Path().apply {
            moveTo(77f * scaleX, 67f * scaleY)
            lineTo(77f * scaleX, 75f * scaleY)
            cubicTo(77f * scaleX, 77f * scaleY, 75f * scaleX, 79f * scaleY, 73f * scaleX, 79f * scaleY)
            lineTo(65f * scaleX, 79f * scaleY)
        }
        drawPath(brPath, accentColor.copy(alpha = 0.85f), style = bracketStroke)

        // Bottom-Left
        val blPath = Path().apply {
            moveTo(35f * scaleX, 79f * scaleY)
            lineTo(27f * scaleX, 79f * scaleY)
            cubicTo(25f * scaleX, 79f * scaleY, 23f * scaleX, 77f * scaleY, 23f * scaleX, 75f * scaleY)
            lineTo(23f * scaleX, 67f * scaleY)
        }
        drawPath(blPath, accentColor.copy(alpha = 0.85f), style = bracketStroke)

        // 2. Left S Monogram (Primary Brand Color)
        val leftSPath = Path().apply {
            moveTo(44f * scaleX, 34f * scaleY)
            cubicTo(37f * scaleX, 34f * scaleY, 33f * scaleX, 37.5f * scaleY, 33f * scaleX, 42f * scaleY)
            cubicTo(33f * scaleX, 47.5f * scaleY, 41f * scaleX, 49f * scaleY, 45f * scaleX, 51f * scaleY)
            cubicTo(49f * scaleX, 53f * scaleY, 51f * scaleX, 55.5f * scaleY, 51f * scaleX, 59f * scaleY)
            cubicTo(51f * scaleX, 64f * scaleY, 46.5f * scaleX, 67f * scaleY, 40f * scaleX, 67f * scaleY)
            cubicTo(34.5f * scaleX, 67f * scaleY, 31f * scaleX, 63.5f * scaleY, 30f * scaleX, 60f * scaleY)
        }
        drawPath(leftSPath, primaryColor, style = monogramStroke)

        // 3. Right S Monogram (Interlocked, Accent / Primary color)
        val rightSPath = Path().apply {
            moveTo(62f * scaleX, 33f * scaleY)
            cubicTo(67.5f * scaleX, 33f * scaleY, 71f * scaleX, 36.5f * scaleY, 71f * scaleX, 41f * scaleY)
            cubicTo(71f * scaleX, 45.5f * scaleY, 67f * scaleX, 48f * scaleY, 61f * scaleX, 50f * scaleY)
            cubicTo(56f * scaleX, 51.5f * scaleY, 52f * scaleX, 53.5f * scaleY, 52f * scaleX, 58f * scaleY)
            cubicTo(52f * scaleX, 62.5f * scaleY, 56.5f * scaleX, 66f * scaleY, 63f * scaleX, 66f * scaleY)
            cubicTo(68f * scaleX, 66f * scaleY, 72f * scaleX, 63f * scaleY, 73f * scaleX, 59f * scaleY)
        }
        drawPath(rightSPath, accentColor, style = monogramStroke)
    }
}
