package com.seasonyuu.fnmusic

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicServerCapabilitiesTest {
    @Test fun folderAuthorizationRequiresServerVersionAtLeastOneZeroTen() {
        listOf(null, "", "unknown", "1.0.9", "v1.0.9", "0.99.99", "1.0.11.1")
            .forEach { assertFalse("version=$it", supportsFolderAuthorization(it)) }
        listOf("1.0.10", "v1.0.10", "1.0.10-2", "1.0.11", "v1.0.11", "1.1.0", "2.0.0")
            .forEach { assertTrue("version=$it", supportsFolderAuthorization(it)) }
    }
}
