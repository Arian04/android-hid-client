package me.arianb.usb_hid_client

import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults.pinnedScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.widget.TextViewCompat
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import me.arianb.usb_hid_client.ui.theme.BasicPage
import me.arianb.usb_hid_client.ui.theme.DarkLightModePreviews
import me.arianb.usb_hid_client.ui.theme.PaddingExtraLarge
import me.arianb.usb_hid_client.ui.theme.PaddingNone
import me.arianb.usb_hid_client.ui.theme.PaddingNormal
import me.arianb.usb_hid_client.ui.theme.PaddingSmall
import me.arianb.usb_hid_client.ui.theme.SimpleNavTopBar


class HelpScreen : Screen {
    @Composable
    override fun Content() {
        HelpPage()
    }
}

@Composable
fun HelpPage() {
    val questionAnswerPairs = remember {
        arrayOf(
            Pair(R.string.help_faq_q1, R.string.help_faq_a1),
            Pair(R.string.help_faq_q2, R.string.help_faq_a2),
            Pair(R.string.help_faq_q3, R.string.help_faq_a3)
        )
    }

    BasicPage(
        topBar = { HelpTopBar() },
        paddingAll = PaddingNormal,
        verticalArrangement = Arrangement.spacedBy(PaddingNone, Alignment.Top),
    ) {
        for (pair in questionAnswerPairs) {
            key(pair) {
                QuestionAnswer(pair.first, pair.second)
                Spacer(Modifier.height(PaddingExtraLarge))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpTopBar() {
    SimpleNavTopBar(
        title = stringResource(R.string.help),
        scrollBehavior = pinnedScrollBehavior()
    )
}

@Composable
fun QuestionAnswer(questionResource: Int, answerResource: Int) {
    Text(
        text = stringResource(questionResource),
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Bold
    )
    Spacer(Modifier.height(PaddingSmall))
    ComposeTextView(
        id = answerResource,
    )
}

/**
 * Helper Composable created due to how painful it is to work with hyperlinks
 * in Compose without a View.
 *
 * NOTE: only a few TextStyle properties work here
 */
@Composable
fun ComposeTextView(@StringRes id: Int, modifier: Modifier = Modifier) {
    val style = MaterialTheme.typography.bodyLarge

    val text = LocalContext.current.resources.getText(id)

    AndroidView(
        modifier = modifier,
        factory = { context ->
            TextView(context).apply {
                movementMethod = LinkMovementMethod.getInstance()

                textSize = style.fontSize.value

                if (style.lineHeight.isSp) {
                    TextViewCompat.setLineHeight(this, TypedValue.COMPLEX_UNIT_SP, style.lineHeight.value)
                } else {
                    // TODO: handle the case when it's em if i want
                }
            }
        },
        update = {
            it.text = text
        }
    )
}


@DarkLightModePreviews
@Composable
fun HelpScreenPreview() {
    Navigator(HelpScreen())
}
