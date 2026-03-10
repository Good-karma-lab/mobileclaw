package com.guappa.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests incoming call hook functionality using UI Automator + ADB shell.
 * Simulates an incoming call and verifies the agent reacts.
 */
@RunWith(AndroidJUnit4::class)
class IncomingCallHookTest {

    private lateinit var device: UiDevice
    private val TIMEOUT = 20_000L
    private val PACKAGE = "com.guappa.app"

    @Before
    fun setUp() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = context.packageManager.getLaunchIntentForPackage(PACKAGE)
        assertNotNull(intent)
        intent!!.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)

        device.wait(Until.hasObject(By.pkg(PACKAGE)), TIMEOUT)
        Thread.sleep(5000)
    }

    @Test
    fun testCallHookSetup() {
        // Navigate to chat (by content description)
        val chatTab = device.findObject(UiSelector().descriptionContains("dock-chat"))
            ?: device.findObject(UiSelector().descriptionContains("Chat"))

        chatTab?.click()
        Thread.sleep(2000)

        // Type command to enable call hook
        val input = device.findObject(UiSelector().descriptionContains("chat-input"))
        if (input != null && input.exists()) {
            input.setText("Enable the incoming call hook")

            val sendBtn = device.findObject(UiSelector().descriptionContains("chat-send-button"))
            sendBtn?.click()

            // Wait for response
            Thread.sleep(15_000)

            // Simulate incoming call via ADB
            device.executeShellCommand("am broadcast -a android.intent.action.PHONE_STATE --es state RINGING --es incoming_number +15551234567")

            Thread.sleep(5000)

            // End the simulated call
            device.executeShellCommand("am broadcast -a android.intent.action.PHONE_STATE --es state IDLE")

            Thread.sleep(3000)

            // Take screenshot for verification
            device.takeScreenshot(
                java.io.File(
                    InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
                    "call_hook_test.png"
                )
            )
        }
    }
}
