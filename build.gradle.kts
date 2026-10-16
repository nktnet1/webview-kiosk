// Top-level build file where you can add configuration options common to all
// subprojects/modules.
plugins {
    base
    alias(libs.plugins.spotless)
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

spotless {
    kotlin {
        target("app/src/**/*.kt", "adblock/src/**/*.kt")
        ktfmt(libs.versions.ktfmt.get()).kotlinlangStyle().configure {
            it.setMaxWidth(85)
        }
    }
    kotlinGradle {
        target("*.gradle.kts", "app/*.gradle.kts", "adblock/*.gradle.kts")
        ktfmt(libs.versions.ktfmt.get()).kotlinlangStyle().configure {
            it.setMaxWidth(85)
        }
    }
}

val kotlinBraces =
    configurations.create("kotlinBraces") {
        isCanBeConsumed = false
    }

dependencies {
    kotlinBraces(libs.detekt.cli) {
        // Keep Detekt's Kotlin parser isolated from the build's Kotlin version.
        artifact { classifier = "all" }
        isTransitive = false
    }
}

val kotlinStyleSources =
    files(
        fileTree("app/src") { include("**/*.kt") },
        fileTree("adblock/src") { include("**/*.kt") },
        fileTree(".") {
            include("*.gradle.kts", "app/*.gradle.kts", "adblock/*.gradle.kts")
        },
    )

val checkKotlinBraces =
    tasks.register<JavaExec>("checkKotlinBraces") {
        group = "verification"
        description = "Require braces on single-line and multiline if/else branches."
        classpath = kotlinBraces
        mainClass.set("io.gitlab.arturbosch.detekt.cli.Main")
        inputs.files(kotlinStyleSources)
        inputs.file("config/detekt/braces.yml")
        outputs.file(layout.buildDirectory.file("reports/detekt/braces.txt"))
        args(
            "--input",
            "app/src,adblock/src,build.gradle.kts,settings.gradle.kts," +
                "app/build.gradle.kts,adblock/build.gradle.kts",
            "--config",
            "config/detekt/braces.yml",
            "--report",
            "txt:build/reports/detekt/braces.txt",
        )
    }

val checkFormatting =
    tasks.register("checkFormatting") {
        group = "verification"
        description = "Check Kotlin formatting and require if/else braces."
        dependsOn("spotlessCheck", checkKotlinBraces)
    }

tasks.named("check") {
    dependsOn(checkFormatting)
}
