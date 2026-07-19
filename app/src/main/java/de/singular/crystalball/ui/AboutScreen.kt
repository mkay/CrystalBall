package de.singular.crystalball.ui

import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import de.singular.crystalball.BuildConfig

private const val REPO_URL = "https://github.com/mkay/CrystalBall"
private const val ISSUES_URL = "$REPO_URL/issues"
private const val KOFI_URL = "https://ko-fi.com/s1ngular"

/**
 * About: what the app is, where it lives, and how to report a bug or chip in. Reached from
 * [SettingsScreen] rather than the side panel — the panel's slots belong to the things you reach
 * for with a guitar in your hands; this is the page you visit once out of curiosity and once when
 * filing an issue.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AboutScreen(onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val uriHandler = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    // versionCode rides along in the copied string: it's what pins a bug report to an exact build
    // when a version was re-released, where the name alone can lie. Read from BuildConfig so it
    // cannot drift from the gradle constants.
    val version = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("About this app") },
            )
        },
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(16.dp))
            Logo()
            Spacer(Modifier.height(12.dp))
            Text("Crystal Ball", style = MaterialTheme.typography.headlineSmall)
            // Long-press to copy, mirroring the song library's press-and-hold idiom. Android 13 and
            // up pops its own clipboard confirmation, so only older versions get a toast.
            Text(
                "v$version",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(ControlShape)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            clipboard.setText(AnnotatedString("Crystal Ball $version"))
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                                Toast
                                    .makeText(context, "Version copied", Toast.LENGTH_SHORT)
                                    .show()
                            }
                        },
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )

            Spacer(Modifier.height(28.dp))
            AboutSection("About") {
                AboutBody(
                    "I built Crystal Ball primarily for myself — maybe you'll find it just as " +
                        "useful as I do.",
                )
            }
            AboutSection("Website") {
                AboutBody("The app lives here:")
                AboutLink(REPO_URL) { uriHandler.openUri(REPO_URL) }
            }
            AboutSection("Bugs") {
                AboutBody("Found a hiccup? Let me know:")
                AboutLink(ISSUES_URL) { uriHandler.openUri(ISSUES_URL) }
            }
            AboutSection("Support") {
                AboutBody("If you can, support its development:")
                AboutLink(KOFI_URL) { uriHandler.openUri(KOFI_URL) }
            }
            AboutSection("License") {
                AboutBody(
                    "Crystal Ball is free software under the AGPL-3.0. It's built with AndroidX " +
                        "and Jetpack Compose, licensed under Apache 2.0.",
                )
            }
        }
    }
}

@Composable
private fun AboutSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(4.dp))
        content()
    }
}

@Composable
private fun AboutBody(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium)
}

/** A tappable URL. Kept full-length rather than hidden behind link text so it stays readable. */
@Composable
private fun AboutLink(url: String, onClick: () -> Unit) {
    Text(
        url,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clip(ControlShape)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
    )
}
