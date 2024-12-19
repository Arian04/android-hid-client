package me.arianb.usb_hid_client.ui.utils

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import me.arianb.usb_hid_client.settings.AppTheme
import me.arianb.usb_hid_client.settings.SettingsViewModel
import me.arianb.usb_hid_client.ui.theme.PaddingLarge
import me.arianb.usb_hid_client.ui.theme.PaddingNone
import me.arianb.usb_hid_client.ui.theme.PaddingNormal
import me.arianb.usb_hid_client.ui.theme.USBHIDClientTheme

@Composable
fun BasicPage(
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

    USBHIDClientTheme {
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
 * Get text color as Int based on current app theme. This is meant to be used for
 * legacy TextViews still present in the app, since they seem to only set their text color
 * based on the system theme.
 */
@Composable
fun getColorByTheme(settingsViewModel: SettingsViewModel = viewModel()): Int? {
    val preferences by settingsViewModel.userPreferencesFlow.collectAsState()

    // These hex values are the colors that Android would set the text to by default during my testing
    val textColor: Int? = when (preferences.appTheme) {
        AppTheme.DarkMode -> 0xB3FFFFFF.toInt()
        AppTheme.LightMode -> 0x8A000000.toInt()
        else -> {
            // Just let the system sort it out
            null
        }
    }

    return textColor
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
