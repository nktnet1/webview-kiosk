import java.util.zip.ZipFile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

dependencies {
    implementation(libs.okhttp)
    implementation(libs.re2j)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}

// Use the same PSL as HttpUrl, without runtime access to OkHttp's internal APIs.
abstract class GenerateScriptletSuffixes : DefaultTask() {
    @get:Classpath abstract val suffixClasspath: ConfigurableFileCollection
    @get:OutputFile abstract val destination: RegularFileProperty

    @TaskAction
    fun generate() {
        val entry = "okhttp3/internal/publicsuffix/PublicSuffixDatabase.list"
        val lists =
            suffixClasspath.files.mapNotNull { file ->
                if (file.extension != "jar") {
                    null
                } else {
                    ZipFile(file).use { zip ->
                        zip.getEntry(entry)?.let {
                            zip.getInputStream(it).use { it.readBytes() }
                        }
                    }
                }
            }
        require(lists.size == 1) { "Expected one OkHttp public suffix resource" }
        val output = destination.get().asFile
        output.parentFile.mkdirs()
        output.writeBytes(lists.single())
    }
}

val suffixResources = layout.buildDirectory.dir("generated/scriptlet-suffixes")
val generateScriptletSuffixes =
    tasks.register<GenerateScriptletSuffixes>("generateScriptletSuffixes") {
        suffixClasspath.from(configurations.runtimeClasspath)
        destination.set(suffixResources.map { it.file("adblock/publicsuffix.list") })
    }

sourceSets.main { resources.srcDir(suffixResources) }

tasks.processResources { dependsOn(generateScriptletSuffixes) }
