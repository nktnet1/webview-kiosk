import java.io.File
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.retrofix)
}

android {
    compileSdk {
        version = release(37) {
            minorApiLevel = 0
        }
    }
    namespace = "uk.nktnet.webviewkiosk"

    defaultConfig {
        applicationId = "uk.nktnet.webviewkiosk"
        minSdk = 21
        targetSdk = 37
        versionCode = 136
        versionName = "0.26.22"
        buildConfigField("int", "MIN_SDK_VERSION", "$minSdk")
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                // Retrofix rewrites app classes, but host-test dependencies stay on
                // JVM APIs. Use the matching original classes for real HiveMQ calls.
                val testedVariant = it.name.removePrefix("test").removeSuffix("UnitTest")
                it.classpath = files(tasks.named("compile${testedVariant}Kotlin").map { task ->
                    task.outputs.files
                }) + it.classpath
                it.jvmArgs(
                    "--add-opens=java.base/java.lang=ALL-UNNAMED",
                    "--add-opens=java.base/java.util=ALL-UNNAMED",
                    "--add-opens=java.base/java.io=ALL-UNNAMED",
                    "--add-opens=java.base/java.net=ALL-UNNAMED",
                    "--add-opens=java.base/java.security=ALL-UNNAMED",
                    "--add-opens=java.base/java.text=ALL-UNNAMED",
                    "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
                    "--add-opens=java.desktop/java.awt.font=ALL-UNNAMED",
                    "--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
                )
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    dependenciesInfo {
        // https://gitlab.com/fdroid/fdroiddata/-/issues/3330
        includeInApk = false
        includeInBundle = false
    }

    packaging {
        // https://izzyondroid.org/docs/reproducibleBuilds/DebugFailedRBs/#native-library-stripping
        jniLibs.keepDebugSymbols.add("**/*.so")

        resources {
            excludes += listOf(
                "META-INF/INDEX.LIST",
                "META-INF/io.netty.versions.properties"
            )
        }
    }

    signingConfigs {
        create("release") {
            val keystore = file("keystore.jks")
            if (keystore.exists()) {
                storeFile = keystore
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (file("keystore.jks").exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.hivemq.mqtt.client)
    implementation(platform(libs.hivemq.mqtt.client.websocket))
    implementation(libs.connector)
    retrofix(libs.android.retrostreams)
    retrofix(libs.android.retrofuture)
    implementation(libs.dhizuku.api)
    implementation(libs.hiddenapibypass)
    implementation(libs.reorderable)
    debugImplementation(libs.androidx.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}

// Build native filter matching from source, rather than checking large ABI binaries into git.
// Host unit tests do not package JNI libraries and need no NDK/Rust installation.
val nativeAdblockSource = layout.projectDirectory.dir("native-adblock").asFile
val nativeAdblockTarget = layout.buildDirectory.dir("native-adblock/target").get().asFile
val nativeAdblockJniLibs = layout.buildDirectory.dir("generated/native-adblock/jniLibs").get().asFile
val localSdkDirectory = rootProject.file("local.properties").takeIf { it.isFile }?.let { propertiesFile ->
    Properties().apply { propertiesFile.inputStream().use(::load) }
        .getProperty("sdk.dir")
}
val nativeAdblockSdkHome = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
    ?: localSdkDirectory
val nativeAdblockNdkHome = System.getenv("ANDROID_NDK_HOME") ?: System.getenv("ANDROID_NDK_ROOT")

val buildNativeAdblock = tasks.register("buildNativeAdblock") {
    group = "build"
    description = "Compile the Rust content-blocking engine for all Android ABIs"
    inputs.file(nativeAdblockSource.resolve("Cargo.toml"))
    inputs.dir(nativeAdblockSource.resolve("src"))
    outputs.dir(nativeAdblockJniLibs)
    doLast {
        val sdkNdk = nativeAdblockSdkHome?.let { File(it, "ndk") }
        val ndkDirectory = nativeAdblockNdkHome?.let { File(it) }
            ?: sdkNdk?.listFiles()?.filter { it.isDirectory }?.maxByOrNull { it.name }
            ?: throw GradleException("Android NDK required for the Rust content-blocking engine")
        val llvmPrebuilt = File(ndkDirectory, "toolchains/llvm/prebuilt")
            .listFiles()?.firstOrNull { it.isDirectory }
            ?: throw GradleException("No LLVM toolchain found in $ndkDirectory")
        val bin = File(llvmPrebuilt, "bin")
        val rustTargets = listOf(
            Triple("arm64-v8a", "aarch64-linux-android", "aarch64-linux-android"),
            Triple("armeabi-v7a", "armv7-linux-androideabi", "armv7a-linux-androideabi"),
            Triple("x86", "i686-linux-android", "i686-linux-android"),
            Triple("x86_64", "x86_64-linux-android", "x86_64-linux-android"),
        )
        for ((abi, target, clangPrefix) in rustTargets) {
            val linker = File(bin, "${clangPrefix}21-clang")
            if (!linker.isFile) throw GradleException("Missing Android linker $linker")
            val command = listOf(
                "cargo", "build", "--release", "--manifest-path",
                File(nativeAdblockSource, "Cargo.toml").absolutePath,
                "--target", target,
            )
            val builder = ProcessBuilder(command)
                .directory(nativeAdblockSource)
                .inheritIO()
            val environment = builder.environment()
            val envTarget = target.uppercase().replace('-', '_')
            environment["CARGO_TARGET_${envTarget}_LINKER"] = linker.absolutePath
            environment["CC_${target.replace('-', '_')}"] = linker.absolutePath
            environment["CARGO_TARGET_DIR"] = nativeAdblockTarget.absolutePath
            val result = builder.start().waitFor()
            if (result != 0) {
                throw GradleException("Rust build failed for $target ($result). Install the Rust Android target and use Cargo offline caches as needed.")
            }
            val artifact = File(nativeAdblockTarget, "$target/release/libwk_adblock.so")
            val abiDir = File(nativeAdblockJniLibs, abi)
            if (!abiDir.isDirectory && !abiDir.mkdirs()) {
                throw GradleException("Cannot create $abiDir")
            }
            artifact.copyTo(File(abiDir, "libwk_adblock.so"), overwrite = true)
        }
    }
}

android.sourceSets.getByName("main").jniLibs.directories.add(nativeAdblockJniLibs.absolutePath)
tasks.matching { it.name.matches(Regex("merge[A-Z].*JniLibFolders")) }.configureEach {
    dependsOn(buildNativeAdblock)
}
