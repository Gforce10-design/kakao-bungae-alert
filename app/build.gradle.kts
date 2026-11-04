import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("io.github.takahirom.roborazzi")
}

val oauthProps = Properties().apply {
    val local = rootProject.file("app/provider-customization.properties")
    val example = rootProject.file("app/provider-customization.properties.example")
    val source = when {
        local.exists() -> local
        example.exists() -> example
        else -> null
    }
    if (source != null) source.inputStream().use { load(it) }
}

fun oauthString(key: String): String {
    val raw = oauthProps.getProperty(key).orEmpty().trim()
    val escaped = raw
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
    return "\"$escaped\""
}

fun envOrProp(key: String): String? {
    val fromEnv = System.getenv(key)?.trim()?.takeIf { it.isNotEmpty() }
    if (fromEnv != null) return fromEnv
    return (findProperty(key) as String?)?.trim()?.takeIf { it.isNotEmpty() }
}

fun buildConfigString(raw: String): String {
    val escaped = raw
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "")
        .replace("\r", "")
    return "\"$escaped\""
}

val releaseStoreFile = envOrProp("BUNGAE_STORE_FILE")
val releaseStorePassword = envOrProp("BUNGAE_KEYSTORE_PASSWORD")
val releaseKeyAlias = envOrProp("BUNGAE_KEY_ALIAS")
val releaseKeyPassword = envOrProp("BUNGAE_KEY_PASSWORD")
val hasReleaseSigning = !releaseStoreFile.isNullOrBlank() &&
    !releaseStorePassword.isNullOrBlank() &&
    !releaseKeyAlias.isNullOrBlank() &&
    !releaseKeyPassword.isNullOrBlank()

val bungaeVersionCode = envOrProp("BUNGAE_VERSION_CODE")?.toIntOrNull() ?: 1
val bungaeVersionName = envOrProp("BUNGAE_VERSION_NAME") ?: "1.0.0"
val bungaeGitSha = envOrProp("BUNGAE_GIT_SHA") ?: "dev"
val bungaeUpdateRepo = envOrProp("BUNGAE_UPDATE_REPO") ?: "Gforce10-design/kakao-bungae-alert"
val bungaeUpdateUrl = envOrProp("BUNGAE_UPDATE_URL") ?: ""

android {
    namespace = "app.bungaealert"
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }

    defaultConfig {
        applicationId = "app.bungaealert"
        minSdk = 26
        targetSdk = 37
        versionCode = bungaeVersionCode
        versionName = bungaeVersionName
        buildConfigField(
            "String",
            "ANTHROPIC_OAUTH_IDENTIFIER_PROMPT",
            oauthString("ANTHROPIC_OAUTH_IDENTIFIER_PROMPT"),
        )
        buildConfigField("String", "GIT_SHA", buildConfigString(bungaeGitSha))
        buildConfigField("String", "UPDATE_REPO", buildConfigString(bungaeUpdateRepo))
        buildConfigField("String", "UPDATE_RELEASES_URL", buildConfigString(bungaeUpdateUrl))
    }

    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.maxHeapSize = "2g"
        }
    }
}

roborazzi {
    outputDir.set(rootProject.layout.projectDirectory.dir("docs/screenshots"))
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-process:2.11.0")
    implementation("androidx.navigation:navigation-compose:2.9.5")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.browser:browser:1.9.0")
    implementation("androidx.security:security-crypto:1.1.0")
    implementation("androidx.work:work-runtime-ktx:2.10.5")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    implementation("com.google.mlkit:genai-prompt:1.0.0-beta4")

    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("androidx.test:core-ktx:1.6.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.77.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.77.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-junit-rule:1.77.0")
}
