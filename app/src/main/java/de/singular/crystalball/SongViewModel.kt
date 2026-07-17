package de.singular.crystalball

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.singular.crystalball.audio.Chord
import de.singular.crystalball.audio.ChordListener
import de.singular.crystalball.audio.ListenEvent
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/** Which page of the song flow is showing. */
sealed interface SongState {
    /** The saved songs: home for the song flow, and where a save lands. */
    data object Library : SongState

    /**
     * Naming the song, as its own step — before any of the music, and again on a rename.
     *
     * Separate from [Editor] because typing a title and capturing chords are different jobs, and a
     * text field sitting next to a Save button reads as though the two are the same commit.
     */
    data object Title : SongState

    /** The song itself: its capo, and the parts captured so far. */
    data object Editor : SongState

    /** The microphone is open, taking one chord per strum. */
    data class Capturing(
        val captured: List<CapturedChord> = emptyList(),
        val level: Float = 0f,
        val heardStrum: Boolean = false,
    ) : SongState

    /** What the capture heard, before it becomes a part. */
    data class Review(val captured: List<CapturedChord>) : SongState

    /** One captured chord, open for a correction or a choice of grip. */
    data class EditChord(val captured: List<CapturedChord>, val index: Int) : SongState

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

    /** Writing the song's comment, on its own page like the title. */
    data object Comment : SongState

    /** Naming the reviewed run, which is what turns it into a part. */
    data class Naming(val captured: List<CapturedChord>) : SongState
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
 * Writing a song down: capture a part, fix what was misheard, say how you play it, name it.
 *
 * Separate from [DetectViewModel] because the jobs are different — that one answers "what is this
 * chord", this one records a document — and folding songs into it would double its size.
 *
 * It owns a second [ChordListener], and therefore a second possible `AudioRecord`. That is safe
 * only because the song screen and the detect screen are never on top of each other, which is an
 * invariant held by the UI rather than by anything here. If the screens ever overlap, this is the
 * thing that breaks, and the fix is a shared capture loop rather than a listener each.
 */
class SongViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SongRepository(application)
    private val listener = ChordListener()
    private var job: Job? = null

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

