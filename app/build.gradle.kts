import java.io.File
import java.util.Locale
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
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
    // Match the native toolchain used by CI. AGP can provision this version via the SDK manager.
    ndkVersion = "29.0.14206865"

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

// Keep native build execution entirely within a typed, configuration-cache-safe task.
// No Gradle Project or Kotlin build-script objects are referenced by the task action.
@DisableCachingByDefault(because = "Cargo dependencies and the Rust toolchain are external to Gradle inputs")
abstract class BuildNativeAdblockTask : DefaultTask() {
    @get:Internal
    abstract val crateDirectory: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceFiles: ConfigurableFileCollection

    @get:Internal
    abstract val sdkDirectory: DirectoryProperty

    @get:Optional
    @get:Input
    abstract val ndkPathOverride: Property<String>

    @get:Input
    abstract val ndkRevision: Property<String>

    @get:Input
    abstract val minAndroidApi: Property<Int>

    @get:Input
    abstract val cargoExecutable: Property<String>

    @get:Internal
    abstract val cargoTargetDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val jniLibsDirectory: DirectoryProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    fun buildNative() {
        // Resolve the SDK through AGP, but do not query its NDK provider: that forces
        // NDK resolution during configuration-cache serialization, even for --dry-run.
        val ndk = ndkPathOverride.orNull?.let(::File)
            ?: File(sdkDirectory.get().asFile, "ndk/${ndkRevision.get()}")
        if (!ndk.isDirectory) {
            throw GradleException(
                "Android NDK ${ndkRevision.get()} is required for native content blocking. " +
                    "Expected NDK at $ndk. Install the specified version in Android Studio " +
                    "(SDK Manager > SDK Tools > Show Package Details > NDK (Side by side)), " +
                    "or use sdkmanager --install \"ndk;${ndkRevision.get()}\". " +
                    "Alternatively set ANDROID_NDK_HOME to your installed NDK directory."
            )
        }
        val prebuilts = File(ndk, "toolchains/llvm/prebuilt")
            .listFiles()
            ?.filter { it.isDirectory && File(it, "bin").isDirectory }
            .orEmpty()
        if (prebuilts.size != 1) {
            throw GradleException("Android NDK ${ndk.absolutePath} has no usable LLVM prebuilt toolchain")
        }
        val bin = File(prebuilts.single(), "bin")
        val androidApi = minAndroidApi.get()
        val isWindows = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
        val clangSuffix = if (isWindows) ".cmd" else ""
        val targets = listOf(
            Triple("arm64-v8a", "aarch64-linux-android", "aarch64-linux-android"),
            Triple("armeabi-v7a", "armv7-linux-androideabi", "armv7a-linux-androideabi"),
            Triple("x86", "i686-linux-android", "i686-linux-android"),
            Triple("x86_64", "x86_64-linux-android", "x86_64-linux-android"),
        )
        val crate = crateDirectory.get().asFile
        val cargoTarget = cargoTargetDirectory.get().asFile
        val output = jniLibsDirectory.get().asFile

        for ((abi, triple, clangPrefix) in targets) {
            val linker = File(bin, "$clangPrefix$androidApi-clang$clangSuffix")
            if (!linker.isFile) {
                throw GradleException("Android NDK is missing the $triple linker: $linker")
            }
            val cargoTargetVariable = triple.uppercase(Locale.ROOT).replace('-', '_')
            try {
                execOperations.exec {
                    workingDir = crate
                    executable = cargoExecutable.get()
                    args(
                        "build", "--release", "--manifest-path", File(crate, "Cargo.toml").absolutePath,
                        "--target", triple,
                    )
                    environment("CARGO_TARGET_${cargoTargetVariable}_LINKER", linker.absolutePath)
                    environment("CC_${triple.replace('-', '_')}", linker.absolutePath)
                    environment("CARGO_TARGET_DIR", cargoTarget.absolutePath)
                }
            } catch (error: Exception) {
                throw GradleException(
                    "Rust compilation failed for $triple using ${cargoExecutable.get()}. " +
                        "Check Cargo's output and ensure Rust Android target '$triple' is installed " +
                        "(rustup target add $triple).",
                    error,
                )
            }
            val artifact = File(cargoTarget, "$triple/release/libwk_adblock.so")
            if (!artifact.isFile) {
                throw GradleException("Cargo did not produce the expected native library: $artifact")
            }
            val abiDir = File(output, abi)
            if (!abiDir.isDirectory && !abiDir.mkdirs()) {
                throw GradleException("Cannot create native library output directory $abiDir")
            }
            artifact.copyTo(File(abiDir, "libwk_adblock.so"), overwrite = true)
        }
    }
}

val nativeAdblockSource = layout.projectDirectory.dir("native-adblock")
val nativeAdblockJniLibs = layout.buildDirectory.dir("generated/native-adblock/jniLibs")

val buildNativeAdblock = tasks.register<BuildNativeAdblockTask>("buildNativeAdblock") {
    group = "build"
    description = "Compile the Rust content-blocking engine for all Android ABIs"
    crateDirectory.set(nativeAdblockSource)
    sourceFiles.from(nativeAdblockSource.file("Cargo.toml"), nativeAdblockSource.dir("src"))
    cargoTargetDirectory.set(layout.buildDirectory.dir("native-adblock/target"))
    jniLibsDirectory.set(nativeAdblockJniLibs)
    ndkRevision.set(android.ndkVersion)
    minAndroidApi.set(21)
    // Android Studio launched from Finder/Dock may not inherit ~/.cargo/bin on PATH.
    val defaultCargo = providers.systemProperty("user.home").map { home ->
        File(home, ".cargo/bin/cargo").takeIf { it.isFile }?.absolutePath ?: "cargo"
    }
    cargoExecutable.set(providers.environmentVariable("CARGO")
        .orElse(providers.environmentVariable("CARGO_HOME").map { File(it, "bin/cargo").absolutePath })
        .orElse(defaultCargo))
    // AGP resolves the correct SDK even when ANDROID_HOME is unset (Android Studio).
    sdkDirectory.set(androidComponents.sdkComponents.sdkDirectory)
    ndkPathOverride.set(providers.environmentVariable("ANDROID_NDK_HOME")
        .orElse(providers.environmentVariable("ANDROID_NDK_ROOT")))
}

android.sourceSets.getByName("main").jniLibs.directories.add(nativeAdblockJniLibs.get().asFile.absolutePath)
tasks.matching { it.name.matches(Regex("merge[A-Z].*JniLibFolders")) }.configureEach {
    dependsOn(buildNativeAdblock)
}
