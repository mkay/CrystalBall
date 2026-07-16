package de.singular.crystalball

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.singular.crystalball.audio.Chord
import de.singular.crystalball.audio.ChordCandidate
import de.singular.crystalball.audio.ChordListener
import de.singular.crystalball.audio.Quality
import de.singular.crystalball.audio.ListenEvent
import de.singular.crystalball.chords.ChordLibrary
import de.singular.crystalball.chords.Voicing
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Parse a stored [ThemeMode] name, falling back to [ThemeMode.SYSTEM] for null/unknown values. */
private fun readThemeMode(name: String?): ThemeMode =
    ThemeMode.entries.firstOrNull { it.name == name } ?: ThemeMode.SYSTEM

/** What the screen is doing. */
sealed interface DetectState {
    /** Waiting for the user to press the button. */
    data object Idle : DetectState

    /** Microphone is live. [level] drives the meter; [heardStrum] flips once a strum lands. */
    data class Listening(val level: Float = 0f, val heardStrum: Boolean = false) : DetectState

    /** Nothing audible arrived before the listener gave up. */
    data object Silence : DetectState

    /**
     * Looking a chord up by hand rather than playing it — the same page as [Result] minus the
     * runner-ups, because nothing was guessed here and there is nothing to have meant instead.
     */
    data class Browse(val chord: Chord) : DetectState

    /**
     * A chord was read. [selected] and [candidates] are always the **sounding** chords — what the
     * microphone actually heard — with any capo applied only when shapes are drawn for them.
     *
     * [selected] starts as the best fit but follows the user if they tap one of the alternatives:
     * the recogniser's ranking is a good guess, not gospel, and the player can see at a glance
     * which one they actually played.
     */
    data class Result(
        val candidates: List<ChordCandidate>,
        val selected: Chord,
    ) : DetectState {
        /** The runner-up chords, for the "did you mean" row. */
        val alternatives: List<ChordCandidate>
            get() = candidates.filter { it.chord != selected }.take(ALTERNATIVE_COUNT)
    }

    companion object {
        const val VARIATION_COUNT = 5
        const val ALTERNATIVE_COUNT = 4
    }
}

/**
 * Everything the result screen draws for one sounding chord: the shape to finger (which is the
 * chord itself when there is no capo), its diagrams, and the two names.
 */
data class ChordView(
    val sounding: Chord,
    val shape: Chord,
    val title: String,
    val subtitle: String?,
    val voicings: List<Voicing>,
) {
    /** The shape shown large. */
    val best: Voicing get() = voicings.first()

    /** Up to five further ways to play the same chord, walking up the neck. */
    val variations: List<Voicing> get() = voicings.drop(1).take(DetectState.VARIATION_COUNT)

    companion object {
        fun of(sounding: Chord, settings: Settings): ChordView {
            val shape = Capo.shapeChord(sounding, settings.capo)
            return ChordView(
                sounding = sounding,
                shape = shape,
                title = Capo.title(sounding, settings.capo, settings.nameStyle),
                subtitle = Capo.subtitle(sounding, settings.capo, settings.nameStyle),
                voicings = ChordLibrary.voicingsFor(shape, settings.capo),
            )
        }
    }
}

class DetectViewModel(application: Application) : AndroidViewModel(application) {

    private val listener = ChordListener()

    private val _state = MutableStateFlow<DetectState>(DetectState.Idle)
    val state: StateFlow<DetectState> = _state.asStateFlow()

    // User preferences that persist across app launches.
    private val prefs = application.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(
        Settings(
            capo = prefs.getInt(KEY_CAPO, 0).coerceIn(0, Capo.MAX_FRET),
            nameStyle = runCatching { NameStyle.valueOf(prefs.getString(KEY_NAME_STYLE, null) ?: "") }
                .getOrDefault(NameStyle.SOUNDING_FIRST),
            keepScreenOn = prefs.getBoolean(KEY_KEEP_SCREEN_ON, false),
            themeMode = readThemeMode(prefs.getString(KEY_THEME_MODE, null)),
        )
    )
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    fun setKeepScreenOn(on: Boolean) {
        _settings.value = _settings.value.copy(keepScreenOn = on)
        prefs.edit { putBoolean(KEY_KEEP_SCREEN_ON, on) }
    }

    fun setThemeMode(mode: ThemeMode) {
        _settings.value = _settings.value.copy(themeMode = mode)
        prefs.edit { putString(KEY_THEME_MODE, mode.name) }
    }

    fun setCapo(fret: Int) {
        val capo = fret.coerceIn(0, Capo.MAX_FRET)
        _settings.value = _settings.value.copy(capo = capo)
        prefs.edit { putInt(KEY_CAPO, capo) }
    }

    fun setNameStyle(style: NameStyle) {
        _settings.value = _settings.value.copy(nameStyle = style)
        prefs.edit { putString(KEY_NAME_STYLE, style.name) }
    }

    private var job: Job? = null

    val hasMicPermission: Boolean
        get() = ContextCompat.checkSelfPermission(
            getApplication(), Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * Start listening, restarting cleanly if already running. No-ops without the microphone
     * permission.
     */
    fun detect() {
        if (!hasMicPermission) return
        startListening()
    }

    /**
     * Start listening on the strength of a permission dialog that has just granted.
     *
     * Separate from [detect] because the dialog's own result is the authoritative answer, while
     * [hasMicPermission] re-reads process-cached permission state that is not guaranteed to have
     * caught up the instant the callback runs. Re-checking it here would risk silently dropping the
     * very press that asked for the microphone.
     */
    fun detectOnPermissionGranted() = startListening()

    @SuppressLint("MissingPermission") // callers above establish the grant
    private fun startListening() {
        job?.cancel()
        _state.value = DetectState.Listening()
        job = viewModelScope.launch {
            listener.listen().collect { event ->
                when (event) {
                    is ListenEvent.Level ->
                        _state.value = DetectState.Listening(event.rms, event.heardStrum)
                    is ListenEvent.Detected ->
                        _state.value = DetectState.Result(event.candidates, event.candidates.first().chord)
                    ListenEvent.Silence -> _state.value = DetectState.Silence
                }
            }
        }
    }

    /** Stop listening and go back to the idle screen. */
    fun cancel() {
        job?.cancel()
        job = null
        _state.value = DetectState.Idle
    }

    /** Open the chord browser, stopping the microphone if it happens to be listening. */
    fun showChords() {
        job?.cancel()
        job = null
        _state.value = DetectState.Browse(Chord(0, Quality.MAJ))
    }

    /** Pick a chord: a different candidate from the ranked list, or a different one to look up. */
    fun select(chord: Chord) {
        when (val current = _state.value) {
            is DetectState.Result -> _state.value = current.copy(selected = chord)
            is DetectState.Browse -> _state.value = current.copy(chord = chord)
            else -> Unit
        }
    }

    override fun onCleared() {
        job?.cancel()
        super.onCleared()
    }

    private companion object {
        const val KEY_CAPO = "capo"
        const val KEY_NAME_STYLE = "name_style"
        const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        const val KEY_THEME_MODE = "theme_mode"
    }
}
