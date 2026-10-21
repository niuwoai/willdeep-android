package com.willdeep.android

import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import com.willdeep.android.mobile.DeviceTokenStore
import com.willdeep.android.mobile.GatewayEnvelope
import com.willdeep.android.mobile.GatewayEvent
import com.willdeep.android.mobile.MobileGatewayClient
import com.willdeep.android.mobile.PairingPayload
import com.willdeep.android.mobile.StoredGatewayCredential
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/** Exercises the installed Release client; preserves app data and restores device selection. */
class LiveHostRelayInstrumentedTest {
    @Test
    fun exerciseHost() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val args = InstrumentationRegistry.getArguments()
        val store = DeviceTokenStore(context)
        val original = store.load()?.id
        val pairingFile = File(context.getExternalFilesDir(null), "acceptance-pairing.json")
        if (args.getString("import_pairing") == "true") {
            val payload = PairingPayload.parse(pairingFile.readText())
            check(pairingFile.delete())
            check(payload.hasRelay() && payload.relayBaseUrl!!.startsWith("https://"))
            store.save(StoredGatewayCredential(
                baseUrl = payload.relayBaseUrl!!, deviceToken = payload.relayToken!!,
                desktopName = payload.desktopName, protocolVersion = payload.protocolVersion,
                relayRoom = payload.relayRoom,
            ))
        }
        val credential = store.loadAll().single { it.id == args.getString("credential_id") }
        check(credential.baseUrl.startsWith("https://"))
        val events = CopyOnWriteArrayList<GatewayEvent>()
        val client = MobileGatewayClient()
        val socket = launch(Dispatchers.IO) {
            repeat(3) {
                client.connect(credential.baseUrl, credential.deviceToken, credential.relayRoom)
                    .collect { events.add(it) }
                delay(1_000)
            }
        }
        // The production ViewModel polls every five seconds; match its presence heartbeat.
        val heartbeat = launch {
            while (isActive) {
                delay(5_000)
                client.sendCommand(GatewayEnvelope(type = "session.list"))
            }
        }
        var sessionId: String? = null
        var completed = false
        fun signal(stage: String, value: String = "passed") {
            instrumentation.sendStatus(0, Bundle().apply { putString("live_" + stage, value) })
        }
        suspend fun awaitEvent(stage: String, timeout: Long = 30_000, predicate: (GatewayEvent) -> Boolean): GatewayEvent =
            withTimeout(timeout) {
                while (true) {
                    events.firstOrNull(predicate)?.let { return@withTimeout it }
                    check(socket.isActive) { "Connection ended during $stage" }
                    delay(100)
                }
                error("unreachable")
            }
        suspend fun command(type: String, payload: JSONObject = JSONObject(), target: String? = sessionId): GatewayEvent.Ack {
            val request = GatewayEnvelope(type = type, sessionId = target, payload = payload)
            check(client.sendCommand(request)) { "Unable to send $type" }
            val reply = awaitEvent(type) { event ->
                (event is GatewayEvent.Ack && event.commandId == request.id) ||
                    (event is GatewayEvent.Error && event.commandId == request.id)
            }
            check(reply is GatewayEvent.Ack) { "Host rejected $type" }
            return reply
        }
        try {
            val snapshot = awaitEvent("session list") { it is GatewayEvent.Snapshot } as GatewayEvent.Snapshot
            signal("session_list", "passed: ${snapshot.sessions.size} sessions")
            if (args.getString("connection_only") == "true") {
                completed = true
                return@runBlocking
            }
            val workspace = requireNotNull(args.getString("workspace"))
            fun isTestWorkspace(session: com.willdeep.android.mobile.GatewaySession): Boolean =
                session.workspacePath == workspace || session.workspaceName == File(workspace).name
            // Persisted Mac message counts can lag a live runtime; always create a fresh test session.
            check(client.sendCommand(GatewayEnvelope(type = "session.create",
                payload = JSONObject().put("workspace_path", workspace))))
            val created = awaitEvent("session create") {
                it is GatewayEvent.SessionUpsert && isTestWorkspace(it.session) &&
                    snapshot.sessions.none { old -> old.id == it.session.id }
            } as GatewayEvent.SessionUpsert
            sessionId = created.session.id
            check(!sessionId.isNullOrBlank())
            signal("test_session", sessionId!!)
            command("session.configure", JSONObject().put("approval_mode", "strict"))
            signal("isolated_session")
            val marker = requireNotNull(args.getString("marker"))
            check(marker.matches(Regex("[1-9][0-9]{5}")))
            val shellCommand = "ruby -e 'puts $marker'"
            val text = "手机 HTTPS 安全验收。不要读取文件或修改项目。请只使用 run_command 工具执行这一条命令：\n" +
                shellCommand + "\n工具参数仅提供 command 字段，不增加其他字段。不要使用 terminal_* 交互终端工具，不要添加其他命令，等待手机审批。" +
                "执行成功后仅回复已完成和标记 $marker。"
            val messageAck = command("message.send", JSONObject().put("text", text).put("approval_mode", "strict"))
            check(messageAck.sessionId.equals(sessionId, ignoreCase = true)) { "Host acknowledged a different session" }
            signal("message_ack")
            fun isTestApproval(approval: com.willdeep.android.mobile.PendingToolApproval): Boolean =
                approval.sessionId == sessionId &&
                    listOf(approval.inputPreview, approval.title, approval.summary).any { it.contains(marker) }
            val pendingEvent = awaitEvent("approval", 120_000) { event ->
                (event is GatewayEvent.ToolPending && isTestApproval(event.approval)) ||
                    (event is GatewayEvent.Snapshot && event.pendingTools.any(::isTestApproval))
            }
            val approval = when (pendingEvent) {
                is GatewayEvent.ToolPending -> pendingEvent.approval
                is GatewayEvent.Snapshot -> pendingEvent.pendingTools.first(::isTestApproval)
                else -> error("Missing approval")
            }
            check(!approval.requiresAnswer && !approval.requiresConfirmation) { "Unexpected interactive approval" }
            val preview = approval.inputPreview.trim().ifBlank {
                listOf(approval.title, approval.summary).filter { it.isNotBlank() }.joinToString("\n")
                    .removePrefix("run command: ")
            }
            val json = runCatching { JSONObject(preview) }.getOrNull()
            val labelledCommands = preview.lineSequence().filter { it.startsWith("command: ") }
                .map { it.removePrefix("command: ") }.toList()
            val actualCommand = json?.optString("command")?.takeIf { it.isNotBlank() }
                ?: json?.optString("cmd")?.takeIf { it.isNotBlank() }
                ?: labelledCommands.singleOrNull()
                ?: preview.removeSuffix("\nworking directory: $workspace")
            check(actualCommand.trim() == shellCommand) { "Refusing to approve a command other than the exact constant-output test" }
            signal("approval_received")
            val boundary = events.size
            val decision = GatewayEnvelope(type = "tool.decide", sessionId = sessionId,
                payload = JSONObject().put("id", approval.id).put("approved", true))
            check(client.sendCommand(decision))
            withTimeout(120_000) {
                while (true) {
                    val replies = events.drop(boundary).flatMap { event ->
                        when (event) {
                            is GatewayEvent.MessageAppend -> listOf(event.message)
                            is GatewayEvent.Snapshot -> event.messages
                            else -> emptyList()
                        }
                    }
                    if (replies.any { it.sessionId == sessionId && it.role == "assistant" &&
                            !it.isStreaming && it.content.contains(marker) && it.content.contains("完成") }) break
                    delay(250)
                }
            }
            // Execution and the matching final reply are stronger evidence than a transient ACK.
            signal("approval_receipt", if (events.any { it is GatewayEvent.Ack && it.commandId == decision.id })
                "ack" else "matching_execution_reply")
            signal("approval_applied")
            signal("assistant_reply")
            completed = true
        } finally {
            if (!completed && sessionId != null) {
                client.sendCommand(GatewayEnvelope(type = "turn.stop", sessionId = sessionId))
            }
            client.disconnect()
            heartbeat.cancelAndJoin()
            socket.cancelAndJoin()
            original?.let(store::select)
        }
    }

    @Test
    fun listPairedHosts() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val credentials = DeviceTokenStore(instrumentation.targetContext).loadAll()
        val hosts = JSONArray()
        credentials.forEach { credential ->
            hosts.put(JSONObject()
                .put("id", credential.id)
                .put("name", credential.displayName)
                .put("secure", credential.baseUrl.startsWith("https://"))
                .put("relay", !credential.relayRoom.isNullOrBlank()))
        }
        instrumentation.sendStatus(0, Bundle().apply { putString("paired_hosts", hosts.toString()) })
    }
}
