package com.willdeep.android.ui

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class ComposerMessageSubmissionTest {
    @Test
    fun busySubmissionQueuesTextAndAttachmentsToTheCurrentSession() {
        val payload = JSONObject().put("text", "next task")
            .put("images", JSONArray().put(JSONObject().put("type", "image_url")))
        val envelope = composerMessageSubmission("current", "/workspace", true, payload)
        assertEquals("queue.update", envelope.type)
        assertEquals("current", envelope.sessionId)
        assertEquals("add", envelope.payload.getString("action"))
        assertEquals("next task", envelope.payload.getString("text"))
        assertEquals(1, envelope.payload.getJSONArray("images").length())
    }

    @Test
    fun idleSubmissionSendsToExistingSession() {
        val envelope = composerMessageSubmission("current", null, false, JSONObject().put("text", "hello"))
        assertEquals("message.send", envelope.type)
        assertEquals("current", envelope.sessionId)
        assertEquals(false, envelope.payload.has("action"))
    }

    @Test
    fun selectedSessionWinsOverLeftoverWorkspacePath() {
        val envelope = composerMessageSubmission("new-session", "/chosen", false,
            JSONObject().put("text", "hello").put("workspace_path", "/chosen"))
        assertEquals("new-session", envelope.sessionId)
        assertEquals(false, envelope.payload.has("workspace_path"))
    }

    @Test
    fun submissionWithoutSessionKeepsChosenWorkspace() {
        val envelope = composerMessageSubmission(null, "/chosen", false, JSONObject().put("text", "hello"))
        assertEquals(null, envelope.sessionId)
        assertEquals("/chosen", envelope.payload.getString("workspace_path"))
    }
}
