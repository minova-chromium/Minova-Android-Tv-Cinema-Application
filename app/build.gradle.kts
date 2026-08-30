import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val localPropertiesFile = rootProject.file("local.properties")
val localProperties = Properties().apply {
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use(::load)
    }
}

// Migration fallback for existing Minova builds. New installations should put
// these values in the ignored local.properties file (see README.md).
val legacyKeystorePropertiesFile = rootProject.file("keystore.properties")
val legacyKeystoreProperties = Properties().apply {
    if (legacyKeystorePropertiesFile.exists()) {
        legacyKeystorePropertiesFile.inputStream().use(::load)
    }
}

fun releaseSigningProperty(localName: String, legacyName: String): String? =
    localProperties.getProperty(localName)?.takeIf(String::isNotBlank)
        ?: legacyKeystoreProperties.getProperty(legacyName)?.takeIf(String::isNotBlank)

val releaseStoreFile = releaseSigningProperty("MINOVA_RELEASE_STORE_FILE", "storeFile")
val releaseStorePassword = releaseSigningProperty("MINOVA_RELEASE_STORE_PASSWORD", "storePassword")
val releaseKeyAlias = releaseSigningProperty("MINOVA_RELEASE_KEY_ALIAS", "keyAlias")
val releaseKeyPassword = releaseSigningProperty("MINOVA_RELEASE_KEY_PASSWORD", "keyPassword")
val releaseSigningIsConfigured = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() }
val homeSdkEnabled = (
    providers.gradleProperty("MINOVA_HOME_SDK_ENABLED").orNull
        ?: localProperties.getProperty("MINOVA_HOME_SDK_ENABLED")
    )
    ?.toBooleanStrictOrNull()
    ?: false

android {
    namespace = "com.minova.cinema"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.minova.cinema"
        // Google Home APIs require Android 10. Minova Cinema 2.6.1 and newer
        // intentionally use API 29 as the public platform baseline.
        minSdk = 29
        targetSdk = 37
        versionCode = 35
        versionName = "2.7.0"

        buildConfigField("String", "PLEX_CLIENT_ID", "\"MinovaCinema\"")
        buildConfigField("String", "UPDATE_GITHUB_OWNER", "\"minova-chromium\"")
        buildConfigField(
            "String",
            "UPDATE_GITHUB_REPOSITORY",
            "\"Minova-Android-Tv-Cinema-Application\"",
        )
        buildConfigField("boolean", "GOOGLE_HOME_SDK_ENABLED", homeSdkEnabled.toString())
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    signingConfigs {
        create("release") {
            // A deliberately missing file keeps Android Studio debug sync usable,
            // while validateSigningRelease prevents an unsigned release artifact.
            storeFile = rootProject.file(
                releaseStoreFile ?: "release-signing/RELEASE_KEYSTORE_NOT_CONFIGURED.jks",
            )
            storePassword = releaseStorePassword ?: "NOT_CONFIGURED"
            keyAlias = releaseKeyAlias ?: "NOT_CONFIGURED"
            keyPassword = releaseKeyPassword ?: "NOT_CONFIGURED"
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/DEPENDENCIES",
        )
    }

    if (homeSdkEnabled) {
        // AGP 9's built-in Kotlin compiler only discovers additional Kotlin
        // directories through AndroidSourceSet.kotlin. Registering this as a
        // Java directory silently omitted the Home controller from v2.6.1.
        sourceSets.getByName("main").kotlin.directories.add("src/homeApi/java")
    }
}

// Give command-line and CI builds a useful error before AGP's generic missing
// keystore failure. The release build type above is always attached to the same
// named signing configuration, so an unsigned release APK cannot be produced.
gradle.taskGraph.whenReady {
    val releaseRequested = allTasks.any { task ->
        task.project == project && task.name.contains("release", ignoreCase = true)
    }
    if (releaseRequested && !releaseSigningIsConfigured) {
        throw GradleException(
            "Release signing is not configured. Add the MINOVA_RELEASE_* values " +
                "to the ignored local.properties file; see README.md.",
        )
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.00")

    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.10.0")
    implementation("androidx.navigation:navigation-compose:2.9.8")
    implementation("androidx.datastore:datastore-preferences:1.2.0")
    implementation("androidx.security:security-crypto:1.1.0")

    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.tv:tv-material:1.1.0")

    implementation("androidx.media3:media3-exoplayer:1.10.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.10.1")
    implementation("androidx.media3:media3-ui:1.10.1")

    // Google requires the Home SDK to be downloaded into mavenLocal() from its
    // signed-in developer portal. Keep ordinary contributors/builds working
    // until that private SDK is configured locally.
    if (homeSdkEnabled) {
        // Home is delivered as an optional Google Play services module. A
        // direct current base dependency provides ModuleInstallClient so a
        // sideloaded TV build can request that module before authorization.
        implementation("com.google.android.gms:play-services-base:18.10.1")
        implementation("com.google.android.gms:play-services-home:17.1.0")
        implementation("com.google.android.gms:play-services-home-types:17.1.0")
    }

    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-gson:3.0.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    implementation("io.coil-kt.coil3:coil-compose:3.5.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.5.0")
    implementation("io.coil-kt.coil3:coil-svg:3.5.0")

    testImplementation("junit:junit:4.13.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
