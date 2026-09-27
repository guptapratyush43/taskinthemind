package com.taskinthemind

import android.app.Application
import com.taskinthemind.alarm.AlarmService
import com.taskinthemind.backup.BackupManager
import com.taskinthemind.data.AppSettings
import com.taskinthemind.data.TaskRepository

class TaskApp : Application() {
    override fun onCreate() {
        super.onCreate()
        TaskRepository.init(this)
        AppSettings.init(this)
        AlarmService.createChannel(this)
        BackupManager.init(this)
    }
}
