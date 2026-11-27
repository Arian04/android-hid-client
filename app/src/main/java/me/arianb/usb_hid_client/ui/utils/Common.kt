package me.arianb.usb_hid_client.ui.utils

import android.content.res.Configuration
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import kotlinx.coroutines.flow.Flow
import me.arianb.usb_hid_client.settings.SettingsViewModel
import me.arianb.usb_hid_client.ui.theme.PaddingLarge
import me.arianb.usb_hid_client.ui.theme.PaddingNormal
import me.arianb.usb_hid_client.ui.theme.USBHIDClientTheme

@Composable
fun BasicPage(
    settingsViewModel: SettingsViewModel = viewModel(),
    topBar: @Composable () -> Unit,
    snackbarHostState: SnackbarHostState? = null,
    padding: PaddingValues = PaddingValues(all = PaddingLarge),
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(PaddingNormal, Alignment.Top),
    scrollable: Boolean = false,
    content: @Composable (ColumnScope.() -> Unit)
) {
    val scrollableModifier = if (scrollable) {
        Modifier.verticalScroll(rememberScrollState())
    } else {
        Modifier
    }

    val preferencesState by settingsViewModel.userPreferencesFlow.collectAsState()
    val appThemePreference = preferencesState.appTheme
    val isDynamicColorEnabledPreference = preferencesState.isDynamicColorEnabled

    USBHIDClientTheme(
        appTheme = appThemePreference,
        isDynamicColorEnabled = isDynamicColorEnabledPreference
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Scaffold(
                topBar = topBar,
                snackbarHost = if (snackbarHostState == null) {
                    {}
                } else {
                    { SnackbarHost(snackbarHostState) }
                },
                contentWindowInsets = WindowInsets.safeDrawing,
                content = { innerPadding: PaddingValues ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(padding)
                            .then(scrollableModifier),
                        horizontalAlignment = horizontalAlignment,
                        verticalArrangement = verticalArrangement,
                    ) {
                        content()
                    }
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleNavTopBar(
    title: String,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    actions: @Composable (RowScope.() -> Unit) = {}
) {
    val navigator = LocalNavigator.currentOrThrow

    BasicTopBar(
        title = title,
        navigationIcon = {
            IconButton(onClick = { navigator.pop() }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Default.ArrowBack,
                    contentDescription = "Back button"
                )
            }
        },
        actions = actions,
        scrollBehavior = scrollBehavior
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BasicTopBar(
    title: String,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable (RowScope.() -> Unit) = {},
    scrollBehavior: TopAppBarScrollBehavior? = null
) {
    TopAppBar(
        title = {
            Text(title)
        },
        navigationIcon = navigationIcon,
        actions = actions,
        scrollBehavior = scrollBehavior
    )
}

@Composable
fun LabeledCategory(
    title: String,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true,
    content: @Composable () -> Unit
) {
    Text(
        text = title,
        modifier = modifier,
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.titleSmall,
    )
    content()
    if (showDivider) {
        HorizontalDivider()
    }
}

/**
 * Get text color as an ARGB Int based on current app theme. This is meant to be used for
 * legacy TextViews still present in the app, since they seem to only set their text color
 * based on the system theme.
 */
@Composable
fun getColorByTheme(): Int {
    val textColor: Color = MaterialTheme.colorScheme.onSurface

    return textColor.toArgb()
}

/**
 * Returns a [ViewModel] scoped to the hosting Activity rather than the current Voyager screen,
 * so screens share a single instance. Falls back to default [viewModel] scoping (ex: in Compose previews).
 */
@Composable
inline fun <reified VM : ViewModel> activityViewModel(): VM {
    val activity = LocalActivity.current
    return if (activity is ViewModelStoreOwner) {
        viewModel(viewModelStoreOwner = activity)
    } else {
        viewModel()
    }
}

/**
 * Remembers a [SnackbarHostState] and automatically collects [snackbarFlow] with the current
 * lifecycle, displaying each emitted message as a snackbar.
 */
@Composable
fun rememberSnackbarHostState(
    snackbarFlow: Flow<String>,
    duration: SnackbarDuration = SnackbarDuration.Long,
): SnackbarHostState {
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(snackbarFlow, lifecycleOwner) {
        snackbarFlow.flowWithLifecycle(lifecycleOwner.lifecycle)
            .collect { message ->
                snackbarHostState.showSnackbar(
                    message = message,
                    duration = duration
                )
            }
    }

    return snackbarHostState
}

@Preview(
    name = "Dark Mode",
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Preview(
    name = "Light Mode",
    uiMode = Configuration.UI_MODE_NIGHT_NO
)
annotation class DarkLightModePreviews
