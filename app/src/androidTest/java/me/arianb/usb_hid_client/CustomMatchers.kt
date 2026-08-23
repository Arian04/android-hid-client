package me.arianb.usb_hid_client

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.test.ext.junit.rules.ActivityScenarioRule

fun AndroidComposeTestRule<ActivityScenarioRule<ComponentActivity>, ComponentActivity>.onAllClickableNodes() =
    onAllNodes(hasClickAction())

fun AndroidComposeTestRule<ActivityScenarioRule<ComponentActivity>, ComponentActivity>.onClickableNode() =
    onNode(hasClickAction())

fun AndroidComposeTestRule<ActivityScenarioRule<ComponentActivity>, ComponentActivity>.onEditableNode() =
    onNode(hasSetTextAction())