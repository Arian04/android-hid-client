package me.arianb.usb_hid_client

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.topjohnwu.superuser.Shell
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

val MANUAL_INPUT_TEST_STRING = """
aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb
cccccccccccccccccccccccccccccccccccccccccccccccccc
11111111111111111111111111111111111111111111111111
22222222222222222222222222222222222222222222222222
33333333333333333333333333333333333333333333333333
testing with     5 spaces
testing with !@#$%^&*()_+ symbols
testing with "" '' "' "" ''' quotes
testing with \a\b\n escaped chars
""".trim()

@RunWith(AndroidJUnit4::class)
class MyInstrumentedTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        Assert.assertEquals(BuildConfig.APPLICATION_ID, appContext.packageName)
    }

    // NOTE: I know arbitrary delays during testing is generally bad, but since I need to make sure the app is properly
    //       sending inputs to the connected device, I can't easily just run this in an emulator. I also need to
    //       actually observe the behavior in real-time. That's why, for the foreseeable future, I won't be running this
    //       non-interactively. So I think it's okay to include delays that allow me to get a chance to make sure
    //       things look good.q
    @Test
    fun launchApp() {
        rule.setContent { Entrypoint() }
        rule.waitForIdle()

        // TODO: automate accepting magisk root prompt. Right now I just click it manually.
        // Trigger Magisk root permissions prompt, returning if we don't have root permissions
        if (!Shell.getShell().isRoot) {
            return
        }

        // Continue past onboarding screen
        val continueButton = rule.onAllClickableNodes().filterToOne(hasText("Continue"))
        continueButton.performClick()
        rule.waitForIdle()

        // If "char devices are missing" alert dialog shows up, click "yes"
        if (rule.onNode(isDialog()).isDisplayed()) {
            rule.onNodeWithText("Yes", ignoreCase = true).performClick()
            delayTest()
        }

        testManualInput()
    }

    private fun testManualInput() {
        // Test manual input
        rule.onEditableNode().performTextInput(MANUAL_INPUT_TEST_STRING)
        delayTest()

        val sendButton = rule.onAllClickableNodes().filterToOne(hasText("Send"))
        sendButton.performClick()

        val fixPermissionsSnackbarNode = rule.onAllClickableNodes().filterToOne(hasText("fix", ignoreCase = true))
        if (fixPermissionsSnackbarNode.isDisplayed()) {
            // Wait until channel clears out the buffer of failing reports
            delayTest(5000)

            fixPermissionsSnackbarNode.performClick()
            rule.waitForIdle()
            sendButton.performClick()
        }

        delayTest(5000)
    }

    private fun delayTest(delayMillis: Long = 500) {
        rule.waitForIdle()

        val startTime = System.currentTimeMillis()
        rule.waitUntil(timeoutMillis = delayMillis + 5000) {
            val currentTime = System.currentTimeMillis()
            (currentTime - startTime) > delayMillis
        }
    }
}

fun AndroidComposeTestRule<ActivityScenarioRule<ComponentActivity>, ComponentActivity>.onAllClickableNodes() =
    onAllNodes(hasClickAction())

fun AndroidComposeTestRule<ActivityScenarioRule<ComponentActivity>, ComponentActivity>.onClickableNode() =
    onNode(hasClickAction())

fun AndroidComposeTestRule<ActivityScenarioRule<ComponentActivity>, ComponentActivity>.onEditableNode() =
    onNode(hasSetTextAction())
