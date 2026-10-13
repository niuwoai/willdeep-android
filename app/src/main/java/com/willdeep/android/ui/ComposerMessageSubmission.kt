package com.willdeep.android.ui

import com.willdeep.android.mobile.GatewayEnvelope
import org.json.JSONObject

internal fun composerMessageSubmission(
    sessionId: String?,
    workspacePath: String?,
    isResponding: Boolean,
    payload: JSONObject,
): GatewayEnvelope {
    if (isResponding && sessionId != null) {
        return GatewayEnvelope(
            type = "queue.update",
            sessionId = sessionId,
            payload = payload.put("action", "add"),
        )
    }
    return GatewayEnvelope(
        type = "message.send",
        sessionId = sessionId.takeIf { workspacePath == null },
        payload = payload,
    )
}
