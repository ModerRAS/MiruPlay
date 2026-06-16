package com.miruplay.tv

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseProguardRulesTest {
    @Test
    fun `release keeps serializers outside core model`() {
        val rules = File("proguard-rules.pro").readText()

        listOf(
            "com.miruplay.tv.core.common.**\$\$serializer",
            "com.miruplay.tv.repository.**\$\$serializer",
            "com.miruplay.tv.scraper.core.**\$\$serializer",
        ).forEach { expectedRule ->
            assertTrue("Missing keep rule for $expectedRule", rules.contains(expectedRule))
        }
    }
}
