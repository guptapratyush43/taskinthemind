package com.taskinthemind.alarm

import android.app.KeyguardManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Snooze
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.taskinthemind.MainActivity
import com.taskinthemind.data.Task
import com.taskinthemind.data.TaskRepository
import com.taskinthemind.ui.Fmt
import com.taskinthemind.ui.theme.TaskMindTheme
import kotlinx.coroutines.delay

/** Shown full-screen when the alarm fires on a locked or idle phone. */
class AlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()

        val launchedFor = intent.getIntExtra(EXTRA_TASK_ID, -1)
        setContent {
            TaskMindTheme {
                val ringing by AlarmService.ringingTaskId.collectAsStateWithLifecycle()
                val tasks by TaskRepository.tasks.collectAsStateWithLifecycle()
                // Close once the alarm is handled elsewhere, e.g. from the notification.
                LaunchedEffect(ringing) { if (ringing == null) finish() }
                val id = ringing ?: launchedFor
                val task = tasks.firstOrNull { it.id == id }
                if (task != null) {
                    RingingScreen(
                        task = task,
                        onGotIt = { AlarmService.dismiss(this, task.id); finish() },
                        onReschedule = { openReschedule(task.id) }
                    )
                }
            }
        }
    }

    private fun openReschedule(id: Int) {
        val go = {
            startActivity(MainActivity.rescheduleIntent(this, id))
            finish()
        }
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard.isKeyguardLocked) {
            keyguard.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() { go() }
            })
        } else go()
    }
}

@Composable
private fun RingingScreen(task: Task, onGotIt: () -> Unit, onReschedule: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val now by produceState(System.currentTimeMillis()) {
        while (true) { delay(5_000); value = System.currentTimeMillis() }
    }
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulseScale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp)
    ) {
        Spacer(Modifier.height(36.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(Fmt.clock(now), style = MaterialTheme.typography.displaySmall.copy(fontSize = 72.sp, lineHeight = 80.sp), color = scheme.onBackground)
            Spacer(Modifier.width(6.dp))
            Text(Fmt.ampm(now), style = MaterialTheme.typography.headlineSmall, color = scheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 14.dp))
        }
        Text(Fmt.longDay(now), style = MaterialTheme.typography.titleSmall, color = scheme.onSurfaceVariant)

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(Modifier.size(132.dp).scale(pulse).background(scheme.primary.copy(alpha = 0.15f), CircleShape))
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(96.dp).background(scheme.primary, CircleShape)) {
                    Icon(Icons.Outlined.Alarm, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(44.dp))
                }
            }
            Spacer(Modifier.height(32.dp))
            Text(task.title, style = MaterialTheme.typography.headlineMedium, color = scheme.onBackground, textAlign = TextAlign.Center)
            if (task.description.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    task.description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 6
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = onReschedule,
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(vertical = 18.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, scheme.outline),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Outlined.Snooze, contentDescription = null, tint = scheme.onSurface, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Reschedule", color = scheme.onSurface, style = MaterialTheme.typography.labelLarge)
            }
            Button(
                onClick = onGotIt,
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(vertical = 18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = scheme.primary, contentColor = scheme.onPrimary),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Got it", style = MaterialTheme.typography.labelLarge)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
