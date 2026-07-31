// SPDX-License-Identifier: GPL-3.0-only

package de.singular.crystalball.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.singular.crystalball.R

/**
 * The "easy way" out of [SupportDialog]: a page carrying a like button the visitor presses. The
 * count deliberately does not come from the visit itself — a Do-Not-Track browser suppresses a
 * plain pixel, so a silent hit counter would have quietly under-counted exactly the privacy-minded
 * audience this app has. A button press is an explicit act the browser has no reason to withhold,
 * and it is also the honest thing to ask for: nothing is recorded unless the visitor means it.
 *
 * The query string names the app, so one page serves all of them.
 */
private const val LIKE_URL = "https://singular.de/apps/feedback/?crystalball"

/**
 * The three ways to signal that the app is worth keeping up, in ascending order of effort.
 *
 * Every one of them is a link out, which is the point: an app that says it doesn't track anything
 * shouldn't then open a socket to say so. The browser does the fetching in its own process, so the
 * manifest stays free of INTERNET and the no-tracking claim stays checkable from the manifest
 * alone. The price is that we never learn what happened over there, so nothing here reports
 * success; each page has to confirm for itself. That suits the easy way in particular, where the
 * act being counted is a button press on the page — see [LIKE_URL].
 */
@Composable
fun SupportDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val toolbarColor = MaterialTheme.colorScheme.surface

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.support_title)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Text(
                    stringResource(R.string.support_no_tracking),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    stringResource(R.string.support_question),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    stringResource(R.string.support_three_ways),
                    style = MaterialTheme.typography.bodyMedium,
                )

                SupportWay(
                    title = R.string.support_easy_title,
                    action = R.string.support_easy_action,
                    icon = Icons.Default.Favorite,
                    // The one that costs nothing, so it gets the filled button.
                    emphasised = true,
                    note = R.string.support_easy_note,
                    onClick = { openCustomTab(context, LIKE_URL, toolbarColor) },
                )
                SupportWay(
                    title = R.string.support_nerdy_title,
                    action = R.string.support_nerdy_action,
                    icon = Icons.Default.Star,
                    note = R.string.support_nerdy_note,
                    onClick = { openCustomTab(context, REPO_URL, toolbarColor) },
                )
                SupportWay(
                    title = R.string.support_generous_title,
                    action = R.string.support_generous_action,
                    icon = Icons.Default.Paid,
                    note = R.string.support_generous_note,
                    onClick = { openCustomTab(context, KOFI_URL, toolbarColor) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}

/** One row of [SupportDialog]: a heading, the button that does the thing, and the small print. */
@Composable
private fun SupportWay(
    @StringRes title: Int,
    @StringRes action: Int,
    icon: ImageVector,
    @StringRes note: Int,
    onClick: () -> Unit,
    emphasised: Boolean = false,
) {
    // The icon and label are the same either way; only the button's weight differs, so the content
    // is built once rather than duplicated into both branches.
    val content: @Composable RowScope.() -> Unit = {
        Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(stringResource(action))
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
        if (emphasised) {
            Button(onClick = onClick, shape = ControlShape, content = content)
        } else {
            OutlinedButton(onClick = onClick, shape = ControlShape, content = content)
        }
        Text(
            stringResource(note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
