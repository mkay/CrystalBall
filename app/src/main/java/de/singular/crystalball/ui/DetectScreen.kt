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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.singular.crystalball.Capo
import de.singular.crystalball.ChordView
import de.singular.crystalball.DetectState
import de.singular.crystalball.Settings
import de.singular.crystalball.R
import de.singular.crystalball.audio.Chord
import de.singular.crystalball.audio.ChordCandidate
import de.singular.crystalball.audio.Quality
import de.singular.crystalball.audio.ROOT_NAMES
import de.singular.crystalball.chords.ChordLibrary
import kotlin.math.log10

/** Diagram sizes: one to read at arm's length, one to scan a row of. */
private val BEST_DIAGRAM_WIDTH = 172.dp
private val SMALL_DIAGRAM_WIDTH = 74.dp

@Composable
fun DetectScreen(
    state: DetectState,
    settings: Settings,
    onDetect: () -> Unit,
    onCancel: () -> Unit,
    onSelect: (Chord) -> Unit,
    onSetCapo: () -> Unit,
    onOpenMenu: () -> Unit,
    onOpenAppSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (state) {
                is DetectState.Idle -> IdlePane(onDetect, settings.capo, onSetCapo)
                is DetectState.Silence -> SilencePane(onDetect, settings.capo, onSetCapo)
                is DetectState.Listening -> ListeningPane(state, onCancel)
                is DetectState.Result -> ResultPane(state, settings, onDetect, onSelect)
                is DetectState.Browse -> BrowsePane(state, settings, onSelect, onDetect)
            }
        }
        var showKeepAwakeInfo by rememberSaveable { mutableStateOf(false) }

        // The app has no top bar — the logo and the chord are the page — so the bar's contents
        // float in the corners instead: the way in and the app's name on the left, the keep-awake
        // notice on the right.
        Row(
            modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onOpenMenu) {
                Icon(
                    Icons.Default.Menu,
                    contentDescription = "Menu",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "Crystal Ball",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // A quiet notice that the display is being kept awake (it drains battery); tap for an
        // explanation and a shortcut to turn it off.
        if (settings.keepScreenOn) {
            IconButton(
                onClick = { showKeepAwakeInfo = true },
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_brightness_alert),
                    contentDescription = "Screen kept on",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (showKeepAwakeInfo) {
            AlertDialog(
                onDismissRequest = { showKeepAwakeInfo = false },
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_brightness_alert),
                        contentDescription = null,
                    )
                },
                title = { Text("Screen stays on") },
                text = {
                    Text(
                        "“Keep screen on” is enabled, so the display won't dim or lock while the " +
                            "app is open. Handy for practice, but it uses more battery.",
                    )
                },
                confirmButton = {
                    TextButton(onClick = { showKeepAwakeInfo = false; onOpenAppSettings() }) {
                        Text("Settings")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showKeepAwakeInfo = false }) { Text("OK") }
                },
            )
        }
    }
}

@Composable
private fun IdlePane(onDetect: () -> Unit, capo: Int, onSetCapo: () -> Unit) {
    Spacer(Modifier.height(40.dp))
    Logo()
    Spacer(Modifier.height(16.dp))
    Text(
        "Play a chord and I'll name it.",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(28.dp))
    DetectButton(onDetect)
    Spacer(Modifier.height(4.dp))
    SetCapoLink(capo, onSetCapo)
}

