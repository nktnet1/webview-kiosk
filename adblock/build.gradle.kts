import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.net.IDN
import java.util.Locale
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
    @get:OutputFile abstract val serializedDestination: RegularFileProperty

    @TaskAction
    fun generate() {
        val entry = "okhttp3/internal/publicsuffix/PublicSuffixDatabase.list"
        val lists =
            suffixClasspath.files.mapNotNull { file ->
                if (file.extension != "jar") {
                    null
                } else {
                    ZipFile(file).use { zip ->
                        zip.getEntry(entry)?.let { zipEntry ->
                            zip.getInputStream(zipEntry).use { stream ->
                                stream.readBytes()
                            }
                        }
                    }
                }
            }
        require(lists.size == 1) { "Expected one OkHttp public suffix resource" }
        val output = destination.get().asFile
        output.parentFile.mkdirs()
        val source = lists.single()
        output.writeBytes(source)
        // Prepare the public suffix data at build time instead of normalising
        // thousands of hostnames on every Android process start.
        val serialized =
            DataInputStream(ByteArrayInputStream(source)).use { input ->
                fun readList(): String {
                    val length = input.readInt()
                    require(length in 0..1024 * 1024)
                    val bytes = ByteArray(length)
                    input.readFully(bytes)
                    return bytes
                        .toString(Charsets.UTF_8)
                        .lineSequence()
                        .filter { it.isNotEmpty() }
                        .map { suffix ->
                            val wildcard = suffix.startsWith("*.")
                            val host =
                                IDN.toASCII(suffix.removePrefix("*."))
                                    .lowercase(Locale.ROOT)
                            require(
                                host.isNotEmpty() &&
                                    host.length <= 253 &&
                                    host.all {
                                        it in 'a'..'z' ||
                                            it in '0'..'9' ||
                                            it == '-' ||
                                            it == '.'
                                    }
                            )
                            if (wildcard) "*.$host" else host
                        }
                        .distinct()
                        .sorted()
                        .joinToString("\n", postfix = "\n")
                }
                val rules = readList()
                val exceptions = readList()
                require(input.read() == -1) { "Unexpected public suffix data" }
                // Normalised hosts contain only safe ASCII characters. Match the
                // runtime JSON encoder's newline escaping exactly.
                fun json(value: String) = "\"${value.replace("\n", "\\u000a")}\""
                "[${json(rules)},${json(exceptions)}]"
            }
        val dispatcher = serializedDestination.get().asFile
        dispatcher.parentFile.mkdirs()
        dispatcher.writeText(serialized, Charsets.UTF_8)
    }
}

val suffixResources = layout.buildDirectory.dir("generated/scriptlet-suffixes")
val generateScriptletSuffixes =
    tasks.register<GenerateScriptletSuffixes>("generateScriptletSuffixes") {
        group = "build"
        description = "Extract OkHttp's public suffix list for scriptlet routing."
        suffixClasspath.from(configurations.runtimeClasspath)
        destination.set(suffixResources.map { it.file("adblock/publicsuffix.list") })
        serializedDestination.set(
            suffixResources.map { it.file("adblock/publicsuffix-serialized.json") }
        )
    }

sourceSets.main { resources.srcDir(suffixResources) }

tasks.processResources { dependsOn(generateScriptletSuffixes) }
