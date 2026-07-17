package de.singular.crystalball.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.singular.crystalball.Capo
import de.singular.crystalball.ChordView
import de.singular.crystalball.Settings
import de.singular.crystalball.SongState
import de.singular.crystalball.audio.Chord
import de.singular.crystalball.chords.Voicing
import de.singular.crystalball.songs.CapturedChord
import de.singular.crystalball.songs.PART_NAMES
import de.singular.crystalball.songs.Part
import de.singular.crystalball.songs.Song
import de.singular.crystalball.songs.SongChord
import de.singular.crystalball.songs.defaultVoicing

/**
 * Names here are titles — "Wonderwall", "Chorus" — so the keyboard starts them in caps rather than
 * making you reach for shift with a guitar in your lap.
 *
 * A hint to the soft keyboard, not a rule: it capitalises what you type without overriding what you
 * mean, so a name that genuinely wants a small letter still can have one.
 */
private val NameKeyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)

/**
 * Writing a song down.
 *
 * A full screen over the rest of the app, like [SettingsScreen]. Every page here renders at the
 * *song's* capo rather than the live setting — a stored grip counts its frets from the capo it was
 * played behind, so [songSettings] substitutes it and nothing below has to remember.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongScreen(
    song: Song,
    state: SongState,
    library: List<Song>,
    error: String?,
    settings: Settings,
    onTitleDone: (String) -> Unit,
    onEditTitle: () -> Unit,
    onCancelTitle: () -> Unit,
    onAddPart: () -> Unit,
    onSetCapo: () -> Unit,
    onRemovePart: (String) -> Unit,
    onMovePart: (Int, Int) -> Unit,
    onStopCapture: () -> Unit,
    onDiscardCapture: () -> Unit,
    onEditChord: (Int) -> Unit,
    onRemoveChord: (Int) -> Unit,
    onSelectChord: (Chord) -> Unit,
    onSelectVoicing: (Voicing) -> Unit,
    onOpenPart: (String) -> Unit,
    onViewSong: () -> Unit,
    onExportPdf: () -> Unit,
    onEditComment: () -> Unit,
    onCommentDone: (String) -> Unit,
    onCancelComment: () -> Unit,
    onEditPartChord: (Int) -> Unit,
    onSelectPartVoicing: (Voicing) -> Unit,
    onBackToPart: () -> Unit,
    onBackToEditor: () -> Unit,
    onBackToReview: () -> Unit,
    onReviewDone: () -> Unit,
    onNamePart: (String) -> Unit,
    onNewSong: () -> Unit,
    onOpenSong: (Song) -> Unit,
    onDeleteSongs: (Set<String>) -> Unit,
    onRenameSong: (String, String) -> Unit,
    onBackToLibrary: () -> Unit,
    onClose: () -> Unit,
) {
    val songSettings = settings.copy(capo = song.capo)

    // The library's selection: the ids picked out by long-press, empty when not selecting. Screen
    // state rather than the view-model's, because it is a thing the finger is doing to a list and
    // means nothing to the songs themselves.
    var selection by remember { mutableStateOf(emptySet<String>()) }
    val selecting = state is SongState.Library && selection.isNotEmpty()

    // The songs picked out to delete, from a row's own menu or from the whole selection. One place
    // for both, so there is one dialog and one last word before songs go.
    var pendingDelete by remember { mutableStateOf(emptySet<String>()) }

    // Keep the selection honest: drop ids the library no longer has — deleted here, or replaced
    // wholesale by a restore — and abandon it on the way out of the list entirely.
    LaunchedEffect(library, state) {
        selection = if (state is SongState.Library) {
            selection intersect library.map { it.id }.toSet()
        } else {
            emptySet()
        }
    }

    // Back means "up one page in the flow", which is not the same page for each of them.
    val back: () -> Unit = when (state) {
        is SongState.Library -> onClose
        // Backing out of the first naming abandons the new song; out of a rename, only the
        // rename.
        is SongState.Title -> if (song.title.isBlank()) onBackToLibrary else onCancelTitle
        is SongState.Editor -> onBackToLibrary
        is SongState.Capturing -> onDiscardCapture
        is SongState.Review -> onDiscardCapture
        is SongState.EditChord -> onBackToReview
        is SongState.PartView -> onBackToEditor
        is SongState.SongView -> onBackToEditor
        is SongState.Comment -> onCancelComment
        is SongState.EditPartChord -> onBackToPart
        is SongState.Naming -> onBackToReview
    }
    // A selection is the innermost thing on screen, so back drops it before it navigates.
    BackHandler { if (selecting) selection = emptySet() else back() }

    Scaffold(
        topBar = {
            TopAppBar(
                // While songs are selected the bar becomes a contextual one — count, a way out, and
                // the delete action — tinted so there is no mistaking the mode. The bar's usual
                // business steps aside until the selection is done with.
                colors = if (selecting) {
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        actionIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                } else {
                    TopAppBarDefaults.topAppBarColors()
                },
                navigationIcon = {
                    if (selecting) {
                        IconButton(onClick = { selection = emptySet() }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel selection")
                        }
                    } else {
                        IconButton(onClick = back) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (selecting) {
                        IconButton(onClick = { pendingDelete = selection }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete selected")
                        }
                    }
                    // The title is the top bar now, so renaming belongs here rather than as a
                    // field competing with Save for what "commit" means.
                    if (state is SongState.Editor) {
                        IconButton(onClick = onEditTitle) {
                            Icon(Icons.Default.Edit, contentDescription = "Rename song")
                        }
                    }
                    // Export sits on the song view, which is the page it prints.
                    if (state is SongState.SongView) {
                        IconButton(onClick = onExportPdf) {
                            Icon(Icons.Default.Share, contentDescription = "Export as PDF")
                        }
                    }
                },
                title = {
                    Text(
                        if (selecting) "${selection.size} selected" else when (state) {
                            is SongState.Library -> "Songs"
                            is SongState.Title ->
                                if (song.title.isBlank()) "New song" else "Rename"
                            is SongState.Editor -> song.title.ifBlank { "Song" }
                            is SongState.Capturing -> "Capture a part"
                            is SongState.Review -> "What I heard"
                            is SongState.EditChord -> "Chord ${state.index + 1}"
                            is SongState.PartView -> state.partName
                            is SongState.SongView -> song.title.ifBlank { "Song" }
                            is SongState.Comment -> "Comment"
                            is SongState.EditPartChord -> "Chord ${state.index + 1}"
                            is SongState.Naming -> "Name the part"
                        },
                    )
                },
            )
        },
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (state) {
                is SongState.Library ->
                    LibraryPane(
                        library = library,
                        error = error,
                        selection = selection,
                        onOpenSong = onOpenSong,
                        onToggleSelect = { id ->
                            selection = if (id in selection) selection - id else selection + id
                        },
                        onRequestDelete = { pendingDelete = setOf(it) },
                        onRenameSong = onRenameSong,
                        onNewSong = onNewSong,
                    )
                is SongState.Title -> TitlePane(song, onTitleDone)
                is SongState.Editor ->
                    SongEditorPane(
                        song, songSettings, onAddPart, onSetCapo, onRemovePart, onMovePart,
                        onOpenPart, onViewSong, onEditComment,
                    )
                is SongState.Capturing -> CapturePane(state, songSettings, onStopCapture)
                is SongState.Review ->
                    ReviewPane(state, songSettings, onEditChord, onReviewDone, onDiscardCapture)
                is SongState.EditChord ->
                    EditChordPane(state, songSettings, onSelectChord, onSelectVoicing, onRemoveChord)
                is SongState.SongView -> SongViewPane(song, songSettings)
                is SongState.Comment -> CommentPane(song, onCommentDone)
                is SongState.PartView ->
                    song.parts.firstOrNull { it.name == state.partName }?.let { part ->
                        PartViewPane(song, part, songSettings, onEditPartChord)
                    }
                is SongState.EditPartChord ->
                    song.parts.firstOrNull { it.name == state.partName }
                        ?.takeIf { state.index in it.chords.indices }
                        ?.let { part ->
                            EditPartChordPane(
                                song, part, state.index, songSettings, onSelectPartVoicing,
                            )
                        }
                is SongState.Naming -> NamingPane(song, onNamePart)
            }
        }
    }

    // One dialog for both ways songs get deleted — a row's own menu, and the selection's bulk
    // action. There is no undo, so this is the last word.
    if (pendingDelete.isNotEmpty()) {
        val doomed = pendingDelete
        val single = doomed.singleOrNull()?.let { id -> library.firstOrNull { it.id == id } }
        AlertDialog(
            onDismissRequest = { pendingDelete = emptySet() },
            title = {
                Text(
                    if (single != null) "Delete ${single.title}?"
                    else "Delete ${doomed.size} songs?",
                )
            },
            text = {
                Text(
                    if (single != null) {
                        "Its parts and the shapes you chose go with it. This can't be undone."
                    } else {
                        "Their parts and the shapes you chose go with them. This can't be undone."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteSongs(doomed)
                    pendingDelete = emptySet()
                    selection = emptySet()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = emptySet() }) { Text("Cancel") }
            },
        )
    }
}

/**
 * The songs you have written down.
 *
 * Home for the flow, and where a save lands — a song you cannot see afterwards is a song you cannot
 * tell was saved.
 *
 * Long-press picks songs out for a bulk delete, the way Rubber Ring's library does; [selection] is
 * which ones, and non-empty means the list is in that mode. Deleting is asked for rather than done —
 * [onRequestDelete] hands the id up to the one confirmation dialog the screen owns, because the
 * selection's own delete action lives up in the app bar and both must ask the same question.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryPane(
    library: List<Song>,
    error: String?,
    selection: Set<String>,
    onOpenSong: (Song) -> Unit,
    onToggleSelect: (String) -> Unit,
    onRequestDelete: (String) -> Unit,
    onRenameSong: (String, String) -> Unit,
    onNewSong: () -> Unit,
) {
    var renameTarget by remember { mutableStateOf<Song?>(null) }

    Spacer(Modifier.height(8.dp))
    when {
        // Said out loud rather than shown as an empty library: the file is refused when it cannot
        // be parsed, and pretending there are no songs is how you end up saving over them.
        error != null -> {
            Text(
                "Your songs could not be read.",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "$error\n\nNothing has been changed or lost. Saving is disabled until this is sorted out.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        library.isEmpty() -> {
            Spacer(Modifier.height(24.dp))
            Text(
                "No songs yet.",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Write one down: play its parts and it remembers the chords and how you play them.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        else -> library.forEach { song ->
            SongRow(
                song = song,
                selected = song.id in selection,
                selecting = selection.isNotEmpty(),
                onOpen = { onOpenSong(song) },
                onToggleSelect = { onToggleSelect(song.id) },
                onRename = { renameTarget = song },
                onDelete = { onRequestDelete(song.id) },
            )
        }
    }

    Spacer(Modifier.height(24.dp))
    Button(
        onClick = onNewSong,
        shape = ControlShape,
        enabled = error == null,
        modifier = Modifier.fillMaxWidth().widthIn(max = BUTTON_MAX_WIDTH).height(BUTTON_HEIGHT),
    ) {
        Text("New song", style = MaterialTheme.typography.titleMedium)
    }

    renameTarget?.let { target ->
        RenameSongDialog(
            song = target,
            onRename = { onRenameSong(target.id, it); renameTarget = null },
            onDismiss = { renameTarget = null },
        )
    }
}

/**
 * One song in the list: what it is, and the two ways to act on it.
 *
 * Long-press starts a selection, as it does in Rubber Ring's library. While one is running a tap
 * picks rather than opens — one meaning per tap — and the row's own menu gives way to a tick, since
 * a per-song menu would be competing with the app bar's action over the same songs.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SongRow(
    song: Song,
    selected: Boolean,
    selecting: Boolean,
    onOpen: () -> Unit,
    onToggleSelect: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    Surface(
        shape = ControlShape,
        // Only the picked rows are tinted; the rest keep the plain background this list has always
        // had, so a library at rest looks exactly as it did.
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .clip(ControlShape)
            .combinedClickable(
                onClick = { if (selecting) onToggleSelect() else onOpen() },
                onLongClick = {
                    // Confirm the grab in the hand: the gesture has no on-screen affordance, and a
                    // guitar player is not watching the screen closely.
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleSelect()
                },
            ),
    ) {
        Row(
            Modifier.padding(start = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                Text(
                    song.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    songSummary(song),
                    style = MaterialTheme.typography.bodySmall,
                    // Follows the row it is written on. The title needs no such help — it takes the
                    // Surface's content colour — but naming a colour here opts out of that, so a
                    // picked row would go on drawing its summary for the background it used to have.
                    color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (selecting) {
                Box(Modifier.padding(12.dp)) {
                    Icon(
                        if (selected) Icons.Default.CheckCircle
                        else Icons.Default.RadioButtonUnchecked,
                        contentDescription = if (selected) "Selected" else "Not selected",
                        tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "Options for ${song.title}",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = { menu = false; onRename() },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                            onClick = { menu = false; onDelete() },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Renaming from the list, in a dialog rather than on [TitlePane].
 *
 * That page is a step in writing a song down — name it, then get on with the music — and reaching it
 * from the library would mean opening the song to rename it and landing in the editor afterwards.
 * From a list, a rename should leave you in the list. Same rule as the page, though: the name
 * arrives whole, on the button, and blank is not a name.
 */