@Composable
private fun SilencePane(onDetect: () -> Unit, capo: Int, onSetCapo: () -> Unit) {
    Spacer(Modifier.height(40.dp))
    Logo()
    Spacer(Modifier.height(16.dp))
    Text(
        "I didn't hear anything.",
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        "Hold the phone near the guitar and strum.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(28.dp))
    DetectButton(onDetect)
    Spacer(Modifier.height(4.dp))
    SetCapoLink(capo, onSetCapo)
}

/**
 * Secondary action: reach the capo without going through the side panel.
 *
 * It sits here rather than in the panel because the capo is the one setting you change with a
 * guitar already in your hands, and the idle screen is where you are when you notice. The label
 * states the fret rather than just inviting a change: there is no diagram on this page to show an
 * accent-coloured nut, so without it a capo left set from yesterday would be invisible until the
 * first result came back.
 */
@Composable
private fun SetCapoLink(capo: Int, onSetCapo: () -> Unit) {
    TextButton(
        onClick = onSetCapo,
        shape = ControlShape,
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = BUTTON_MAX_WIDTH)
            .height(48.dp),
    ) {
        Text(
            if (capo == 0) "Set capo" else "Capo set to $capo (change)",
            style = MaterialTheme.typography.titleSmall,
        )
    }
}

/**
 * The chord dictionary: pick a root and a quality, see how to play it.
 *
 * Deliberately the same page as a detection result minus the runner-ups — you chose this chord, so
 * there is nothing you might have meant instead.
 */
@Composable
private fun BrowsePane(
    state: DetectState.Browse,
    settings: Settings,
    onSelect: (Chord) -> Unit,
    onDetect: () -> Unit,
) {
    val view = ChordView.of(state.chord, settings)

    // The settings and keep-awake icons float over this column, and the root chips scroll the full
    // width — without this they would pass underneath them.
    Spacer(Modifier.height(ICON_ROW_HEIGHT))

    ChipRow {
        ROOT_NAMES.forEachIndexed { root, name ->
            FilterChip(
                selected = state.chord.root == root,
                onClick = { onSelect(state.chord.copy(root = root)) },
                shape = ControlShape,
                label = { Text(name) },
            )
        }
    }
    Spacer(Modifier.height(6.dp))
    ChipRow {
        Quality.entries.forEach { quality ->
            FilterChip(
                selected = state.chord.quality == quality,
                onClick = { onSelect(state.chord.copy(quality = quality)) },
                shape = ControlShape,
                label = { Text(quality.label) },
            )
        }
    }

    Spacer(Modifier.height(20.dp))
    Text(
        view.title,
        style = MaterialTheme.typography.displaySmall,
        fontWeight = FontWeight.SemiBold,
    )
    if (view.subtitle != null) {
        Text(
            view.subtitle,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(8.dp))
    ChordDiagram(
        voicing = view.best,
        width = BEST_DIAGRAM_WIDTH,
        caption = view.best.label,
        capo = settings.capo,
    )

    if (view.variations.isNotEmpty()) {
        Spacer(Modifier.height(28.dp))
        SectionLabel("Other ways to play ${view.title}")
        DiagramRow {
            view.variations.forEach { voicing ->
                ChordDiagram(
                    voicing = voicing,
                    width = SMALL_DIAGRAM_WIDTH,
                    caption = voicing.label,
                    capo = settings.capo,
                )
            }
        }
    }

    Spacer(Modifier.height(28.dp))
    DetectButton(onDetect)
    Spacer(Modifier.height(16.dp))
}

/** A scrolling row of choices — twelve roots do not fit across a phone. */
@Composable
private fun ChipRow(content: @Composable () -> Unit) {
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

/** The app mark. [rotation] turns the swirl, which the listening screen animates. */
@Composable
private fun Logo(rotation: Float = 0f, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.ic_ball),
        contentDescription = null,
        modifier = modifier
            .size(LOGO_SIZE)
            .graphicsLayer { rotationZ = rotation },
    )
}

/**
 * The primary action. Deliberately big: it is pressed with a guitar already in your hands, often
 * without looking, so it spans the width and stands well clear of the 48dp minimum touch target.
 */
