package me.arianb.usb_hid_client

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.core.annotation.ExperimentalVoyagerApi
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.NavigatorDisposeBehavior
import cafe.adriel.voyager.transitions.SlideTransition
import com.topjohnwu.superuser.Shell
import me.arianb.usb_hid_client.settings.SettingsViewModel
import me.arianb.usb_hid_client.troubleshooting.ProductionTree
import me.arianb.usb_hid_client.ui.standalone_screens.OnboardingScreen
import timber.log.Timber

// TODO: move all misc strings used in snackbars and alerts throughout the app into strings.xml for translation purposes.

// Notes on terminology:
// 		A key that has been pressed in conjunction with the shift key (ex: @ = 2 + shift, $ = 4 + shift, } = ] + shift)
// 		will be referred to as a "shifted" key. In the previous example, 2, 4, and ] would be considered
// 		the "un-shifted" keys.
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
            Shell.enableVerboseLogging = true
        }

        Timber.plant(ProductionTree())

        setContent {
            Entrypoint()
        }
    }
}

@Composable
fun Entrypoint(settingsViewModel: SettingsViewModel = viewModel()) {
    val userPreferencesState by settingsViewModel.userPreferencesFlow.collectAsState()

    // If this is the first time the app has been opened, then show OnboardingActivity
    val onboardingDone = userPreferencesState.isOnboardingDone
    val startScreen = if (!onboardingDone) {
        OnboardingScreen()
    } else {
        MainScreen()
    }

    ScreenEntrypoint(startScreen)
}

@OptIn(ExperimentalVoyagerApi::class)
@Composable
fun ScreenEntrypoint(startScreen: Screen) {
    // dispose params are due to this issue: https://github.com/adrielcafe/voyager/issues/106
    Navigator(
        startScreen,
        disposeBehavior = NavigatorDisposeBehavior(disposeSteps = false),
    ) { navigator ->
        SlideTransition(
            navigator,
            disposeScreenAfterTransitionEnd = true
        )
    }
}
