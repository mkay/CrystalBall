package de.singular.crystalball.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.singular.crystalball.ChordView
import de.singular.crystalball.R
import de.singular.crystalball.chords.Voicing
import kotlin.math.log10

/**
 * The furniture both the detect screen and the song screen are built from.
 *
 * These live here rather than in either screen because a capture is a listening screen too: the
 * same mark turning, the same meter, the same rows that have to scroll sideways on a phone.
 */

/** Diagram sizes: one to read at arm's length, one to scan a row of. */
val BEST_DIAGRAM_WIDTH = 172.dp
val SMALL_DIAGRAM_WIDTH = 74.dp

/** Clearance for the icon row floating at the top of the screen. */
val ICON_ROW_HEIGHT = 40.dp

val LOGO_SIZE = 132.dp
val BUTTON_HEIGHT = 64.dp
val BUTTON_MAX_WIDTH = 340.dp
const val SPIN_PERIOD_MS = 9000

/**
 * The wordmark and what the app is for, stacked above the mark on the home screen.
 *
 * The wordmark is a single-colour vector — the type is already outlined to paths — so it is tinted
 * here rather than carrying its own colour, and reads in either theme. Sized to the width it is
 * given, keeping the banner's aspect, so it never runs off the edge of a narrow phone. The tagline
 * underneath is live text rather than more outlined paths, so it stays legible at any size and is
 * read aloud as words; the wordmark carries no description of its own to keep it from being spoken
 * twice.
 */
@Composable
fun Claim(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.width(300.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(R.drawable.claim),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(CLAIM_ASPECT),
        )
        Text(
            text = "detects guitar chords\nand combines them into song sheets",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** The wordmark's width-to-height, from its 1263×270 artwork. */
private const val CLAIM_ASPECT = 1263f / 270f

/** The app mark. [rotation] turns the swirl, which the listening screens animate. */
@Composable
fun Logo(rotation: Float = 0f, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.ic_ball),
        contentDescription = null,
        modifier = modifier
            .size(LOGO_SIZE)
            .graphicsLayer { rotationZ = rotation },
    )
}

/**
 * The mark, turning while the microphone is open — the one moving thing on screen, so it is obvious
 * the app is still listening and has not simply frozen.
 */
@Composable
fun SpinningLogo() {
    val spin = rememberInfiniteTransition(label = "spin")
    val angle by spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(SPIN_PERIOD_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "angle",
    )
    Logo(rotation = angle)
}

/**
 * Input level, so it is obvious the microphone is actually hearing the guitar.
 *
 * Scaled in decibels rather than linearly. Hearing is logarithmic and the signals here span a huge
 * range — an unplugged hollow-body sits near -50 dBFS where a linear meter shows a sliver of
 * movement indistinguishable from nothing, which is exactly the case where the user most needs to
 * see that the app can hear them.
 */
@Composable
fun LevelMeter(level: Float) {
    val db = 20f * log10(level.coerceAtLeast(MIN_LEVEL))
    val target = ((db - METER_FLOOR_DB) / -METER_FLOOR_DB).coerceIn(0f, 1f)
    val width by animateFloatAsState(target, tween(90), label = "level")
    Box(
        Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            Modifier
                .fillMaxWidth(width)
                .height(6.dp)
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

/** Bottom of the meter's range. Below this it is room tone, not an instrument. */
private const val METER_FLOOR_DB = -60f

/** Floor for the log, guarding log10(0) on digital silence. */
private const val MIN_LEVEL = 0.001f

/**
 * What the capo control says, wherever it is drawn.
 *
 * States the fret rather than merely inviting a change: the idle screen and the song editor draw no
 * diagram, so there is no accent-coloured nut to do the reminding, and a capo left set from
 * yesterday would otherwise stay invisible. It stops at stating it — that a button can be pressed is
 * the button's job to say, not the sentence's.
 */
fun capoLabel(capo: Int): String = if (capo == 0) "Set capo" else "Capo set to $capo"

/**
 * The way to the capo, from wherever you are when you notice it is wrong.
 *
 * Full width and standing alone, for the pages whose business is elsewhere. Where the capo belongs
 * inside a block of text instead, the label goes on a plain button rather than this one.
 */
@Composable
fun CapoLink(capo: Int, onSetCapo: () -> Unit) {
    TextButton(
        onClick = onSetCapo,
        shape = ControlShape,
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = BUTTON_MAX_WIDTH)
            .height(48.dp),
    ) {
        Text(capoLabel(capo), style = MaterialTheme.typography.titleSmall)
    }
}

/** A scrolling row of choices — twelve roots do not fit across a phone. */
@Composable
fun ChipRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}

/**
 * Chips that wrap instead of scrolling away.
 *
 * For a set that grows while you are watching it — a part being captured — where [ChipRow] is
 * exactly wrong: about four fit across a phone, and the fifth would land off-screen, so the one
 * piece of feedback that says "that chord landed" would be the one you cannot see.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipFlow(content: @Composable () -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        content()
    }
}

/**
 * Chord boxes that wrap instead of scrolling away.
 *
 * For a set you are meant to read as a whole — the chords of a part, or every way to play one —
 * rather than glance along. Four fit across a phone, and a part is often longer than that.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiagramFlow(content: @Composable () -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(DIAGRAM_GAP, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(DIAGRAM_GAP),
    ) {
        content()
    }
}

/** The rows scroll sideways: five chord boxes do not fit across a phone at a readable size. */
@Composable
fun DiagramRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(DIAGRAM_GAP),
        verticalAlignment = Alignment.Top,
    ) {
        content()
    }
}

private val DIAGRAM_GAP: Dp = 10.dp

@Composable
fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
    )
}

/**
 * The variations row, made a picker — the whole reason a song is worth more than a list of chord
 * names: tapping one records *the shape your hands make*, rather than the one the library leads with.
 *
 * Shared by the detect screen's captured-chord editor and the song screen's part-chord editor, so a
 * chord is chosen the same way whether it is on its way into a song or already in one. It wraps
 * rather than scrolls — a chord with eight ways to play it would otherwise hide most of them.
 *
 * The choice is marked with a ring rather than a filled card. [ChordDiagram] draws its lines and
 * dots in colours picked for the surface underneath, so tinting that surface puts light on light in
 * the dark theme and dark on dark in the light one — the selected shape would be the one you could
 * not read. A ring sits outside the diagram and leaves it on the background it was drawn for.
 */
@Composable
fun VoicingPicker(
    view: ChordView,
    chosen: Voicing,
    capo: Int,
    onSelectVoicing: (Voicing) -> Unit,
) {
    SectionLabel("Other ways to play ${view.title}")
    DiagramFlow {
        view.voicings.forEach { voicing ->
            val selected = voicing == chosen
            ChordDiagram(
                voicing = voicing,
                width = SMALL_DIAGRAM_WIDTH,
                caption = voicing.label,
                capo = capo,
                modifier = Modifier
                    .clip(ControlShape)
                    .then(
                        if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, ControlShape)
                        else Modifier,
                    )
                    .clickable { onSelectVoicing(voicing) }
                    .padding(6.dp),
            )
        }
    }
}
