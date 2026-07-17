package de.singular.crystalball

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.singular.crystalball.chords.Voicing
import de.singular.crystalball.songs.CapturedChord
import de.singular.crystalball.songs.defaultVoicing
import de.singular.crystalball.songs.Part
import de.singular.crystalball.songs.Song
import de.singular.crystalball.songs.SongChord
import de.singular.crystalball.songs.SongRepository
import de.singular.crystalball.ui.SongPdf
import de.singular.crystalball.songs.movePart
import de.singular.crystalball.songs.upsertPart
import de.singular.crystalball.songs.removePart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Which page of the song library is showing.
 *
 * Capturing chords is no longer here — it lives on the detect screen, with the microphone (see
 * [DetectViewModel]). This is the library and its editor: viewing, ordering, and refining songs the
 * capture flow has already saved.
 */
sealed interface SongState {
    /** The saved songs: home for the library, and where a save lands. */
    data object Library : SongState

    /** The song itself: its capo, and the parts captured so far. */
    data object Editor : SongState

    /**
     * A saved part, drawn out: its chords as shapes rather than names.
     *
     * Held by name rather than by index so reordering the song underneath cannot leave this
     * pointing at a different part.
     */
    data class PartView(val partName: String) : SongState

    /** One chord of a saved part, open to choose how you play it. */
    data class EditPartChord(val partName: String, val index: Int) : SongState

    /** The whole song on one page: every part, and the shapes it asks for. */
    data object SongView : SongState

    /** Writing the song's comment, on its own page. */
    data object Comment : SongState
}

/**
 * Where a captured run is saved: onto a new song, or onto one already in the library.
 *
 * The capture happens before either exists as a decision — that is the point of the rework — so the
 * choice is carried here and resolved once, in [SongViewModel.saveCapture].
 */
sealed interface SaveTarget {
    /** A song that does not exist yet: its title, and the capo it was captured behind. */
    data class NewSong(val title: String, val capo: Int) : SaveTarget

    /** A song already in the library, addressed by id so a stale copy cannot overwrite it. */
    data class Existing(val id: String) : SaveTarget
}

/**
 * How a save from the capture flow went, for a one-shot toast on the detect screen.
 *
 * The capture screen is not the library, so a failure there cannot fall to [SongViewModel] error
 * state the way an edit does — that surfaces only on the library screen. This carries the outcome
 * back to where the user actually is.
 */
sealed interface SaveResult {
    data class Saved(val songTitle: String, val partName: String) : SaveResult
    data class Failed(val reason: String) : SaveResult
}

/**
 * The outcome of the last backup or restore, for a one-shot toast.
 *
 * [RESTORED] carries the count because a restore is the one operation here that silently changes
 * everything: "12 songs restored" is the difference between a reassurance and an announcement that
 * something happened to your library.
 */
sealed interface BackupResult {
    data object Exported : BackupResult
    data class Restored(val songs: Int) : BackupResult
    data class Failed(val reason: String) : BackupResult
}

/**
 * The song library and its editor: the songs a capture has saved, and the ways to view and refine
 * them — order the parts, move the capo, choose grips, write a comment, print a sheet.
 *
 * Separate from [DetectViewModel] because the jobs are different — that one listens to the guitar,
 * this one keeps a document — and folding them together would double its size. The microphone lives
 * entirely on that side now; capture hands its chords here through [saveCapture], and nothing in
 * this class opens an `AudioRecord`.
 */
class SongViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SongRepository(application)

    private val _song = MutableStateFlow(emptySong(capo = 0))
    val song: StateFlow<Song> = _song.asStateFlow()

    private val _state = MutableStateFlow<SongState>(SongState.Library)
    val state: StateFlow<SongState> = _state.asStateFlow()

    private val _library = MutableStateFlow<List<Song>>(emptyList())
    val library: StateFlow<List<Song>> = _library.asStateFlow()

    /**
     * Why the library could not be read or written, for the screen to say out loud.
     *
     * Not decoration. [SongJson.decode] refuses a file it cannot understand rather than reporting
     * an empty library, precisely so a save cannot overwrite songs we merely failed to parse — and
     * that refusal is worth nothing if it surfaces as an empty list or a crash.
     */
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Set when a PDF lands, so the screen can say so; cleared by [consumeExported]. */
    private val _exported = MutableStateFlow(false)
    val exported: StateFlow<Boolean> = _exported.asStateFlow()

    /**
     * How the last backup or restore went, for a toast; cleared by [consumeBackupResult].
     *
     * Its own channel rather than [_error], which means something else entirely: that the library
     * on disk cannot be read, a state the screen stays in and disables saving from. A backup that
     * failed because the user picked the wrong file is a passing remark, and routing it through
     * [_error] would blank the song list and announce that saving is off.
     */
    private val _backupResult = MutableStateFlow<BackupResult?>(null)
    val backupResult: StateFlow<BackupResult?> = _backupResult.asStateFlow()

    /** How the last [saveCapture] went, for a toast; cleared by [consumeSaveResult]. */
    private val _saveResult = MutableStateFlow<SaveResult?>(null)
    val saveResult: StateFlow<SaveResult?> = _saveResult.asStateFlow()

    init {
        // Read the library up front, not just when the songs screen opens: the detect screen's
        // "Save to an existing song" needs it too, and that is reachable without ever opening songs.
        refresh()
    }

    /** Open the library, and go and read it. */
    fun open() {
        _state.value = SongState.Library
        refresh()
    }

    /** Back to the library, dropping anything the current song had not saved. */
    fun backToLibrary() {
        _state.value = SongState.Library
        refresh()
    }

    /**
     * Save a captured run as a part, onto a new song or one already in the library.
     *
     * This is where a song is born now — not from a Title page before any music, but from chords
     * already in hand. [target] says which song; either way the part's grips are counted from
     * *that song's* capo, which is the whole reason [CapturedChord] stores sounding chords: a run
     * captured behind one capo drops cleanly onto a song sitting behind another. See [Song].
     *
     * Lands on the saved song's editor, so the save is something you can see — and, when the run
     * was captured from an open song, returns you to it. [SaveResult] carries the outcome back to
     * the detect screen for a toast, since a failure here cannot fall to the library's error line.
     */
    fun saveCapture(captured: List<CapturedChord>, partName: String, target: SaveTarget) {
        val name = partName.trim()
        if (name.isEmpty() || captured.isEmpty()) return
        viewModelScope.launch {
            val base = when (target) {
                is SaveTarget.NewSong -> {
                    val title = target.title.trim()
                    if (title.isEmpty()) return@launch
                    emptySong(target.capo).copy(title = title)
                }
                is SaveTarget.Existing -> {
                    val found = runCatching { repository.list() }
                        .onFailure { _saveResult.value = SaveResult.Failed(it.readable()) }
                        .getOrNull()?.firstOrNull { it.id == target.id }
                    found ?: run {
                        if (_saveResult.value == null) {
                            _saveResult.value = SaveResult.Failed("That song is no longer in your library.")
                        }
                        return@launch
                    }
                }
            }
            val updated = base.upsertPart(Part(name, captured.map { it.toSongChord(base.capo) }))
            runCatching { repository.save(updated) }
                .onSuccess {
                    _song.value = it
                    _state.value = SongState.Editor
                    _saveResult.value = SaveResult.Saved(it.title, name)
                }
                .onFailure { _saveResult.value = SaveResult.Failed(it.readable()) }
            reload()
        }
    }

    fun consumeSaveResult() {
        _saveResult.value = null
    }

    /** Open a saved song to view or refine it. */
    fun openSong(song: Song) {
        _song.value = song
        _state.value = SongState.Editor
    }

    /** Delete [ids] — one song from its own menu, or a whole selection at once. */
    fun deleteSongs(ids: Set<String>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            runCatching { repository.removeAll(ids) }.onFailure { _error.value = it.readable() }
            reload()
        }
    }

    /**
     * Rename the song with [id] where it lies, without opening it.
     *
     * Reads the song back from the library rather than taking one handed in, so a rename writes the
     * stored song with a new title and nothing else — the copy the list is holding could be a
     * moment stale, and saving that would quietly undo whatever changed in between.
     *
     * Blank is refused here as it is on the title page: a song with no name is not something the
     * library can show you.
     */
    fun renameSong(id: String, title: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                repository.list().firstOrNull { it.id == id }
                    ?.let { repository.save(it.copy(title = trimmed)) }
            }.onFailure { _error.value = it.readable() }
            reload()
        }
    }

    private fun refresh() {
        viewModelScope.launch { reload() }
    }

    private suspend fun reload() {
        runCatching { repository.list() }
            .onSuccess { songs ->
                // Most recent first: the song you were just working on is the one you want back.
                _library.value = songs.sortedByDescending { it.updatedAt }
                _error.value = null
            }
            .onFailure { _error.value = it.readable() }
    }

    private fun Throwable.readable() =
        message ?: "The song library could not be read."

    /**
     * Rename the song open in the editor.
     *
     * Reached from the editor's own rename dialog, so it works on [_song] directly rather than
     * reading back by id the way [renameSong] does for the library list. Blank is refused: a song
     * with no name is not something the library can show you.
     */
    fun renameCurrentSong(title: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        _song.value = _song.value.copy(title = trimmed)
        persist()
    }

    /**
     * Move the song's capo, redrawing every part behind it.
     *
     * Nothing is transposed. The song goes on sounding exactly as it did — a part captured at capo
     * 2 as E, A, B is still E, A, B at capo 0 — and what changes is only what your hands do to
     * sound it: D, G, A shapes behind a capo at 2, E, A, B shapes without one.
     *
     * There is one capo on the guitar, so moving it moves it for the whole song — and the parts
     * already captured have to follow, because a grip stored at capo 2 counts its frets from the
     * 2nd fret and means something else at capo 4.
     *
     * They *can* follow, and that is the payoff for storing sounding chords rather than shapes: the
     * chords are what the microphone heard and stay true at any capo, so the shapes are simply
     * derived again. What does not survive is a grip you chose by hand — it was a way of playing
     * that chord from the old capo, and there is no honest way to translate it. The editor says so.
     */
    fun setCapo(fret: Int) {
        val capo = fret.coerceIn(0, Capo.MAX_FRET)
        val current = _song.value
        if (capo == current.capo) return
        _song.value = current.copy(
            capo = capo,
            parts = current.parts.map { part ->
                part.copy(
                    chords = part.chords.map { SongChord(it.sounding, defaultVoicing(it.sounding, capo)) },
                )
            },
        )
        persist()
    }

    /** True when a part holds a grip the user picked rather than the library's first choice. */
    val hasChosenGrips: Boolean
        get() = _song.value.parts.any { part ->
            part.chords.any { it.voicing != defaultVoicing(it.sounding, _song.value.capo) }
        }

    fun removePart(name: String) {
        _song.value = _song.value.removePart(name)
        persist()
    }

    /** Write or change the song's comment. */
    fun editComment() {
        if (_state.value is SongState.Editor) _state.value = SongState.Comment
    }

    /**
     * Keep the comment and go back to the song.
     *
     * Blank is a real answer here, unlike a title — clearing a comment is how you delete one.
     */
    fun commentDone(comment: String) {
        _song.value = _song.value.copy(comment = comment.trim())
        _state.value = SongState.Editor
        persist()
    }

    fun cancelComment() {
        if (_state.value is SongState.Comment) _state.value = SongState.Editor
    }

    /** The whole song on one page. */
    fun viewSong() {
        if (_state.value is SongState.Editor) _state.value = SongState.SongView
    }

    /** Open a saved part to see its shapes. */
    fun openPart(name: String) {
        if (_state.value is SongState.Editor) _state.value = SongState.PartView(name)
    }

    /** Open one chord of a saved part. */
    fun editPartChord(index: Int) {
        val current = _state.value as? SongState.PartView ?: return
        _state.value = SongState.EditPartChord(current.partName, index)
    }

    /** Back from a saved chord to its part. */
    fun backToPart() {
        val current = _state.value as? SongState.EditPartChord ?: return
        _state.value = SongState.PartView(current.partName)
    }

    /** Back from a saved part to the song. */
    fun backToEditor() {
        _state.value = SongState.Editor
    }

    /**
     * Record how a chord of a saved part is played.
     *
     * The sounding chord is untouched — this says which of the ways to play it your hands actually
     * make, which is the difference between a list of chord names and a song written down.
     */
    fun selectPartVoicing(voicing: Voicing) {
        val current = _state.value as? SongState.EditPartChord ?: return
        _song.value = _song.value.copy(
            parts = _song.value.parts.map { part ->
                if (part.name != current.partName) part
                else part.copy(
                    chords = part.chords.mapIndexed { i, chord ->
                        if (i == current.index) chord.copy(voicing = voicing) else chord
                    },
                )
            },
        )
        persist()
    }

    /** Move a part up or down the song. [offset] is -1 or 1; the ends stop it. */
    fun movePart(index: Int, offset: Int) {
        val moved = _song.value.movePart(index, offset)
        if (moved == _song.value) return
        _song.value = moved
        persist()
    }

    /**
     * Write the song down, if there is a song yet.
     *
     * Every edit persists, rather than waiting behind a Save button. A song opened out of the
     * library is a document that already exists, so renaming it and walking away has to mean the
     * rename happened — the alternative is a button you have no reason to press and a change that
     * quietly is not there when you get back.
     *
     * A song joins the library the moment it has a part, and not before: a title on its own is an
     * intention, and abandoning one should not leave an empty song lying about. After that it stays,
     * even stripped back to no parts, because by then it is something you can see and chose to keep.
     */
    private fun persist() {
        val song = _song.value
        if (song.title.isBlank()) return
        if (song.parts.isEmpty() && _library.value.none { it.id == song.id }) return
        viewModelScope.launch {
            runCatching { repository.save(song) }
                .onSuccess { _song.value = it }
                .onFailure { _error.value = it.readable() }
            reload()
        }
    }

    /**
     * Write the current song to [uri] as a chord sheet.
     *
     * [nameStyle] is passed in rather than read here: it lives with the detect screen's settings,
     * and this view-model has no business knowing about them.
     */
    fun exportPdf(uri: Uri, nameStyle: NameStyle) {
        val song = _song.value
        if (song.parts.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val resolver = getApplication<Application>().contentResolver
                    checkNotNull(resolver.openOutputStream(uri)) { "could not write to that file" }
                        .use { SongPdf.write(getApplication(), song, nameStyle, it) }
                }
            }
                .onSuccess { _exported.value = true }
                .onFailure { _error.value = it.readable() }
        }
    }

    fun consumeExported() {
        _exported.value = false
    }

    /** Write every song to the user-chosen [uri] as a backup zip. */
    fun backupSongs(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val resolver = getApplication<Application>().contentResolver
                    checkNotNull(resolver.openOutputStream(uri)) { "That file could not be written." }
                        .use { repository.exportTo(it) }
                }
            }
                .onSuccess { _backupResult.value = BackupResult.Exported }
                .onFailure { _backupResult.value = BackupResult.Failed(it.backupReason()) }
        }
    }

    /**
     * Replace the whole library with the backup at [uri].
     *
     * Lands back in the library on success, whatever was open before: the song in the editor was one
     * this library may no longer contain, and leaving it on screen would invite a save that puts it
     * straight back into the library the user just replaced.
     */
    fun restoreSongs(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val resolver = getApplication<Application>().contentResolver
                    checkNotNull(resolver.openInputStream(uri)) { "That file could not be read." }
                        .use { repository.importFrom(it) }
                }
            }
                .onSuccess { count ->
                    _song.value = emptySong(capo = 0)
                    _state.value = SongState.Library
                    // Reload before reporting, so the list is the restored one by the time the
                    // toast claims it is.
                    reload()
                    _backupResult.value = BackupResult.Restored(count)
                }
                .onFailure { _backupResult.value = BackupResult.Failed(it.backupReason()) }
        }
    }

    fun consumeBackupResult() {
        _backupResult.value = null
    }

    /**
     * Why a backup failed, in the words the repository used.
     *
     * [readable] is wrong here: its fallback blames reading the library, which is exactly what did
     * not happen when a restore was handed a photo of a cat.
     */
    private fun Throwable.backupReason() = message ?: "That file could not be used."

    fun consumeError() {
        _error.value = null
    }

    private fun emptySong(capo: Int) =
        Song(id = UUID.randomUUID().toString(), title = "", capo = capo)
}
