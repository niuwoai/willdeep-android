package com.willdeep.android.ui

import com.willdeep.android.mobile.GatewayEnvelope
import org.json.JSONObject

internal fun composerMessageSubmission(
    sessionId: String?,
    workspacePath: String?,
    isResponding: Boolean,
    payload: JSONObject,
): GatewayEnvelope {
    // An explicit session always owns the message; workspace is only for a new conversation.
    if (sessionId != null) payload.remove("workspace_path")
    else workspacePath?.let { payload.put("workspace_path", it) }
    if (isResponding && sessionId != null) {
        return GatewayEnvelope(
            type = "queue.update",
            sessionId = sessionId,
            payload = payload.put("action", "add"),
        )
    }
    return GatewayEnvelope(
        type = "message.send",
        sessionId = sessionId,
        payload = payload,
    )
}
