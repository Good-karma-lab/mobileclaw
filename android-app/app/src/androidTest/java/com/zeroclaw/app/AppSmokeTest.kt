package com.zeroclaw.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppSmokeTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun appLoadsMainTabs() {
        rule.onNodeWithText("Chat").assertExists()
        rule.onNodeWithText("LLM").assertExists()
        rule.onNodeWithText("Device").assertExists()
    }
}
