package com.willdeep.android

import android.content.pm.ApplicationInfo
import android.security.NetworkSecurityPolicy
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityConfigurationInstrumentedTest {
    @Test
    fun installedAppDisablesBackupAndNetworkCleartext() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.willdeep.android", context.packageName)
        assertEquals(0, context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
        val policy = NetworkSecurityPolicy.getInstance()
        assertFalse(policy.isCleartextTrafficPermitted("example.com"))
        assertFalse(policy.isCleartextTrafficPermitted("192.168.1.20"))
        assertFalse(policy.isCleartextTrafficPermitted("100.64.0.1"))
        assertTrue(policy.isCleartextTrafficPermitted("127.0.0.1"))
    }
}
