package com.example.aicompanion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.example.aicompanion.ui.navigation.AppNavHost
import com.example.aicompanion.ui.navigation.NavRoutes
import com.example.aicompanion.ui.theme.AICompanionTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_OPEN_PLAN = "extra_open_plan"
        const val EXTRA_OPEN_TRIAGE = "extra_open_triage"
        const val EXTRA_OPEN_CAPTURE = "extra_open_capture"
        const val EXTRA_OPEN_VOICE_COMMAND = "extra_open_voice_command"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val deepLinkTaskId = intent?.getLongExtra(EXTRA_TASK_ID, -1L)?.takeIf { it != -1L }
        val openPlan = intent?.getBooleanExtra(EXTRA_OPEN_PLAN, false) ?: false
        val openTriage = intent?.getBooleanExtra(EXTRA_OPEN_TRIAGE, false) ?: false
        // Shortcuts pass string extras; programmatic intents pass booleans — handle both
        val openCapture = intent?.getBooleanExtra(EXTRA_OPEN_CAPTURE, false) ?: false
            || intent?.getStringExtra(EXTRA_OPEN_CAPTURE) == "true"
        val openVoiceCommand = intent?.getBooleanExtra(EXTRA_OPEN_VOICE_COMMAND, false) ?: false
            || intent?.getStringExtra(EXTRA_OPEN_VOICE_COMMAND) == "true"
        setContent {
            AICompanionTheme {
                val navController = rememberNavController()
                AppNavHost(
                    navController = navController,
                    deepLinkTaskId = deepLinkTaskId,
                    openPlanMyDay = openPlan,
                    openTaskTriage = openTriage,
                    openCapture = openCapture,
                    openVoiceCommand = openVoiceCommand
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Trigger Google Tasks sync on app resume (debounced inside SyncEngine)
        val app = application as? AICompanionApplication ?: return
        lifecycleScope.launch {
            app.container.syncEngine.sync()
        }
    }
}
