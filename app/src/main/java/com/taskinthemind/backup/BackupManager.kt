package com.taskinthemind.backup

import android.content.Context
import android.content.SharedPreferences
import android.media.RingtoneManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.android.gms.auth.GoogleAuthUtil
import com.taskinthemind.alarm.AlarmScheduler
import com.taskinthemind.data.AppSettings
import com.taskinthemind.data.Task
import com.taskinthemind.data.TaskList
import com.taskinthemind.data.TaskRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class NeedsSignInException : IOException("Google access expired. Sign in again to keep backing up.")

/**
 * Backs up every task and setting (plus a custom tone file) to one JSON file
 * in the hidden Drive appDataFolder, and restores it on a new or reset phone.
 * After sign-in, any change is backed up a few seconds later in the background.
 */
object BackupManager {
    private const val BACKUP_NAME = "task-in-the-mind-backup.json"
    private const val TONE_NAME = "task-in-the-mind-tone"
    private const val MAX_TONE_BYTES = 20L * 1024 * 1024

    data class State(
        val email: String? = null,
        val lastBackupAt: Long = 0L,
        val busy: String? = null,
        val error: String? = null
    )

    data class RemoteInfo(val modified: Long, val taskCount: Int)

    private lateinit var app: Context
    private lateinit var prefs: SharedPreferences
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()
    private val lock = Mutex()
    @Volatile private var restoring = false

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        app = context.applicationContext
        prefs = app.getSharedPreferences("backup", Context.MODE_PRIVATE)
        _state.value = State(prefs.getString("email", null), prefs.getLong("last_backup", 0L), error = prefs.getString("error", null))
        TaskRepository.onChanged = ::onDataChanged
        AppSettings.onChanged = ::onDataChanged
    }

    val signedIn get() = _state.value.email != null

    /** Debounced: a burst of edits becomes one upload a few seconds after the last one. */
    private fun onDataChanged() {
        if (!signedIn || restoring) return
        val work = OneTimeWorkRequestBuilder<BackupWorker>()
            .setInitialDelay(5, TimeUnit.SECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(app).enqueueUniqueWork("auto-backup", ExistingWorkPolicy.REPLACE, work)
    }

    /** Called once the user has granted Drive access. Returns the backup found in Drive, if any. */
    suspend fun completeSignIn(token: String): RemoteInfo? = withContext(Dispatchers.IO) {
        val drive = Drive(token)
        val email = drive.email() ?: "Google account"
        prefs.edit().putString("email", email).remove("error").apply()
        _state.update { it.copy(email = email, error = null) }
        remoteInfo(drive)
    }

    fun signOut() {
        WorkManager.getInstance(app).cancelUniqueWork("auto-backup")
        prefs.edit().clear().apply()
        _state.value = State()
    }

    suspend fun remoteInfo(): RemoteInfo? = withDrive("Checking Drive…") { remoteInfo(it) }

    private fun remoteInfo(drive: Drive): RemoteInfo? {
        val ref = drive.find(BACKUP_NAME) ?: return null
        val tasks = runCatching { JSONObject(String(drive.download(ref.id))).getJSONArray("tasks").length() }.getOrDefault(0)
        return RemoteInfo(ref.modified, tasks)
    }

    suspend fun backupNow() = withDrive("Backing up…") { drive ->
        val s = AppSettings.state.value
        var toneFile: String? = null
        if (s.toneFromFile && s.toneUri != null) {
            val existing = drive.find(TONE_NAME)
            if (existing == null || prefs.getString("tone_uploaded_for", null) != s.toneUri) {
                val bytes = readTone(Uri.parse(s.toneUri))
                if (bytes != null) {
                    drive.upsert(TONE_NAME, "application/octet-stream", bytes, existing?.id)
                    prefs.edit().putString("tone_uploaded_for", s.toneUri).apply()
                    toneFile = s.toneName
                }
            } else {
                toneFile = s.toneName
            }
        }

        val json = JSONObject()
            .put("format", 1)
            .put("createdAt", System.currentTimeMillis())
            .put("nextId", TaskRepository.peekNextId())
            .put("tasks", JSONArray().apply {
                TaskRepository.tasks.value.forEach {
                    put(JSONObject().put("id", it.id).put("title", it.title).put("description", it.description)
                        .put("triggerAt", it.triggerAt).put("done", it.done).put("listId", it.listId ?: JSONObject.NULL))
                }
            })
            .put("nextListId", TaskRepository.peekNextListId())
            .put("lists", JSONArray().apply {
                TaskRepository.lists.value.forEach { put(JSONObject().put("id", it.id).put("name", it.name)) }
            })
            .put("settings", JSONObject()
                .put("vibrate", s.vibrate)
                .put("toneName", s.toneName)
                .put("toneUri", if (s.toneFromFile) JSONObject.NULL else (s.toneUri ?: JSONObject.NULL))
                .put("toneFile", toneFile ?: JSONObject.NULL))

        drive.upsert(BACKUP_NAME, "application/json", json.toString().toByteArray(), drive.find(BACKUP_NAME)?.id)
        val now = System.currentTimeMillis()
        prefs.edit().putLong("last_backup", now).remove("error").apply()
        _state.update { it.copy(lastBackupAt = now, error = null) }
    }

    /** Replaces the tasks and settings on this phone with the ones in Drive. Returns the task count. */
    suspend fun restore(): Int = withDrive("Restoring…") { drive ->
        val ref = drive.find(BACKUP_NAME) ?: throw IOException("No backup found in this Google account yet.")
        val json = JSONObject(String(drive.download(ref.id)))
        val arr = json.getJSONArray("tasks")
        val tasks = List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            Task(
                o.getInt("id"), o.getString("title"), o.optString("description"), o.getLong("triggerAt"), o.optBoolean("done"),
                if (o.isNull("listId")) null else o.getInt("listId")
            )
        }

        restoring = true
        try {
            TaskRepository.tasks.value.forEach { AlarmScheduler.cancel(app, it.id) }
            val listArr = json.optJSONArray("lists") ?: JSONArray()
            TaskRepository.replaceLists(
                List(listArr.length()) { i -> listArr.getJSONObject(i).let { TaskList(it.getInt("id"), it.getString("name")) } },
                json.optInt("nextListId", 1)
            )
            TaskRepository.replaceAll(tasks, json.optInt("nextId", 1))
            AlarmScheduler.rescheduleAll(app)

            val set = json.optJSONObject("settings")
            if (set != null) {
                AppSettings.setVibrate(set.optBoolean("vibrate", true))
                val toneFile = set.optString("toneFile").takeIf { it.isNotBlank() && it != "null" }
                val toneUri = set.optString("toneUri").takeIf { it.isNotBlank() && it != "null" }
                when {
                    toneFile != null -> {
                        val remote = drive.find(TONE_NAME)
                        val file = remote?.let { r ->
                            File(app.filesDir, "tones").apply { mkdirs() }.resolve("restored-tone").also { it.writeBytes(drive.download(r.id)) }
                        }
                        if (file != null) {
                            val uri = Uri.fromFile(file).toString()
                            AppSettings.setTone(uri, toneFile, fromFile = true)
                            prefs.edit().putString("tone_uploaded_for", uri).apply()
                        } else AppSettings.setTone(null, "Default alarm", fromFile = false)
                    }
                    // Device tones differ between phones: keep it only if this phone has it too.
                    toneUri != null && RingtoneManager.getRingtone(app, Uri.parse(toneUri)) != null ->
                        AppSettings.setTone(toneUri, set.optString("toneName", "Device tone"), fromFile = false)
                    else -> AppSettings.setTone(null, "Default alarm", fromFile = false)
                }
            }
        } finally {
            restoring = false
        }
        tasks.size
    }

    suspend fun deleteBackup() = withDrive("Deleting backup…") { drive ->
        drive.find(BACKUP_NAME)?.let { drive.delete(it.id) }
        drive.find(TONE_NAME)?.let { drive.delete(it.id) }
        prefs.edit().remove("last_backup").remove("tone_uploaded_for").apply()
        _state.update { it.copy(lastBackupAt = 0L) }
    }

    fun clearError() {
        prefs.edit().remove("error").apply()
        _state.update { it.copy(error = null) }
    }

    /** Runs [block] with a Drive client, retrying once with a fresh token if the old one was rejected. */
    private suspend fun <T> withDrive(label: String, block: (Drive) -> T): T = lock.withLock {
        _state.update { it.copy(busy = label) }
        try {
            withContext(Dispatchers.IO) {
                val token = DriveAuth.silentToken(app) ?: throw NeedsSignInException()
                try {
                    block(Drive(token))
                } catch (e: HttpException) {
                    if (e.code != 401) throw e
                    runCatching { GoogleAuthUtil.clearToken(app, token) }
                    block(Drive(DriveAuth.silentToken(app) ?: throw NeedsSignInException()))
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e // stopped on purpose; not an error worth showing
        } catch (e: Throwable) {
            val msg = friendly(e)
            prefs.edit().putString("error", msg).apply()
            _state.update { it.copy(error = msg) }
            throw e
        } finally {
            _state.update { it.copy(busy = null) }
        }
    }

    /** A short message for the user; empty for a cancelled job, which shows nothing. */
    fun friendly(e: Throwable): String = when (e) {
        is kotlinx.coroutines.CancellationException -> ""
        is NeedsSignInException -> e.message!!
        is HttpException -> if (e.code == 403) "Drive said no (403). Check this account is a test user." else "Drive hiccup (${e.code}). Try again."
        is java.net.UnknownHostException, is java.net.SocketTimeoutException -> "No internet right now. We'll retry."
        else -> e.message ?: "Something went wrong."
    }

    private fun readTone(uri: Uri): ByteArray? = runCatching {
        val size = app.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use {
            if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else null
        }
        if (size != null && size > MAX_TONE_BYTES) return null
        app.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    }.getOrNull()
}

class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!BackupManager.signedIn) return Result.success()
        return try {
            BackupManager.backupNow()
            Result.success()
        } catch (e: NeedsSignInException) {
            Result.success() // the error is shown in Settings; retrying can't fix it
        } catch (e: IOException) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        } catch (e: Throwable) {
            Result.failure()
        }
    }
}
