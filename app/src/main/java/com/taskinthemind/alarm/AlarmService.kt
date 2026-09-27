package com.taskinthemind.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.taskinthemind.MainActivity
import com.taskinthemind.R
import com.taskinthemind.data.AppSettings
import com.taskinthemind.data.Task
import com.taskinthemind.data.TaskRepository
import com.taskinthemind.ui.Fmt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Rings like a stock alarm clock: loops the chosen tone on the alarm stream
 * and vibrates until the user taps Got it or Reschedule.
 */
class AlarmService : Service() {

    companion object {
        private const val ACTION_RING = "ring"
        private const val ACTION_DISMISS = "dismiss"
        private const val ACTION_SILENCE = "silence"
        private const val CHANNEL = "ringing_alarms"
        private const val NOTIFICATION_ID = 4201

        private val _ringing = MutableStateFlow<Int?>(null)
        val ringingTaskId: StateFlow<Int?> = _ringing

        fun ring(context: Context, id: Int) = ContextCompat.startForegroundService(
            context, Intent(context, AlarmService::class.java).setAction(ACTION_RING).putExtra(EXTRA_TASK_ID, id)
        )

        /** Stops ringing and marks the task done. */
        fun dismiss(context: Context, id: Int) {
            context.startService(dismissIntent(context, id))
        }

        /** Stops ringing but leaves the task untouched (used before rescheduling). */
        fun silence(context: Context) {
            if (_ringing.value == null) return
            context.startService(Intent(context, AlarmService::class.java).setAction(ACTION_SILENCE))
        }

        private fun dismissIntent(context: Context, id: Int) =
            Intent(context, AlarmService::class.java).setAction(ACTION_DISMISS).putExtra(EXTRA_TASK_ID, id)

        fun createChannel(context: Context) {
            val channel = NotificationChannel(CHANNEL, "Ringing alarms", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Pops up while a task alarm is ringing"
                setSound(null, null) // the service plays the tone itself, on loop
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private var player: MediaPlayer? = null
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var focus: AudioFocusRequest? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getIntExtra(EXTRA_TASK_ID, -1) ?: -1
        when (intent?.action) {
            ACTION_RING -> {
                val task = TaskRepository.get(id)
                if (task == null) {
                    // A foreground start must always reach startForeground.
                    goForeground(placeholder())
                    stopAll()
                } else {
                    goForeground(buildNotification(task))
                    startRinging()
                    _ringing.value = id
                }
            }
            ACTION_DISMISS -> {
                TaskActions.setDone(this, id, true)
                stopAll()
            }
            else -> stopAll()
        }
        return START_NOT_STICKY
    }

    private fun goForeground(notification: Notification) {
        val type = when {
            Build.VERSION.SDK_INT >= 34 -> ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED
            Build.VERSION.SDK_INT >= 29 -> ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            else -> 0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    private fun buildNotification(task: Task): Notification {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val fullScreen = PendingIntent.getActivity(
            this, task.id,
            Intent(this, AlarmActivity::class.java).putExtra(EXTRA_TASK_ID, task.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            flags
        )
        val gotIt = PendingIntent.getService(this, task.id, dismissIntent(this, task.id), flags)
        val reschedule = PendingIntent.getActivity(this, task.id, MainActivity.rescheduleIntent(this, task.id), flags)

        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(task.title)
            .setContentText(task.description.ifBlank { "It's ${Fmt.time(task.triggerAt)}" })
            .setStyle(NotificationCompat.BigTextStyle().bigText(task.description.ifBlank { "It's ${Fmt.time(task.triggerAt)}" }))
            .setSubText("Alarm · ${Fmt.time(task.triggerAt)}")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setColor(0xFFC15F3C.toInt())
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(fullScreen)
            .setFullScreenIntent(fullScreen, true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(R.drawable.ic_stat_alarm, "Got it", gotIt)
            .addAction(R.drawable.ic_stat_alarm, "Reschedule", reschedule)
            .build()
    }

    private fun placeholder(): Notification = NotificationCompat.Builder(this, CHANNEL)
        .setSmallIcon(R.drawable.ic_stat_alarm)
        .setContentTitle("Task in the Mind")
        .build()

    private fun startRinging() {
        stopSound()
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TaskMind:ringing")
            .apply { acquire(60 * 60 * 1000L) }

        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attrs).build()
            .also { getSystemService(AudioManager::class.java).requestAudioFocus(it) }

        val chosen = AppSettings.state.value.toneUri?.let(Uri::parse)
        val fallback = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

        if (!playWithMediaPlayer(chosen ?: fallback, attrs)) {
            // Tones on shared storage can need a read permission MediaPlayer lacks;
            // Ringtone falls back to the system player, which has it.
            val tone = RingtoneManager.getRingtone(this, chosen ?: fallback)
            if (tone != null && Build.VERSION.SDK_INT >= 28) {
                tone.audioAttributes = attrs
                tone.isLooping = true
                tone.play()
                ringtone = tone
            } else if (chosen != null) {
                playWithMediaPlayer(fallback, attrs)
            }
        }

        if (AppSettings.state.value.vibrate) {
            vibrator = if (Build.VERSION.SDK_INT >= 31) {
                getSystemService(VibratorManager::class.java).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Vibrator::class.java)
            }
            vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 600), 0), attrs)
        }
    }

    private fun playWithMediaPlayer(uri: Uri?, attrs: AudioAttributes): Boolean {
        if (uri == null) return false
        return runCatching {
            player = MediaPlayer().apply {
                setAudioAttributes(attrs)
                setDataSource(this@AlarmService, uri)
                isLooping = true
                prepare()
                start()
            }
        }.onFailure {
            player?.release()
            player = null
        }.isSuccess
    }

    private fun stopSound() {
        player?.run { runCatching { stop() }; release() }
        player = null
        ringtone?.stop()
        ringtone = null
        vibrator?.cancel()
        vibrator = null
        focus?.let { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }
        focus = null
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    private fun stopAll() {
        stopSound()
        _ringing.value = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopSound()
        _ringing.value = null
        super.onDestroy()
    }
}
