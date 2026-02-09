package me.arianb.usb_hid_client.ui.standalone_screens

import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults.pinnedScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.widget.TextViewCompat
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import me.arianb.usb_hid_client.R
import me.arianb.usb_hid_client.ui.theme.PaddingNone
import me.arianb.usb_hid_client.ui.theme.PaddingNormal
import me.arianb.usb_hid_client.ui.theme.PaddingSmall
import me.arianb.usb_hid_client.ui.utils.BasicPage
import me.arianb.usb_hid_client.ui.utils.DarkLightModePreviews
import me.arianb.usb_hid_client.ui.utils.SimpleNavTopBar
import me.arianb.usb_hid_client.ui.utils.getColorByTheme

class HelpScreen : Screen {
    @Composable
    override fun Content() {
        HelpPage()
    }
}

private data class FaqItem(
    @StringRes val titleResource: Int,
    @StringRes val textResource: Int,
    val hasHyperLink: Boolean = false,
)

@Composable
fun HelpPage() {
    val faqItems = remember {
        arrayOf(
            FaqItem(R.string.help_faq_q1, R.string.help_faq_a1),
            FaqItem(R.string.help_faq_q2, R.string.help_faq_a2),
            FaqItem(R.string.help_faq_q3, R.string.help_faq_a3),
            FaqItem(R.string.help_faq_q4, R.string.help_faq_a4),
            FaqItem(R.string.help_faq_q5, R.string.help_faq_a5, hasHyperLink = true),
            FaqItem(R.string.help_faq_q6, R.string.help_faq_a6, hasHyperLink = true),
        )
    }

    BasicPage(
        topBar = { HelpTopBar() },
        padding = PaddingValues(all = PaddingNormal),
        verticalArrangement = Arrangement.spacedBy(PaddingNone, Alignment.Top),
        scrollable = true,
    ) {
        for (item in faqItems) {
            key(item) {
                ExpandableText(
                    item.titleResource,
                    item.textResource,
                    useLegacyTextViewForText = item.hasHyperLink
                )

                HorizontalDivider(
                    modifier = Modifier.padding(PaddingSmall),
                    thickness = Dp.Hairline
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HelpTopBar() {
    SimpleNavTopBar(
        title = stringResource(R.string.help),
        scrollBehavior = pinnedScrollBehavior()
    )
}

@Composable
fun ExpandableText(
    @StringRes titleResource: Int,
    @StringRes textResource: Int,
    useLegacyTextViewForText: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }
    val degrees by animateFloatAsState(if (expanded) 180f else 0f)
    Column {
        Row(
            modifier = Modifier
                .clip(MaterialTheme.shapes.large)
                .clickable { expanded = !expanded }
                .fillMaxWidth()
                .padding(PaddingNormal),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                stringResource(titleResource),

                // Stops the icon below from being hidden if text overflows the line
                modifier = Modifier.weight(
                    weight = 1f,
                    fill = false
                ),
            )
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier
                    .rotate(degrees),
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(
                spring(
                    visibilityThreshold = IntSize.VisibilityThreshold
                )
            ),
            exit = shrinkVertically()
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(PaddingNormal)
            ) {
                if (useLegacyTextViewForText) {
                    ComposeTextView(
                        textResource,
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    Text(
                        stringResource(textResource),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

/**
 * Helper Composable created due to how painful it is to work with hyperlinks
 * in Compose without a View.
 *
 * NOTE: only a few TextStyle properties work here
 */
@Composable
fun ComposeTextView(
    @StringRes id: Int,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val text = LocalContext.current.resources.getText(id)
    val textColor = getColorByTheme()

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
            if (textColor != null) {
                it.setTextColor(textColor)
            }
        }
    )
}

@DarkLightModePreviews
@Composable
private fun HelpScreenPreview() {
    Navigator(HelpScreen())
}
