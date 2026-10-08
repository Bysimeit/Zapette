package dev.zapette.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object ZIcons {
    val Live = icon("live") {
        roundRect(2.5f, 6.5f, 21.5f, 19.5f, 2f)
        moveTo(8f, 2.5f)
        lineTo(12f, 6.5f)
        lineTo(16f, 2.5f)
    }

    val Movies = icon("movies") {
        roundRect(3f, 3f, 21f, 21f, 2f)
        moveTo(7.5f, 3f)
        lineTo(7.5f, 21f)
        moveTo(16.5f, 3f)
        lineTo(16.5f, 21f)
        for (y in listOf(8f, 12f, 16f)) {
            moveTo(3f, y)
            lineTo(7.5f, y)
            moveTo(16.5f, y)
            lineTo(21f, y)
        }
    }

    val Series = icon("series") {
        roundRect(2.5f, 8.5f, 17.5f, 20.5f, 1.5f)
        moveTo(5.5f, 5.5f)
        lineTo(20.5f, 5.5f)
        lineTo(20.5f, 17f)
        moveTo(8.5f, 2.5f)
        lineTo(21.5f, 2.5f)
    }

    val Search = icon("search") {
        circle(10.5f, 10.5f, 6.5f)
        moveTo(15.5f, 15.5f)
        lineTo(21f, 21f)
    }

    val Settings = icon("settings") {
        for ((y, knob) in listOf(6f to 8f, 12f to 16f, 18f to 11f)) {
            moveTo(3f, y)
            lineTo(21f, y)
            moveTo(knob, y - 2.5f)
            lineTo(knob, y + 2.5f)
        }
    }

    private fun icon(name: String, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
            .path(
                fill = null,
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = block,
            )
            .build()

    private fun PathBuilder.roundRect(left: Float, top: Float, right: Float, bottom: Float, r: Float) {
        moveTo(left + r, top)
        lineTo(right - r, top)
        quadTo(right, top, right, top + r)
        lineTo(right, bottom - r)
        quadTo(right, bottom, right - r, bottom)
        lineTo(left + r, bottom)
        quadTo(left, bottom, left, bottom - r)
        lineTo(left, top + r)
        quadTo(left, top, left + r, top)
        close()
    }

    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx - r, cy)
        arcToRelative(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, dx1 = 2 * r, dy1 = 0f)
        arcToRelative(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, dx1 = -2 * r, dy1 = 0f)
        close()
    }
}
