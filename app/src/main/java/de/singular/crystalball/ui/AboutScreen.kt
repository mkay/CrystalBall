// SPDX-License-Identifier: GPL-3.0-only

package de.singular.crystalball.ui

import android.os.Build
import android.widget.Toast
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import de.singular.crystalball.BuildConfig

// Not private: [SupportDialog] points at the same two places, and one pair of constants is the
// only way the panel's dialog and this page can't drift apart.
internal const val REPO_URL = "https://github.com/mkay/CrystalBall"
private const val ISSUES_URL = "$REPO_URL/issues"
internal const val KOFI_URL = "https://ko-fi.com/s1ngular"

/**
 * About: what the app is, where it lives, and how to report a bug or chip in. A tab inside
 * [SettingsScreen] rather than an item in the side panel — the panel's slots belong to the things
 * you reach for with a guitar in your hands; this is the page you visit once out of curiosity and
 * once when filing an issue.
 *
 * It was a screen of its own, stacked on top of Settings, until the settings grew tabs and it became
 * a destination buried inside one of them; see [SettingsTab] for why a tab is where it ended up.
 * Being a tab, it brings no bar and no back handler of its own: the settings screen around it owns
 * both, and closing them closes this too.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AboutScreen(modifier: Modifier = Modifier) {
    val clipboard = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val toolbarColor = MaterialTheme.colorScheme.surface
    // versionCode rides along in the copied string: it's what pins a bug report to an exact build
    // when a version was re-released, where the name alone can lie. Read from BuildConfig so it
    // cannot drift from the gradle constants.
    val version = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

    Column(
        modifier
            .fillMaxSize()
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
            AboutLink(REPO_URL) { openCustomTab(context, REPO_URL, toolbarColor) }
        }
        AboutSection("Bugs") {
            AboutBody("Found a hiccup? Let me know:")
            AboutLink(ISSUES_URL) { openCustomTab(context, ISSUES_URL, toolbarColor) }
        }
        AboutSection("Support") {
            AboutBody("If you can, support its development:")
            AboutLink(KOFI_URL) { openCustomTab(context, KOFI_URL, toolbarColor) }
        }
        // GPL §5 requires a derivative to preserve legal notices, so a licence stated *in the
        // app* rather than only in the repo is worth more than it looks: a clone that stripped
        // this screen has done so deliberately, and the before-and-after is a screenshot.
        AboutSection("License") {
            AboutBody("Copyright © 2026 Kreuder")
            AboutBody(
                "Crystal Ball is free software under the GPL-3.0-only. The wordmark and the " +
                    "icon are CC BY 4.0; the name is not licensed, so a fork needs its own. " +
                    "Built with AndroidX and Jetpack Compose, licensed under Apache 2.0.",
            )
            AboutLink(REPO_URL) { openCustomTab(context, REPO_URL, toolbarColor) }
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
