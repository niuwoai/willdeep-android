package com.willdeep.android.ui

import com.willdeep.android.mobile.GatewayEvent
import com.willdeep.android.mobile.GatewayMessage
import com.willdeep.android.mobile.parseGatewayEvent
import org.junit.Assert.*
import org.junit.Test

class SessionCreationStateTest {
    private fun response(commandId: String) = parseGatewayEvent(
        """{"id":"$commandId","type":"session.upsert","payload":{"session":{"id":"new","title":"New","workspace_path":"/chosen","is_active":true}}}"""
    ) as GatewayEvent.SessionUpsert

    @Test
    fun matchingResponseSelectsNewSessionAndDiscardsOldConversation() {
        val oldMessage = GatewayMessage("m", "assistant", "old", "", "old")
        val initial = MobileGatewayUiState(
            selectedSessionId = "old", pendingSessionCreateId = "create-1",
            preferredWorkspacePath = "/chosen", conversationMessages = listOf(oldMessage),
        )
        val next = initial.applyCreatedSession(response("create-1"))
        assertEquals("/chosen", response("create-1").session.workspacePath)
        assertEquals("new", next.selectedSessionId)
        assertEquals("new", next.createdSessionId)
        assertEquals("new", next.pendingSessionSelectionId)
        assertNull(next.pendingSessionCreateId)
        assertTrue(next.conversationMessages.isEmpty())
        assertEquals("", next.preferredWorkspacePath)
    }

    @Test
    fun unrelatedActiveSessionCannotCompleteCreation() {
        val initial = MobileGatewayUiState(selectedSessionId = "old", pendingSessionCreateId = "create-1")
        assertEquals(initial, initial.applyCreatedSession(response("heartbeat")))
    }

    @Test
    fun pendingSelectionCannotBeOverriddenByAnotherActiveSession() {
        val initial = MobileGatewayUiState(selectedSessionId = "chosen", pendingSessionSelectionId = "chosen")
        assertEquals(initial, initial.applyCreatedSession(response("other")))
    }

    @Test
    fun internalMessagesAreHiddenWhileActualConversationRemainsVisible() {
        for (role in listOf("system", "developer", "tool", "SYSTEM")) {
            assertFalse(GatewayMessage("m", role, "internal instruction", "", "s").hasDisplayableBody())
        }
        for (role in listOf("user", "assistant")) {
            assertTrue(GatewayMessage("m", role, "system is a word in ordinary conversation", "", "s").hasDisplayableBody())
        }
        assertTrue(GatewayMessage("m", "user", "", "", "s", imageUrls = listOf("image")).hasDisplayableBody())
    }
}
