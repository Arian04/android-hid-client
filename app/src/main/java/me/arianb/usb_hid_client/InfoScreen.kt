package me.arianb.usb_hid_client

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import me.arianb.usb_hid_client.ui.theme.BasicPage
import me.arianb.usb_hid_client.ui.theme.DarkLightModePreviews
import me.arianb.usb_hid_client.ui.theme.SimpleNavTopBar

class InfoScreen : Screen {
    @Composable
    override fun Content() {
        InfoPage()
    }
}

@Composable
fun InfoPage() {
    BasicPage(
        topBar = { InfoTopBar() },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = stringResource(R.string.info_icon_description)
        )
        AppDescriptionText()
        AppSourceCodeButton()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoTopBar() {
    SimpleNavTopBar(
        title = stringResource(id = R.string.info)
    )
}

@Composable
fun AppDescriptionText() {
    Text(
        text = stringResource(R.string.info_description),
        textAlign = TextAlign.Center
    )
}

@Composable
fun AppSourceCodeButton() {
    val uriHandler = LocalUriHandler.current
    val sourceCodeURL = stringResource(R.string.info_source_code_url)

    Button(
        onClick = { uriHandler.openUri(sourceCodeURL) },
    ) {
        Image(
            painter = painterResource(R.drawable.ic_github),
            contentDescription = "Github icon",
            modifier = Modifier.padding(end = 16.dp),
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primaryContainer)
        )
        Text(text = "Source Code")
    }
}

@DarkLightModePreviews
@Composable
fun InfoScreenPreview() {
    Navigator(InfoScreen())
}
