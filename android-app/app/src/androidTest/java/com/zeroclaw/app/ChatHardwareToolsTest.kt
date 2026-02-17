package com.zeroclaw.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatHardwareToolsTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun chatCanAskAgentForHardwareTools() {
        val prompt = "List all available hardware tools you can use in this build. Include android_device in the answer."

        rule.onNodeWithText("Message").performTextInput(prompt)
        rule.onNodeWithText("Send").performClick()

        rule.waitUntil(timeoutMillis = 180_000) {
            runCatching {
                rule.onNodeWithText("android_device", substring = true).assertExists()
            }.isSuccess
        }
    }
}
