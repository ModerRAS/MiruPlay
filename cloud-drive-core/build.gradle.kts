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
    api(project(":cloud-drive-api"))
    api(project(":core:model"))
    api(project(":core:common"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.grpc.okhttp)
    api(libs.grpc.protobuf.lite)
    api(libs.grpc.stub)
    api(libs.protobuf.javalite)
    implementation(libs.javax.annotation.api)

    testImplementation(libs.junit)
    testImplementation("io.grpc:grpc-netty-shaded:$grpcVersion")
}
