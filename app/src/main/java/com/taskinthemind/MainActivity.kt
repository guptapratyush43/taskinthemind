package com.taskinthemind

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.taskinthemind.alarm.AlarmService
import com.taskinthemind.alarm.EXTRA_TASK_ID
import com.taskinthemind.ui.AppRoot
import com.taskinthemind.ui.theme.TaskMindTheme
import kotlinx.coroutines.flow.MutableStateFlow

sealed interface Screen {
    data object Home : Screen
    data object Settings : Screen
    data class Editor(val taskId: Int?, val listId: Int? = null) : Screen
    data class Reschedule(val taskId: Int, val fromAlarm: Boolean) : Screen
}

class NavViewModel : ViewModel() {
    val screen = MutableStateFlow<Screen>(Screen.Home)
}

class MainActivity : ComponentActivity() {

    companion object {
        private const val ACTION_RESCHEDULE = "com.taskinthemind.RESCHEDULE"

        fun rescheduleIntent(context: Context, id: Int) = Intent(context, MainActivity::class.java)
            .setAction(ACTION_RESCHEDULE)
            .putExtra(EXTRA_TASK_ID, id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }

    private lateinit var nav: NavViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        nav = ViewModelProvider(this)[NavViewModel::class.java]
        if (savedInstanceState == null) handleIntent(intent)

        setContent {
            TaskMindTheme {
                val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED
                    ) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }

                AppRoot(
                    screenFlow = nav.screen,
                    onRescheduled = { fromAlarm ->
                        // A plain Toast is the same system pop-up Android uses for
                        // "cleared recent apps"; it outlives the closing activity.
                        Toast.makeText(applicationContext, "Task rescheduled", Toast.LENGTH_SHORT).show()
                        if (fromAlarm) finishAndRemoveTask() else nav.screen.value = Screen.Home
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action != ACTION_RESCHEDULE) return
        val id = intent.getIntExtra(EXTRA_TASK_ID, -1)
        AlarmService.silence(this)
        nav.screen.value = Screen.Reschedule(id, fromAlarm = true)
    }
}
