import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.retrofix)
}

android {
    compileSdk = 36
    namespace = "uk.nktnet.webviewkiosk"

    defaultConfig {
        applicationId = "uk.nktnet.webviewkiosk"
        minSdk = 21
        targetSdk = 36
        versionCode = 110
        versionName = "0.25.5"
        buildConfigField("int", "MIN_SDK_VERSION", "$minSdk")
    }

    buildFeatures {
        buildConfig = true
        compose = true
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
        resources {
            excludes += listOf(
                "META-INF/INDEX.LIST",
                "META-INF/io.netty.versions.properties"
            )
        }
    }

    base {
        archivesName.set(
            "${defaultConfig.applicationId}-v${defaultConfig.versionCode}-${defaultConfig.versionName}"
        )
    }

    applicationVariants.all {
        outputs.all {
            val outputImpl = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            val appIdFormatted = applicationId.replace('.', '_')
            val apkName = "${appIdFormatted}-v${versionName}.apk"
            outputImpl.outputFileName = apkName
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file("keystore.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
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
            signingConfig = signingConfigs.getByName("release")
        }
        applicationVariants.all {
            outputs.all {
                val outputImpl = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
                val appId = applicationId.replace('.', '_')
                val versionName = versionName
                val apkName = "${appId}-v$versionName.apk"
                outputImpl.outputFileName = apkName
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
    implementation(libs.hivemq.mqtt.client)
    implementation(platform(libs.hivemq.mqtt.client.websocket))
    implementation(libs.connector)
    retrofix(libs.android.retrostreams)
    retrofix(libs.android.retrofuture)
    implementation(libs.dhizuku.api)
    implementation(libs.hiddenapibypass)
    implementation(libs.reorderable)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
