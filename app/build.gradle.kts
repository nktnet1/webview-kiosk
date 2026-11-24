import com.android.build.api.variant.HostTestBuilder
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.retrofix)
}

android {
    compileSdk {
        version =
            release(37) {
                minorApiLevel = 0
            }
    }
    namespace = "uk.nktnet.webviewkiosk"

    defaultConfig {
        testInstrumentationRunner =
            "uk.nktnet.webviewkiosk.r8.R8RuntimeInstrumentation"
        applicationId = "uk.nktnet.webviewkiosk"
        minSdk = 21
        targetSdk = 37
        versionCode = 143
        versionName = "0.26.28"
        buildConfigField("int", "MIN_SDK_VERSION", "$minSdk")
    }

    buildFeatures {
        aidl = true
        buildConfig = true
        compose = true
    }

    // Optional runtime probes exercise the R8-optimised APK, not debug.
    testBuildType = "smokeRelease"

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                // Robolectric retains SDK sandboxes and graphics state between
                // classes. Size the test JVM separately from Gradle's daemon,
                // and recycle it before the full CI suite exhausts its heap.
                it.maxHeapSize = "2g"
                it.maxParallelForks = 1
                it.forkEvery = 20
                // Retrofix rewrites app classes, but host-test dependencies stay on
                // JVM APIs. Use the matching original classes for real HiveMQ calls.
                val testedVariant =
                    it.name.removePrefix("test").removeSuffix("UnitTest")
                it.classpath =
                    files(
                        tasks.named("compile${testedVariant}Kotlin").map { task ->
                            task.outputs.files
                        }
                    ) + it.classpath
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
        // Retain native symbols for reproducible APK packaging.
        jniLibs.keepDebugSymbols.add("**/*.so")

        resources {
            excludes +=
                listOf(
                    "META-INF/INDEX.LIST",
                    "META-INF/io.netty.versions.properties",
                    // The APK runs generated Protobuf code, not the source schemas.
                    "google/protobuf/*.proto",
                    // GraalVM native-image metadata is not used by Android ART.
                    "META-INF/native-image/**",
                )
        }
    }

    signingConfigs {
        create("release") {
            // Assign the path even when the keystore is decoded after configuration.
            storeFile = file("keystore.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".debug"
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("release")
        }
        // Locally test the release shrinker without a production signing key.
        // This builds a separate APK: the ordinary release variant is unchanged.
        create("smokeRelease") {
            initWith(getByName("release"))
            // Install alongside production with separate settings and data.
            applicationIdSuffix = ".smoke"
            isDebuggable = false
            signingConfig = signingConfigs.getByName("debug")
            // Keep only the Java instrumentation entry point ABI. The probe
            // implementation is compiled and shrunk inside smokeRelease.
            proguardFiles("proguard-smoke-test.pro")
            matchingFallbacks += listOf("release")
        }
    }
}

// AGP reports a generic keystore error when any signing credential is missing.
// Diagnose missing environment variables before its release signing validator runs.
tasks
    .matching { it.name == "validateSigningRelease" }
    .configureEach {
        doFirst {
            val missing =
                listOf("KEYSTORE_PASSWORD", "KEY_ALIAS", "KEY_PASSWORD").filter {
                    System.getenv(it).isNullOrBlank()
                }
            if (missing.isNotEmpty()) {
                throw GradleException(
                    "Missing release signing environment variables: " +
                        missing.joinToString(", ") +
                        ". Gradle does not load .env automatically."
                )
            }
        }
    }

