plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
}

val grpcVersion = "1.81.0"

kotlin {
    jvmToolchain(21)
}

dependencies {
    api(project(":cloud-drive-core"))
    api(project(":cloud-drive-api"))
    api(project(":core:model"))
    api(project(":core:common"))

    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation("io.grpc:grpc-netty-shaded:$grpcVersion")
}

val smokeCloudDrive2 by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Verify a live CloudDrive2 endpoint using the JVM gRPC client without printing the token."
    classpath = sourceSets.named("main").get().runtimeClasspath
    mainClass.set("com.miruplay.tv.clouddrive.CloudDriveLiveSmokeKt")

    val endpoint = providers.gradleProperty("cloudDriveEndpoint")
    val token = providers.gradleProperty("cloudDriveToken")
    val path = providers.gradleProperty("cloudDrivePath").orElse("/")
    val reportPath = providers.gradleProperty("cloudDriveReportPath")
    val maxPreview = providers.gradleProperty("cloudDriveMaxPreview").orElse("10")

    doFirst {
        if (!endpoint.isPresent || !token.isPresent) {
            throw GradleException(
                "Provide -PcloudDriveEndpoint=http://host:port and -PcloudDriveToken=<token>. " +
                    "Optional: -PcloudDrivePath=/path -PcloudDriveMaxPreview=10 " +
                    "-PcloudDriveReportPath=build/cloud-drive-smoke/report.json"
            )
        }
        args(
            "--endpoint",
            endpoint.get(),
            "--token",
            token.get(),
            "--path",
            path.get(),
            "--max-preview",
            maxPreview.get(),
        )
        if (reportPath.isPresent) {
            args("--report-path", reportPath.get())
        }
    }
}
