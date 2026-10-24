package com.willdeep.android.ui

import com.willdeep.android.mobile.GatewayEvent

internal fun MobileGatewayUiState.applyCreatedSession(event: GatewayEvent.SessionUpsert): MobileGatewayUiState {
    val isCreatedSession = pendingSessionCreateId != null && event.commandId == pendingSessionCreateId
    if (isCreatedSession) {
        return clearCapabilities().copy(
            selectedSessionId = event.session.id,
            pendingSessionSelectionId = event.session.id,
            pendingSessionCreateId = null,
            createdSessionId = event.session.id,
            preferredWorkspacePath = "",
            conversationMessages = emptyList(),
            commandStatuses = commandStatuses.markCommandAccepted(event.commandId, "session.create"),
        )
    }
    if (pendingSessionCreateId != null || pendingSessionSelectionId != null) return this
    return copy(
        selectedSessionId = if (selectedSessionId == null || event.session.isActive) event.session.id else selectedSessionId,
    )
}
