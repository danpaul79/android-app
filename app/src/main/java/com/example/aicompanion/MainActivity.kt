package com.example.aicompanion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.example.aicompanion.ui.navigation.AppNavHost
import com.example.aicompanion.ui.theme.AICompanionTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AICompanionTheme {
                val navController = rememberNavController()
                AppNavHost(navController = navController)
            }
        }
    }
}
