package de.singular.crystalball.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.singular.crystalball.chords.MUTED
import de.singular.crystalball.chords.STRING_COUNT
import de.singular.crystalball.chords.Voicing

/**
 * A chord box: six strings across, [Voicing.WINDOW_FRETS] frets down, dots where fingers go.
 *
 * Reads like a printed chord chart — the low E string on the left, the nut as a thick bar when the
 * shape sits in open position, and an "5fr" marker instead when it does not. Open strings get a
 * circle above the nut and unplayed ones a cross.
 *
 * The whole thing scales from [width], so the same composable draws the large best-fit diagram and
 * the small ones in the alternatives rows.
 */
@Composable
fun ChordDiagram(
    voicing: Voicing,
    width: Dp,
    modifier: Modifier = Modifier,
    caption: String? = null,
    capo: Int = 0,
    dotColor: Color = MaterialTheme.colorScheme.primary,
) {
    val lineColor = MaterialTheme.colorScheme.onSurfaceVariant
    val markerColor = MaterialTheme.colorScheme.onSurfaceVariant
    val capoColor = MaterialTheme.colorScheme.primary
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = lineColor, fontSize = (width.value * LABEL_TEXT_RATIO).sp)

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            Modifier
                .width(width)
                .height(width * BOX_ASPECT),
        ) {
            drawChordBox(voicing, capo, lineColor, dotColor, markerColor, capoColor, measurer, labelStyle)
        }
        if (caption != null) {
            Spacer(Modifier.height(3.dp))
            Text(
                text = caption,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(width),
            )
        }
    }
}

/**
 * The chord box itself, on whatever surface you hand it.
 *
 * Colours and the measurer come in rather than being read from the theme, which is what lets the
 * PDF export replay this exact painter onto a page — black on white, at print size — instead of
 * keeping a second renderer that would drift from this one.
 */
