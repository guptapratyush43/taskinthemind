package com.taskinthemind

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.taskinthemind.alarm.AlarmScheduler
import com.taskinthemind.alarm.AlarmService
import com.taskinthemind.backup.BackupManager
import com.taskinthemind.update.UpdateManager
import com.taskinthemind.data.AppSettings
import com.taskinthemind.data.TaskRepository

/**
 * App-wide scope for work that must outlive a screen: sign-in, backup, restore
 * and update downloads keep going when Google's sign-in window or a rotation
 * rebuilds the UI underneath them.
 */
val AppScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

class TaskApp : Application() {
    override fun onCreate() {
        super.onCreate()
        TaskRepository.init(this)
        AppSettings.init(this)
        AlarmService.createChannel(this)
        BackupManager.init(this)
        UpdateManager.init(this)
        // Some phones (Xiaomi, Oppo, Vivo...) block the boot/update broadcast that normally
        // re-arms alarms. Re-arming whenever the app starts covers them; it is cheap and idempotent.
        AlarmScheduler.rescheduleAll(this)
    }
}
