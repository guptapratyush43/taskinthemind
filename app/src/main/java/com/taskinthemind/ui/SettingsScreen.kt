package com.taskinthemind.ui

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.taskinthemind.data.AppSettings
import com.taskinthemind.ui.theme.LocalStatusColors

/** [alwaysShow] keeps a row visible once granted, with a Manage button instead of Allow. */
private class Perm(
    val icon: ImageVector, val title: String, val body: String, val granted: Boolean,
    val alwaysShow: Boolean = false, val grantedBody: String = body, val fix: () -> Unit
)

object AlarmPermissions {
    fun notifications(c: Context) = NotificationManagerCompat.from(c).areNotificationsEnabled()
    fun fullScreen(c: Context) =
        Build.VERSION.SDK_INT < 34 || c.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
    fun exact(c: Context) =
        Build.VERSION.SDK_INT < 31 || c.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    fun battery(c: Context) = c.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(c.packageName)

    /**
     * Opens this app's own battery page, the same one as App info > Battery.
     * Xiaomi/HyperOS keeps it in its power keeper; stock Android has a detail
     * page per app. Each is tried in turn, ending at plain App info.
     */
    fun openBatterySettings(c: Context) {
        val pkg = Uri.parse("package:${c.packageName}")
        val attempts = listOf(
            Intent().setClassName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity")
                .putExtra("package_name", c.packageName)
                .putExtra("package_label", c.applicationInfo.loadLabel(c.packageManager).toString()),
            Intent("android.settings.VIEW_ADVANCED_POWER_USAGE_DETAIL", pkg),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
        )
        for (intent in attempts) {
            try {
                c.startActivity(intent)
                return
            } catch (_: Exception) {
                // Not on this phone, or not ours to open: try the next one.
            }
        }
    }

    /** Xiaomi-family phones hide a separate "Autostart" switch that blocks boot/update wake-ups. */
    fun hasAutostart() = Build.MANUFACTURER.lowercase() in setOf("xiaomi", "redmi", "poco")

    fun openAutostart(c: Context) {
        val attempts = listOf(
            Intent().setClassName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${c.packageName}"))
        )
        for (intent in attempts) {
            try { c.startActivity(intent); return } catch (_: Exception) { }
        }
    }

