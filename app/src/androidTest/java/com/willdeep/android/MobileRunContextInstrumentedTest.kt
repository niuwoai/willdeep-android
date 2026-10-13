package com.willdeep.android

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.willdeep.android.mobile.DeviceTokenStore
import com.willdeep.android.ui.ApprovalMode
import com.willdeep.android.ui.ConnectionStatus
import com.willdeep.android.ui.MobileGatewayViewModel
import com.willdeep.android.ui.SessionDetailScreen
import com.willdeep.android.ui.theme.WillDeepTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.util.Base64

@RunWith(AndroidJUnit4::class)
class MobileRunContextInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun modifiesActualDesktopConfigurationThroughRenderedSelectors() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        require(context.packageName == "com.willdeep.android.contexttest") {
            "Live tests must use the isolated test installation"
        }
        val arguments = InstrumentationRegistry.getArguments()
        val backend = requireNotNull(arguments.getString("backend"))
        val payload = String(Base64.getDecoder().decode(requireNotNull(arguments.getString("pairing_base64"))))
        DeviceTokenStore(context).clear()
        val viewModel = MobileGatewayViewModel(context.applicationContext as Application)
        try {
            composeRule.setContent {
                val state by viewModel.state.collectAsState()
                WillDeepTheme { SessionDetailScreen(viewModel, state, onBack = {}, onScan = {}) }
            }
            composeRule.runOnIdle { viewModel.scanAndPair(payload) }
            composeRule.waitUntil(30_000) {
                val state = viewModel.state.value
                state.status == ConnectionStatus.Connected && state.capabilitiesLoaded && state.providerOptions.size == 2
            }
            assertEquals(4, viewModel.state.value.availableApprovalModes.size)
            composeRule.onNodeWithContentDescription(context.getString(R.string.approval_mode_smart)).performClick()
            listOf(R.string.approval_mode_ask_every_time, R.string.approval_mode_smart,
                R.string.approval_mode_workspace_writable, R.string.approval_mode_full_access).forEach {
                composeRule.onNodeWithText(context.getString(it)).performScrollTo().assertIsDisplayed()
            }
            capture("$backend-approval.png")
            composeRule.onNodeWithText(context.getString(R.string.approval_mode_full_access)).performClick()
            composeRule.waitUntil(10_000) { viewModel.state.value.approvalMode == ApprovalMode.FullAccess }

            composeRule.onNodeWithContentDescription(context.getString(R.string.composer_provider_title)).performClick()
            capture("$backend-providers.png")
            composeRule.onNodeWithText("beta").performScrollTo().performClick()
            composeRule.waitUntil(10_000) {
                val state = viewModel.state.value
                state.providerOptions.any { it.title == "beta" && it.id == state.selectedProviderId && it.isActive }
            }
            composeRule.onNodeWithContentDescription(context.getString(R.string.composer_model_title)).performClick()
            composeRule.onNodeWithText("remote-model").performScrollTo().assertIsDisplayed()
            capture("$backend-models.png")
            composeRule.onNodeWithText("remote-model").performClick()
            composeRule.waitUntil(10_000) { viewModel.state.value.selectedModelId == "remote-model" }
            val uniqueSkills = viewModel.state.value.skillOptions.groupBy { it.title }.filterValues { it.size == 1 }
            val preferredSkill = arguments.getString("preferred_skill")
            val skill = viewModel.state.value.skillOptions.firstOrNull { it.id == preferredSkill }
                ?: uniqueSkills.values.first().single()
            composeRule.onNodeWithContentDescription(context.getString(R.string.composer_skills_title)).performClick()
            composeRule.onNodeWithText(skill.title).performScrollTo().performClick()
            composeRule.waitUntil(10_000) { skill.id in viewModel.state.value.selectedSkillIds }
            capture("$backend-skills.png")
            composeRule.onNodeWithText(context.getString(R.string.done_button)).performClick()
            composeRule.onAllNodes(hasSetTextAction()).onFirst().performTextReplacement("phone runtime proof")
            composeRule.onNodeWithContentDescription(context.getString(R.string.send_button)).performClick()
            composeRule.waitUntil(60_000) {
                viewModel.state.value.conversationMessages.any { it.role == "assistant" && it.content.contains("fixture complete") }
            }
            assertEquals(ApprovalMode.FullAccess, viewModel.state.value.approvalMode)
            capture("$backend-reply.png")
        } finally {
            viewModel.disconnect()
            DeviceTokenStore(context).clear()
        }
    }

    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.waitForIdle()
        val image = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        FileOutputStream(File(context.getExternalFilesDir(null), name)).use {
            check(image.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }
}
