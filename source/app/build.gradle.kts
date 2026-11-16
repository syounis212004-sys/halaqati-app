import java.util.Properties
import groovy.json.JsonOutput

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.kapt")
    id("com.google.gms.google-services")
}

val props = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun conf(name: String, fallback: String = ""): String = sequenceOf(
    providers.gradleProperty(name).orNull,
    System.getenv(name),
    props.getProperty(name)
).filterNotNull().firstOrNull { it.isNotBlank() } ?: fallback

android {
    namespace = "com.mohammedsamir.halaqati"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.mohammedsamir.halaqati"
        minSdk = 23
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        versionCode = 29011
        versionName = "2.90.11"
        vectorDrawables { useSupportLibrary = true }
        buildConfigField("String", "SUPABASE_URL", JsonOutput.toJson(conf("SUPABASE_URL", "https://xlxzwucqdtggkfwwqibk.supabase.co")))
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", JsonOutput.toJson(conf("SUPABASE_PUBLISHABLE_KEY", "sb_publishable_VZGYTedRpXfJdswijXoxig_F0Hva8ks")))
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", JsonOutput.toJson(conf("GOOGLE_WEB_CLIENT_ID")))
    }
    buildFeatures { compose = true; buildConfig = true }
    packaging { resources.excludes += setOf("META-INF/LICENSE*", "META-INF/NOTICE*") }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    signingConfigs {
        create("release") {
            val storeFilePath = conf("HALAQATI_KEYSTORE_PATH")
            if (storeFilePath.isNotBlank()) storeFile = file(storeFilePath)
            storePassword = conf("HALAQATI_STORE_PASSWORD")
            keyAlias = conf("HALAQATI_KEY_ALIAS")
            keyPassword = conf("HALAQATI_KEY_PASSWORD")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val ks = conf("HALAQATI_KEYSTORE_PATH")
            if (ks.isNotBlank()) signingConfig = signingConfigs.getByName("release")
        }
        debug {
            // Keep the production applicationId so the existing Firebase client in
            // google-services.json matches during CI unit-test resource processing.
            // versionNameSuffix is safe and does not change the Firebase package id.
            versionNameSuffix = "-debug"
        }
    }
}

kapt {
    correctErrorTypes = true
    arguments { arg("room.schemaLocation", "$projectDir/schemas") }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.10.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    // ActivityResult lint requires Fragment 1.3.0+; a transitive dependency was resolving 1.1.0 in CI.
    implementation("androidx.fragment:fragment:1.8.9")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    implementation("androidx.navigation:navigation-compose:2.9.5")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")
    // RC27.12: Halaqati motion identity (Lottie files in res/raw).
    implementation("com.airbnb.android:lottie-compose:6.6.2")
    implementation("androidx.work:work-runtime-ktx:2.11.2")
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    kapt("androidx.room:room-compiler:2.8.5")
    implementation("androidx.security:security-crypto:1.1.0")
    implementation("androidx.browser:browser:1.9.0")
    implementation("com.google.firebase:firebase-messaging:25.1.1")
    implementation("androidx.credentials:credentials:1.7.0-alpha03")
    implementation("androidx.credentials:credentials-play-services-auth:1.7.0-alpha03")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}

// RC8 Quran Core: the exact QCF4 line layout is generated from a pinned, SHA-verified
// public source before every Android build. The generator is idempotent once the asset exists.
val prepareCairoFont by tasks.registering(org.gradle.api.tasks.Exec::class) {
    workingDir(rootProject.projectDir)
    commandLine("python3", "scripts/fetch-cairo-font.py")
    inputs.property("cairoGitBlob", "25641b1c828857698457bcd9f5a102616b4e8033")
    outputs.file(layout.projectDirectory.file("src/main/res/font/cairo.ttf"))
    outputs.file(layout.projectDirectory.file("src/main/assets/documents/cairo.ttf"))
}

val prepareQcf4Layout by tasks.registering(org.gradle.api.tasks.Exec::class) {
    workingDir(rootProject.projectDir)
    commandLine("python3", "scripts/generate-qcf4-layout.py")
    inputs.property("qcf4Commit", "5130511027e769f0a8f4eeb7f00f46bde3788d60")
    outputs.file(layout.projectDirectory.file("src/main/assets/quran/qcf4-layout-v2.bin"))
    outputs.file(layout.projectDirectory.file("src/main/assets/quran/qcf4-layout-source.json"))
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(prepareQcf4Layout)
    dependsOn(prepareCairoFont)
}
