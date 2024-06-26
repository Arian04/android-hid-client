package me.arianb.usb_hid_client.ui.theme

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import me.arianb.usb_hid_client.settings.AppTheme
import me.arianb.usb_hid_client.settings.SettingsViewModel

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80,
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

fun isDynamicColorAvailable(): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
}

@Composable
fun USBHIDClientTheme(
    settingsViewModel: SettingsViewModel = viewModel(),
    content: @Composable () -> Unit
) {
    val preferencesState by settingsViewModel.userPreferencesFlow.collectAsState()

    val darkTheme = when (preferencesState.appTheme) {
        AppTheme.DarkMode -> true
        AppTheme.LightMode -> false
        else -> isSystemInDarkTheme()
    }

    val dynamicColor = preferencesState.isDynamicColorEnabled

    val colorScheme = when {
        // Dynamic color is available on Android 12+
        dynamicColor && isDynamicColorAvailable() -> {
            val context = LocalContext.current
            if (darkTheme) {
                dynamicDarkColorScheme(context)
            } else {
                dynamicLightColorScheme(context)
            }
        }

        // Else fall back to non-dynamic colors
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()

            // FIXME: I'm flipping darkTheme because for SOME REASON, this property seems to be having
            //        the exact opposite effect of what it says it should do. Or I'm misunderstanding.
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
