package me.arianb.usb_hid_client.unit_ui

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import me.arianb.usb_hid_client.R
import me.arianb.usb_hid_client.ui.standalone_screens.ExpandableText
import org.junit.Rule
import org.junit.Test

class ExpandableTextTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun bodyText_isNotShown_untilTitleRowIsClicked() {
        val bodyText = context.getString(BODY_TEXT_RESOURCE)

        setContent()

        composeTestRule.onNodeWithText(bodyText).assertDoesNotExist()
    }

    @Test
    fun bodyText_becomesVisible_afterClickingTitleRow() {
        val titleText = context.getString(TITLE_RESOURCE)
        val bodyText = context.getString(BODY_TEXT_RESOURCE)

        setContent()

        composeTestRule.onNodeWithText(titleText).performClick()

        composeTestRule.onNodeWithText(bodyText).assertIsDisplayed()
    }

    @Test
    fun bodyText_isHiddenAgain_afterClickingTitleRowTwice() {
        val titleText = context.getString(TITLE_RESOURCE)
        val bodyText = context.getString(BODY_TEXT_RESOURCE)

        setContent()

        composeTestRule.onNodeWithText(titleText).performClick() // expand
        composeTestRule.onNodeWithText(titleText).performClick() // collapse again

        composeTestRule.onNodeWithText(bodyText).assertDoesNotExist()
    }

    private fun setContent() {
        composeTestRule.setContent {
            MaterialTheme {
                ExpandableText(
                    titleResource = TITLE_RESOURCE,
                    textResource = BODY_TEXT_RESOURCE,
                )
            }
        }
    }

    companion object {
        // Arbitrarily chosen String resources used for these tests
        private val TITLE_RESOURCE = R.string.direct_input
        private val BODY_TEXT_RESOURCE = R.string.experimental_mode_summary
    }
}