@Composable
private fun RenameSongDialog(song: Song, onRename: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable(song.id) { mutableStateOf(song.title) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename song") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Song title") },
                singleLine = true,
                shape = ControlShape,
                keyboardOptions = NameKeyboard,
            )
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = { onRename(text) }) {
                Text("Rename")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** A song at a glance: what it asks of your hands, and what is in it. */
private fun songSummary(song: Song): String {
    val capo = if (song.capo == 0) "No capo" else "Capo ${song.capo}"
    val parts = song.parts.joinToString(", ") { it.name }
    return if (parts.isEmpty()) capo else "$capo · $parts"
}

/**
 * The variations row, made a picker.
 *
 * The whole reason a song is worth more than a list of chord names: tapping one records *the shape
 * your hands make*, rather than the one the library happens to lead with. It wraps rather than
 * scrolls — a chord with eight ways to play it would otherwise hide most of them off the edge.
 *
 * The choice is marked with a ring rather than a filled card. [ChordDiagram] draws its lines and
 * dots in colours picked for the surface underneath, so tinting that surface puts light on light in
 * the dark theme and dark on dark in the light one — the selected shape would be the one you could
 * not read. A ring sits outside the diagram and leaves it on the background it was drawn for.
 */
@Composable
private fun VoicingPicker(
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

/**
 * The song's comment, on its own page like the title.
 *
 * Held here and handed over whole on Save, for the same reason the title is: a field wired straight
 * into the song looks committed while it is not.
 */
@Composable
private fun CommentPane(song: Song, onCommentDone: (String) -> Unit) {
    var text by rememberSaveable(song.comment) { mutableStateOf(song.comment) }

    Spacer(Modifier.height(16.dp))
    Text(
        "Anything the chords don't say",
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        "A tuning, a strumming pattern, which verse drops out — whatever you'd want to be told.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text("Comment") },
        minLines = 4,
        shape = ControlShape,
        keyboardOptions = NameKeyboard,
        modifier = Modifier.fillMaxWidth().widthIn(max = BUTTON_MAX_WIDTH),
    )
    Spacer(Modifier.height(16.dp))
    Button(
        onClick = { onCommentDone(text) },
        shape = ControlShape,
        modifier = Modifier.fillMaxWidth().widthIn(max = BUTTON_MAX_WIDTH).height(BUTTON_HEIGHT),
    ) {
        // Not disabled when empty: clearing the field is how a comment is deleted.
        Text("Save comment", style = MaterialTheme.typography.titleMedium)
    }
}

/**
 * The whole song on one page: every part, with the shape for every chord in it.
 *
 * A diagram per chord rather than a name list and a legend of shapes. A legend has to collapse
 * repeats, and the moment the same chord is played two ways in one part — which is the entire point
 * of choosing voicings — the sequence can no longer tell you which G is which. Drawn in place, each
 * chord carries its own answer.
 *
 * This is the layout the PDF export will print, so what you see here is what comes out.
 */
@Composable
private fun SongViewPane(song: Song, settings: Settings) {
    Spacer(Modifier.height(8.dp))
    Text(
        if (song.capo == 0) "No capo" else "Capo ${song.capo}",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(Modifier.height(20.dp))
    song.parts.forEach { part ->
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                part.name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            DiagramFlow {
                part.chords.forEach { chord -> ChordCell(chord, settings) }
            }
        }
    }

    if (song.comment.isNotBlank()) {
        Column(Modifier.fillMaxWidth()) {
            // Headed exactly like a part, because on this page it is one: the last section of the
            // sheet, not a note the app is making about it.
            Text(
                "Comment",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            Text(song.comment, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * A chord as it is actually played: its name, and the shape chosen for it.
 *
 * One cell wherever a stored chord is drawn, so a part reads the same whether you are looking at it
 * alone or as part of the whole song.
 */
@Composable
private fun ChordCell(
    chord: SongChord,
    settings: Settings,
    onClick: (() -> Unit)? = null,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(ControlShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(4.dp),
    ) {
        Text(
            Capo.shortName(chord.sounding, settings.capo, settings.nameStyle),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(4.dp))
        ChordDiagram(
            voicing = chord.voicing,
            width = SMALL_DIAGRAM_WIDTH,
            caption = chord.voicing.label,
            capo = settings.capo,
        )
    }
}

/** A saved part, drawn out: the shapes it asks of your hands, in the order you play them. */
@Composable
private fun PartViewPane(
    song: Song,
    part: Part,
    settings: Settings,
    onEditPartChord: (Int) -> Unit,
) {
    Spacer(Modifier.height(8.dp))
    Text(
        "Tap a chord to change how you play it.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(16.dp))
    DiagramFlow {
        part.chords.forEachIndexed { index, chord ->
            ChordCell(chord, settings) { onEditPartChord(index) }
        }
    }
}

/**
 * One chord of a saved part: what it is, and how you play it.
 *
 * No "did you mean" row here, unlike the same page during a capture. The recogniser's runner-ups
 * are not stored — they were an opinion about a sound that has long since stopped, and fixing a
 * misread belongs in the moment you played it.
 */
@Composable
private fun EditPartChordPane(
    song: Song,
    part: Part,
    index: Int,
    settings: Settings,
    onSelectVoicing: (Voicing) -> Unit,
) {
    val chord = part.chords[index]
    val view = ChordView.of(chord.sounding, settings)

    Spacer(Modifier.height(8.dp))
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
        voicing = chord.voicing,
        width = BEST_DIAGRAM_WIDTH,
        caption = chord.voicing.label,
        capo = song.capo,
    )
    Spacer(Modifier.height(28.dp))
    VoicingPicker(view, chord.voicing, song.capo, onSelectVoicing)
}

/**
 * Naming the song, on its own, before any of the music.
 *
 * The text is held here and handed over whole on Next, rather than bound to the song keystroke by
 * keystroke. That is the point of the step: a field wired straight into the song looks saved while
 * it is not, and a Save button sitting beside it made it unclear which one you were committing.
 */
@Composable
private fun TitlePane(song: Song, onTitleDone: (String) -> Unit) {
    var text by rememberSaveable(song.title) { mutableStateOf(song.title) }

    Spacer(Modifier.height(24.dp))
    Text(
        "What's it called?",
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text("Song title") },
        singleLine = true,
        shape = ControlShape,
        keyboardOptions = NameKeyboard,
        modifier = Modifier.fillMaxWidth().widthIn(max = BUTTON_MAX_WIDTH),
    )
    Spacer(Modifier.height(16.dp))
    Button(
        onClick = { onTitleDone(text) },
        shape = ControlShape,
        enabled = text.isNotBlank(),
        modifier = Modifier.fillMaxWidth().widthIn(max = BUTTON_MAX_WIDTH).height(BUTTON_HEIGHT),
    ) {
        Text(
            if (song.title.isBlank()) "Next" else "Rename",
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

/** The song: what it was played behind, and the parts written down so far. */
@Composable
private fun SongEditorPane(
    song: Song,
    settings: Settings,
    onAddPart: () -> Unit,
    onSetCapo: () -> Unit,
    onRemovePart: (String) -> Unit,
    onMovePart: (Int, Int) -> Unit,
    onOpenPart: (String) -> Unit,
    onViewSong: () -> Unit,
    onEditComment: () -> Unit,
) {
    Spacer(Modifier.height(8.dp))
    if (song.parts.isEmpty()) {
        Text(
            "No parts yet. Capture one and name it.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        song.parts.forEachIndexed { index, part ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(ControlShape)
                    .clickable { onOpenPart(part.name) }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(part.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        part.chords.joinToString("  ") {
                            Capo.shortName(it.sounding, song.capo, settings.nameStyle)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Up and down rather than a dragged handle: the list is a handful of parts, and a
                // thumb that has to hold something still while aiming is the harder ask on a phone.
                // The ends grey out, so the list says where a part can actually go.
                IconButton(
                    onClick = { onMovePart(index, -1) },
                    enabled = index > 0,
                ) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move ${part.name} up")
                }
                IconButton(
                    onClick = { onMovePart(index, 1) },
                    enabled = index < song.parts.lastIndex,
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = "Move ${part.name} down",
                    )
                }
                IconButton(onClick = { onRemovePart(part.name) }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Remove ${part.name}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    Spacer(Modifier.height(12.dp))
    // Under the parts because it is about all of them: the strumming pattern, the odd tuning, the
    // bit that goes quiet — whatever the chords cannot say for themselves.
    Column(
        Modifier
            .fillMaxWidth()
            .widthIn(max = BUTTON_MAX_WIDTH)
            .clip(ControlShape)
            .clickable { onEditComment() }
            .padding(8.dp),
    ) {
        if (song.comment.isBlank()) {
            Text(
                "Add a comment",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Text(
                "Comment",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(song.comment, style = MaterialTheme.typography.bodyMedium)
        }
    }

    Spacer(Modifier.height(20.dp))
    Button(
        onClick = onAddPart,
        shape = ControlShape,
        modifier = Modifier.fillMaxWidth().widthIn(max = BUTTON_MAX_WIDTH).height(BUTTON_HEIGHT),
    ) {
        Icon(Icons.Default.Mic, contentDescription = null)
        Spacer(Modifier.width(10.dp))
        Text("Capture a part", style = MaterialTheme.typography.titleMedium)
    }
    if (song.parts.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onViewSong,
            shape = ControlShape,
            modifier = Modifier.fillMaxWidth().widthIn(max = BUTTON_MAX_WIDTH).height(BUTTON_HEIGHT),
        ) {
            Text("View song", style = MaterialTheme.typography.titleMedium)
        }
    }
    CapoLink(song.capo, onSetCapo)
    Text(
        if (song.parts.isEmpty()) "The song is played behind this capo."
        else "Changing the capo keeps the key — only the chord diagrams change. " +
            "A shape you picked yourself goes back to the default.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )

    Spacer(Modifier.height(16.dp))
    // The answer to "is it saved?", now that nothing here asks you to save it.
    Text(
        if (song.parts.isEmpty()) "The song joins your library once it has a part."
        else "Saved as you go.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

/**
 * Capturing a part: strum, let it ring, watch it land, mute, strum the next.
 *
 * The muting is not fussiness, it is the technique the capture needs, and testing on the guitar is
 * what established it: a chord left ringing is still sounding when the next pass opens, so its
 * decay is read as the next chord — the reading comes back wrong rather than absent. Silence
 * between chords is what separates them, so the screen has to ask for it in as many words.
 */
@Composable
private fun CapturePane(
    state: SongState.Capturing,
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
    Spacer(Modifier.height(12.dp))
    CapturedRow(state.captured, settings)
    Spacer(Modifier.height(20.dp))
    LevelMeter(state.level)
    Spacer(Modifier.height(28.dp))
    OutlinedButton(
        onClick = onDone,
        shape = ControlShape,
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
 * The captured run, before it is committed.
 *
 * Review is not optional and this is why: a forgotten mute produces a *wrong* chord rather than a
 * missing one, and nothing about it looks broken. Tapping one opens the same question the result
 * page answers.
 */
@Composable
private fun ReviewPane(
    state: SongState.Review,
    settings: Settings,
    onEditChord: (Int) -> Unit,
    onDone: () -> Unit,
    onDiscard: () -> Unit,
) {
    Spacer(Modifier.height(8.dp))
    if (state.captured.isEmpty()) {
        Text("Nothing captured.", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Hold the phone near the guitar and try again.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    } else {
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
        Spacer(Modifier.height(12.dp))
        CapturedRow(state.captured, settings, onClick = onEditChord)
    }

    Spacer(Modifier.height(28.dp))
    Button(
        onClick = onDone,
        shape = ControlShape,
        enabled = state.captured.isNotEmpty(),
        modifier = Modifier.fillMaxWidth().widthIn(max = BUTTON_MAX_WIDTH).height(BUTTON_HEIGHT),
    ) {
        Text("Name the part", style = MaterialTheme.typography.titleMedium)
    }
    Spacer(Modifier.height(4.dp))
    TextButton(
        onClick = onDiscard,
        shape = ControlShape,
        modifier = Modifier.fillMaxWidth().widthIn(max = BUTTON_MAX_WIDTH).height(48.dp),
    ) {
        Text("Discard", style = MaterialTheme.typography.titleSmall)
    }
}

/**
 * One captured chord: what it is, and how you play it.
 *
 * The same two rows the result page shows, both live here: the runner-ups fix what was misheard,
 * and the variations are the whole reason a song is worth more than a list of chord names — tapping
 * one records *the shape your hands make*, rather than the one the library happens to lead with.
 */
@Composable
private fun EditChordPane(
    state: SongState.EditChord,
    settings: Settings,
    onSelectChord: (Chord) -> Unit,
    onSelectVoicing: (Voicing) -> Unit,
    onRemoveChord: (Int) -> Unit,
) {
    val captured = state.captured[state.index]
    val view = ChordView.of(captured.selected, settings)
    val chosen = captured.voicing ?: view.best

    Spacer(Modifier.height(8.dp))
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
                val voicing = defaultVoicing(candidate.chord, settings.capo)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(ControlShape)
                        .clickable { onSelectChord(candidate.chord) }
                        .padding(4.dp),
                ) {
                    Text(
                        Capo.shortName(candidate.chord, settings.capo, settings.nameStyle),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(4.dp))
                    ChordDiagram(
                        voicing = voicing,
                        width = SMALL_DIAGRAM_WIDTH,
                        capo = settings.capo,
                    )
                }
            }
        }
    }

    Spacer(Modifier.height(24.dp))
    TextButton(
        onClick = { onRemoveChord(state.index) },
        shape = ControlShape,
        modifier = Modifier.fillMaxWidth().widthIn(max = BUTTON_MAX_WIDTH).height(48.dp),
    ) {
        Text("Remove this chord", style = MaterialTheme.typography.titleSmall)
    }
}

/**
 * Naming the run, which is what turns it into a part.
 *
 * Naming comes after playing on purpose: typing "Chorus" with a guitar on your lap is the worst
 * moment to ask for it, so the common names are one tap. A name already used is disabled rather
 * than hidden — that is the one-of-each rule showing its work, and it doubles as a reminder of
 * what the song already has.
 */
@Composable
private fun NamingPane(song: Song, onNamePart: (String) -> Unit) {
    var custom by rememberSaveable { mutableStateOf("") }
    val used = song.parts.map { it.name }.toSet()

    Spacer(Modifier.height(8.dp))
    Text(
        "What was that?",
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(12.dp))
    ChipRow {
        PART_NAMES.forEach { name ->
            FilterChip(
                selected = false,
                enabled = name !in used,
                onClick = { onNamePart(name) },
                shape = ControlShape,
                label = { Text(name) },
            )
        }
    }
    Spacer(Modifier.height(20.dp))
    OutlinedTextField(
        value = custom,
        onValueChange = { custom = it },
        label = { Text("Or a name of your own") },
        singleLine = true,
        shape = ControlShape,
        keyboardOptions = NameKeyboard,
        modifier = Modifier.fillMaxWidth().widthIn(max = BUTTON_MAX_WIDTH),
    )
    Spacer(Modifier.height(12.dp))
    Button(
        onClick = { onNamePart(custom) },
        shape = ControlShape,
        enabled = custom.isNotBlank(),
        modifier = Modifier.fillMaxWidth().widthIn(max = BUTTON_MAX_WIDTH).height(BUTTON_HEIGHT),
    ) {
        Text("Save part", style = MaterialTheme.typography.titleMedium)
    }
    if (custom.trim() in used) {
        Spacer(Modifier.height(8.dp))
        Text(
            "\"${custom.trim()}\" already exists — saving replaces it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** The captured run, in order. Named through [Capo], so the song's capo decides what to call it. */
@Composable
private fun CapturedRow(
    captured: List<CapturedChord>,
    settings: Settings,
    onClick: ((Int) -> Unit)? = null,
) {
    if (captured.isEmpty()) {
        Text(
            "—",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    // Enabled even where there is nothing to tap: during capture these are feedback, not controls,
    // and a disabled chip greys out exactly the word you are waiting to read.
    ChipFlow {
        captured.forEachIndexed { index, chord ->
            FilterChip(
                selected = false,
                onClick = { onClick?.invoke(index) },
                shape = ControlShape,
                label = {
                    Text(Capo.shortName(chord.selected, settings.capo, settings.nameStyle))
                },
            )
        }
    }
}
