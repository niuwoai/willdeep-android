package com.willdeep.android.mobile

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Stored pairings are untrusted input too; credentials must not traverse cleartext networks. */
object GatewayTransportPolicy {
    fun requireSecureBaseUrl(raw: String): HttpUrl {
        val url = raw.trim().toHttpUrlOrNull() ?: throw InvalidPairingPayloadException()
        val loopback = url.host == "127.0.0.1" || url.host == "::1"
        if ((!url.isHttps && !loopback) || url.username.isNotEmpty() ||
            url.password.isNotEmpty() || url.query != null
        ) throw InvalidPairingPayloadException()
        return url.newBuilder().fragment(null).build()
    }

    fun normalizeOrNull(raw: String): String? = runCatching {
        requireSecureBaseUrl(raw).toString().trimEnd('/')
    }.getOrNull()
}