@Composable
private fun DetectButton(onDetect: () -> Unit, label: String = "Detect Chord") {
    Button(
        onClick = onDetect,
        shape = ControlShape,
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = BUTTON_MAX_WIDTH)
            .height(BUTTON_HEIGHT),
    ) {
        Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ListeningPane(state: DetectState.Listening, onCancel: () -> Unit) {
    // The swirl turns while the microphone is open — the one moving thing on screen, so it is
    // obvious the app is still listening and has not simply frozen.
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

    Spacer(Modifier.height(40.dp))
    Logo(rotation = angle)
    Spacer(Modifier.height(16.dp))
    Text(
        if (state.heardStrum) "Listening…" else "Strum a chord",
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(20.dp))
    LevelMeter(state.level)
    Spacer(Modifier.height(28.dp))
    OutlinedButton(
        onClick = onCancel,
        shape = ControlShape,
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = BUTTON_MAX_WIDTH)
            .height(BUTTON_HEIGHT),
    ) {
        Text("Cancel", style = MaterialTheme.typography.titleMedium)
    }
}

/** Clearance for the icon row floating at the top-right of the screen. */
private val ICON_ROW_HEIGHT = 40.dp

private val LOGO_SIZE = 132.dp
private val BUTTON_HEIGHT = 64.dp
private val BUTTON_MAX_WIDTH = 340.dp
private const val SPIN_PERIOD_MS = 9000

/**
 * Input level, so it is obvious the microphone is actually hearing the guitar.
 *
 * Scaled in decibels rather than linearly. Hearing is logarithmic and the signals here span a huge
 * range — an unplugged hollow-body sits near -50 dBFS where a linear meter shows a sliver of movement
 * indistinguishable from nothing, which is exactly the case where the user most needs to see that
 * the app can hear them.
 */
@Composable
private fun LevelMeter(level: Float) {
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

@Composable
private fun ResultPane(
    state: DetectState.Result,
    settings: Settings,
    onDetect: () -> Unit,
    onSelect: (Chord) -> Unit,
) {
    val view = ChordView.of(state.selected, settings)

    // Clear the floating title row — the chord name is centred and would otherwise run into it.
    Spacer(Modifier.height(ICON_ROW_HEIGHT))

    Text(
        view.title,
        style = MaterialTheme.typography.displayMedium,
        fontWeight = FontWeight.SemiBold,
    )
    if (view.subtitle != null) {
        Text(
            view.subtitle,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(8.dp))
    ChordDiagram(
        voicing = view.best,
        width = BEST_DIAGRAM_WIDTH,
        caption = view.best.label,
        capo = settings.capo,
    )

    Spacer(Modifier.height(28.dp))

    if (view.variations.isNotEmpty()) {
        SectionLabel("Other ways to play ${view.title}")
        DiagramRow {
            view.variations.forEach { voicing ->
                ChordDiagram(
                    voicing = voicing,
                    width = SMALL_DIAGRAM_WIDTH,
                    caption = voicing.label,
                    capo = settings.capo,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (state.alternatives.isNotEmpty()) {
        SectionLabel("Or did you mean")
        DiagramRow {
            state.alternatives.forEach { candidate ->
                AlternativeDiagram(
                    candidate = candidate,
                    settings = settings,
                    onClick = { onSelect(candidate.chord) },
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    DetectButton(onDetect, label = "Detect Again")
    Spacer(Modifier.height(16.dp))
}

/** A tappable runner-up: its own name above its most idiomatic shape. */
@Composable
private fun AlternativeDiagram(
    candidate: ChordCandidate,
    settings: Settings,
    onClick: () -> Unit,
) {
    val shape = Capo.shapeChord(candidate.chord, settings.capo)
    val voicing = ChordLibrary.voicingsFor(shape, settings.capo).first()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(ControlShape)
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        Text(
            Capo.shortName(candidate.chord, settings.capo, settings.nameStyle),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(4.dp))
        ChordDiagram(voicing = voicing, width = SMALL_DIAGRAM_WIDTH, capo = settings.capo)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
    )
}

/** The rows scroll sideways: five chord boxes do not fit across a phone at a readable size. */
@Composable
private fun DiagramRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(GAP),
        verticalAlignment = Alignment.Top,
    ) {
        content()
    }
}

private val GAP: Dp = 10.dp
