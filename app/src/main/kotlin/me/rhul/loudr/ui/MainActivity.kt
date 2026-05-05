package me.rhul.loudr.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import me.rhul.loudr.service.VolumeBoostService
import me.rhul.loudr.ui.main.MainScreen
import me.rhul.loudr.ui.theme.LoudrTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val appViewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Start the foreground service so audio effects stay active
        // even when the app is in the background.
        ContextCompat.startForegroundService(
            this,
            Intent(this, VolumeBoostService::class.java),
        )

        setContent {
            val appTheme by appViewModel.appTheme.collectAsStateWithLifecycle()

            LoudrTheme(appTheme = appTheme) {
                MainScreen()
            }
        }
    }
}
