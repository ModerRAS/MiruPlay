plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
    id("com.google.protobuf")
}

val protobufVersion = "3.25.9"
val grpcVersion = "1.81.0"

kotlin {
    jvmToolchain(21)
}

sourceSets {
    named("main") {
        proto {
            srcDir("../cloud-drive/src/main/proto")
        }
    }
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:$protobufVersion"
    }
    plugins {
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:$grpcVersion"
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                named("java") {
                    option("lite")
                }
            }
            task.plugins {
                create("grpc") {
                    option("lite")
                }
            }
        }
    }
}

dependencies {
    api(project(":core:model"))
    api(project(":core:common"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.grpc.okhttp)
    implementation(libs.grpc.protobuf.lite)
    implementation(libs.grpc.stub)
    implementation(libs.protobuf.javalite)
    implementation(libs.javax.annotation.api)

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

    doFirst {
        if (!endpoint.isPresent || !token.isPresent) {
            throw GradleException(
                "Provide -PcloudDriveEndpoint=http://host:port and -PcloudDriveToken=<token>. " +
                    "Optional: -PcloudDrivePath=/path"
            )
        }
        args(
            "--endpoint",
            endpoint.get(),
            "--token",
            token.get(),
            "--path",
            path.get(),
        )
    }
}
