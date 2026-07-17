package de.singular.crystalball.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.singular.crystalball.Capo
import de.singular.crystalball.ChordView
import de.singular.crystalball.DetectState
import de.singular.crystalball.SaveTarget
import de.singular.crystalball.Settings
import de.singular.crystalball.R
import de.singular.crystalball.audio.Chord
import de.singular.crystalball.audio.ChordCandidate
import de.singular.crystalball.audio.Quality
import de.singular.crystalball.audio.ROOT_NAMES
import de.singular.crystalball.chords.ChordLibrary
import de.singular.crystalball.chords.Voicing
import de.singular.crystalball.songs.CapturedChord
import de.singular.crystalball.songs.PART_NAMES
import de.singular.crystalball.songs.Song
import de.singular.crystalball.songs.defaultVoicing

@Composable
fun DetectScreen(
    state: DetectState,
    settings: Settings,
    library: List<Song>,
    captureTargetId: String?,
    onDetect: () -> Unit,
    onCapture: () -> Unit,
    onCancel: () -> Unit,
    onSelect: (Chord) -> Unit,
    onSelectVoicing: (Voicing) -> Unit,
    onStopCapture: () -> Unit,
    onDiscardCapture: () -> Unit,
    onEditCaptured: (Int) -> Unit,
    onSelectCapturedChord: (Chord) -> Unit,
    onSelectCapturedVoicing: (Voicing) -> Unit,
    onRemoveCaptured: (Int) -> Unit,
    onBackToCaptureReview: () -> Unit,
    onSaveCapture: (List<CapturedChord>, String, SaveTarget) -> Unit,
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
                is DetectState.Idle -> IdlePane(onDetect, onCapture, settings.capo, onSetCapo)
                is DetectState.Silence -> SilencePane(onDetect, onCapture, settings.capo, onSetCapo)
                is DetectState.Listening -> ListeningPane(state, onCancel)
                is DetectState.Result -> ResultPane(state, settings, onDetect, onSelect, onSelectVoicing)
                is DetectState.Browse ->
                    BrowsePane(state, settings, onSelect, onSelectVoicing, onSetCapo, onDetect)
                is DetectState.Capturing -> CapturingPane(state, settings, onStopCapture)
                is DetectState.CaptureReview ->
                    CaptureReviewPane(
                        state, settings, library, captureTargetId,
                        onEditCaptured, onDiscardCapture, onSaveCapture,
                    )
                is DetectState.EditCaptured ->
                    EditCapturedPane(
                        state, settings, onSelectCapturedChord, onSelectCapturedVoicing,
                        onRemoveCaptured,
                    )
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
            // Named in the corner everywhere except home, where the wordmark banner already says it
            // — twice over would be one Crystal Ball too many.
            if (state !is DetectState.Idle) {
                Text(
                    "Crystal Ball",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
private fun IdlePane(
    onDetect: () -> Unit,
    onCapture: () -> Unit,
    capo: Int,
    onSetCapo: () -> Unit,
) {
    Spacer(Modifier.height(ICON_ROW_HEIGHT))
    Claim()
    Spacer(Modifier.height(20.dp))
    Logo()
    Spacer(Modifier.height(16.dp))
    Text(
        "Play a chord and I'll name it.",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(28.dp))
    DetectButtons(onDetect, onCapture)
    Spacer(Modifier.height(4.dp))
    CapoLink(capo, onSetCapo)
}

@Composable
private fun SilencePane(
    onDetect: () -> Unit,
    onCapture: () -> Unit,
    capo: Int,
    onSetCapo: () -> Unit,
) {
    Spacer(Modifier.height(ICON_ROW_HEIGHT))
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
    DetectButtons(onDetect, onCapture)
    Spacer(Modifier.height(4.dp))
    CapoLink(capo, onSetCapo)
}

/**
 * The two ways in: name one chord, or take a run of them.
 *
 * The single-chord button keeps the weight — it is what the app is for, and what the hands reach for
 * without looking. Capturing a run is the deliberate act, so it sits below as the quieter outlined
 * button and carries two microphones to say, at a glance, that it hears more than one.
 */
@Composable
private fun DetectButtons(onDetect: () -> Unit, onCapture: () -> Unit) {
    DetectButton(onDetect, label = "Detect single chord")
    Spacer(Modifier.height(12.dp))
    OutlinedButton(
        onClick = onCapture,
        shape = ControlShape,
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = BUTTON_MAX_WIDTH)
            .height(BUTTON_HEIGHT),
    ) {
        Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(24.dp))
        Icon(
            Icons.Default.Mic,
            contentDescription = null,
            modifier = Modifier.size(24.dp).offset(x = (-8).dp),
        )
        Spacer(Modifier.width(4.dp))
        Text("Detect multiple chords", style = MaterialTheme.typography.titleMedium)
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

/**
 * Detecting a run of chords: strum, let it ring, watch its shape land, mute, strum the next.
 *
 * The muting is the technique, not fussiness — a chord left ringing bleeds into the next pass and is
 * read as the next chord. Each landed chord shows the shape you'd play it with, stacking down the
 * page, so a wrong read is something you can see the moment it happens rather than at the end.
 */
@Composable
private fun CapturingPane(
    state: DetectState.Capturing,
    settings: Settings,
    onDone: () -> Unit,
) {
    Spacer(Modifier.height(8.dp))
    SpinningLogo()
    Spacer(Modifier.height(16.dp))
    Text(
        if (state.captured.isEmpty() && !state.heardStrum) "Strum the first chord"
        else "Chord ${state.captured.size + 1} — strum and let it ring",
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        "Mute the strings once it lands. A chord still ringing bleeds into the next one and blurs it.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(16.dp))
    CapturedDiagrams(state.captured, settings)
    Spacer(Modifier.height(20.dp))
    LevelMeter(state.level)
    Spacer(Modifier.height(28.dp))
    Button(
        onClick = onDone,
        shape = ControlShape,
        enabled = state.captured.isNotEmpty(),
        modifier = Modifier.fillMaxWidth().widthIn(max = BUTTON_MAX_WIDTH).height(BUTTON_HEIGHT),
    ) {
        Text("Done", style = MaterialTheme.typography.titleMedium)
    }
    Spacer(Modifier.height(8.dp))
    Text(
        "Or just stop playing — it ends on its own.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

/**
 * The captured run, stopped and held: fix what was misheard, then say where it goes.
 *
 * Review is not optional and this is why: a forgotten mute produces a *wrong* chord rather than a
 * missing one, and nothing about it looks broken. Tapping one opens the same question the result
 * page answers. Nothing is a song yet — [SaveAsDialog] is where the run becomes a part.
 */
@Composable
private fun CaptureReviewPane(
    state: DetectState.CaptureReview,
    settings: Settings,
    library: List<Song>,
    captureTargetId: String?,
    onEditCaptured: (Int) -> Unit,
    onDiscard: () -> Unit,
    onSaveCapture: (List<CapturedChord>, String, SaveTarget) -> Unit,
) {
    var saveOpen by rememberSaveable { mutableStateOf(false) }

    Spacer(Modifier.height(ICON_ROW_HEIGHT))
    if (state.captured.isEmpty()) {
        Text("Nothing captured.", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Hold the phone near the guitar and try again.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        OutlinedButton(
            onClick = onDiscard,
            shape = ControlShape,
            modifier = Modifier.fillMaxWidth().widthIn(max = BUTTON_MAX_WIDTH).height(BUTTON_HEIGHT),
        ) {
            Text("Back", style = MaterialTheme.typography.titleMedium)
        }
        return
    }

    Text(
        "${state.captured.size} chords",
        style = MaterialTheme.typography.displaySmall,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        "Tap a chord to fix it, or to say how you play it.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(16.dp))
    CapturedDiagrams(state.captured, settings, onEdit = onEditCaptured)

    Spacer(Modifier.height(28.dp))
    Button(
        onClick = { saveOpen = true },
        shape = ControlShape,
        modifier = Modifier.fillMaxWidth().widthIn(max = BUTTON_MAX_WIDTH).height(BUTTON_HEIGHT),
    ) {
        Text("Save as…", style = MaterialTheme.typography.titleMedium)
    }
    Spacer(Modifier.height(4.dp))
    TextButton(
        onClick = onDiscard,
        shape = ControlShape,
        modifier = Modifier.fillMaxWidth().widthIn(max = BUTTON_MAX_WIDTH).height(48.dp),
    ) {
        Text("Discard", style = MaterialTheme.typography.titleSmall)
    }

    if (saveOpen) {
        SaveAsDialog(
            settings = settings,
            library = library,
            presetTargetId = captureTargetId,
            onDismiss = { saveOpen = false },
            onSave = { partName, target ->
                saveOpen = false
                onSaveCapture(state.captured, partName, target)
            },
        )
    }
}

/**
 * The run so far, each chord as the shape you'd play it, stacking as they land.
 *
 * In review [onEdit] makes each one tappable to fix; during capture it is null and these are
 * feedback, not controls — the diagram appearing is how you know that chord landed.
 */
@Composable
private fun CapturedDiagrams(
    captured: List<CapturedChord>,
    settings: Settings,
    onEdit: ((Int) -> Unit)? = null,
) {
    if (captured.isEmpty()) {
        Text(
            "—",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    DiagramFlow {
        captured.forEachIndexed { index, chord ->
            val voicing = chord.voicing ?: defaultVoicing(chord.selected, settings.capo)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(ControlShape)
                    .then(if (onEdit != null) Modifier.clickable { onEdit(index) } else Modifier)
                    .padding(4.dp),
            ) {
                Text(
                    Capo.shortName(chord.selected, settings.capo, settings.nameStyle),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(4.dp))
                ChordDiagram(voicing = voicing, width = SMALL_DIAGRAM_WIDTH, capo = settings.capo)
            }
        }
    }
}

/**
 * One captured chord: what it is, and how you play it.
 *
 * The same two rows the result page shows: the runner-ups fix what was misheard, and the variations
 * record *the shape your hands make*. The twin of the song editor's chord page, on the detect side —
 * because fixing a misread belongs in the moment you played it, before it is a song at all.
 */
@Composable
private fun EditCapturedPane(
    state: DetectState.EditCaptured,
    settings: Settings,
    onSelectChord: (Chord) -> Unit,
    onSelectVoicing: (Voicing) -> Unit,
    onRemove: (Int) -> Unit,
) {
    val captured = state.captured.getOrNull(state.index) ?: return
    val view = ChordView.of(captured.selected, settings)
    val chosen = captured.voicing ?: view.best

    Spacer(Modifier.height(ICON_ROW_HEIGHT))
    Text(view.title, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
    if (view.subtitle != null) {
        Text(
            view.subtitle,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(8.dp))
    ChordDiagram(
        voicing = chosen,
        width = BEST_DIAGRAM_WIDTH,
        caption = chosen.label,
        capo = settings.capo,
    )

    Spacer(Modifier.height(28.dp))
    VoicingPicker(view, chosen, settings.capo, onSelectVoicing)

    if (captured.alternatives.isNotEmpty()) {
        Spacer(Modifier.height(24.dp))
        SectionLabel("Or did you mean")
        DiagramRow {
            captured.alternatives.forEach { candidate ->
                AlternativeDiagram(
                    candidate = candidate,
                    settings = settings,
                    onClick = { onSelectChord(candidate.chord) },
                )
            }
        }
    }

    Spacer(Modifier.height(24.dp))
    TextButton(
        onClick = { onRemove(state.index) },
        shape = ControlShape,
        modifier = Modifier.fillMaxWidth().widthIn(max = BUTTON_MAX_WIDTH).height(48.dp),
    ) {
        Text("Remove this chord", style = MaterialTheme.typography.titleSmall)
    }
}

/**
 * Where the captured run goes: a part name, and a song — new or one already in the library.
 *
 * The two decisions the old flow made you take *before* playing a note, asked once at the end when
 * the chords are already in hand. Naming leads with the common section names as chips, because
 * typing "Chorus" with a guitar on your lap is the worst moment to ask for it.
 *
 * Adding to an existing song at a different capo is safe — the chords are stored as they sound, so
 * only the shapes change — but it is said out loud rather than done silently, so the shapes turning
 * out different from what you just played is never a surprise.
 */
@Composable
private fun SaveAsDialog(
    settings: Settings,
    library: List<Song>,
    presetTargetId: String?,
    onDismiss: () -> Unit,
    onSave: (String, SaveTarget) -> Unit,
) {
    val preset = library.firstOrNull { it.id == presetTargetId }
    var partName by rememberSaveable { mutableStateOf("") }
    var toExisting by rememberSaveable { mutableStateOf(preset != null) }
    var title by rememberSaveable { mutableStateOf("") }
    var selectedId by rememberSaveable { mutableStateOf(preset?.id ?: library.firstOrNull()?.id) }

    val selectedSong = library.firstOrNull { it.id == selectedId }
    val used = if (toExisting) selectedSong?.parts?.map { it.name }?.toSet().orEmpty() else emptySet()
    val name = partName.trim()

    val canSave = name.isNotEmpty() &&
        if (toExisting) selectedId != null else title.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save these chords") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                SectionLabel("Name this part")
                ChipFlow {
                    PART_NAMES.forEach { partLabel ->
                        FilterChip(
                            selected = name == partLabel,
                            enabled = partLabel !in used,
                            onClick = { partName = partLabel },
                            shape = ControlShape,
                            label = { Text(partLabel) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = partName,
                    onValueChange = { partName = it },
                    label = { Text("Or a name of your own") },
                    singleLine = true,
                    shape = ControlShape,
                    keyboardOptions = NameKeyboard,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (name in used) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "\"$name\" already exists in that song — saving replaces it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(20.dp))
                SectionLabel("Save it to")
                RadioRow("A new song", selected = !toExisting) { toExisting = false }
                if (!toExisting) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Song title") },
                        singleLine = true,
                        shape = ControlShape,
                        keyboardOptions = NameKeyboard,
                        modifier = Modifier.fillMaxWidth().padding(start = 32.dp),
                    )
                }
                if (library.isNotEmpty()) {
                    RadioRow("An existing song", selected = toExisting) { toExisting = true }
                    if (toExisting) {
                        Column(Modifier.padding(start = 32.dp)) {
                            ChipFlow {
                                library.forEach { song ->
                                    FilterChip(
                                        selected = selectedId == song.id,
                                        onClick = { selectedId = song.id },
                                        shape = ControlShape,
                                        label = { Text(song.title) },
                                    )
                                }
                            }
                            if (selectedSong != null && selectedSong.capo != settings.capo) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    capoMoveNote(settings.capo, selectedSong.capo),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    val target =
                        if (toExisting) SaveTarget.Existing(selectedId!!)
                        else SaveTarget.NewSong(title, settings.capo)
                    onSave(name, target)
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** A radio option with its label, the whole row tappable. */
@Composable
private fun RadioRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(ControlShape)
            .clickable(onClick = onSelect)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Say what moving the run onto a song at a different capo does — and, quietly, what it does not. */
private fun capoMoveNote(from: Int, to: Int): String {
    val here = if (from == 0) "without a capo" else "behind capo $from"
    val there = if (to == 0) "has no capo" else "is at capo $to"
    return "You played this $here; that song $there. The chords keep their sound — only the shapes change."
}

/**
 * Names here are titles — "Wonderwall", "Chorus" — so the keyboard starts them in caps rather than
 * making you reach for shift with a guitar in your lap.
 */
private val NameKeyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
