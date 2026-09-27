package com.taskinthemind.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.taskinthemind.MainActivity
import com.taskinthemind.data.Task
import com.taskinthemind.data.TaskRepository

const val EXTRA_TASK_ID = "task_id"

object AlarmScheduler {
    const val ACTION_FIRE = "com.taskinthemind.FIRE"

    private fun operation(context: Context, id: Int): PendingIntent = PendingIntent.getBroadcast(
        context,
        id,
        Intent(context, AlarmReceiver::class.java).setAction(ACTION_FIRE).putExtra(EXTRA_TASK_ID, id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    fun schedule(context: Context, task: Task) {
        val am = context.getSystemService(AlarmManager::class.java)
        if (task.done || task.triggerAt <= System.currentTimeMillis()) {
            am.cancel(operation(context, task.id))
            return
        }
        val op = operation(context, task.id)
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, task.triggerAt, op)
            return
        }
        // setAlarmClock is exempt from Doze and shows the alarm icon in the status bar.
        val show = PendingIntent.getActivity(
            context, task.id, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.setAlarmClock(AlarmManager.AlarmClockInfo(task.triggerAt, show), op)
    }

    fun cancel(context: Context, id: Int) {
        context.getSystemService(AlarmManager::class.java).cancel(operation(context, id))
    }

    fun rescheduleAll(context: Context) = TaskRepository.tasks.value.forEach { schedule(context, it) }
}

/** Everything the UI does to a task goes through here so alarms stay in sync. */
object TaskActions {
    fun save(context: Context, id: Int?, title: String, description: String, triggerAt: Long, listId: Int?) {
        val task = Task(id ?: TaskRepository.newId(), title.trim(), description.trim(), triggerAt, done = false, listId = listId)
        TaskRepository.upsert(task)
        AlarmScheduler.schedule(context, task)
    }

    fun reschedule(context: Context, id: Int, triggerAt: Long) {
        val task = TaskRepository.get(id)?.copy(triggerAt = triggerAt, done = false) ?: return
        TaskRepository.upsert(task)
        AlarmScheduler.schedule(context, task)
    }

    fun setDone(context: Context, id: Int, done: Boolean) {
        val task = TaskRepository.get(id)?.copy(done = done) ?: return
        TaskRepository.upsert(task)
        AlarmScheduler.schedule(context, task)
    }

    fun delete(context: Context, id: Int) {
        AlarmScheduler.cancel(context, id)
        TaskRepository.delete(id)
        if (AlarmService.ringingTaskId.value == id) AlarmService.silence(context)
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmScheduler.ACTION_FIRE) return
        val task = TaskRepository.get(intent.getIntExtra(EXTRA_TASK_ID, -1)) ?: return
        // Ignore a stale alarm left over from before the task was moved later.
        if (task.done || task.triggerAt > System.currentTimeMillis() + 60_000) return
        AlarmService.ring(context, task.id)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = AlarmScheduler.rescheduleAll(context)
}
