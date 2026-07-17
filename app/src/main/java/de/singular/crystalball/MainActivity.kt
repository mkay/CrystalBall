package de.singular.crystalball

import android.Manifest
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.singular.crystalball.ui.CapoSheet
import de.singular.crystalball.ui.CrystalBallTheme
import de.singular.crystalball.ui.CrystalDrawer
import de.singular.crystalball.ui.QuickHelpSheet
import de.singular.crystalball.ui.DetectScreen
import de.singular.crystalball.ui.SettingsScreen
import de.singular.crystalball.ui.SongScreen
import de.singular.crystalball.ui.isDark
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val view = LocalView.current
            val viewModel: DetectViewModel = viewModel()
            val songViewModel: SongViewModel = viewModel()
            val settings by viewModel.settings.collectAsStateWithLifecycle()

            // Keep the system bar icons legible against whichever theme is in effect (the
            // enableEdgeToEdge default only tracks the OS setting, not our in-app override).
            val dark = isDark(settings.themeMode)
            SideEffect {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            CrystalBallTheme(settings.themeMode) {
                val state by viewModel.state.collectAsStateWithLifecycle()

                // Detection is the only thing the app does, so the permission is requested on the
                // first press rather than at launch — by then it is obvious what it is for. Granting
                // it starts the pass immediately, so one press is enough.
                // Which of the two microphone actions a pending grant belongs to: detecting one
                // chord, or capturing a part.
                var pendingCapture by rememberSaveable { mutableStateOf(false) }
                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { granted ->
                    if (granted) {
                        if (pendingCapture) songViewModel.startCaptureOnPermissionGranted()
                        else viewModel.detectOnPermissionGranted()
                    }
                    pendingCapture = false
                }

                val onDetect = {
                    if (viewModel.hasMicPermission) viewModel.detect()
                    else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }

                val onCapturePart = {
                    if (songViewModel.hasMicPermission) songViewModel.startCapture()
                    else {
                        pendingCapture = true
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }

                // Opens itself at launch when the user asked for it — a capo moved since yesterday
                // is then the first thing they set. Saveable, so a rotation does not bring the
                // sheet back after it has been dismissed.
                var capoOpen by rememberSaveable { mutableStateOf(settings.showCapoOnStart) }
                var settingsOpen by rememberSaveable { mutableStateOf(false) }
                var songsOpen by rememberSaveable { mutableStateOf(false) }
                var songCapoOpen by rememberSaveable { mutableStateOf(false) }
                var helpOpen by rememberSaveable { mutableStateOf(false) }

                val drawerState = rememberDrawerState(DrawerValue.Closed)
                val scope = rememberCoroutineScope()
                fun closeThen(action: () -> Unit) {
                    scope.launch { drawerState.close() }
                    action()
                }

                // Hold the display awake while the app is open. Released on dispose, so the setting
                // can never leak the flag into whatever the user opens next.
                DisposableEffect(settings.keepScreenOn) {
                    view.keepScreenOn = settings.keepScreenOn
                    onDispose { view.keepScreenOn = false }
                }

                // Songs is a full screen too, and owns the microphone while it is up — which is why
                // it and the detect screen must never be on top of each other. See [SongViewModel].
                if (songsOpen) {
                    val song by songViewModel.song.collectAsStateWithLifecycle()
                    val songState by songViewModel.state.collectAsStateWithLifecycle()
                    val library by songViewModel.library.collectAsStateWithLifecycle()
                    val songError by songViewModel.error.collectAsStateWithLifecycle()
                    val exported by songViewModel.exported.collectAsStateWithLifecycle()

                    // The system picker chooses where it lands, so the file is the user's from
                    // the moment it exists — no storage permission, nothing left in our sandbox.
                    val pdfLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.CreateDocument("application/pdf"),
                    ) { uri -> if (uri != null) songViewModel.exportPdf(uri, settings.nameStyle) }

                    LaunchedEffect(exported) {
                        if (exported) {
                            Toast.makeText(view.context, "PDF saved", Toast.LENGTH_SHORT).show()
                            songViewModel.consumeExported()
                        }
                    }

                    SongScreen(
                        song = song,
                        state = songState,
                        library = library,
                        error = songError,
                        settings = settings,
                        onTitleDone = songViewModel::titleDone,
                        onEditTitle = songViewModel::editTitle,
                        onCancelTitle = songViewModel::cancelTitle,
                        onAddPart = onCapturePart,
                        onSetCapo = { songCapoOpen = true },
                        onRemovePart = songViewModel::removePart,
                        onMovePart = songViewModel::movePart,
                        onStopCapture = songViewModel::stopCapture,
                        onDiscardCapture = songViewModel::discardCapture,
                        onEditChord = songViewModel::editChord,
                        onRemoveChord = songViewModel::removeChord,
                        onSelectChord = songViewModel::selectChord,
                        onSelectVoicing = songViewModel::selectVoicing,
                        onOpenPart = songViewModel::openPart,
                        onViewSong = songViewModel::viewSong,
                        onExportPdf = { pdfLauncher.launch(pdfFileName(song.title)) },
                        onEditComment = songViewModel::editComment,
                        onCommentDone = songViewModel::commentDone,
                        onCancelComment = songViewModel::cancelComment,
                        onEditPartChord = songViewModel::editPartChord,
                        onSelectPartVoicing = songViewModel::selectPartVoicing,
                        onBackToPart = songViewModel::backToPart,
                        onBackToEditor = songViewModel::backToEditor,
                        onBackToReview = songViewModel::backToReview,
                        onReviewDone = songViewModel::reviewDone,
                        onNamePart = songViewModel::namePart,
                        onNewSong = { songViewModel.startNewSong(settings.capo) },
                        onOpenSong = songViewModel::openSong,
                        onDeleteSong = songViewModel::deleteSong,
                        onBackToLibrary = songViewModel::backToLibrary,
                        // Releases the microphone on the way out, whatever page we leave from.
                        onClose = { songViewModel.discardCapture(); songsOpen = false },
                    )
                    if (songCapoOpen) {
                        // Shows the song's capo, not the live one — and moving it moves both,
                        // because there is only one capo and it is on the guitar.
                        CapoSheet(
                            settings = settings.copy(capo = song.capo),
                            onCapoChange = { fret ->
                                songViewModel.setCapo(fret)
                                viewModel.setCapo(fret)
                            },
                            onDismiss = { songCapoOpen = false },
                        )
                    }
                    return@CrystalBallTheme
                }

                // Settings is a full screen shown over everything else, with a back arrow and its
                // own back handler.
                if (settingsOpen) {
                    SettingsScreen(
                        settings = settings,
                        onKeepScreenOnChange = viewModel::setKeepScreenOn,
                        onThemeModeChange = viewModel::setThemeMode,
                        onNameStyleChange = viewModel::setNameStyle,
                        onShowCapoOnStartChange = viewModel::setShowCapoOnStart,
                        onClose = { settingsOpen = false },
                    )
                    return@CrystalBallTheme
                }

                // Listening is home. A chord's detail page is somewhere you came *to*, so the back
                // gesture — a swipe in from the screen edge — returns there rather than dropping
                // out of the app. From home itself, back keeps its usual meaning and exits.
                //
                // Stood down while the drawer is open, so back closes the drawer first (its own
                // handler) instead of teleporting the page out from underneath it.
                BackHandler(enabled = state !is DetectState.Idle && !drawerState.isOpen) {
                    viewModel.cancel()
                }

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    // Only while it is open, so swiping it shut still works but a closed drawer
                    // does not swallow the left-edge swipe that means "back to listening".
                    gesturesEnabled = drawerState.isOpen,
                    drawerContent = {
                        CrystalDrawer(
                            onDetect = { closeThen { viewModel.showDetect() } },
                            onSongs = { closeThen { songViewModel.open(); songsOpen = true } },
                            onShowChords = { closeThen { viewModel.showChords() } },
                            onSettings = { closeThen { settingsOpen = true } },
                            onQuickHelp = { closeThen { helpOpen = true } },
                        )
                    },
                ) {
                    Surface {
                        DetectScreen(
                            state = state,
                            settings = settings,
                            onDetect = onDetect,
                            onCancel = viewModel::cancel,
                            onSelect = viewModel::select,
                            onSelectVoicing = viewModel::selectVoicing,
                            onSetCapo = { capoOpen = true },
                            onOpenMenu = { scope.launch { drawerState.open() } },
                            onOpenAppSettings = { settingsOpen = true },
                            modifier = Modifier.padding(
                                WindowInsets.systemBars.asPaddingValues(),
                            ),
                        )
                        if (capoOpen) {
                            CapoSheet(
                                settings = settings,
                                onCapoChange = viewModel::setCapo,
                                onDismiss = { capoOpen = false },
                            )
                        }
                        if (helpOpen) {
                            QuickHelpSheet(onDismiss = { helpOpen = false })
                        }
                    }
                }
            }
        }
    }
}

/**
 * A file name the picker can offer and a filesystem will accept.
 *
 * Titles are free text and end up as a real file name, so anything a path might choke on comes out
 * — rather than trusting every filesystem this could be saved to.
 */
private fun pdfFileName(title: String): String {
    val safe = title.replace(Regex("[^A-Za-z0-9 ()_-]"), "").trim().ifEmpty { "song" }
    return "$safe.pdf"
}
