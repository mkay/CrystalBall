// SPDX-License-Identifier: GPL-3.0-only

package de.singular.crystalball.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.singular.crystalball.Capo
import de.singular.crystalball.R
import de.singular.crystalball.ChordView
import de.singular.crystalball.Settings
import de.singular.crystalball.SongState
import de.singular.crystalball.audio.Chord
import de.singular.crystalball.chords.Voicing
import de.singular.crystalball.songs.Part
import de.singular.crystalball.songs.Song
import de.singular.crystalball.songs.SongChord

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
    @StringRes error: Int?,
    settings: Settings,
    onRenameCurrentSong: (String) -> Unit,
    onAddPart: () -> Unit,
    onSetCapo: () -> Unit,
    onRemovePart: (String) -> Unit,
    onDuplicatePart: (String) -> Unit,
    onRenamePart: (String, String) -> Unit,
    onMovePart: (Int, Int) -> Unit,
    onOpenPart: (String) -> Unit,
    onViewSong: () -> Unit,
    onExportPdf: () -> Unit,
    onSharePdf: () -> Unit,
    onEditComment: () -> Unit,
    onCommentDone: (String) -> Unit,
    onCancelComment: () -> Unit,
    onEditPartChord: (Int) -> Unit,
    onCorrectPartChord: (Int, Chord) -> Unit,
    onSelectPartVoicing: (Voicing) -> Unit,
    onBackToPart: () -> Unit,
    onBackToEditor: () -> Unit,
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

    // The editor's rename dialog. Renaming is a dialog here, as it is from the library list — a
    // detour that leaves you where it found you, not a page in a flow.
    var renameOpen by remember { mutableStateOf(false) }

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
        is SongState.Editor -> onBackToLibrary
        is SongState.PartView -> onBackToEditor
        is SongState.SongView -> onBackToEditor
        is SongState.Comment -> onCancelComment
        is SongState.EditPartChord -> onBackToPart
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
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.cd_cancel_selection),
                            )
                        }
                    } else {
                        IconButton(onClick = back) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                            )
                        }
                    }
                },
                actions = {
                    if (selecting) {
                        IconButton(onClick = { pendingDelete = selection }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.cd_delete_selected),
                            )
                        }
                    }
                    // The title is the top bar now, so renaming belongs here rather than as a
                    // field competing with Save for what "commit" means.
                    if (state is SongState.Editor) {
                        IconButton(onClick = { renameOpen = true }) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = stringResource(R.string.action_rename_song),
                            )
                        }
                    }
                    // Export sits on the song view, which is the page it prints. Two ways out of
                    // here, because they are different errands: sending the sheet to whoever you
                    // are playing with, and keeping a copy where you file things.
                    if (state is SongState.SongView) {
                        var exportMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { exportMenu = true }) {
                                Icon(
                                    Icons.Default.Share,
                                    contentDescription = stringResource(R.string.cd_export_pdf),
                                )
                            }
                            DropdownMenu(
                                expanded = exportMenu,
                                onDismissRequest = { exportMenu = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_share)) },
                                    leadingIcon = {
                                        Icon(Icons.Default.Share, contentDescription = null)
                                    },
                                    onClick = { exportMenu = false; onSharePdf() },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_save_as_file)) },
                                    leadingIcon = {
                                        Icon(Icons.Default.Download, contentDescription = null)
                                    },
                                    onClick = { exportMenu = false; onExportPdf() },
                                )
                            }
                        }
                    }
                },
                title = {
                    Text(
                        if (selecting) {
                            pluralStringResource(
                                R.plurals.selection_count,
                                selection.size,
                                selection.size,
                            )
                        } else {
                            when (state) {
                                is SongState.Library -> stringResource(R.string.drawer_songs)
                                is SongState.Editor -> songTitle(song)
                                is SongState.PartView -> state.partName
                                is SongState.SongView -> songTitle(song)
                                is SongState.Comment -> stringResource(R.string.comment_title)
                                is SongState.EditPartChord ->
                                    stringResource(R.string.chord_number, state.index + 1)
                            }
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
                    )
                is SongState.Editor ->
                    SongEditorPane(
                        song, songSettings, onAddPart, onSetCapo, onRemovePart, onDuplicatePart,
                        onMovePart, onRenamePart, onOpenPart, onViewSong, onEditComment,
                    )
                is SongState.SongView -> SongViewPane(song, songSettings)
                is SongState.Comment -> CommentPane(song, onCommentDone)
                is SongState.PartView ->
                    song.parts.firstOrNull { it.name == state.partName }?.let { part ->
                        PartViewPane(
                            song, part, songSettings, onEditPartChord, onCorrectPartChord,
                        )
                    }
                is SongState.EditPartChord ->
                    song.parts.firstOrNull { it.name == state.partName }
                        ?.takeIf { state.index in it.chords.indices }
                        ?.let { part ->
                            EditPartChordPane(
                                song, part, state.index, songSettings, onSelectPartVoicing,
                            )
                        }
            }
        }
    }

    if (renameOpen) {
        RenameSongDialog(
            song = song,
            onRename = { onRenameCurrentSong(it); renameOpen = false },
            onDismiss = { renameOpen = false },
        )
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
                    if (single != null) {
                        stringResource(R.string.delete_song_title, single.title)
                    } else {
                        pluralStringResource(
                            R.plurals.delete_songs_title,
                            doomed.size,
                            doomed.size,
                        )
                    },
                )
            },
            text = {
                Text(
                    if (single != null) {
                        stringResource(R.string.delete_song_body)
                    } else {
                        stringResource(R.string.delete_songs_body)
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteSongs(doomed)
                    pendingDelete = emptySet()
                    selection = emptySet()
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = emptySet() }) {
                    Text(stringResource(R.string.action_cancel))
                }
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
    @StringRes error: Int?,
    selection: Set<String>,
    onOpenSong: (Song) -> Unit,
    onToggleSelect: (String) -> Unit,
    onRequestDelete: (String) -> Unit,
    onRenameSong: (String, String) -> Unit,
) {
    var renameTarget by remember { mutableStateOf<Song?>(null) }

    Spacer(Modifier.height(8.dp))
    when {
        // Said out loud rather than shown as an empty library: the file is refused when it cannot
        // be parsed, and pretending there are no songs is how you end up saving over them.
        error != null -> {
            Text(
                stringResource(R.string.library_unreadable_title),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.library_unreadable_body, stringResource(error)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        library.isEmpty() -> {
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(R.string.library_empty_title),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.library_empty_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        else -> library.forEachIndexed { index, song ->
            SongRow(
                song = song,
                banded = index % 2 == 1,
                selected = song.id in selection,
                selecting = selection.isNotEmpty(),
                onOpen = { onOpenSong(song) },
                onToggleSelect = { onToggleSelect(song.id) },
                onRename = { renameTarget = song },
                onDelete = { onRequestDelete(song.id) },
            )
        }
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
    banded: Boolean,
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
        // Every other row gets a faint band, so a long title running onto a second line still reads
        // as one song. It is a surface token rather than a tinted one: it should be felt as texture
        // and not seen as meaning, since only selection is allowed to say something with colour —
        // which is why selection wins here outright rather than layering over the band.
        color = when {
            selected -> MaterialTheme.colorScheme.secondaryContainer
            banded -> MaterialTheme.colorScheme.surfaceContainerHigh
            else -> Color.Transparent
        },
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
                        contentDescription = stringResource(
                            if (selected) R.string.cd_selected else R.string.cd_not_selected,
                        ),
                        tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.cd_options_for, song.title),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_rename)) },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = { menu = false; onRename() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_delete)) },
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
        title = { Text(stringResource(R.string.rename_song_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.save_song_title)) },
                singleLine = true,
                shape = ControlShape,
                keyboardOptions = NameKeyboard,
            )
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = { onRename(text) }) {
                Text(stringResource(R.string.action_rename))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** What to head a song with when it has no name of its own yet. */
@Composable
private fun songTitle(song: Song): String =
    song.title.ifBlank { stringResource(R.string.untitled_song) }

/** A song at a glance: what it asks of your hands, and what is in it. */
@Composable
private fun songSummary(song: Song): String {
    val capo = capoStatus(song.capo)
    val parts = song.parts.joinToString(", ") { it.name }
    return if (parts.isEmpty()) capo else stringResource(R.string.song_summary, capo, parts)
}

/** "No capo" or "Capo 3" — what the song asks of your hands before a note is played. */
@Composable
private fun capoStatus(capo: Int): String =
    if (capo == 0) stringResource(R.string.capo_none_set)
    else stringResource(R.string.capo_at, capo)

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
        stringResource(R.string.comment_prompt),
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        stringResource(R.string.comment_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text(stringResource(R.string.comment_title)) },
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
        Text(stringResource(R.string.action_save_comment), style = MaterialTheme.typography.titleMedium)
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
        capoStatus(song.capo),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    // Says the page is read-only, rather than letting you find out by pressing a chord and having
    // nothing happen. Screen-only: the PDF is drawn by [SongPdf], which prints no such line —
    // sheet music that explains the app it came from would be a strange thing to hand someone.
    Spacer(Modifier.height(12.dp))
    Text(
        stringResource(R.string.song_view_readonly),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
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
                stringResource(R.string.comment_title),
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
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChordCell(
    chord: SongChord,
    settings: Settings,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val haptic = LocalHapticFeedback.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(ControlShape)
            .then(
                if (onClick == null) Modifier
                else Modifier.combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick?.let {
                        {
                            // Same confirmation in the hand the library's long-press gives: the
                            // gesture has nothing on screen to say it is there.
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            it()
                        }
                    },
                ),
            )
            .padding(4.dp),
    ) {
        Text(
            Capo.shortName(chord.sounding, settings.capo, settings.nameStyle, settings.noteNaming),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(4.dp))
        ChordDiagram(
            voicing = chord.voicing,
            width = SMALL_DIAGRAM_WIDTH,
            caption = voicingCaption(chord.voicing),
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
    onCorrectPartChord: (Int, Chord) -> Unit,
) {
    // Which chord is being put right, if any. Screen state: a sheet you are holding open, not
    // anything the song knows about.
    var correcting by remember { mutableStateOf<Int?>(null) }

    Spacer(Modifier.height(8.dp))
    Text(
        // The gestures carry the weight: the two lines are one shape on the page, and what differs
        // between them is the first word.
        gestureHint(),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(16.dp))
    DiagramFlow {
        part.chords.forEachIndexed { index, chord ->
            ChordCell(
                chord = chord,
                settings = settings,
                onClick = { onEditPartChord(index) },
                onLongClick = { correcting = index },
            )
        }
    }

    correcting?.takeIf { it in part.chords.indices }?.let { index ->
        CorrectChordSheet(
            chord = part.chords[index],
            settings = settings,
            onCorrect = { onCorrectPartChord(index, it) },
            onDismiss = { correcting = null },
        )
    }
}

/**
 * Put right a chord that was heard wrong.
 *
 * A sheet rather than a page, because it is a repair and not a step: it opens over the part, and
 * what you came to check — whether the row now reads the way the song goes — is still behind it.
 *
 * The correction applies as you tap, with no confirmation, and the sheet stays open: naming a chord
 * takes up to two taps (root, then quality), and the diagram redrawing under them is the only
 * confirmation worth having. Nothing is lost by a wrong tap that a right one does not undo — the
 * grip resets to the library's default either way.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CorrectChordSheet(
    chord: SongChord,
    settings: Settings,
    onCorrect: (Chord) -> Unit,
    onDismiss: () -> Unit,
) {
    val view = ChordView.of(chord.sounding, settings, chord.voicing)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.correct_chord_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.align(Alignment.Start),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.correct_chord_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Start),
            )

            Spacer(Modifier.height(16.dp))
            ChordChooser(chord.sounding, settings.noteNaming, onCorrect)

            // The name is chosen in sounding terms, so behind a capo it is the shape underneath it
            // that tells you whether you picked the right one.
            Spacer(Modifier.height(20.dp))
            Text(view.title, style = MaterialTheme.typography.titleLarge)
            view.shapeLine?.let { shapeLine ->
                Text(
                    chordSubtitle(shapeLine, settings.capo, settings.noteNaming),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            ChordDiagram(
                voicing = chord.voicing,
                width = SMALL_DIAGRAM_WIDTH,
                caption = voicingCaption(chord.voicing),
                capo = settings.capo,
            )
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
    val view = ChordView.of(chord.sounding, settings, chord.voicing)

    Spacer(Modifier.height(8.dp))
    Text(view.title, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
    view.shapeLine?.let { shapeLine ->
        Text(
            chordSubtitle(shapeLine, song.capo, settings.noteNaming),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(8.dp))
    ChordDiagram(
        voicing = chord.voicing,
        width = BEST_DIAGRAM_WIDTH,
        caption = voicingCaption(chord.voicing),
        capo = song.capo,
    )
    Spacer(Modifier.height(28.dp))
    VoicingPicker(view, chord.voicing, song.capo, onSelectVoicing)
}

/** The song: what it was played behind, and the parts written down so far. */
@Composable
private fun SongEditorPane(
    song: Song,
    settings: Settings,
    onAddPart: () -> Unit,
    onSetCapo: () -> Unit,
    onRemovePart: (String) -> Unit,
    onDuplicatePart: (String) -> Unit,
    onMovePart: (Int, Int) -> Unit,
    onRenamePart: (String, String) -> Unit,
    onOpenPart: (String) -> Unit,
    onViewSong: () -> Unit,
    onEditComment: () -> Unit,
) {
    // Which part is being renamed, by the name it still has. Screen state: a dialog you have open.
    var renameTarget by remember { mutableStateOf<String?>(null) }

    Spacer(Modifier.height(8.dp))
    // Only over a list there is: with no parts yet the line below is already about their absence,
    // and heading it would be labelling an empty shelf.
    if (song.parts.isNotEmpty()) SectionLabel(stringResource(R.string.song_parts))
    if (song.parts.isEmpty()) {
        Text(
            stringResource(R.string.song_no_parts),
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
                            Capo.shortName(it.sounding, song.capo, settings.nameStyle, settings.noteNaming)
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
                    Icon(
                        Icons.Default.KeyboardArrowUp,
                        contentDescription = stringResource(R.string.cd_move_part_up, part.name),
                    )
                }
                IconButton(
                    onClick = { onMovePart(index, 1) },
                    enabled = index < song.parts.lastIndex,
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.cd_move_part_down, part.name),
                    )
                }
                // The same overflow the library's rows carry, for the same reason: a bare trash can
                // is one slip from losing a part, and it leaves nowhere to put a second action.
                var menu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.cd_options_for, part.name),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_rename)) },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = { menu = false; renameTarget = part.name },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_duplicate)) },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                            onClick = { menu = false; onDuplicatePart(part.name) },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_delete)) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                            onClick = { menu = false; onRemovePart(part.name) },
                        )
                    }
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
                stringResource(R.string.action_add_comment),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Text(
                stringResource(R.string.comment_title),
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
        Text(stringResource(R.string.action_capture_part), style = MaterialTheme.typography.titleMedium)
    }
    if (song.parts.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onViewSong,
            shape = ControlShape,
            modifier = Modifier.fillMaxWidth().widthIn(max = BUTTON_MAX_WIDTH).height(BUTTON_HEIGHT),
        ) {
            Text(stringResource(R.string.action_view_song), style = MaterialTheme.typography.titleMedium)
        }
    }
    CapoLink(song.capo, onSetCapo)
    Text(
        stringResource(
            if (song.parts.isEmpty()) R.string.song_capo_note_empty
            else R.string.song_capo_note,
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )

    Spacer(Modifier.height(16.dp))
    // The answer to "is it saved?", now that nothing here asks you to save it.
    Text(
        stringResource(
            if (song.parts.isEmpty()) R.string.song_saved_note_empty
            else R.string.song_saved_note,
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )

    renameTarget?.takeIf { name -> song.parts.any { it.name == name } }?.let { name ->
        RenamePartDialog(
            name = name,
            taken = song.parts.mapTo(mutableSetOf()) { it.name } - name,
            onRename = { onRenamePart(name, it); renameTarget = null },
            onDismiss = { renameTarget = null },
        )
    }
}

/**
 * Rename a part, from the editor's list.
 *
 * A part's name is what the rest of the song addresses it by, so a name another part already has is
 * refused here rather than quietly ignored downstream — with the reason on the field, because a
 * disabled button that will not say why is the more annoying half of this.
 */
@Composable
private fun RenamePartDialog(
    name: String,
    taken: Set<String>,
    onRename: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable(name) { mutableStateOf(name) }
    val clash = text.trim() in taken

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename_part_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.part_name_label)) },
                singleLine = true,
                shape = ControlShape,
                keyboardOptions = NameKeyboard,
                isError = clash,
                supportingText = if (clash) {
                    { Text(stringResource(R.string.part_name_taken, text.trim())) }
                } else {
                    null
                },
            )
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank() && !clash, onClick = { onRename(text) }) {
                Text(stringResource(R.string.action_rename))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/**
 * "**Tap** a chord to change how you play it." — the two gestures, with the gesture itself in bold.
 *
 * Built from two whole sentences and a marker rather than from four fragments: which word carries
 * the gesture is a matter of grammar, and in another language it is rarely the first one. The
 * translation says where the bold goes by wrapping that word in asterisks, and this splits on them.
 */
@Composable
private fun gestureHint(): AnnotatedString {
    val lines = listOf(
        stringResource(R.string.part_hint_tap),
        stringResource(R.string.part_hint_long_press),
    )
    return buildAnnotatedString {
        lines.forEachIndexed { index, line ->
            if (index > 0) append("\n")
            line.split("*").forEachIndexed { part, text ->
                // Odd pieces are the ones that were between the markers.
                if (part % 2 == 1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text) }
                } else {
                    append(text)
                }
            }
        }
    }
}

