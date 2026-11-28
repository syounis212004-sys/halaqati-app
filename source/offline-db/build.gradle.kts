plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.mohammedsamir.halaqati.offline"
    compileSdk = 36
    defaultConfig { minSdk = 23 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

ksp {
    // Keep the existing authoritative version-1 schema path for upgrade checks.
    arg("room.schemaLocation", rootProject.file("app/schemas").absolutePath)
    // Keep the existing Java implementation backend; switch only the symbol processor.
    arg("room.generateKotlin", "false")
}

dependencies {
    api("androidx.room:room-runtime:2.8.5")
    api("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
}
