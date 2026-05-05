package me.rhul.loudr.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ---------------------------------------------------------------------------
// Loudr brand palette — coral-red on deep near-black
// ---------------------------------------------------------------------------

/** Near-black with a faint warm undertone to complement the red. */
private val LoudrBackground = Color(0xFF0D0D11)
private val LoudrSurface    = Color(0xFF111115)
private val LoudrSurfaceVar = Color(0xFF1C1C22)

/** Loudr coral-red — energetic, bold, non-garish. */
private val LoudrPrimary    = Color(0xFFFF4560)
private val LoudrOnPrimary  = Color(0xFFFFFFFF)
private val LoudrSecondary  = Color(0xFFFF8C9E)

/** Primary container — dark muted red for selected chip backgrounds. */
private val LoudrPrimaryContainer   = Color(0xFF5C0018)
private val LoudrOnPrimaryContainer = Color(0xFFFFDADC)

private val LoudrError   = Color(0xFFFF6B8A)
private val LoudrOnError = Color(0xFF68001F)

private val LoudrDarkScheme = darkColorScheme(
    primary               = LoudrPrimary,
    onPrimary             = LoudrOnPrimary,
    primaryContainer      = LoudrPrimaryContainer,
    onPrimaryContainer    = LoudrOnPrimaryContainer,
    secondary             = LoudrSecondary,
    background            = LoudrBackground,
    surface               = LoudrSurface,
    surfaceVariant        = LoudrSurfaceVar,
    error                 = LoudrError,
    onError               = LoudrOnError,
)

private val LoudrLightScheme = lightColorScheme(
    primary               = Color(0xFFB3001B),
    onPrimary             = Color(0xFFFFFFFF),
    primaryContainer      = Color(0xFFFFDADC),
    onPrimaryContainer    = Color(0xFF40000D),
    secondary             = Color(0xFF974051),
    background            = Color(0xFFFFF8F7),
    surface               = Color(0xFFFFF8F7),
)

// ---------------------------------------------------------------------------
// AMOLED override — pure black to save battery on OLED panels
// ---------------------------------------------------------------------------

private val AmoledScheme = LoudrDarkScheme.copy(
    background     = Color(0xFF000000),
    surface        = Color(0xFF0A0A0A),
    surfaceVariant = Color(0xFF141419),
)

// ---------------------------------------------------------------------------
// Theme entry point
// ---------------------------------------------------------------------------

enum class AppTheme { DYNAMIC, DARK, AMOLED }

@Composable
fun LoudrTheme(
    appTheme: AppTheme = AppTheme.DYNAMIC,
    content: @Composable () -> Unit,
) {
    val context         = LocalContext.current
    val isDark          = isSystemInDarkTheme()
    val supportsDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val colorScheme = when {
        appTheme == AppTheme.AMOLED -> AmoledScheme
        appTheme == AppTheme.DARK   -> LoudrDarkScheme

        // Material You: dynamic color on Android 12+
        appTheme == AppTheme.DYNAMIC && supportsDynamic -> {
            if (isDark) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }

        // Fallback for older devices
        isDark -> LoudrDarkScheme
        else   -> LoudrLightScheme
    }

    // enableEdgeToEdge() in MainActivity already makes bars transparent.
    // We only need to update icon appearance (light vs dark) to match the theme.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = !isDark && appTheme != AppTheme.AMOLED
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = LoudrTypography,
        content     = content,
    )
}
