package com.paifa.ubikitouch.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun mainScreenShowsPrimaryControls() {
        val activity = composeRule.activity

        composeRule.onNodeWithText("ubikiTouch").assertIsDisplayed()
        composeRule.onNodeWithText(activity.getString(R.string.service_title)).assertIsDisplayed()
        composeRule.onNodeWithText(activity.getString(R.string.global_controls_title)).assertIsDisplayed()
        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasText(activity.getString(R.string.left_edge)))
        composeRule.onNodeWithText(activity.getString(R.string.left_edge)).assertIsDisplayed()
    }

    @Test
    fun mainScreenContainsScrmSettingsPanel() {
        val activity = composeRule.activity

        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasText(activity.getString(R.string.scrm_settings_title)))
        composeRule.onNodeWithText(activity.getString(R.string.scrm_settings_title))
            .assertIsDisplayed()
    }
}
