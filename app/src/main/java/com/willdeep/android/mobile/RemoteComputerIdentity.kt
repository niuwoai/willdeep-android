package com.willdeep.android.mobile

const val MAX_COMPUTER_ALIAS_LENGTH = 60
val COMPUTER_PLATFORMS = listOf("", "macos", "linux", "windows")

fun reportedComputerPlatform(desktopName: String): String = when {
    desktopName.startsWith("macOS:") -> "macos"
    desktopName.startsWith("Linux:") -> "linux"
    desktopName.startsWith("Windows:") -> "windows"
    else -> ""
}

internal fun StoredGatewayCredential.preservingLocalIdentity(
    existing: StoredGatewayCredential?,
): StoredGatewayCredential {
    if (existing == null) return this
    return copy(
        alias = existing.alias,
        computerPlatform = existing.computerPlatform.ifBlank { computerPlatform },
        lastDesktopResponseAtEpochMillis = lastDesktopResponseAtEpochMillis.takeIf { it != 0L }
            ?: existing.lastDesktopResponseAtEpochMillis,
    )
}
