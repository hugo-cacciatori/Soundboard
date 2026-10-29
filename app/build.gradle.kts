import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// The owner's signing key, described by the git-ignored keystore.properties (storeFile,
// storePassword, keyAlias, keyPassword). It signs every APK, debug builds included, so any build
// installs over any other and keeps the user's buttons. Without it (a fresh clone), debug builds
// use the default debug key and release builds are unsigned.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val hasOwnerKey = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
    .all { !keystoreProperties.getProperty(it).isNullOrBlank() }
if (!hasOwnerKey) logger.warn("keystore.properties missing or incomplete: debug key for debug builds, release unsigned")

// The git commit a debug build comes from, shown next to the version so the owner knows which
// build is on the phone; "+" marks uncommitted changes. Empty when git isn't available.
fun git(vararg args: String): String = runCatching {
    providers.exec {
        commandLine("git", *args)
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim()
}.getOrDefault("")
val gitCommit = git("rev-parse", "--short", "HEAD").let { commit ->
    if (commit.isNotEmpty() && git("status", "--porcelain").isNotEmpty()) "$commit+" else commit
}

android {
    namespace = "com.example.soundboard"
    compileSdk {
        version = release(37)
    }

    signingConfigs {
        if (hasOwnerKey) {
            create("owner") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    defaultConfig {
        // Never change after the first release: Android would treat it as another app.
        applicationId = "io.github.hugocacciatori.soundboard"
        minSdk = 24
        targetSdk = 37
        versionCode = 2
        versionName = "1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            if (hasOwnerKey) signingConfig = signingConfigs.getByName("owner")
            buildConfigField("String", "GIT_COMMIT", "\"$gitCommit\"")
        }
        release {
            if (hasOwnerKey) signingConfig = signingConfigs.getByName("owner")
            // A release is identified by its version alone.
            buildConfigField("String", "GIT_COMMIT", "\"\"")
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
                keepRules { files.add(file("keep-rules.pro")) }
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// Room writes each schema version to app/schemas (committed), so the upgrade tests can check
// that old databases migrate to exactly the schema the app expects.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    testImplementation(libs.sqlite.jdbc)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
