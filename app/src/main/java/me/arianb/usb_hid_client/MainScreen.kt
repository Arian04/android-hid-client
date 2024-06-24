package me.arianb.usb_hid_client

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import me.arianb.usb_hid_client.input_views.DirectInput
import me.arianb.usb_hid_client.input_views.DirectInputIconButton
import me.arianb.usb_hid_client.input_views.ManualInput
import me.arianb.usb_hid_client.input_views.Touchpad
import me.arianb.usb_hid_client.settings.SettingsScreen
import me.arianb.usb_hid_client.shell_utils.RootStateHolder
import me.arianb.usb_hid_client.ui.theme.BasicPage
import me.arianb.usb_hid_client.ui.theme.BasicTopBar
import me.arianb.usb_hid_client.ui.theme.DarkLightModePreviews
import timber.log.Timber

class MainScreen : Screen {
    @Composable
    override fun Content() {
        MainPage()
    }
}

@Composable
private fun MainPage(mainViewModel: MainViewModel = viewModel()) {
    val rootStateHolder = RootStateHolder.getInstance()
    val rootState by rootStateHolder.uiState.collectAsState()

    // TODO: should i do this in VM constructor? but then I cant differentiate between
    //       missing char dev on startup or a weird issue of it missing AFTER startup.
    //       but should I even do that? should I just handle both situations the same way?
    val showMissingCharDeviceOnStartupAlert = remember { mutableStateOf(mainViewModel.anyCharacterDeviceMissing()) }

    val uiState by mainViewModel.uiState.collectAsState()
    Timber.d("in MainScreen, uiState is: %s", uiState.toString())

    val snackbarHostState = remember { SnackbarHostState() }
    BasicPage(
        snackbarHostState = snackbarHostState,
        topBar = { MainTopBar() },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (showMissingCharDeviceOnStartupAlert.value) {
            Timber.d("MISSING CHAR DEV ON START")
            CreateCharDevicesAlertDialog(showMissingCharDeviceOnStartupAlert)
        }

        ManualInput()
        DirectInput()
        Touchpad()

        LaunchedEffect(uiState) {
            Timber.d("LAUNCHED EFFECT RUNNING WITH UI STATE = %s", uiState.toString())
            if (rootState.missingRootPrivileges) {
                // TODO: if this fails here, I need to make it incredibly clear that the app will not work.
                //       right now, you can still try to use it and it'll fail. It should just "lock" the inputs
                //       if this fails I think.
                snackbarHostState.showSnackbar(
                    message = "Missing root permissions",
                    duration = SnackbarDuration.Long
                )
            } else if (uiState.isDeviceUnplugged) {
                snackbarHostState.showSnackbar(
                    message = "ERROR: Your device seems to be disconnected. If not, try reseating the USB cable",
                    duration = SnackbarDuration.Long
                )
            } else if (!showMissingCharDeviceOnStartupAlert.value && uiState.missingCharacterDevice) {
                val result = snackbarHostState.showSnackbar(
                    message = "ERROR: Character device has disappeared since the app was started.",
                    actionLabel = "RECREATE",
                )
                when (result) {
                    SnackbarResult.ActionPerformed -> {
                        mainViewModel.createCharacterDevices()
                    }

                    SnackbarResult.Dismissed -> {}
                }
            } else if (uiState.isCharacterDevicePermissionsBroken != null) {
                val characterDevicePath = uiState.isCharacterDevicePermissionsBroken!!
                val result = snackbarHostState.showSnackbar(
                    message = "ERROR: Character device permissions seem incorrect.",
                    actionLabel = "FIX",
                )
                when (result) {
                    SnackbarResult.ActionPerformed -> {
                        mainViewModel.fixCharacterDevicePermissions(characterDevicePath)
                    }

                    SnackbarResult.Dismissed -> {}
                }
            }
        }
    }
}

private typealias MenuItem = Pair<Screen, String>

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainTopBar() {
    val navigator = LocalNavigator.currentOrThrow
    var showDropdownMenu by remember { mutableStateOf(false) }

    BasicTopBar(
        title = stringResource(R.string.app_name),
        actions = {
            DirectInputIconButton()
            IconButton(onClick = { showDropdownMenu = true }) {
                Icon(
                    imageVector = Icons.Outlined.MoreVert,
                    contentDescription = "Overflow Menu",
                )
                DropdownMenu(
                    expanded = showDropdownMenu,
                    onDismissRequest = { showDropdownMenu = false }
                ) {
                    val menuItems = arrayOf(
                        MenuItem(SettingsScreen(), stringResource(R.string.settings)),
                        MenuItem(TroubleshootingScreen(), stringResource(R.string.troubleshooting_title)),
                        MenuItem(HelpScreen(), stringResource(R.string.help)),
                        MenuItem(InfoScreen(), stringResource(R.string.info))
                    )
                    for (item in menuItems) {
                        DropdownMenuItem(
                            text = { Text(item.second) },
                            onClick = {
                                // Navigate to screen (safely)
                                //
                                // NOTE:
                                //  Extra code here is necessary because the user can spam click the DropdownMenuItem
                                //  before the navigation has completed. This would lead to it trying to navigate to the
                                //  same screen twice. As of right now, Voyager will crash if this happens without you
                                //  setting unique keys in every Screen. However, even after fixing that, being able
                                //  to navigate to the same screen multiple times is undesirable. For this reason, I have
                                //  added extra code that makes sure the given subclass of Screen isn't already present
                                //  in the navigation stack before we navigate.

                                val thisScreen = item.first

                                // Ensure that the Screen we're about to push isn't already in the navigation stack
                                // iterates in reverse because it's more likely for the duplicate item to be at the end
                                for (screen in navigator.items.reversed()) {
                                    if (screen::class == thisScreen::class) {
                                        return@DropdownMenuItem
                                    }
                                }

                                // Navigate to screen
                                navigator.push(thisScreen)
                                showDropdownMenu = false
                            }
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun CreateCharDevicesAlertDialog(showAlert: MutableState<Boolean>, mainViewModel: MainViewModel = viewModel()) {
    AlertDialog(
        title = { Text("Character device(s) do not exist") },
        text = { Text("Add HID functions to the default USB gadget? This must be re-done after every reboot.\n\n**The app will not work if you decline**") },
        confirmButton = {
            TextButton(
                content = { Text("YES") },
                onClick = {
                    mainViewModel.createCharacterDevices()
                    showAlert.value = false
                }
            )
        },
        dismissButton = {
            TextButton(
                content = { Text("NO") },
                onClick = {
                    showAlert.value = false
                }
            )
        },
        onDismissRequest = {
            // Intentionally blocking dialog dismissal here since I want the user to make a conscious decision
        }
    )
}

@DarkLightModePreviews
@Composable
private fun MainScreenPreview() {
    Navigator(MainScreen())
}