// AGP 9 exposes per-variant host-test enablement through this incubating API.
// Keep both the debug Robolectric suite and the smokeRelease R8 probes active.
@Suppress("UnstableApiUsage")
androidComponents {
    // AGP 9 only creates host unit tests for testBuildType (smokeRelease) by
    // default. Restore the other variants so CI can run debug Robolectric
    // tests and Compose Preview can package resources for every variant.
    beforeVariants(selector().all()) {
        it.hostTests[HostTestBuilder.UNIT_TEST_TYPE]?.enable = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

afterEvaluate {
    // Apply after built-in Kotlin assigns defaults when creating Android variants.
    tasks.withType<KotlinCompile>().configureEach {
        // Avoid corrupt incremental caches and stale declarations in this module.
        // Gradle up-to-date checks still skip unchanged tasks.
        incremental = false
    }
}

dependencies {
    implementation(project(":adblock"))
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
    implementation(libs.re2j)
    implementation(libs.hivemq.mqtt.client)
    // HiveMQ references the SLF4J API directly; do not mask missing classes in R8.
    implementation(libs.slf4j.api)
    implementation(platform(libs.hivemq.mqtt.client.websocket))
    implementation(libs.connector)
    retrofix(libs.android.retrostreams)
    retrofix(libs.android.retrofuture)
    implementation(libs.dhizuku.api)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    implementation(libs.hiddenapibypass)
    implementation(libs.reorderable)
    debugImplementation(libs.androidx.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}

// Use separate classpaths for Android 5.0-6.0 and modern Robolectric tests.
afterEvaluate {
    val modernRuntime = configurations.getByName("debugUnitTestRuntimeClasspath")
    val legacyRobolectricVersion = libs.versions.robolectricLegacy.get()
    val legacyRobolectricRuntime =
        configurations.create("legacyRobolectricRuntimeClasspath") {
            isCanBeConsumed = false
            isCanBeResolved = true
            // Include app dependencies directly instead of AGP's self-project
            // dependency, which has no unambiguous standalone runtime artifact.
            extendsFrom(configurations.getByName("debugRuntimeClasspath"))
            dependencies.addAll(
                modernRuntime.allDependencies
                    .filterNot { it is ProjectDependency && it.path == project.path }
                    .map { it.copy() }
            )
            attributes {
                fun <T : Any> copyAttribute(key: Attribute<T>) {
                    modernRuntime.attributes.getAttribute(key)?.let {
                        attribute(key, it)
                    }
                }
                modernRuntime.attributes.keySet().forEach { copyAttribute(it) }
            }
            // Resolve one consistent graph, including Conscrypt, native runtime
            // and AndroidX Test dependencies, using the legacy runner's versions.
            resolutionStrategy.dependencySubstitution {
                substitute(module("org.robolectric:robolectric"))
                    .using(
                        module(
                            "org.robolectric:robolectric:$legacyRobolectricVersion"
                        )
                    )
            }
        }

    // Robolectric selects SDK variants before executing each test method.
    // Apply the split to every AGP variant, including release and smokeRelease.
    tasks.withType<Test>().configureEach {
        if (name != "testLegacyUnitTest") {
            systemProperty("robolectric.enabledSdks", (24..37).joinToString(","))
        }
    }

    val legacyTest =
        tasks.register<Test>("testLegacyUnitTest") {
            group = "verification"
            description =
                "Run Android 5.0-6.0 tests using Robolectric $legacyRobolectricVersion."

            val modernTest = tasks.named<Test>("testDebugUnitTest").get()
            testClassesDirs = modernTest.testClassesDirs
            // AGP puts both classes and Java resources on the test classpath.
            // Replace their external artifacts by component identity so transformed
            // JARs and transitive dependencies cannot retain modern runner classes.
            val artifactType = Attribute.of("artifactType", String::class.java)
            fun externalArtifacts(configuration: Configuration) =
                listOf("android-classes-jar", "android-java-res").map { type ->
                    configuration.incoming
                        .artifactView {
                            attributes.attribute(artifactType, type)
                            componentFilter { it is ModuleComponentIdentifier }
                        }
                        .files
                }
            classpath =
                modernTest.classpath - files(externalArtifacts(modernRuntime)) +
                    files(externalArtifacts(legacyRobolectricRuntime))
            maxHeapSize = modernTest.maxHeapSize
            maxParallelForks = modernTest.maxParallelForks
            forkEvery = modernTest.forkEvery
            jvmArgs(modernTest.jvmArgs)
            systemProperties(modernTest.systemProperties)
            systemProperty("robolectric.enabledSdks", "21,22,23")
            // Discover tests normally so newly added legacy SDK cases are included.
        }

    tasks.named("test") { dependsOn(legacyTest) }
    tasks.named("check") { dependsOn(legacyTest) }
}
