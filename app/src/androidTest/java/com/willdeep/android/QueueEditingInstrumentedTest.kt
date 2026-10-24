package com.willdeep.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.willdeep.android.mobile.GatewayQueuedMessage
import com.willdeep.android.ui.ConnectionStatus
import com.willdeep.android.ui.MobileGatewayUiState
import com.willdeep.android.ui.QueueCard
import com.willdeep.android.ui.QueueEditDialog
import com.willdeep.android.ui.QueueEditState
import com.willdeep.android.ui.afterReply
import com.willdeep.android.ui.theme.WillDeepTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QueueEditingInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val message = GatewayQueuedMessage("q", "preview", 1, 0, "s", "完整原文\nsecond line")

    @Test fun editButtonOpensCompleteTextAndWaitsForConfirmation() {
        var edit by mutableStateOf<QueueEditState?>(null)
        compose.setContent {
            WillDeepTheme {
                QueueCard(MobileGatewayUiState(status = ConnectionStatus.Connected, queuedMessages = listOf(message)),
                    onSendNow = {}, onRemove = {}, onClear = {}, onEdit = { edit = QueueEditState(it, it.text!!) })
                edit?.let { current ->
                    QueueEditDialog(current, true, onChange = { edit = current.copy(text = it) },
                        onSave = { edit = current.copy(commandId = "save") }, onDismiss = { edit = null })
                }
            }
        }
        compose.onNodeWithText(context.getString(R.string.queue_edit_button)).performClick()
        compose.onNodeWithText(message.text!!).assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performTextReplacement("修改后的内容")
        compose.onNodeWithText(context.getString(R.string.queue_edit_save)).performClick()
        compose.onNodeWithText(context.getString(R.string.queue_edit_saving)).assertIsNotEnabled()
        compose.runOnIdle { assertEquals("修改后的内容", edit?.text); edit = edit.afterReply("save") }
        compose.onNodeWithText(context.getString(R.string.queue_edit_title)).assertDoesNotExist()
    }

    @Test fun rejectedEditKeepsDraftAndAllowsAnotherSave() {
        var edit by mutableStateOf<QueueEditState?>(QueueEditState(message, "保留的草稿", commandId = "save"))
        compose.setContent {
            WillDeepTheme {
                edit?.let { current ->
                    QueueEditDialog(current, true, onChange = { edit = current.copy(text = it) },
                        onSave = {}, onDismiss = { edit = null })
                }
            }
        }
        compose.runOnIdle { edit = edit.afterReply("save", "snapshot changed") }
        compose.onNodeWithText("保留的草稿").assertIsDisplayed()
        compose.onNodeWithText("snapshot changed").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.queue_edit_cancel)).performClick()
        compose.onNodeWithText(context.getString(R.string.queue_edit_title)).assertDoesNotExist()
    }
}
