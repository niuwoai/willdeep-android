package com.willdeep.android.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DeviceTokenStoreTest {
    @Test
    fun onlyExplicitCliSystemPrefixesIdentifyPlatform() {
        assertEquals("macos", reportedComputerPlatform("macOS:home-mac"))
        assertEquals("linux", reportedComputerPlatform("Linux:h2"))
        assertEquals("windows", reportedComputerPlatform("Windows:pc"))
        assertEquals("", reportedComputerPlatform("WillDeep CLI"))
        assertEquals("", reportedComputerPlatform("macbook-server"))
    }

    @Test
    fun sameNameComputersKeepIndependentAliasesAndPlatforms() {
        val home = credential("https://relay.example.com", "home", "WillDeep CLI")
            .copy(alias = "家里的 Mac", computerPlatform = "macos")
        val server = home.copy(relayRoom = "server", alias = "Linux 服务器", computerPlatform = "linux")
        val restored = decodeStoredGatewayCredentials(encodeStoredGatewayCredentials(listOf(home, server)))
        assertEquals(listOf(home, server), restored)
        assertNotEquals(restored[0].id, restored[1].id)
        assertEquals("家里的 Mac", restored[0].displayName)
        assertEquals("Linux 服务器", restored[1].displayName)
    }

    @Test
    fun rescanningPreservesLocalIdentityAndLastResponse() {
        val original = credential("https://relay.example.com", "home", "WillDeep CLI", 1234L)
            .copy(alias = "家里的 Mac", computerPlatform = "macos")
        val scanned = original.copy(desktopName = "new hostname", alias = "", computerPlatform = "",
            lastDesktopResponseAtEpochMillis = 0L)
        val merged = scanned.preservingLocalIdentity(original)
        assertEquals("家里的 Mac", merged.displayName)
        assertEquals("macos", merged.computerPlatform)
        assertEquals("new hostname", merged.desktopName)
        assertEquals(1234L, merged.lastDesktopResponseAtEpochMillis)
        assertEquals(original.id, merged.id)
    }

    @Test
    fun legacyCredentialsDoNotGuessSystemFromCliName() {
        val json = """[{"base_url":"https://relay.example.com","device_token":"fixture", "desktop_name":"WillDeep CLI"}]"""
        val restored = decodeStoredGatewayCredentials(json).single()
        assertEquals("", restored.computerPlatform)
        assertEquals("", restored.alias)
        assertEquals("WillDeep CLI", restored.displayName)
        assertEquals("WillDeep CLI", restored.copy(alias = "").displayName)
    }

    @Test
    fun storedCredentialsRoundTripMultipleMacs() {
        val credentials = listOf(
            credential(
                baseUrl = "https://relay.example.com",
                relayRoom = "macbook-room",
                desktopName = "Rocky 的 MacBook Pro",
                lastResponseAt = 1234L,
            ),
            credential(
                baseUrl = "http://192.168.1.12:8787",
                relayRoom = null,
                desktopName = "工作室 Mac mini",
                lastResponseAt = 5678L,
            ),
        )

        assertEquals(credentials, decodeStoredGatewayCredentials(encodeStoredGatewayCredentials(credentials)))
    }

    @Test
    fun credentialIdentityUsesRelayRoomWhenAvailable() {
        val first = credential(
            baseUrl = "https://first-relay.example.com",
            relayRoom = "stable-room",
            desktopName = "Mac",
        )
        val moved = first.copy(baseUrl = "https://second-relay.example.com")
        val other = first.copy(relayRoom = "other-room")

        assertEquals(first.id, moved.id)
        assertNotEquals(first.id, other.id)
    }

    private fun credential(
        baseUrl: String,
        relayRoom: String?,
        desktopName: String,
        lastResponseAt: Long = 0L,
    ): StoredGatewayCredential {
        return StoredGatewayCredential(
            baseUrl = baseUrl,
            fallbackBaseUrls = listOf("http://10.0.0.2:8787"),
            deviceToken = "test-token-$desktopName",
            desktopName = desktopName,
            protocolVersion = MOBILE_GATEWAY_PROTOCOL_VERSION,
            relayRoom = relayRoom,
            lastDesktopResponseAtEpochMillis = lastResponseAt,
        )
    }
}
