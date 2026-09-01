import java.time.Instant
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// --- Version identity (plan §10.1) -------------------------------------------
// One number, everywhere: versionCode is DERIVED from versionName so the two
// can never drift. 1.4.0 -> 10400. Caps at 99 minor / 99 patch per major.
val appVersionName = "0.1.11"
val appVersionCode = appVersionName.split(".")
    .map(String::toInt)
    .let { (maj, min, patch) -> maj * 10_000 + min * 100 + patch }

// Tolerates "not a git repo yet" — the build must not depend on git existing.
val gitSha: String = runCatching {
    providers.exec {
        commandLine("git", "rev-parse", "--short", "HEAD")
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim().ifEmpty { "nogit" }
}.getOrDefault("nogit")

/**
 * Whether the tree had uncommitted changes at build time.
 *
 * Learned the hard way: 0.1.1 through 0.1.3 were each built *before* their
 * version bump was committed, so every one of them reports the commit before
 * its own — check it out and you get the previous version. That is exactly the
 * failure §10.1 exists to prevent, and it is invisible without this marker,
 * because a plain SHA always looks authoritative.
 *
 * The About screen now shows `abc1234-dirty`, which is unmistakable.
 */
val gitDirty: Boolean = runCatching {
    providers.exec {
        commandLine("git", "status", "--porcelain")
        isIgnoreExitValue = true
    }.standardOutput.asText.get().isNotBlank()
}.getOrDefault(false)

val gitDescription: String = if (gitDirty) gitSha + "-dirty" else gitSha

/**
 * Signing (plan §10). The keystore and its passwords live outside the repo and
 * are gitignored — `keystore.properties` at the project root.
 *
 * **The same key must sign every release, forever.** Obtainium installs updates
 * in place and a signature change fails the install outright, which breaks the
 * update channel permanently rather than just once. Back the keystore up
 * off-machine before the first GitHub release; losing it cannot be undone.
 *
 * A missing properties file is not an error — debug builds must still work on a
 * machine that has never seen the key. Only the release variant needs it.
 */
// `java.util.Properties` fully qualified would resolve against Gradle's own
// `java` extension, not the package — hence the import above.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use(::load)
}
val hasSigningKey = keystoreProperties.getProperty("storeFile") != null

android {
    namespace = "com.ar13x.jarvis"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.ar13x.jarvis"
        // minSdk 31 is deliberate (plan §2): canScheduleExactAlarms() exists from
        // 31, which deletes an entire compatibility branch from the alarm code.
        minSdk = 31
        targetSdk = 36
        versionName = appVersionName
        versionCode = appVersionCode

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "GIT_SHA", "\"$gitDescription\"")
        buildConfigField("String", "BUILD_TIME", "\"${Instant.now()}\"")
        // The gateway is mounted at /api by `tailscale serve`, which strips the
        // prefix — so the app must include it and the OpenAPI paths must not.
        buildConfigField("String", "DEFAULT_GATEWAY_URL", "\"https://gw03.tail9662e3.ts.net/api\"")
    }

    signingConfigs {
        if (hasSigningKey) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                // v2 gives fast verification; v3 lets the key be rotated later
                // without the in-place update breaking.
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            if (hasSigningKey) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    // Room's generated schema, checked in so a migration is reviewable in a diff
    // rather than discovered at runtime.
    ksp { arg("room.schemaLocation", "$projectDir/schemas") }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true

            // Forward `-Djarvis.live.dir=...` into the forked test JVM.
            //
            // Gradle does NOT pass command-line system properties through to the
            // test process, so `LiveContractTest` silently SKIPPED and the run
            // still reported BUILD SUCCESSFUL -- a green suite that had verified
            // nothing. Without this line the only signal is the skip count,
            // which nobody reads.
            all { test ->
                test.systemProperty(
                    "jarvis.live.dir",
                    System.getProperty("jarvis.live.dir") ?: "",
                )
            }
        }
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/LICENSE.md",
            "/META-INF/LICENSE-notice.md",
        )
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)
    implementation(libs.okhttp)
    // Not debugImplementation: NetworkModule references the type, so a
    // debug-only dependency makes the release variant fail to compile — which
    // is exactly how this was found, one phase later than it should have been.
    // The interceptor is behind `if (BuildConfig.DEBUG)`, which R8 folds to
    // false and strips, so release carries the dependency but never the code.
    implementation(libs.okhttp.logging)
    implementation(libs.androidx.datastore.preferences)

    // Reminders (phase E)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

/**
 * One APK asset per release, named consistently (plan §10): `jarvis-v1.4.0.apk`.
 *
 * Multiple assets, or a name that changes shape between releases, force an
 * Obtainium regex filter for no benefit — and `app-release.apk` says nothing
 * about which build it is once it is sitting in a downloads folder next to the
 * last three.
 *
 * Runs off the back of `assembleRelease` so the release checklist (§10.4) has a
 * correctly named file to hand `gh release create` without a manual rename that
 * only has to be forgotten once.
 */
val renameReleaseApk = tasks.register<Copy>("renameReleaseApk") {
    // Held as a local: a lambda that reads a script-level property captures the
    // build script itself, which the configuration cache cannot serialise.
    val apkName = "jarvis-v" + appVersionName + ".apk"
    from(layout.buildDirectory.dir("outputs/apk/release")) { include("*.apk") }
    into(layout.buildDirectory.dir("outputs/release"))
    rename { apkName }
}

tasks.matching { it.name == "assembleRelease" }.configureEach {
    finalizedBy(renameReleaseApk)
}
