package de.singular.crystalball

import android.Manifest
import android.content.Intent
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
                // Collected out here, not just inside the songs screen: the detect screen's
                // "Save to an existing song" reads it too.
                val library by songViewModel.library.collectAsStateWithLifecycle()

                // Detection is the only thing the app does, so the permission is requested on the
                // first press rather than at launch — by then it is obvious what it is for. Granting
                // it starts the pass immediately, so one press is enough.
                // Which of the two microphone actions a pending grant belongs to: detecting one
                // chord, or capturing a run of them. Both live on [DetectViewModel] now.
                var pendingCapture by rememberSaveable { mutableStateOf(false) }
                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { granted ->
                    if (granted) {
                        if (pendingCapture) viewModel.startCaptureOnPermissionGranted()
                        else viewModel.detectOnPermissionGranted()
                    }
                    pendingCapture = false
                }

                // The song this capture will be offered to first, set when the run is started from an
                // open song's editor. Null for a capture begun from the home screen. Saveable, so a
                // permission dialog or a rotation cannot lose which song we were adding to.
                var captureTarget by rememberSaveable { mutableStateOf<String?>(null) }

                val onDetect = {
                    if (viewModel.hasMicPermission) viewModel.detect()
                    else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }

                val onCapture = {
                    if (viewModel.hasMicPermission) viewModel.startCapture()
                    else {
                        pendingCapture = true
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }

                // Opens itself at launch unless turned off — a capo moved since yesterday is then
                // the first thing they set. Saveable, so a rotation does not bring the
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

                // Backup writes a zip wherever the user points; restore reads one back over every
                // song. Both are reached from Settings, but they live out here: that screen returns
                // early, so a launcher or dialog composed inside it would leave the composition the
                // moment it closed — and the picker closes it on the way out.
                val backupResult by songViewModel.backupResult.collectAsStateWithLifecycle()
                val backupLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.CreateDocument("application/zip"),
                ) { uri -> if (uri != null) songViewModel.backupSongs(uri) }
                val restoreLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocument(),
                ) { uri -> if (uri != null) songViewModel.restoreSongs(uri) }
                var confirmRestore by rememberSaveable { mutableStateOf(false) }

                val backupName = remember {
                    val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                    "crystalball-backup-$day.zip"
                }

                LaunchedEffect(backupResult) {
                    val message = when (val result = backupResult) {
                        null -> return@LaunchedEffect
                        is BackupResult.Exported -> "Songs backed up"
                        is BackupResult.Restored ->
                            if (result.songs == 1) "1 song restored"
                            else "${result.songs} songs restored"
                        // The repository says why in words worth repeating — which file it was not,
                        // which version wrote it — so say that rather than "something went wrong".
                        is BackupResult.Failed -> result.reason
                    }
                    Toast.makeText(view.context, message, Toast.LENGTH_LONG).show()
                    songViewModel.consumeBackupResult()
                }

                // A capture saved from the detect screen lands here: say what happened, and on
                // success step into the songs screen, which [saveCapture] has already opened at the
                // saved song's editor — so a save is something you see, not just a toast.
                val saveResult by songViewModel.saveResult.collectAsStateWithLifecycle()
                LaunchedEffect(saveResult) {
                    when (val result = saveResult) {
                        null -> return@LaunchedEffect
                        is SaveResult.Saved -> {
                            Toast.makeText(
                                view.context,
                                "Saved “${result.partName}” to ${result.songTitle}",
                                Toast.LENGTH_LONG,
                            ).show()
                            captureTarget = null
                            viewModel.showDetect()
                            songsOpen = true
                        }
                        is SaveResult.Failed ->
                            Toast.makeText(view.context, result.reason, Toast.LENGTH_LONG).show()
                    }
                    songViewModel.consumeSaveResult()
                }

                if (confirmRestore) {
                    AlertDialog(
                        onDismissRequest = { confirmRestore = false },
                        title = { Text("Restore songs?") },
                        text = {
                            Text(
                                "This replaces every song in your library with the ones in the " +
                                    "backup. This can't be undone.",
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                confirmRestore = false
                                // Some file pickers label a zip octet-stream, and a backup the
                                // picker greys out is a backup you cannot restore.
                                restoreLauncher.launch(
                                    arrayOf(
                                        "application/zip",
                                        "application/octet-stream",
                                        "application/x-zip-compressed",
                                    ),
                                )
                            }) { Text("Choose backup") }
                        },
                        dismissButton = {
                            TextButton(onClick = { confirmRestore = false }) { Text("Cancel") }
                        },
                    )
                }

                // Songs is a full screen too, and owns the microphone while it is up — which is why
                // it and the detect screen must never be on top of each other. See [SongViewModel].
                if (songsOpen) {
                    val song by songViewModel.song.collectAsStateWithLifecycle()
                    val songState by songViewModel.state.collectAsStateWithLifecycle()
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

                    // The system's own share sheet, once the sheet exists as a file to offer.
                    // Writing it is the view-model's job and showing it around is the activity's,
                    // so the uri arrives as state rather than the view-model reaching for an Intent.
                    val shareUri by songViewModel.shareUri.collectAsStateWithLifecycle()
                    LaunchedEffect(shareUri) {
                        val uri = shareUri ?: return@LaunchedEffect
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "application/pdf"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            putExtra(Intent.EXTRA_TITLE, song.title)
                            // The grant travels with the Intent: whoever the chooser lands on may
                            // read this one file, and only while they hold it.
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        startActivity(Intent.createChooser(send, null))
                        songViewModel.consumeShareUri()
                    }

                    SongScreen(
                        song = song,
                        state = songState,
                        library = library,
                        error = songError,
                        settings = settings,
                        onRenameCurrentSong = songViewModel::renameCurrentSong,
                        // Adding a part is a capture, and capture lives on the detect screen — so
                        // leave songs, remember which song this run is for, and start listening.
                        onAddPart = {
                            captureTarget = song.id
                            songsOpen = false
                            onCapture()
                        },
                        onSetCapo = { songCapoOpen = true },
                        onRemovePart = songViewModel::removePart,
                        onDuplicatePart = songViewModel::duplicatePart,
                        onRenamePart = songViewModel::renamePart,
                        onMovePart = songViewModel::movePart,
                        onOpenPart = songViewModel::openPart,
                        onViewSong = songViewModel::viewSong,
                        onExportPdf = { pdfLauncher.launch(pdfFileName(song.title)) },
                        onSharePdf = {
                            songViewModel.sharePdf(settings.nameStyle, pdfFileName(song.title))
                        },
                        onEditComment = songViewModel::editComment,
                        onCommentDone = songViewModel::commentDone,
                        onCancelComment = songViewModel::cancelComment,
                        onEditPartChord = songViewModel::editPartChord,
                        onCorrectPartChord = songViewModel::correctPartChord,
                        onSelectPartVoicing = songViewModel::selectPartVoicing,
                        onBackToPart = songViewModel::backToPart,
                        onBackToEditor = songViewModel::backToEditor,
                        onOpenSong = songViewModel::openSong,
                        onDeleteSongs = songViewModel::deleteSongs,
                        onRenameSong = songViewModel::renameSong,
                        onBackToLibrary = songViewModel::backToLibrary,
                        onClose = { songsOpen = false },
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
                            onShowOnStartChange = viewModel::setShowCapoOnStart,
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
                        onBackupSongs = { backupLauncher.launch(backupName) },
                        onRestoreSongs = { confirmRestore = true },
                        onClose = { settingsOpen = false },
                    )
                    return@CrystalBallTheme
                }

                // Listening is home. A chord's detail page is somewhere you came *to*, so the back
                // gesture — a swipe in from the screen edge — returns there rather than dropping
                // out of the app. From home itself, back keeps its usual meaning and exits.
                //
                // Editing one captured chord steps back to the run; everything else backs out to
                // home, which for a captured run means dropping it. Stood down while the drawer is
                // open, so back closes the drawer first (its own handler) instead of teleporting the
                // page out from underneath it.
                BackHandler(enabled = state !is DetectState.Idle && !drawerState.isOpen) {
                    when (state) {
                        is DetectState.EditCaptured -> viewModel.backToCaptureReview()
                        else -> {
                            viewModel.cancel()
                            captureTarget = null
                        }
                    }
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
                            library = library,
                            captureTargetId = captureTarget,
                            onDetect = onDetect,
                            onCapture = { captureTarget = null; onCapture() },
                            onCancel = viewModel::cancel,
                            onSelect = viewModel::select,
                            onSelectVoicing = viewModel::selectVoicing,
                            onStopCapture = viewModel::stopCapture,
                            onDiscardCapture = { viewModel.showDetect(); captureTarget = null },
                            onEditCaptured = viewModel::editCaptured,
                            onSelectCapturedChord = viewModel::selectCapturedChord,
                            onSelectCapturedVoicing = viewModel::selectCapturedVoicing,
                            onRemoveCaptured = viewModel::removeCaptured,
                            onBackToCaptureReview = viewModel::backToCaptureReview,
                            onSaveCapture = { captured, partName, target ->
                                songViewModel.saveCapture(captured, partName, target)
                            },
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
                                onShowOnStartChange = viewModel::setShowCapoOnStart,
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
