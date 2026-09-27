package com.taskinthemind.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.taskinthemind.ui.theme.LocalStatusColors
import com.taskinthemind.update.UpdateManager
import com.taskinthemind.update.UpdateManager.Download
import kotlinx.coroutines.launch

/** Settings row: current version and a manual "Check for updates". */
@Composable
fun AboutCard() {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    WarmCard(padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.SystemUpdate, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Version ${UpdateManager.currentVersion}", style = MaterialTheme.typography.titleSmall, color = scheme.onSurface)
                Text(
                    if (checking) "Checking…" else "New versions show up here automatically",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            }
            TextButton(enabled = !checking, onClick = {
                scope.launch {
                    checking = true
                    runCatching { UpdateManager.checkNow() }
                        .onSuccess { if (it == null) toast("You're on the latest version") }
                        .onFailure { toast("Couldn't reach GitHub. Try again in a bit.") }
                    checking = false
                }
            }) { Text("Check", style = MaterialTheme.typography.labelLarge, color = scheme.primary) }
        }
    }
}

/**
 * "A new version is here" pop-up. Ignore skips this version for good;
 * tapping outside only closes it until the next daily check.
 */
@Composable
fun UpdateDialog(release: UpdateManager.Release) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val download by UpdateManager.download.collectAsStateWithLifecycle()
    val running = download is Download.Running

    WarmDialog(
        icon = Icons.Outlined.SystemUpdate,
        accent = scheme.primary,
        title = "Version ${release.version} is here",
        confirmLabel = when (download) {
            is Download.Running -> "Downloading…"
            is Download.Ready -> "Install"
            is Download.Failed -> "Try again"
            Download.Idle -> "Update"
        },
        onConfirm = {
            when (val d = download) {
                is Download.Running -> Unit
                is Download.Ready -> UpdateManager.install(context, d.file)
                else -> scope.launch { UpdateManager.startDownload(release) }
            }
        },
        dismissLabel = "Ignore",
        onDismiss = { UpdateManager.ignore(release) },
        onDismissRequest = { if (!running) UpdateManager.close() }
    ) {
        Text(
            "You have ${UpdateManager.currentVersion}. Your tasks and settings stay as they are.",
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (release.notes.isNotBlank()) {
            Spacer(Modifier.height(14.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp)
                    .background(scheme.surface, RoundedCornerShape(14.dp))
                    .border(1.dp, scheme.outline, RoundedCornerShape(14.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(14.dp)
            ) {
                Text(release.notes, style = MaterialTheme.typography.bodySmall, color = scheme.onSurface)
            }
        }
        when (val d = download) {
            is Download.Running -> {
                Spacer(Modifier.height(16.dp))
                val bar = Modifier.fillMaxWidth().height(6.dp)
                if (d.progress == null) {
                    LinearProgressIndicator(bar, color = scheme.primary, trackColor = scheme.surfaceVariant, strokeCap = StrokeCap.Round)
                } else {
                    LinearProgressIndicator({ d.progress }, bar, color = scheme.primary, trackColor = scheme.surfaceVariant, strokeCap = StrokeCap.Round)
                }
            }
            is Download.Ready -> {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Downloaded. Tap Install, then Install again on Android's screen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            is Download.Failed -> {
                Spacer(Modifier.height(12.dp))
                Text(d.message, style = MaterialTheme.typography.bodySmall, color = LocalStatusColors.current.danger, textAlign = TextAlign.Center)
            }
            Download.Idle -> Unit
        }
    }
}