    fun missingCritical(c: Context) = !notifications(c) || !fullScreen(c) || !exact(c)
}

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val settings by AppSettings.state.collectAsStateWithLifecycle()
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumes++ }

    var previewing by remember { mutableStateOf<MediaPlayer?>(null) }
    fun stopPreview() { previewing?.run { runCatching { stop() }; release() }; previewing = null }
    DisposableEffect(Unit) { onDispose { stopPreview() } }

    val deviceTones = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.let { IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java) }
            ?: return@rememberLauncherForActivityResult
        stopPreview()
        val name = if (uri == Settings.System.DEFAULT_ALARM_ALERT_URI) "Default alarm"
        else RingtoneManager.getRingtone(context, uri)?.getTitle(context) ?: "Device tone"
        AppSettings.setTone(uri.toString(), name, fromFile = false)
    }
    val fileTone = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        stopPreview()
        AppSettings.setTone(uri.toString(), displayName(context, uri), fromFile = true)
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("Settings", onBack)
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            SectionLabel("Backup")
            BackupSection()

            Spacer(Modifier.height(24.dp))
            SectionLabel("Alarm sound")
            WarmCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBubble(Icons.Outlined.MusicNote, size = 44.dp, iconSize = 22.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("CURRENT TONE", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                        Text(settings.toneName, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = {
                        if (previewing != null) stopPreview() else previewing = playPreview(context, settings.toneUri)
                    }) {
                        Icon(
                            if (previewing != null) Icons.Outlined.Stop else Icons.Outlined.PlayArrow,
                            contentDescription = "Preview",
                            tint = scheme.primary
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                HairLine()
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.padding(top = 4.dp)) {
                    SourceButton(Icons.Outlined.LibraryMusic, "Device tones", Modifier.weight(1f)) {
                        deviceTones.launch(Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Alarm tone")
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, Settings.System.DEFAULT_ALARM_ALERT_URI)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, settings.toneUri?.let(Uri::parse))
                        })
                    }
                    SourceButton(Icons.Outlined.FolderOpen, "From files", Modifier.weight(1f)) {
                        fileTone.launch(arrayOf("audio/*"))
                    }
                }
                Spacer(Modifier.height(6.dp))
                HairLine()
                Spacer(Modifier.height(14.dp))
                // Vibrate lives with the sound it accompanies; its icon sits in the same
                // 44dp column as the tone bubble so both rows' text lines up.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.width(44.dp)) {
                        Icon(Icons.Outlined.Vibration, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Vibrate", style = MaterialTheme.typography.titleSmall, color = scheme.onSurface)
                        RowBody("Pulse along with the tone")
                    }
                    Switch(
                        checked = settings.vibrate,
                        onCheckedChange = AppSettings::setVibrate,
                        colors = SwitchDefaults.colors(checkedTrackColor = scheme.primary, uncheckedBorderColor = scheme.outline)
                    )
                }
            }

            // Re-read on every resume, since the user fixes these in system settings.
            val pkg = Uri.parse("package:${context.packageName}")
            val perms = remember(resumes) {
                buildList {
                    add(Perm(Icons.Outlined.Notifications, "Notifications", "Needed for the alarm pop-up", AlarmPermissions.notifications(context)) {
                        context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                    })
                    if (Build.VERSION.SDK_INT >= 34) add(Perm(Icons.Outlined.OpenInFull, "Full-screen alarms", "Shows the alarm over the lock screen", AlarmPermissions.fullScreen(context)) {
                        context.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg))
                    })
                    if (Build.VERSION.SDK_INT >= 31) add(Perm(Icons.Outlined.Schedule, "Exact timing", "Rings on the minute, not roughly", AlarmPermissions.exact(context)) {
                        context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg))
                    })
                    if (AlarmPermissions.hasAutostart()) add(Perm(Icons.Outlined.RestartAlt, "Autostart", "Lets alarms come back after a restart or update", granted = true,
                        alwaysShow = true, grantedBody = "Turn on so alarms come back after a restart or update") {
                        AlarmPermissions.openAutostart(context)
                    })
                    add(Perm(BatteryBolt, "Unrestricted battery", "Optional; helps on aggressive phones", AlarmPermissions.battery(context),
                        alwaysShow = true, grantedBody = "On, so the phone never delays your alarms") {
                        AlarmPermissions.openBatterySettings(context)
                    })
                }
            }
            // Missing permissions get a card; battery stays so it can be changed later.
            val shown = perms.filter { !it.granted || it.alwaysShow }
            if (shown.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                SectionLabel("Make sure it rings")
                WarmCard(padding = 14.dp) {
                    shown.forEachIndexed { i, p ->
                        if (i > 0) HairLine(Modifier.padding(vertical = 10.dp))
                        PermissionRow(p.icon, p.title, if (p.granted) p.grantedBody else p.body, granted = p.granted, onFix = p.fix)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            SectionLabel("About")
            AboutCard()

            Spacer(Modifier.height(24.dp))
            Footnote("Alarms play on the alarm volume, so they still ring when media is muted.")
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SourceButton(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = modifier) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun PermissionRow(icon: ImageVector, title: String, body: String, granted: Boolean, onFix: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = scheme.onSurface)
            RowBody(body)
        }
        TextButton(onClick = onFix) {
            Text(if (granted) "Manage" else "Allow", color = scheme.primary, style = MaterialTheme.typography.labelLarge)
        }
    }
}

private fun playPreview(context: Context, uri: String?): MediaPlayer? = runCatching {
    val target = uri?.let(Uri::parse) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
    MediaPlayer().apply {
        setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
        setDataSource(context, target)
        isLooping = true
        prepare()
        start()
    }
}.getOrNull()

private fun displayName(context: Context, uri: Uri): String =
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
        if (it.moveToFirst()) it.getString(0)?.substringBeforeLast('.') else null
    } ?: "Custom tone"
