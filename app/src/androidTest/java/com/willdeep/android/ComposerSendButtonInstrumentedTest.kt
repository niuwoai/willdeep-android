package com.willdeep.android

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.willdeep.android.ui.ComposerSendButton
import com.willdeep.android.ui.theme.WillDeepTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ComposerSendButtonInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun busyComposerClickSubmitsInsteadOfStopping() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var submissions = 0
        composeRule.setContent {
            WillDeepTheme {
                ComposerSendButton(canSend = true, isResponding = true) { submissions++ }
            }
        }
        composeRule.onNodeWithContentDescription(context.getString(R.string.composer_queue_button)).performClick()
        composeRule.onNodeWithContentDescription(context.getString(R.string.stop_turn_button)).assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(1, submissions) }
    }

    @Test
    fun busyComposerWithoutDraftCannotSubmit() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.setContent {
            WillDeepTheme { ComposerSendButton(canSend = false, isResponding = true) {} }
        }
        composeRule.onNodeWithContentDescription(context.getString(R.string.composer_queue_button)).assertIsNotEnabled()
    }
}
