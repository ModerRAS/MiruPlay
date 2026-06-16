package com.miruplay.tv

import org.junit.Assert.assertFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class ReleaseMappingCompatibilityTest {
    @Test
    fun `release mapping excludes Java 9 invoke bytecode from Android APK`() {
        assumeTrue(
            "Enable with -Dmiruplay.checkReleaseMapping=true after building release",
            System.getProperty("miruplay.checkReleaseMapping") == "true",
        )
        val mapping = releaseMappingFile()
        assumeTrue("Release mapping is only available after a release build", mapping.isFile)

        val text = mapping.readText()

        assertFalse(
            "Release APK must not include VarHandle bytecode; it is rejected by older Android TV ART.",
            text.contains("java.lang.invoke.VarHandle"),
        )
        assertFalse(
            "Release APK must not include Lucene BKD VarHandle accessors in startup-verifiable classes.",
            text.contains("org.apache.lucene.util.bkd.HeapPointWriter\$HeapPointValue.docID"),
        )
    }

    private fun releaseMappingFile(): File =
        listOf(
            File("app/build/outputs/mapping/release/mapping.txt"),
            File("build/outputs/mapping/release/mapping.txt"),
        ).firstOrNull { it.isFile }
            ?: File("app/build/outputs/mapping/release/mapping.txt")
}
