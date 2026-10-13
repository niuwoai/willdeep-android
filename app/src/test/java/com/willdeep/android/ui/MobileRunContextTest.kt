package com.willdeep.android.ui

import com.willdeep.android.mobile.GatewayCapabilities
import com.willdeep.android.mobile.GatewayCapabilityOption
import com.willdeep.android.mobile.GatewayEvent
import com.willdeep.android.mobile.parseGatewayEvent
import org.junit.Assert.*
import org.junit.Test

class MobileRunContextTest {
    @Test
    fun understandsAllDesktopApprovalModesAndLegacyAliases() {
        assertEquals(4, ApprovalMode.entries.size)
        mapOf(
            "strict" to ApprovalMode.AskEveryTime,
            "requestEveryTime" to ApprovalMode.AskEveryTime,
            "smart" to ApprovalMode.SmartApproval,
            "workspace-write" to ApprovalMode.WorkspaceWritable,
            "workspaceAccess" to ApprovalMode.WorkspaceWritable,
            "full-access" to ApprovalMode.FullAccess,
            "silent" to ApprovalMode.FullAccess,
        ).forEach { (wire, mode) -> assertEquals(wire, mode, wire.toApprovalMode()) }
        assertNull("invalid".toApprovalMode())
        assertEquals(listOf("strict", "smart", "workspace-write", "full-access"), ApprovalMode.entries.map { it.gatewayValue() })
    }

    @Test
    fun parsesSessionScopedCatalogAndAppliesActualDesktopSelections() {
        val event = parseGatewayEvent("""{
            "id":"catalog", "type":"capabilities.updated", "session_id":"session-a",
            "payload": {
              "providers":[{"id":"alpha","title":"Alpha","is_active":true}],
              "models":[{"id":"model-a","title":"Model A","is_active":true}],
              "skills":[{"id":"skill-a","title":"Skill A","is_active":true}],
              "plugins":[], "experts":[], "active_provider_id":"alpha", "active_model_id":"model-a",
              "active_approval_mode":"full-access", "supports_configuration":true,
              "approval_modes":["strict","smart","workspace-write","full-access"], "models_status":"complete"
            }
        }""") as GatewayEvent.CapabilitiesUpdated
        assertEquals("session-a", event.sessionId)
        val applied = MobileGatewayUiState().applyCapabilities(event.capabilities)
        assertEquals(ApprovalMode.FullAccess, applied.approvalMode)
        assertTrue(applied.approvalModeReported)
        assertTrue(applied.supportsConfiguration)
        assertEquals("alpha", applied.selectedProviderId)
        assertEquals("model-a", applied.selectedModelId)
        assertEquals(setOf("skill-a"), applied.selectedSkillIds)
        val removed = applied.applyCapabilities(event.capabilities.copy(skills = listOf(
            GatewayCapabilityOption("skill-a", "Skill A", null, false),
        )))
        assertTrue(removed.selectedSkillIds.isEmpty())
        val firstReply = MobileGatewayUiState().applyCapabilitiesEvent(event)
        assertEquals("session-a", firstReply.selectedSessionId)
        assertTrue(firstReply.capabilitiesLoaded)
        val switched = firstReply.clearCapabilities().copy(selectedSessionId = "session-b")
        assertEquals(switched, switched.applyCapabilitiesEvent(event))
    }

    @Test
    fun switchingComputerClearsSelectionsAndReadOnlyHasNoEditableModes() {
        val caps = GatewayCapabilities(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), null, null,
            activeApprovalMode = "read-only", approvalModes = emptyList(), supportsConfiguration = true)
        val applied = MobileGatewayUiState(selectedProviderId = "previous", selectedSkillIds = setOf("old"))
            .applyCapabilities(caps)
        assertTrue(applied.availableApprovalModes.isEmpty())
        val cleared = applied.clearCapabilities()
        assertFalse(cleared.capabilitiesLoaded)
        assertFalse(cleared.approvalModeReported)
        assertTrue(cleared.selectedProviderId.isEmpty())
        assertTrue(cleared.selectedSkillIds.isEmpty())
    }

    @Test
    fun parsesActualRustCapabilityEnvelopeWhenIntegrationFixtureIsProvided() {
        val path = System.getenv("WILLDEEP_MOBILE_RUST_FIXTURE")
        org.junit.Assume.assumeNotNull(path)
        val event = parseGatewayEvent(java.io.File(requireNotNull(path)).readText()) as GatewayEvent.CapabilitiesUpdated
        val state = MobileGatewayUiState().applyCapabilities(event.capabilities)
        assertNotNull(event.sessionId)
        assertTrue(state.supportsConfiguration)
        assertEquals(2, state.providerOptions.size)
        assertEquals("alpha", state.selectedProviderId)
        assertEquals("configured-model", state.selectedModelId)
        assertTrue(state.modelOptions.any { it.id == "remote-model" })
        assertTrue(state.skillOptions.any { it.id == "mobile-fixture" })
        assertEquals(4, state.availableApprovalModes.size)
    }

    @Test
    fun parsesActualMacCapabilityEnvelopeWhenIntegrationFixtureIsProvided() {
        val path = System.getenv("WILLDEEP_MOBILE_MAC_FIXTURE")
        org.junit.Assume.assumeNotNull(path)
        val event = parseGatewayEvent(java.io.File(requireNotNull(path)).readText()) as GatewayEvent.CapabilitiesUpdated
        val state = MobileGatewayUiState().applyCapabilities(event.capabilities)
        assertNotNull(event.sessionId)
        assertTrue(state.supportsConfiguration)
        assertEquals(ApprovalMode.FullAccess, state.approvalMode)
        assertEquals("mobile-fixture-model", state.selectedModelId)
        assertEquals(2, state.providerOptions.size)
        assertTrue(state.providerOptions.any { it.id == state.selectedProviderId && it.isActive })
        assertEquals(4, state.availableApprovalModes.size)
    }
}
