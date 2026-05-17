// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "8.6.0" apply false
    id("com.android.library") version "8.6.0" apply false
    id("org.jetbrains.kotlin.android") version "2.0.0" apply false
    id("org.jetbrains.kotlin.jvm") version "2.0.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.0" apply false
    id("org.jetbrains.compose") version "1.6.11" apply false
    id("com.google.dagger.hilt.android") version "2.52" apply false
    id("com.google.devtools.ksp") version "2.0.0-1.0.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.0" apply false
    id("com.google.protobuf") version "0.9.6" apply false
}

val miruPlayPaletteLiterals = listOf(
    "0xFFE94560",
    "0xFFC73E54",
    "0xFF1A1A2E",
    "0xFF16213E",
    "0xFF0F3460",
    "0xFFEEEEEE",
    "0xFFAAAAAA",
    "0xFF1E2A45",
    "0xFF4CAF50",
    "0xFFFFC107",
    "0xFFCF6679",
    "0xFFF5F5F5",
)

tasks.register("checkUiPaletteDrift") {
    group = "verification"
    description = "Fails when TV or desktop UI reintroduces raw MiruPlay palette literals instead of using :ui-design."

    inputs.files(
        fileTree("ui-tv/src") {
            include("**/*.kt", "**/*.kts")
        },
        fileTree("desktop-app/src") {
            include("**/*.kt", "**/*.kts")
        },
    )

    doLast {
        val violations = mutableListOf<String>()
        inputs.files.files
            .filter { it.isFile }
            .sortedBy { it.absolutePath }
            .forEach { file ->
                file.useLines { lines ->
                    lines.forEachIndexed { index, line ->
                        miruPlayPaletteLiterals
                            .filter { literal -> line.contains(literal, ignoreCase = true) }
                            .forEach { literal ->
                                val relativePath = file.relativeTo(rootDir).path.replace(java.io.File.separatorChar, '/')
                                violations += "$relativePath:${index + 1}: $literal"
                            }
                    }
                }
            }

        if (violations.isNotEmpty()) {
            throw GradleException(
                "Raw MiruPlay palette literal(s) found outside :ui-design. " +
                    "Use com.miruplay.tv.design.MiruPlayPalette instead:\n" +
                    violations.joinToString(separator = "\n") { " - $it" }
            )
        }
    }
}

val desktopComposeOnlyForbiddenPatterns = mapOf(
    "javax.swing" to Regex("""javax\.swing"""),
    "kotlinx.coroutines.swing" to Regex("""kotlinx\.coroutines\.swing"""),
    "Dispatchers.Swing" to Regex("""Dispatchers\.Swing"""),
    "SwingUtilities" to Regex("""\bSwingUtilities\b"""),
    "JFrame" to Regex("""\bJFrame\b"""),
    "JPanel" to Regex("""\bJPanel\b"""),
    "JButton" to Regex("""\bJButton\b"""),
    "JFileChooser" to Regex("""\bJFileChooser\b"""),
    "coroutines-swing" to Regex("""coroutines-swing"""),
)

tasks.register("checkDesktopComposeOnly") {
    group = "verification"
    description = "Fails when the desktop app reintroduces Swing UI dependencies instead of Compose Desktop."

    inputs.files(
        fileTree("desktop-app/src") {
            include("**/*.kt", "**/*.kts")
        },
        file("desktop-app/build.gradle.kts"),
        file("gradle/libs.versions.toml"),
    )

    doLast {
        val violations = mutableListOf<String>()
        inputs.files.files
            .filter { it.isFile }
            .sortedBy { it.absolutePath }
            .forEach { file ->
                file.useLines { lines ->
                    lines.forEachIndexed { index, line ->
                        desktopComposeOnlyForbiddenPatterns
                            .filterValues { pattern -> pattern.containsMatchIn(line) }
                            .keys
                            .forEach { token ->
                                val relativePath = file.relativeTo(rootDir).path.replace(java.io.File.separatorChar, '/')
                                violations += "$relativePath:${index + 1}: $token"
                            }
                    }
                }
            }

        if (violations.isNotEmpty()) {
            throw GradleException(
                "Swing UI dependency found in the desktop app. " +
                    "Use Compose Desktop for Windows UI work:\n" +
                    violations.joinToString(separator = "\n") { " - $it" }
            )
        }
    }
}

subprojects {
    plugins.withId("com.android.application") {
        extensions.configure<com.android.build.gradle.BaseExtension>("android") {
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_21
                targetCompatibility = JavaVersion.VERSION_21
            }
        }
    }
    plugins.withId("com.android.library") {
        extensions.configure<com.android.build.gradle.BaseExtension>("android") {
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_21
                targetCompatibility = JavaVersion.VERSION_21
            }
        }
    }
    plugins.withId("org.jetbrains.kotlin.android") {
        extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension>("kotlin") {
            jvmToolchain(21)
        }
    }
    plugins.withId("org.jetbrains.kotlin.jvm") {
        extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension>("kotlin") {
            jvmToolchain(21)
        }
    }
}
