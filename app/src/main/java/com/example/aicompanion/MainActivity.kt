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
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val deepLinkTaskId = intent?.getLongExtra(EXTRA_TASK_ID, -1L)?.takeIf { it != -1L }
        val openPlan = intent?.getBooleanExtra(EXTRA_OPEN_PLAN, false) ?: false
        setContent {
            AICompanionTheme {
                val navController = rememberNavController()
                AppNavHost(
                    navController = navController,
                    deepLinkTaskId = deepLinkTaskId,
                    openPlanMyDay = openPlan
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