internal fun DrawScope.drawChordBox(
    voicing: Voicing,
    capo: Int,
    lineColor: Color,
    dotColor: Color,
    markerColor: Color,
    capoColor: Color,
    measurer: TextMeasurer,
    labelStyle: TextStyle,
) {
    val w = size.width
    val h = size.height

    val open = voicing.isOpenPosition
    val base = voicing.baseFret
    val thin = (w * THIN_STROKE).coerceAtLeast(1f)

    // Room above the grid for the open/muted markers, and to the right for the "5fr" label. The
    // right gutter is measured rather than guessed: it has to hold the label *plus* the rounded cap
    // of a barre, which overhangs the outermost string by a dot radius. A fixed share of the width
    // either clipped "10fr" or wasted a gutter's worth of grid on shapes that carry no label.
    // The label says which fret the window starts at, as numbered on the guitar — so the capo is
    // added back on. The shape's own frets are counted from the capo, but the player reads the dots
    // on their neck, and a number that means neither is just confusing. The capo's own position is
    // not written here; the caption under the diagram already carries it.
    val label = if (open) null else measurer.measure("${base + capo}fr", labelStyle)
    val markerBand = h * MARKER_BAND
    val gridLeft = w * SIDE_GUTTER
    // Solve for the grid width that leaves room for the barre overhang the grid itself implies.
    val overhangRatio = DOT_RADIUS / (STRING_COUNT - 1)
    val labelSpace = if (label == null) 0f else label.size.width + thin * 2f
    val gridRight = ((w - gridLeft - labelSpace) + gridLeft * overhangRatio) / (1f + overhangRatio)
    val gridTop = markerBand
    val gridBottom = h
    val gridWidth = gridRight - gridLeft
    val fretGap = (gridBottom - gridTop) / Voicing.WINDOW_FRETS
    val stringGap = gridWidth / (STRING_COUNT - 1)
    val dotRadius = stringGap * DOT_RADIUS

    fun stringX(s: Int) = gridLeft + s * stringGap

    // Strings.
    for (s in 0 until STRING_COUNT) {
        drawLine(lineColor, Offset(stringX(s), gridTop), Offset(stringX(s), gridBottom), thin)
    }
    // Frets. The nut (top edge in open position) is drawn heavy — and when a capo is on, it *is*
    // the capo: a capo is simply a movable nut, so the shape behind it is fingered and drawn
    // exactly as an open-position shape, which is the whole reason this needs no other special
    // case. It takes the accent colour to say so.
    for (f in 0..Voicing.WINDOW_FRETS) {
        val y = gridTop + f * fretGap
        val heavy = f == 0 && open
        drawLine(
            if (heavy && capo > 0) capoColor else lineColor,
            Offset(gridLeft, y),
            Offset(gridRight, y),
            if (heavy) thin * NUT_STROKE_FACTOR else thin,
        )
    }

    if (label != null) {
        drawText(
            label,
            topLeft = Offset(
                gridRight + dotRadius + thin * 2f,
                gridTop + fretGap * 0.5f - label.size.height / 2f,
            ),
        )
    }

    val markerRadius = stringGap * MARKER_RADIUS

    // Open and muted markers above the grid.
    for (s in 0 until STRING_COUNT) {
        val cx = stringX(s)
        val cy = gridTop - markerBand * 0.45f
        when (voicing.frets[s]) {
            MUTED -> {
                val r = markerRadius
                drawLine(markerColor, Offset(cx - r, cy - r), Offset(cx + r, cy + r), thin * 1.4f)
                drawLine(markerColor, Offset(cx - r, cy + r), Offset(cx + r, cy - r), thin * 1.4f)
            }
            0 -> drawCircle(markerColor, markerRadius, Offset(cx, cy), style = Stroke(thin * 1.2f))
        }
    }

    // A barre, when one finger flattens across several strings at the shape's lowest fret.
    val barre = barreSpan(voicing)
    if (barre != null) {
        val y = gridTop + (voicing.lowestFret - base + 0.5f) * fretGap
        val left = stringX(barre.first)
        val right = stringX(barre.last)
        drawRoundRect(
            color = dotColor,
            topLeft = Offset(left - dotRadius, y - dotRadius),
            size = Size(right - left + dotRadius * 2, dotRadius * 2),
            cornerRadius = CornerRadius(dotRadius, dotRadius),
        )
    }

    // Fretted notes.
    for (s in 0 until STRING_COUNT) {
        val fret = voicing.frets[s]
        if (fret <= 0) continue
        val row = fret - base
        if (row !in 0 until Voicing.WINDOW_FRETS) continue
        val cx = stringX(s)
        val cy = gridTop + (row + 0.5f) * fretGap
        drawCircle(dotColor, dotRadius, Offset(cx, cy))
    }
}

/**
 * The string range a barre covers, or null if the shape has none.
 *
 * A barre is one finger laid flat: the shape's lowest fret is held on two or more strings, every
 * string between them is played (nothing is muted under the finger) and none of them is fretted
 * lower. Requiring a span of at least [MIN_BARRE_SPAN] keeps ordinary two-finger grips — where two
 * notes merely share a fret — from being drawn as a bar.
 */
private fun barreSpan(voicing: Voicing): IntRange? {
    val lowest = voicing.lowestFret
    if (lowest <= 0) return null // an open-position shape is barred by the nut, not a finger
    val at = (0 until STRING_COUNT).filter { voicing.frets[it] == lowest }
    if (at.size < 2) return null
    val span = at.first()..at.last()
    if (span.last - span.first + 1 < MIN_BARRE_SPAN) return null
    // Nothing muted or fretted lower underneath the finger.
    if (span.any { voicing.frets[it] == MUTED || voicing.frets[it] < lowest }) return null
    return span
}

/** Height of the box as a multiple of its width — a chord chart is taller than it is wide. */
internal const val BOX_ASPECT = 1.25f

/** Share of the height reserved above the grid for open/muted markers. */
private const val MARKER_BAND = 0.17f

/** Share of the width left free on the left, so a dot on the low E string is not clipped. */
private const val SIDE_GUTTER = 0.11f

private const val THIN_STROKE = 0.012f
private const val NUT_STROKE_FACTOR = 4f
private const val DOT_RADIUS = 0.30f
private const val MARKER_RADIUS = 0.19f
internal const val LABEL_TEXT_RATIO = 0.10f
private const val MIN_BARRE_SPAN = 3
