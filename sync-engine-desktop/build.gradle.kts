plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    api(project(":core:model"))
    api(project(":core:common"))
    api(project(":repository-api"))
    api(project(":cloud-drive-desktop"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.grpc.protobuf.lite)
    testImplementation(libs.grpc.stub)
    testImplementation(libs.protobuf.javalite)
    testImplementation("io.grpc:grpc-netty-shaded:${libs.versions.grpc.get()}")
}

val smokeCloudDriveRssDryRun by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Verify a live CloudDrive2 endpoint and RSS feed without submitting offline downloads."
    classpath = sourceSets.named("main").get().runtimeClasspath
    mainClass.set("com.miruplay.tv.sync.rss.CloudDriveRssLiveSmokeKt")

    val endpoint = providers.gradleProperty("cloudDriveEndpoint")
    val token = providers.gradleProperty("cloudDriveToken")
    val rssUrl = providers.gradleProperty("cloudDriveRssUrl")
    val inbox = providers.gradleProperty("cloudDriveInbox")
    val library = providers.gradleProperty("cloudDriveLibrary")
    val filter = providers.gradleProperty("cloudDriveRssFilter")
    val maxPreview = providers.gradleProperty("cloudDriveRssMaxPreview").orElse("20")
    val proxyEnabled = providers.gradleProperty("cloudDriveRssProxyEnabled").orElse("false")
    val proxyHost = providers.gradleProperty("cloudDriveRssProxyHost").orElse("")
    val proxyPort = providers.gradleProperty("cloudDriveRssProxyPort").orElse("1080")
    val reportPath = providers.gradleProperty("cloudDriveRssReportPath")

    doFirst {
        if (!endpoint.isPresent || !token.isPresent || !rssUrl.isPresent || !inbox.isPresent || !library.isPresent) {
            throw GradleException(
                    "Provide -PcloudDriveEndpoint=http://host:port -PcloudDriveToken=<token> " +
                    "-PcloudDriveRssUrl=<rss-url> -PcloudDriveInbox=/Downloads -PcloudDriveLibrary=/Library. " +
                    "Optional: -PcloudDriveRssFilter=<regex> -PcloudDriveRssMaxPreview=20 " +
                    "-PcloudDriveRssProxyEnabled=true -PcloudDriveRssProxyHost=127.0.0.1 -PcloudDriveRssProxyPort=7890 " +
                    "-PcloudDriveRssReportPath=build/cloud-rss-smoke/report.json"
            )
        }

        args(
            "--endpoint",
            endpoint.get(),
            "--token",
            token.get(),
            "--rss-url",
            rssUrl.get(),
            "--inbox",
            inbox.get(),
            "--library",
            library.get(),
            "--max-preview",
            maxPreview.get(),
            "--proxy-enabled",
            proxyEnabled.get(),
            "--proxy-host",
            proxyHost.get(),
            "--proxy-port",
            proxyPort.get(),
        )
        if (filter.isPresent) {
            args("--filter", filter.get())
        }
        if (reportPath.isPresent) {
            args("--report-path", reportPath.get())
        }
    }
}
