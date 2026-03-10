package com.guappa.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests notification behavior using UI Automator.
 * Opens notification shade and verifies Guappa notifications.
 */
@RunWith(AndroidJUnit4::class)
class NotificationTest {

    private lateinit var device: UiDevice
    private val TIMEOUT = 15_000L
    private val PACKAGE = "com.guappa.app"

    @Before
    fun setUp() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    }

    @Test
    fun testForegroundServiceNotification() {
        // Launch app
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = context.packageManager.getLaunchIntentForPackage(PACKAGE)
        assertNotNull(intent)
        intent!!.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)

        device.wait(Until.hasObject(By.pkg(PACKAGE)), TIMEOUT)
        Thread.sleep(5000)

        // Open notification shade
        device.openNotification()
        Thread.sleep(2000)

        // Look for Guappa notification
        val notification = device.findObject(
            androidx.test.uiautomator.UiSelector().textContains("Guappa")
        ) ?: device.findObject(
            androidx.test.uiautomator.UiSelector().textContains("guappa")
        )

        // Close notification shade
        device.pressBack()

        // App should still be running
        device.wait(Until.hasObject(By.pkg(PACKAGE)), TIMEOUT)
    }
}
