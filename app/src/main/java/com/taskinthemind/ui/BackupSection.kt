package com.taskinthemind.ui

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.taskinthemind.backup.BackupManager
import com.taskinthemind.backup.DriveAuth
import com.taskinthemind.ui.theme.LocalStatusColors
import kotlinx.coroutines.launch

/** Settings card for Google Drive backup: sign in, back up now, restore, delete. */
@Composable
fun BackupSection() {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val status = LocalStatusColors.current
    val state by BackupManager.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var showNotice by rememberSaveable { mutableStateOf(false) }
    var offer by remember { mutableStateOf<BackupManager.RemoteInfo?>(null) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    fun afterSignIn(token: String) {
        scope.launch {
            try {
                val remote = BackupManager.completeSignIn(token)
                if (remote != null && remote.taskCount > 0) {
                    offer = remote // a phone that is new or reset: offer to bring everything back
                } else {
                    BackupManager.backupNow()
                    toast("You're in. First backup done ✨")
                }
            } catch (e: Throwable) {
                toast(BackupManager.friendly(e))
            }
        }
    }

    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        val token = if (res.resultCode == Activity.RESULT_OK) {
            runCatching { Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(res.data).accessToken }.getOrNull()
        } else null
        if (token != null) afterSignIn(token) else toast("Sign-in cancelled")
    }

    fun signIn() {
        scope.launch {
            try {
                val result = DriveAuth.authorize(context)
                val pending = result.pendingIntent
                if (result.hasResolution() && pending != null) {
                    consent.launch(IntentSenderRequest.Builder(pending.intentSender).build())
                } else {
                    result.accessToken?.let(::afterSignIn) ?: toast("Sign-in failed")
                }
            } catch (e: ApiException) {
                toast(if (e.statusCode == 10) "Google sign-in isn't set up for this build (code 10)." else "Sign-in failed (code ${e.statusCode}).")
            } catch (e: Throwable) {
                toast(e.message ?: "Sign-in failed")
            }
        }
    }

    WarmCard {
        if (state.email == null) {
            // Title beside the icon, description as its own full-width paragraph,
            // so the lines keep one even rhythm instead of wrapping in a narrow column.
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBubble(Icons.Outlined.CloudUpload, size = 44.dp, iconSize = 22.dp)
                Spacer(Modifier.width(14.dp))
                Text("Google Drive backup", style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Your tasks, settings and alarm tone, saved to a hidden spot in your Drive. New phone? Restore in a tap.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))
            PrimaryButton("Sign in with Google", null, onClick = { showNotice = true }, modifier = Modifier.fillMaxWidth())
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBubble(Icons.Outlined.CloudDone, size = 44.dp, iconSize = 22.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(state.email!!, style = MaterialTheme.typography.titleSmall, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val line = state.busy
                        ?: if (state.lastBackupAt > 0) "Last backup · ${Fmt.day(state.lastBackupAt)}, ${Fmt.time(state.lastBackupAt)}" else "No backup yet"
                    Text(line, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
                if (state.busy != null) {
                    CircularProgressIndicator(strokeWidth = 2.dp, color = scheme.primary, modifier = Modifier.size(18.dp))
                } else Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "More", tint = scheme.onSurfaceVariant)
                    }
                    DropdownMenu(
                        expanded = menu,
                        onDismissRequest = { menu = false },
                        shape = RoundedCornerShape(14.dp),
                        containerColor = scheme.surface,
                        border = BorderStroke(1.dp, scheme.outline)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Delete backup", color = status.danger) },
                            leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null, tint = status.danger) },
                            onClick = { menu = false; confirmDelete = true }
                        )
                        DropdownMenuItem(
                            text = { Text("Sign out") },
                            leadingIcon = { Icon(Icons.Outlined.Logout, null) },
                            onClick = { menu = false; BackupManager.signOut(); toast("Signed out. Your backup stays safe in Drive.") }
                        )
                    }
                }
            }
            state.error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = status.danger)
            }
            Spacer(Modifier.height(12.dp))
            HairLine()
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                CardAction(Icons.Outlined.CloudUpload, "Back up now", Modifier.weight(1f), enabled = state.busy == null) {
                    scope.launch {
                        runCatching { BackupManager.backupNow() }
                            .onSuccess { toast("Backed up ✨") }
                            .onFailure { toast(BackupManager.friendly(it)) }
                    }
                }
                CardAction(Icons.Outlined.CloudDownload, "Restore", Modifier.weight(1f), enabled = state.busy == null) {
                    scope.launch {
                        runCatching { BackupManager.remoteInfo() }
                            .onSuccess { if (it == null) toast("No backup in Drive yet") else offer = it }
                            .onFailure { toast(BackupManager.friendly(it)) }
                    }
                }
            }
            Text(
                "Auto-backup is on: every change syncs a few seconds later.",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
            )
        }
    }

    if (showNotice) {
        WarmDialog(
            icon = Icons.Outlined.CloudUpload,
            accent = scheme.primary,
            title = "Quick vibe check",
            confirmLabel = "Bet, sign me in",
            onConfirm = { showNotice = false; signIn() },
            dismissLabel = "Nah, later",
            onDismiss = { showNotice = false }
        ) {
            // Same icon-and-text rows as the rest of the app, just with more personality.
            WarmCard(padding = 16.dp) {
                Feature(Icons.Outlined.Visibility, "Google might say “unverified app”", "That's just us being indie, not sus. Tap Advanced, then Go to Task in the Mind.")
                NoticeGap()
                Feature(Icons.Outlined.Lock, "We get one tiny secret locker", "Can't see your pics, docs, or that folder called “misc final FINAL (2)”.")
                NoticeGap()
                Feature(Icons.Outlined.VisibilityOff, "Your backup stays hidden", "Zero clutter in My Drive. Curious? Drive, Settings, Manage apps.")
                NoticeGap()
                Feature(Icons.Outlined.Wifi, "Internet is only for backup", "Alarms still ring offline, no cap.")
            }
        }
    }

    offer?.let { info ->
        WarmDialog(
            icon = Icons.Outlined.Restore,
            accent = scheme.primary,
            title = "Found your backup",
            confirmLabel = "Restore",
            onConfirm = {
                offer = null
                scope.launch {
                    runCatching { BackupManager.restore() }
                        .onSuccess { toast("Restored $it ${if (it == 1) "task" else "tasks"}. Welcome back ✨") }
                        .onFailure { toast(BackupManager.friendly(it)) }
                }
            },
            dismissLabel = "Not now",
            onDismiss = { offer = null }
        ) {
            Text(
                "From ${Fmt.day(info.modified)}, ${Fmt.time(info.modified)} with ${info.taskCount} ${if (info.taskCount == 1) "task" else "tasks"}. " +
                    "It replaces what's on this phone right now, and upcoming alarms are set again.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }

    if (confirmDelete) {
        WarmDialog(
            icon = Icons.Outlined.DeleteOutline,
            accent = status.danger,
            title = "Delete your backup?",
            confirmLabel = "Delete",
            onConfirm = {
                confirmDelete = false
                scope.launch {
                    runCatching { BackupManager.deleteBackup() }
                        .onSuccess { toast("Backup deleted from Drive") }
                        .onFailure { toast(BackupManager.friendly(it)) }
                }
            },
            dismissLabel = "Keep",
            onDismiss = { confirmDelete = false }
        ) {
            Text(
                "Removes it from your Drive. Tasks on this phone stay, and the next change backs them up again unless you sign out.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun CardAction(icon: ImageVector, label: String, modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled, modifier = modifier) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun NoticeGap() {
    Spacer(Modifier.height(12.dp))
    HairLine()
    Spacer(Modifier.height(12.dp))
}

/** The app's standard dialog: icon bubble, serif title, content, then two wide buttons. */
@Composable
fun WarmDialog(
    icon: ImageVector,
    accent: Color,
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    dismissLabel: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .background(scheme.background, RoundedCornerShape(24.dp))
                .border(1.dp, scheme.outline, RoundedCornerShape(24.dp))
                .padding(24.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(60.dp).background(accent.copy(alpha = 0.12f), CircleShape)) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall, color = scheme.onBackground, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            content()
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, scheme.outline),
                    contentPadding = PaddingValues(vertical = 14.dp),
                    modifier = Modifier.weight(1f)
                ) { Text(dismissLabel, color = scheme.onSurface, style = MaterialTheme.typography.labelLarge) }
                Button(
                    onClick = onConfirm,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent,
                        contentColor = if (accent == scheme.primary) scheme.onPrimary else Color.White
                    ),
                    contentPadding = PaddingValues(vertical = 14.dp),
                    modifier = Modifier.weight(1f)
                ) { Text(confirmLabel, style = MaterialTheme.typography.labelLarge, maxLines = 1) }
            }
        }
    }
}
