package com.taskinthemind.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.taskinthemind.Screen
import com.taskinthemind.alarm.TaskActions
import com.taskinthemind.data.TaskRepository
import com.taskinthemind.update.UpdateManager
import kotlinx.coroutines.flow.MutableStateFlow

@Composable
fun AppRoot(screenFlow: MutableStateFlow<Screen>, onRescheduled: (fromAlarm: Boolean) -> Unit) {
    val screen by screenFlow.collectAsStateWithLifecycle()
    val tasks by TaskRepository.tasks.collectAsStateWithLifecycle()
    val lists by TaskRepository.lists.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val goHome = { screenFlow.value = Screen.Home }
    // Collapsed each time the app opens; kept while moving between screens.
    var showCompleted by rememberSaveable { mutableStateOf(false) }
    var selectedList by rememberSaveable { mutableStateOf<Int?>(null) }
    // A deleted (or restored-away) list falls back to All tasks.
    LaunchedEffect(lists) { if (selectedList != null && lists.none { it.id == selectedList }) selectedList = null }
    // The same system pop-up as "cleared recent apps".
    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    fun toggleDone(id: Int) {
        val done = TaskRepository.get(id)?.done ?: return
        TaskActions.setDone(context, id, !done)
        toast(if (done) "Moved back to your tasks" else "Task marked as done")
    }

    BackHandler(enabled = screen != Screen.Home, onBack = goHome)

    val update by UpdateManager.offer.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { UpdateManager.checkOnLaunch() }
    update?.let { UpdateDialog(it) }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        AnimatedContent(
            targetState = screen,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(150)) },
            label = "screen"
        ) { s ->
            when (s) {
                Screen.Home -> HomeScreen(
                    tasks = tasks,
                    onNewTask = { screenFlow.value = Screen.Editor(null, selectedList) },
                    lists = lists,
                    selectedList = selectedList,
                    onSelectList = { selectedList = it },
                    onCreateList = { name -> selectedList = TaskRepository.createList(name) },
                    onRenameList = { id, name -> TaskRepository.renameList(id, name) },
                    onReorderLists = { ids -> TaskRepository.reorderLists(ids) },
                    onDeleteList = { id -> TaskRepository.deleteList(id); if (selectedList == id) selectedList = null; toast("List deleted") },
                    onOpenTask = { screenFlow.value = Screen.Editor(it.id) },
                    onToggleDone = { toggleDone(it.id) },
                    onDelete = { TaskActions.delete(context, it.id); toast("Task deleted") },
                    showCompleted = showCompleted,
                    onToggleCompleted = { showCompleted = !showCompleted },
                    onOpenSettings = { screenFlow.value = Screen.Settings }
                )

                Screen.Settings -> SettingsScreen(onBack = goHome)

                is Screen.Editor -> EditorScreen(
                    task = s.taskId?.let { id -> tasks.firstOrNull { it.id == id } },
                    onBack = goHome,
                    lists = lists,
                    initialListId = s.listId,
                    onSave = { title, desc, at, listId ->
                        TaskActions.save(context, s.taskId, title, desc, at, listId)
                        goHome()
                    },
                    onDelete = { id ->
                        TaskActions.delete(context, id)
                        toast("Task deleted")
                        goHome()
                    },
                    onToggleDone = { id ->
                        toggleDone(id)
                        goHome()
                    }
                )

                is Screen.Reschedule -> {
                    val task = tasks.firstOrNull { it.id == s.taskId }
                    if (task == null) {
                        LaunchedEffect(Unit) { goHome() }
                    } else {
                        RescheduleScreen(
                            task = task,
                            onBack = goHome,
                            onConfirm = { at ->
                                TaskActions.reschedule(context, task.id, at)
                                onRescheduled(s.fromAlarm)
                            }
                        )
                    }
                }
            }
        }
    }
}
