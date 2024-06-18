package me.arianb.usb_hid_client.settings

import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import me.arianb.usb_hid_client.MainViewModel
import me.arianb.usb_hid_client.ui.theme.BasicPage
import me.arianb.usb_hid_client.ui.theme.DarkLightModePreviews
import me.arianb.usb_hid_client.ui.theme.SimpleNavTopBar

// FIXME: implement

class GadgetConfigurationScreen : Screen {
    // expected configuration options
    //  - change which gadget to add HID functions to (with option to create new gadget)
    //  - create/remove/re-create gadget
    //  - show and export debugging info or logs
    //
    // that's actually not that many things. maybe it should be a settings section, not a whole page.

    @Composable
    override fun Content() {
        GadgetConfigurationPage()
    }
}

@Composable
fun GadgetConfigurationPage(mainViewModel: MainViewModel = viewModel()) {
    BasicPage(
        topBar = { GadgetConfigurationTopBar() },
    ) {
        Button(onClick = { mainViewModel.deleteCharacterDevices() }) {
            Text(text = "Delete Character Devices")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GadgetConfigurationTopBar() {
    SimpleNavTopBar(
        title = "HID Gadget Configuration"
    )
}

@DarkLightModePreviews
@Composable
fun GadgetConfigurationScreenPreview() {
    Navigator(GadgetConfigurationScreen())
}
