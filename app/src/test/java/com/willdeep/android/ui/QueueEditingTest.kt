package com.willdeep.android.ui

import com.willdeep.android.mobile.GatewayEvent
import com.willdeep.android.mobile.GatewayQueuedMessage
import com.willdeep.android.mobile.parseGatewayEvent
import org.junit.Assert.*
import org.junit.Test
import org.junit.Assume.assumeTrue
import java.io.File

class QueueEditingTest {
    private val original = "中文\n日本語\n" + "long text ".repeat(100)
    private val message = GatewayQueuedMessage("q", "short preview", 1, 2, "owning-session", original)

    @Test fun editUsesCompleteOriginalAndOwnSessionWithoutReplacingAttachments() {
        val submission = QueueEditState(message, "edited").submission()!!
        assertEquals("owning-session", submission.sessionId)
        assertEquals("edit", submission.payload.getString("action"))
        assertEquals("q", submission.payload.getString("message_id"))
        assertEquals(original, submission.payload.getString("expected_text"))
        assertEquals("edited", submission.payload.getString("text"))
        assertFalse(submission.payload.has("images"))
    }

    @Test fun editCanLeaveAttachmentOnlyMessageButRejectsEmptyTextOnlyMessage() {
        assertNotNull(QueueEditState(message, "").submission())
        assertNull(QueueEditState(message.copy(imageCount = 0, textAttachmentCount = 0), " ").submission())
    }

    @Test fun pendingEditCannotBeSubmittedTwiceAndFailureKeepsDraft() {
        val edit = QueueEditState(message, "my draft", commandId = "save")
        assertNull(edit.submission())
        assertEquals(edit, edit.afterReply("unrelated"))
        val failed = edit.afterReply("save", "stale queue")!!
        assertEquals("my draft", failed.text)
        assertEquals(original, failed.message.text)
        assertEquals("stale queue", failed.error)
        assertNotNull(failed.submission())
        assertNull(edit.afterReply("save"))
    }

    @Test fun legacyTruncatedQueueCannotBeEdited() {
        assertNull(QueueEditState(message.copy(text = null), "partial preview").submission())
    }

    @Test fun rejectedQueueOperationKeepsConnectionAndAuthoritativeMessages() {
        val state = MobileGatewayUiState(status = ConnectionStatus.Connected,
            queuedMessages = listOf(message), queueEdit = QueueEditState(message, "draft", "save"),
            commandStatuses = listOf(MobileCommandStatus("save", "queue.update", MobileCommandState.Pending)))
        val rejected = state.queueCommandFailed("save", "stale snapshot")
        assertEquals(ConnectionStatus.Connected, rejected.status)
        assertEquals(listOf(message), rejected.queuedMessages)
        assertEquals("draft", rejected.queueEdit?.text)
        assertEquals(MobileCommandState.Failed, rejected.commandStatuses.single().state)
    }

    @Test fun snapshotReadsFullTextAndDisablesUnsafeLegacyEditing() {
        val event = parseGatewayEvent("""{"type":"state.snapshot","payload":{"queued_messages":[
            {"id":"q","session_id":"s","text_preview":"preview","text":"完整正文","image_count":1},
            {"id":"legacy","session_id":"s","text_preview":"shortened"},
            {"id":"unsafe","session_id":"s","text":"","can_edit":false}
        ]}}""") as GatewayEvent.Snapshot
        assertEquals("完整正文", event.queuedMessages[0].text)
        assertNull(event.queuedMessages[1].text)
        assertNull(event.queuedMessages[2].text)
    }

    @Test fun actualRustAndMacSnapshotsProduceCompatibleEditCommands() {
        val paths = listOf(System.getenv("WILLDEEP_QUEUE_RUST_FIXTURE"), System.getenv("WILLDEEP_QUEUE_MAC_FIXTURE"))
        assumeTrue("Real host snapshots must be supplied for cross-platform acceptance", paths.all { it != null })
        for (path in paths) {
            val event = parseGatewayEvent(File(path!!).readText()) as GatewayEvent.Snapshot
            val message = event.queuedMessages.single { it.text != null }
            assertTrue(message.text!!.length > message.textPreview.length)
            assertEquals(1, message.imageCount)
            val edit = QueueEditState(message, "手机编辑\nAndroid updated").submission()!!
            assertEquals(message.sessionId, edit.sessionId)
            assertEquals(message.id, edit.payload.getString("message_id"))
            assertEquals(message.text, edit.payload.getString("expected_text"))
        }
    }
}
