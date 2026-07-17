package de.singular.crystalball.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import de.singular.crystalball.chords.Voicing

@Composable
fun DetectScreen(
    state: DetectState,
    settings: Settings,
    onDetect: () -> Unit,
    onCancel: () -> Unit,
    onSelect: (Chord) -> Unit,
    onSelectVoicing: (Voicing) -> Unit,
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
                is DetectState.Result -> ResultPane(state, settings, onDetect, onSelect, onSelectVoicing)
                is DetectState.Browse ->
                    BrowsePane(state, settings, onSelect, onSelectVoicing, onSetCapo, onDetect)
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
    CapoLink(capo, onSetCapo)
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
    CapoLink(capo, onSetCapo)
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
    onSelectVoicing: (Voicing) -> Unit,
    onSetCapo: () -> Unit,
    onDetect: () -> Unit,
) {
    val view = ChordView.of(state.chord, settings, state.voicing)

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
    // The capo says itself, on its own line, so the shape line does not repeat it.
    if (view.shapeLine != null) {
        Text(
            view.shapeLine,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    CapoStatusLink(settings.capo, onSetCapo)

    Spacer(Modifier.height(8.dp))
    ChordDiagram(
        voicing = view.best,
        width = BEST_DIAGRAM_WIDTH,
        caption = view.best.label,
        capo = settings.capo,
    )

    VariationsSection(view, settings, onSelectVoicing)

    Spacer(Modifier.height(28.dp))
    DetectButton(onDetect)
    Spacer(Modifier.height(16.dp))
}

/**
 * The capo, stated under the chord name and tappable to change.
 *
 * Says the same as [CapoLink] and differs only in shape: this one sits in the name block, reading as
 * the last line of "C, A shape, capo 3", so it is sized to the words rather than to the page.
 */
@Composable
private fun CapoStatusLink(capo: Int, onSetCapo: () -> Unit) {
    TextButton(onClick = onSetCapo, shape = ControlShape) {
        Text(capoLabel(capo), style = MaterialTheme.typography.titleSmall)
    }
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
    Spacer(Modifier.height(40.dp))
    SpinningLogo()
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

@Composable
private fun ResultPane(
    state: DetectState.Result,
    settings: Settings,
    onDetect: () -> Unit,
    onSelect: (Chord) -> Unit,
    onSelectVoicing: (Voicing) -> Unit,
) {
    val view = ChordView.of(state.selected, settings, state.voicing)

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

    VariationsSection(view, settings, onSelectVoicing)
    Spacer(Modifier.height(24.dp))

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

/**
 * The other ways to play the chord that is already big, each one tappable to take its place.
 *
 * Shared by both panes that draw a chord, so the browser and a detection result stay the same page
 * — which is the [BrowsePane] premise, and would rot the moment one of them grew a row the other
 * lacked. Draws nothing when there are no variations, leading spacer included, so a chord with a
 * single shape does not leave a gap where a section would have been.
 */
@Composable
private fun VariationsSection(
    view: ChordView,
    settings: Settings,
    onSelectVoicing: (Voicing) -> Unit,
) {
    if (view.variations.isEmpty()) return

    Spacer(Modifier.height(28.dp))
    SectionLabel("Other ways to play ${view.title}")
    DiagramFlow {
        view.variations.forEach { voicing ->
            ChordDiagram(
                voicing = voicing,
                width = SMALL_DIAGRAM_WIDTH,
                caption = voicing.label,
                capo = settings.capo,
                modifier = Modifier
                    .clip(ControlShape)
                    .clickable { onSelectVoicing(voicing) }
                    .padding(4.dp),
            )
        }
    }
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
