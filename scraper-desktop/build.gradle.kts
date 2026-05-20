plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    api(project(":core:model"))
    api(project(":core:common"))
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
}

val smokeBangumiLive by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Verify live Bangumi search/details/episodes using the desktop scraper and write a token-free report."
    classpath = sourceSets.named("main").get().runtimeClasspath
    mainClass.set("com.miruplay.tv.scraper.desktop.BangumiLiveSmokeKt")

    val query = providers.gradleProperty("bangumiSmokeQuery").orElse("葬送的芙莉莲")
    val subjectId = providers.gradleProperty("bangumiSmokeSubjectId")
    val expectedTitle = providers.gradleProperty("bangumiSmokeExpectedTitle")
    val minResults = providers.gradleProperty("bangumiSmokeMinResults").orElse("1")
    val reportPath = providers.gradleProperty("bangumiSmokeReportPath")
        .orElse("build/bangumi-smoke/live-report.json")

    doFirst {
        args(
            "--query",
            query.get(),
            "--min-results",
            minResults.get(),
            "--report-path",
            reportPath.get(),
        )
        if (subjectId.isPresent) {
            args("--subject-id", subjectId.get())
        }
        if (expectedTitle.isPresent) {
            args("--expected-title", expectedTitle.get())
        }
    }
}
