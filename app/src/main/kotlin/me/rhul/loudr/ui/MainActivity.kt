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
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
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

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                if (androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(this, android.Manifest.permission.POST_NOTIFICATIONS)) {
                    android.app.AlertDialog.Builder(this)
                        .setTitle("Notification Permission")
                        .setMessage("Loudr uses a persistent notification to keep the audio booster active in the background and give you quick access to volume controls. Please allow notifications to ensure the booster isn't killed by the system.")
                        .setPositiveButton("OK") { _, _ ->
                            androidx.core.app.ActivityCompat.requestPermissions(
                                this,
                                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                                101
                            )
                        }
                        .setNegativeButton("No Thanks", null)
                        .show()
                } else {
                    androidx.core.app.ActivityCompat.requestPermissions(
                        this,
                        arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                        101
                    )
                }
            }
        }

        // Start the foreground service so audio effects stay active
        // even when the app is in the background.
        ContextCompat.startForegroundService(
            this,
            Intent(this, VolumeBoostService::class.java),
        )

        setContent {
            val appTheme by appViewModel.appTheme.collectAsStateWithLifecycle()

            @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
            val windowSizeClass = calculateWindowSizeClass(this)

            LoudrTheme(appTheme = appTheme) {
                MainScreen(windowSizeClass = windowSizeClass)
            }
        }
    }
}
