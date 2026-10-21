package com.willdeep.android.mobile

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GatewayTransportPolicyTest {
    @Test
    fun rejectsCleartextNetworksAndEmbeddedCredentials() {
        listOf(
            "http://example.com", "http://192.168.1.20:8877", "http://100.64.0.1",
            "http://localhost", "http://127.0.0.1.evil.example", "http://[::ffff:192.168.1.20]",
            "https://user:secret@example.com", "https://example.com?token=secret", "ftp://example.com",
        ).forEach { url ->
            assertThrows(url, InvalidPairingPayloadException::class.java) {
                GatewayTransportPolicy.requireSecureBaseUrl(url)
            }
        }
    }

    @Test
    fun permitsTlsAndLiteralOnDeviceLoopbackOnly() {
        assertEquals("https://example.com/path", GatewayTransportPolicy.normalizeOrNull("https://example.com/path/#ignored"))
        assertEquals("http://127.0.0.1:8877", GatewayTransportPolicy.normalizeOrNull("http://127.0.0.1:8877/"))
        assertEquals("http://[::1]:8877", GatewayTransportPolicy.normalizeOrNull("http://[::1]:8877/"))
    }

    @Test
    fun storedEndpointsCannotDowngradeToCleartextFallback() {
        assertEquals(listOf("https://safe.example"), connectionBaseUrls(
            "http://192.168.1.20", listOf("http://example.com", "https://safe.example"),
        ))
    }

    @Test
    fun scannedPayloadRejectsCleartextAndKeepsOnlySecureFallbacks() {
        val payload = """{"base_url":"https://safe.example","pairing_token":"test-token",
            "protocol_version":"mobile-gateway.v1","desktop_name":"Test","expires_at":"2099-01-01T00:00:00Z",
            "fallback_base_urls":["http://192.168.1.20","https://fallback.example"]}"""
        assertEquals(listOf("https://fallback.example"), PairingPayload.parse(payload).fallbackBaseUrls)
        assertThrows(InvalidPairingPayloadException::class.java) {
            PairingPayload.parse(payload.replace("https://safe.example", "http://192.168.1.20"))
        }
        assertThrows(InvalidPairingPayloadException::class.java) {
            PairingPayload.parse("""{"relay_base_url":"http://relay.example","relay_room":"test","relay_token":"test-token"}""")
        }
    }

    @Test
    fun clientRejectsPersistedOrManuallyConstructedInsecureEndpointsBeforeIo() {
        val client = MobileGatewayClient()
        assertThrows(InvalidPairingPayloadException::class.java) {
            runBlocking { client.checkHealth("http://192.168.1.20") }
        }
        assertThrows(InvalidPairingPayloadException::class.java) {
            runBlocking { client.connect("http://192.168.1.20", "private-device-token").first() }
        }
        assertThrows(InvalidPairingPayloadException::class.java) {
            runBlocking { client.claimPairing(PairingPayload(
                "http://192.168.1.20", pairingToken = "private-pair-token",
                protocolVersion = MOBILE_GATEWAY_PROTOCOL_VERSION, desktopName = "Test", expiresAt = "2099-01-01T00:00:00Z",
            ), "Test") }
        }
    }
}