    val hasMicPermission: Boolean
        get() = ContextCompat.checkSelfPermission(
            getApplication(), Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

    /** Open the song flow at the library, and go and read it. */
    fun open() {
        _state.value = SongState.Library
        refresh()
    }

    /** Back to the library, dropping anything the current song had not saved. */
    fun backToLibrary() {
        job?.cancel()
        job = null
        _state.value = SongState.Library
        refresh()
    }

    /**
     * Begin a new song at [capo].
     *
     * The capo comes from the live setting because that is the fret you are actually playing
     * behind right now — but from here it is the *song's*, and every voicing in it will be counted
     * from it. See [Song].
     */
    fun startNewSong(capo: Int) {
        job?.cancel()
        job = null
        _song.value = emptySong(capo)
        _state.value = SongState.Title
    }

    /** Open a saved song to add to it. */
    fun openSong(song: Song) {
        job?.cancel()
        job = null
        _song.value = song
        _state.value = SongState.Editor
    }

    fun deleteSong(id: String) {
        viewModelScope.launch {
            runCatching { repository.remove(id) }.onFailure { _error.value = it.readable() }
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

    /** Rename the song: the naming step again, reached from the editor. */
    fun editTitle() {
        if (_state.value is SongState.Editor) _state.value = SongState.Title
    }

    /**
     * Commit the name and get on with the music.
     *
     * The title arrives here whole rather than keystroke by keystroke: a field bound live to the
     * song looks committed while it is not, which is the confusion this step exists to remove.
     */
    fun titleDone(title: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        _song.value = _song.value.copy(title = trimmed)
        _state.value = SongState.Editor
        persist()
    }

    /** Back out of a rename, leaving the name as it was. */
    fun cancelTitle() {
        if (_state.value is SongState.Title) _state.value = SongState.Editor
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

    /** Start capturing a part. No-ops without the microphone. */
    fun startCapture() {
        if (!hasMicPermission) return
        beginCapture()
    }

    /** See [DetectViewModel.detectOnPermissionGranted] for why the grant path is separate. */
    fun startCaptureOnPermissionGranted() = beginCapture()

    /**
     * Take chords one per strum, by running the ordinary one-shot listener over and over.
     *
     * Crude on purpose, and it holds up on the guitar: each chord gets the strum-and-wait treatment
     * the recogniser was actually tuned for, which is the whole reason stepped capture beats
     * listening continuously. Reopening the recorder between chords costs nothing you can feel.
     *
     * The condition is that the player mutes between chords — a chord left ringing is still
     * sounding when the next pass opens and gets read as the next chord. The capture screen asks
     * for it in as many words. That makes a forgotten mute a *wrong* answer rather than a missing
     * one, which is what [SongState.Review] is for.
     *
     * Ends on [ListenEvent.Silence], the listener's own timeout with nothing heard, so putting the
     * guitar down finishes the part without touching the phone.
     */
    @SuppressLint("MissingPermission") // callers above establish the grant
    private fun beginCapture() {
        job?.cancel()
        _state.value = SongState.Capturing()
        job = viewModelScope.launch {
            val captured = mutableListOf<CapturedChord>()
            while (isActive) {
                var heard = false
                listener.listen().collect { event ->
                    when (event) {
                        is ListenEvent.Level -> _state.value =
                            SongState.Capturing(captured.toList(), event.rms, event.heardStrum)
                        is ListenEvent.Detected -> {
                            heard = true
                            captured += CapturedChord(event.candidates)
                            _state.value =
                                SongState.Capturing(captured.toList(), heardStrum = true)
                        }
                        ListenEvent.Silence -> Unit
                    }
                }
                if (!heard) break
            }
            _state.value = SongState.Review(captured.toList())
        }
    }

    /**
     * Stop capturing and keep what was heard.
     *
     * The state is set here rather than left to [beginCapture]'s tail, which cancellation skips.
     */
    fun stopCapture() {
        job?.cancel()
        job = null
        val current = _state.value
        if (current is SongState.Capturing) _state.value = SongState.Review(current.captured)
    }

    /** Throw the capture away and go back to the song. */
    fun discardCapture() {
        job?.cancel()
        job = null
        _state.value = SongState.Editor
    }

    /** Open one captured chord to correct it or choose its grip. */
    fun editChord(index: Int) {
        val current = _state.value
        if (current is SongState.Review && index in current.captured.indices) {
            _state.value = SongState.EditChord(current.captured, index)
        }
    }

    /** Back out of the chord editor or the naming page, to the reviewed run. */
    fun backToReview() {
        _state.value = when (val current = _state.value) {
            is SongState.EditChord -> SongState.Review(current.captured)
            is SongState.Naming -> SongState.Review(current.captured)
            else -> return
        }
    }

    /**
     * Correct a misheard chord to one of the recogniser's runner-ups.
     *
     * This drops any grip chosen for it: the voicing was a way of playing a *different* chord, and
     * keeping it would silently document a shape that does not sound what the name says.
     */
    fun selectChord(chord: Chord) {
        val current = _state.value as? SongState.EditChord ?: return
        _state.value = current.copy(
            captured = current.captured.replaceAt(current.index) {
                it.copy(selected = chord, voicing = null)
            },
        )
    }

    /** Record how this chord is actually played. */
    fun selectVoicing(voicing: Voicing) {
        val current = _state.value as? SongState.EditChord ?: return
        _state.value = current.copy(
            captured = current.captured.replaceAt(current.index) { it.copy(voicing = voicing) },
        )
    }

    /** Drop a chord that should not have been captured — a stray strum, or a chord read twice. */
    fun removeChord(index: Int) {
        val remaining = when (val current = _state.value) {
            is SongState.Review -> current.captured
            is SongState.EditChord -> current.captured
            else -> return
        }.filterIndexed { i, _ -> i != index }
        _state.value = SongState.Review(remaining)
    }

    /** Done reviewing: on to naming, which is what makes it a part. */
    fun reviewDone() {
        val current = _state.value as? SongState.Review ?: return
        if (current.captured.isEmpty()) return
        _state.value = SongState.Naming(current.captured)
    }

    /**
     * Commit the captured run as [name].
     *
     * Recapturing an existing part replaces it: `upsertPart` is where the one-of-each rule lives.
     */
    fun namePart(name: String) {
        val current = _state.value as? SongState.Naming ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val capo = _song.value.capo
        _song.value = _song.value.upsertPart(
            Part(trimmed, current.captured.map { it.toSongChord(capo) }),
        )
        _state.value = SongState.Editor
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
                    job?.cancel()
                    job = null
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

    override fun onCleared() {
        job?.cancel()
        super.onCleared()
    }

    private fun emptySong(capo: Int) =
        Song(id = UUID.randomUUID().toString(), title = "", capo = capo)

    private fun <T> List<T>.replaceAt(index: Int, transform: (T) -> T): List<T> =
        mapIndexed { i, item -> if (i == index) transform(item) else item }
}
