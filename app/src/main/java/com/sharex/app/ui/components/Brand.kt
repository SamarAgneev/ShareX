package com.sharex.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sharex.app.ui.theme.ShareX

/** ShareX teal — the mark stays this colour in both themes. */
val BrandMark = Color(0xFF19B7A5)

/**
 * The ShareX mark: an X whose four ends are connected nodes (devices exchanging files).
 * Same geometry as the launcher icon (108 x 108 viewport).
 */
@Composable
fun ShareXMark(size: Dp, modifier: Modifier = Modifier, color: Color = BrandMark) {
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension / 108f
        val mark = Path().apply {
            moveTo(36f * s, 36f * s); lineTo(72f * s, 72f * s)
            moveTo(36f * s, 72f * s); lineTo(72f * s, 36f * s)
        }
        drawPath(mark, color, style = Stroke(width = 8.5f * s, cap = StrokeCap.Round, join = StrokeJoin.Round))
        listOf(36f to 36f, 72f to 72f, 36f to 72f, 72f to 36f).forEach { (x, y) ->
            drawCircle(color, radius = 7.5f * s, center = Offset(x * s, y * s))
        }
    }
}

/** Mark + ShareX lockup used in the home header. */
@Composable
fun Wordmark(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ShareXMark(30.dp)
        Text("ShareX", style = MaterialTheme.typography.headlineSmall, color = ShareX.colors.text)
    }
}
