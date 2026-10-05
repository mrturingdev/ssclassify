package com.mrturingdev.ssclassify.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import image_categorizer.shared.generated.resources.Res
import image_categorizer.shared.generated.resources.quicksand_semibold
import org.jetbrains.compose.resources.Font

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
        drawPath(logoS(scaleX, 30f * scaleX, 34f * scaleY), primaryColor, style = monogramStroke)

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

/**
 * The logo's left S as a glyph: 21 x 33 units at [scale], top-left corner at ([dx], [dy]).
 */
private fun logoS(scale: Float, dx: Float, dy: Float) = Path().apply {
    fun x(v: Float) = dx + (v - 30f) * scale
    fun y(v: Float) = dy + (v - 34f) * scale
    moveTo(x(44f), y(34f))
    cubicTo(x(37f), y(34f), x(33f), y(37.5f), x(33f), y(42f))
    cubicTo(x(33f), y(47.5f), x(41f), y(49f), x(45f), y(51f))
    cubicTo(x(49f), y(53f), x(51f), y(55.5f), x(51f), y(59f))
    cubicTo(x(51f), y(64f), x(46.5f), y(67f), x(40f), y(67f))
    cubicTo(x(34.5f), y(67f), x(31f), y(63.5f), x(30f), y(60f))
}

/**
 * "S.S. Classify" wordmark: the S's are drawn with the logo's S glyph, sized and weighted
 * to match Quicksand SemiBold (cap height 0.7em, stem 0.1em, period 0.114em) used for "Classify".
 */
@Composable
fun AppWordmark(
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 20.sp,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    accentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    val em = with(LocalDensity.current) { fontSize.toDp() }
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = "S.S. Classify" },
        horizontalArrangement = Arrangement.spacedBy(em * 0.22f),
    ) {
        // Bottom edge sits on the text baseline.
        Canvas(Modifier.size(em * 1.516f, em * 0.7f).alignBy { it.measuredHeight }) {
            val e = size.height / 0.7f
            val stem = 0.1f * e
            val stroke = Stroke(width = stem, cap = StrokeCap.Round, join = StrokeJoin.Round)
            val scale = (0.7f * e - stem) / 33f
            val dotY = size.height - 0.063f * e
            drawPath(logoS(scale, 0.10f * e, stem / 2), primaryColor, style = stroke)
            drawCircle(accentColor, radius = 0.057f * e, center = Offset(0.645f * e, dotY))
            drawPath(logoS(scale, 0.858f * e, stem / 2), accentColor, style = stroke)
            drawCircle(accentColor, radius = 0.057f * e, center = Offset(1.403f * e, dotY))
        }
        Text(
            "Classify",
            modifier = Modifier.alignByBaseline(),
            color = accentColor,
            fontSize = fontSize,
            fontFamily = FontFamily(Font(Res.font.quicksand_semibold, FontWeight.SemiBold)),
            fontWeight = FontWeight.SemiBold,
        )
    }
}
