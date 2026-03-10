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
 * Tests permission flow on first launch.
 * Handles Android permission dialogs that appear on first run.
 */
@RunWith(AndroidJUnit4::class)
class PermissionFlowTest {

    private lateinit var device: UiDevice
    private val TIMEOUT = 15_000L
    private val PACKAGE = "com.guappa.app"

    @Before
    fun setUp() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    }

    @Test
    fun testHandlePermissionDialogs() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = context.packageManager.getLaunchIntentForPackage(PACKAGE)
        assertNotNull(intent)
        intent!!.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)

        device.wait(Until.hasObject(androidx.test.uiautomator.By.pkg(PACKAGE)), TIMEOUT)

        // Handle any permission dialogs that appear
        for (i in 0..5) {
            Thread.sleep(2000)

            // Try different permission dialog buttons
            val allowBtn = device.findObject(UiSelector().textContains("Allow"))
                ?: device.findObject(UiSelector().textContains("ALLOW"))
                ?: device.findObject(UiSelector().resourceId("com.android.permissioncontroller:id/permission_allow_button"))

            if (allowBtn != null && allowBtn.exists()) {
                allowBtn.click()
                Thread.sleep(500)
            }

            // Handle "While using the app" option
            val whileUsing = device.findObject(UiSelector().textContains("While using"))
            if (whileUsing != null && whileUsing.exists()) {
                whileUsing.click()
                Thread.sleep(500)
            }
        }

        // Verify we got past permissions to the main app
        Thread.sleep(3000)
        val hasApp = device.findObject(UiSelector().packageName(PACKAGE))
        assertTrue("App should be visible after permissions", hasApp != null && hasApp.exists())
    }
}
