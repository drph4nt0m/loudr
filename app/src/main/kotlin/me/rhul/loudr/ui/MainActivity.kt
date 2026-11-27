package me.rhul.loudr.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import me.rhul.loudr.service.VolumeBoostService
import me.rhul.loudr.ui.main.MainScreen
import me.rhul.loudr.ui.theme.LoudrTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val appViewModel: AppViewModel by viewModels()
    private var showNotificationRationale by mutableStateOf(false)

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                VolumeBoostService.start(this)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Only prompt on a fresh launch, not on configuration changes (e.g. rotation)
        if (savedInstanceState == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(
                        this,
                        Manifest.permission.POST_NOTIFICATIONS,
                    )
                ) {
                    showNotificationRationale = true
                } else {
                    requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }

        // Start the foreground service so audio effects stay active
        // even when the app is in the background.
        VolumeBoostService.start(this)

        setContent {
            val appTheme by appViewModel.appTheme.collectAsStateWithLifecycle()

            @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
            val windowSizeClass = calculateWindowSizeClass(this)

            LoudrTheme(appTheme = appTheme) {
                if (showNotificationRationale && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    AlertDialog(
                        onDismissRequest = { showNotificationRationale = false },
                        title = { Text("Notification Permission") },
                        text = {
                            Text(
                                "Loudr uses a persistent notification to keep the audio booster active " +
                                    "in the background and give you quick access to volume controls. " +
                                    "Please allow notifications to ensure the booster isn't killed by the system.",
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showNotificationRationale = false
                                    requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                },
                            ) {
                                Text("Allow")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showNotificationRationale = false }) {
                                Text("No Thanks")
                            }
                        },
                    )
                }
                MainScreen(windowSizeClass = windowSizeClass)
            }
        }
    }
}
