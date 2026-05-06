plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

dependencies {
    compileOnly("com.android.application:com.android.application.gradle.plugin:8.7.0")
    compileOnly("com.android.library:com.android.library.gradle.plugin:8.7.0")
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.21")
    compileOnly("com.google.dagger:hilt-android:2.52")
    compileOnly("com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin:2.0.21-1.0.28")
}
