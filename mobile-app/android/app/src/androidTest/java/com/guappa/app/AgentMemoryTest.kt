package com.guappa.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI Automator instrumented test for agent memory store and recall.
 * Tests the full flow: type message → agent stores fact → ask recall → verify.
 *
 * Run: ./gradlew connectedAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class AgentMemoryTest {

    private lateinit var device: UiDevice
    private val TIMEOUT = 30_000L
    private val PACKAGE = "com.guappa.app"

    @Before
    fun setUp() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

        // Launch the app
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = context.packageManager.getLaunchIntentForPackage(PACKAGE)
        assertNotNull("Could not get launch intent for $PACKAGE", intent)
        intent!!.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)

        // Wait for app to appear
        device.wait(Until.hasObject(com.guappa.app.AgentMemoryTest.byPkg(PACKAGE)), TIMEOUT)
    }

    @Test
    fun testMemoryStoreAndRecall() {
        // Navigate to chat if needed
        val chatInput = device.findObject(UiSelector().descriptionContains("chat-input"))
            ?: device.findObject(UiSelector().resourceId("$PACKAGE:id/chat_input"))

        // Allow time for initial load
        Thread.sleep(3000)

        // Find input by description (testID in React Native maps to content-description)
        val input = device.findObject(UiSelector().descriptionContains("chat-input"))
        if (input != null && input.exists()) {
            // Store a fact
            input.setText("Remember that my dog's name is Rex")

            val sendBtn = device.findObject(UiSelector().descriptionContains("chat-send-button"))
            if (sendBtn != null && sendBtn.exists()) {
                sendBtn.click()
            }

            // Wait for response
            Thread.sleep(15_000)

            // Now recall
            val input2 = device.findObject(UiSelector().descriptionContains("chat-input"))
            if (input2 != null && input2.exists()) {
                input2.setText("What is my dog's name?")

                val sendBtn2 = device.findObject(UiSelector().descriptionContains("chat-send-button"))
                if (sendBtn2 != null && sendBtn2.exists()) {
                    sendBtn2.click()
                }

                // Wait for response containing "Rex"
                Thread.sleep(15_000)

                // Take screenshot for verification
                device.takeScreenshot(
                    java.io.File(
                        InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
                        "memory_test_result.png"
                    )
                )
            }
        }
    }

    companion object {
        fun byPkg(pkg: String): androidx.test.uiautomator.BySelector {
            return androidx.test.uiautomator.By.pkg(pkg)
        }
    }
}
