package com.dungeonmasterai.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.dungeonmasterai.R

/** Degrees clockwise from twelve. The screen paints these. It does not advance time. */
internal data class ClockHands(
    val hourDegrees: Float,
    val minuteDegrees: Float,
)

/**
 * How one hand picture sits on the face.
 * The files are square and include a counterweight almost as long as the tip.
 * Painting that tail runs the minute hand across the dial and turns the hour
 * hand into a diagonal. [tailFraction] keeps the medallion and drops the rest.
 * [scale] is the drawn size relative to the clock, so the hour tip stays short
 * of the numerals and the minute tip reaches them.
 */
internal data class HandPicture(
    val axle: Offset,
    val scale: Float,
    val tailFraction: Float,
)

private const val HandPictureSize = 1254f

/** Medallion at (626.5, 690). The tip is 447px above it. The circle ends at y=762. */
internal val HourPicture = HandPicture(
    axle = Offset(626.5f / HandPictureSize, 690f / HandPictureSize),
    scale = 0.50f,
    tailFraction = 762f / HandPictureSize,
)

/** Medallion at (627, 632). The tip is 494px above it. Scale 0.87 carries that tip to the numeral. */
internal val MinutePicture = HandPicture(
    axle = Offset(627f / HandPictureSize, 632f / HandPictureSize),
    scale = 0.87f,
    tailFraction = 710f / HandPictureSize,
)

/** Where [drawHand] puts the picture for a square clock of [clockSize]. */
internal data class HandLayout(
    val side: Float,
    val origin: Offset,
    val clipBottom: Float,
)

/**
 * Sideways copies of one hand, in pixels, overlapping so they read as one stroke.
 * A header-sized clock otherwise shrinks the minute shaft to a couple of pixels.
 * The large clock passes [bold] false and is drawn once.
 */
internal fun handOffsets(bold: Boolean): FloatArray {
    if (!bold) return floatArrayOf(0f)
    val step = 0.55f
    return FloatArray(7) { index -> (index - 3) * step }
}

/** The medallion lands on the clock center. The clip drops the counterweight. */
internal fun handLayout(picture: HandPicture, clockSize: Float): HandLayout {
    val side = clockSize * picture.scale
    val center = clockSize / 2f
    return HandLayout(
        side = side,
        origin = Offset(
            center - picture.axle.x * side,
            center - picture.axle.y * side,
        ),
        clipBottom = picture.tailFraction * side,
    )
}

internal fun clockHands(hour: Int, minute: Int): ClockHands {
    val hourOnFace = Math.floorMod(hour, 12)
    return ClockHands(
        hourDegrees = hourOnFace * 30f + minute * 0.5f,
        minuteDegrees = minute * 6f,
    )
}

@Composable
internal fun AnalogClock(
    hour: Int,
    minute: Int,
    modifier: Modifier = Modifier,
    boldHands: Boolean = false,
) {
    val face = painterResource(R.drawable.clock_face)
    val hourHand = painterResource(R.drawable.clock_hour_hand)
    val minuteHand = painterResource(R.drawable.clock_minute_hand)
    val hands = clockHands(hour, minute)
    Canvas(modifier = modifier.clearAndSetSemantics {}) {
        val square = Size(size.width, size.height)
        with(face) { draw(square) }
        drawHand(hourHand, hands.hourDegrees, HourPicture, boldHands)
        drawHand(minuteHand, hands.minuteDegrees, MinutePicture, boldHands)
    }
}

private fun DrawScope.drawHand(
    hand: Painter,
    degrees: Float,
    picture: HandPicture,
    bold: Boolean,
) {
    val layout = handLayout(picture, size.minDimension)
    rotate(degrees = degrees, pivot = center) {
        handOffsets(bold).forEach { sideways ->
            translate(left = layout.origin.x + sideways, top = layout.origin.y) {
                clipRect(right = layout.side, bottom = layout.clipBottom) {
                    with(hand) { draw(Size(layout.side, layout.side)) }
                }
            }
        }
    }
}
