import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.bundling.Zip
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.register
import io.papermc.paperweight.tasks.CreateBundlerJar
import io.papermc.paperweight.tasks.CreatePaperclipJar
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

plugins {
    java
    id("io.papermc.paperweight.patcher") version "2.0.0-beta.21"
}

val paperMavenPublicUrl = "https://repo.papermc.io/repository/maven-public/"
val huntercoreVersionName = providers.gradleProperty("huntercoreVersion").get().trim()
val huntercoreBuildNumber = providers.environmentVariable("BUILD_NUMBER")
    .orElse(providers.environmentVariable("GITHUB_RUN_NUMBER"))
    .orElse(providers.gradleProperty("huntercoreBuildNumber"))
    .get()
    .trim()
val huntercoreVersionLabel = if (huntercoreBuildNumber.isBlank()) huntercoreVersionName else "$huntercoreVersionName-build.$huntercoreBuildNumber"
val huntercoreReleaseChannel = providers.gradleProperty("releaseChannel").get().trim()
val huntercoreReleaseMaxBytes = providers.gradleProperty("huntercoreReleaseMaxBytes")
    .orElse("220000000")
    .map { value ->
        value.toLongOrNull()?.takeIf { it > 0 }
            ?: error("huntercoreReleaseMaxBytes must be a positive integer, got: $value")
    }
val huntercoreMcVersion = providers.gradleProperty("mcVersion").get().trim()
val huntercoreReleaseJarName = "HunterCore-$huntercoreVersionLabel-MinecraftServer-$huntercoreMcVersion-$huntercoreReleaseChannel.jar"
val huntercoreWebPanelZipName = "HunterCore-$huntercoreVersionLabel-WebPanel-$huntercoreMcVersion-$huntercoreReleaseChannel.zip"

data class EmbeddedLibraryTrim(
    val embeddedPath: String,
    val libraryListPath: String,
    val keepEntry: (String) -> Boolean,
)

fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

fun trimNestedJar(bytes: ByteArray, keepEntry: (String) -> Boolean): ByteArray {
    val output = ByteArrayOutputStream(bytes.size)
    ZipInputStream(bytes.inputStream()).use { input ->
        ZipOutputStream(output).use { zip ->
            while (true) {
                val entry = input.nextEntry ?: break
                val name = entry.name
                if (entry.isDirectory || !keepEntry(name)) {
                    input.closeEntry()
                    continue
                }
                zip.putNextEntry(ZipEntry(name))
                zip.write(input.readBytes())
                zip.closeEntry()
                input.closeEntry()
            }
        }
    }
    return output.toByteArray()
}

fun writeCommonNativePaperclip(sourceJar: File, outputJar: File, trims: List<EmbeddedLibraryTrim>) {
    val trimByEmbeddedPath = trims.associateBy { it.embeddedPath }
    val newHashes = linkedMapOf<String, String>()
    val entries = mutableListOf<Triple<String, Boolean, ByteArray>>()

    ZipInputStream(sourceJar.inputStream().buffered()).use { input ->
        while (true) {
            val entry = input.nextEntry ?: break
            val name = entry.name
            val data = if (entry.isDirectory) ByteArray(0) else input.readBytes()
            val trim = trimByEmbeddedPath[name]
            val finalData = if (trim != null) {
                trimNestedJar(data, trim.keepEntry).also {
                    newHashes[trim.libraryListPath] = sha256Hex(it)
                }
            } else {
                data
            }
            entries.add(Triple(name, entry.isDirectory, finalData))
            input.closeEntry()
        }
    }

    outputJar.parentFile.mkdirs()
    ZipOutputStream(outputJar.outputStream().buffered()).use { output ->
        entries.forEach { (name, directory, data) ->
            output.putNextEntry(ZipEntry(name))
            if (!directory) {
                val finalData = if (name == "META-INF/libraries.list" && newHashes.isNotEmpty()) {
                    val text = data.toString(Charsets.UTF_8)
                    val trailingNewline = text.endsWith("\n")
                    val body = if (trailingNewline) text.dropLast(1) else text
                    body.lines().joinToString("\n") { line ->
                        val parts = line.split("\t")
                        if (parts.size == 3 && newHashes.containsKey(parts[2])) {
                            "${newHashes.getValue(parts[2])}\t${parts[1]}\t${parts[2]}"
                        } else {
                            line
                        }
                    }.let { if (trailingNewline) "$it\n" else it }.toByteArray(Charsets.UTF_8)
                } else {
                    data
                }
                output.write(finalData)
            }
            output.closeEntry()
        }
    }
}

paperweight {
    upstreams.register("purpur") {
        repo = github("PurpurMC", "Purpur")
        ref = providers.gradleProperty("purpurRef")

        patchFile {
            path = "purpur-server/build.gradle.kts"
            outputFile = file("divinemc-server/build.gradle.kts")
            patchFile = file("divinemc-server/build.gradle.kts.patch")
        }
        patchFile {
            path = "purpur-api/build.gradle.kts"
            outputFile = file("divinemc-api/build.gradle.kts")
            patchFile = file("divinemc-api/build.gradle.kts.patch")
        }
        patchRepo("paperApi") {
            upstreamPath = "paper-api"
            patchesDir = file("divinemc-api/paper-patches")
            outputDir = file("paper-api")
        }
        patchDir("purpurApi") {
            upstreamPath = "purpur-api"
            excludes = listOf("build.gradle.kts", "build.gradle.kts.patch", "paper-patches")
            patchesDir = file("divinemc-api/purpur-patches")
            outputDir = file("purpur-api")
        }
    }
}

allprojects {
    apply(plugin = "java")
    apply(plugin = "maven-publish")

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(25)
        }
    }

    tasks.compileJava {
        options.compilerArgs.add("-Xlint:-deprecation")
        options.isWarnings = false
    }

    tasks.withType(JavaCompile::class.java).configureEach {
        options.isFork = true
        options.forkOptions.memoryMaximumSize = "4G"
    }
}

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "maven-publish")

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion = JavaLanguageVersion.of(25)
        }
    }

    tasks.withType<JavaCompile> {
        options.encoding = Charsets.UTF_8.name()
        options.release = 25
        options.isFork = true
    }
    tasks.withType<Javadoc> {
        options.encoding = Charsets.UTF_8.name()
    }
    tasks.withType<ProcessResources> {
        filteringCharset = Charsets.UTF_8.name()
    }
    tasks.withType<Test> {
        testLogging {
            showStackTraces = true
            exceptionFormat = TestExceptionFormat.FULL
            events(TestLogEvent.STANDARD_OUT)
        }
    }

    repositories {
        mavenCentral()
        maven(paperMavenPublicUrl)
        maven("https://jitpack.io")
        maven("https://s01.oss.sonatype.org/content/repositories/snapshots")
    }

    extensions.configure<PublishingExtension> {
        repositories {
            maven("https://repo.bxteam.org/snapshots") {
                name = "divinemc"

                credentials.username = System.getenv("REPO_USERNAME")
                credentials.password = System.getenv("REPO_PASSWORD")
            }
        }
    }
}

val bundledPluginOutput = layout.buildDirectory.dir("huntercore/bundled-plugins")
val huntEngineArtifact = layout.projectDirectory.file("third-party/hunt-engine/target/HuntEngine.jar")
val bundledPluginPackagingTasks = setOf("createPaperclipJar", "packageHunterCoreRelease", "verifyHunterCoreRelease")
val includeBundledPlugins = providers.gradleProperty("includeBundledPlugins")
    .map { value ->
        value.toBooleanStrictOrNull()
            ?: error("Gradle property includeBundledPlugins must be true or false, got: $value")
    }
    .orElse(providers.provider {
        gradle.startParameter.taskNames.any { requestedTask ->
            requestedTask.substringAfterLast(':') in bundledPluginPackagingTasks
        }
    })
val prepareExternalBundledPlugins by tasks.registering(Exec::class) {
    group = "huntercore"
    description = "Downloads and builds external HunterCore bundled plugin jars."
    val outputDir = bundledPluginOutput.get().asFile
    inputs.file(layout.projectDirectory.file("scripts/prepare-bundled-plugins.sh"))
    outputs.dir(outputDir)
    commandLine("bash", layout.projectDirectory.file("scripts/prepare-bundled-plugins.sh").asFile.absolutePath, outputDir.absolutePath)
}

val verifyHuntEngineArtifact by tasks.registering(Exec::class) {
    group = "huntercore"
    description = "Verifies the independently built HuntEngine source provenance and Paper artifact."
    val verificationScript = layout.projectDirectory.file("scripts/verify-hunt-engine-vendor.sh")
    inputs.file(verificationScript)
    inputs.file(huntEngineArtifact)
    commandLine("bash", verificationScript.asFile.absolutePath, huntEngineArtifact.asFile.absolutePath)
}

gradle.projectsEvaluated {
    if (!includeBundledPlugins.get()) {
        return@projectsEvaluated
    }

    val tpaJar = project(":huntercore-plugins:hunter-tpa").tasks.named<Jar>("jar")
    val authJar = project(":huntercore-plugins:hunter-auth").tasks.named<Jar>("jar")
    val toolsJar = project(":huntercore-plugins:hunter-tools").tasks.named<Jar>("jar")

    project(":divinemc-server").tasks.named<ProcessResources>("processResources") {
        dependsOn(prepareExternalBundledPlugins, verifyHuntEngineArtifact, tpaJar, authJar, toolsJar)
        from(bundledPluginOutput.map { it.dir("plugins") }) {
            into("META-INF/huntercore/bundled-plugins")
        }
        from(bundledPluginOutput.map { it.file("bundled-plugins.external.yml") }) {
            into("META-INF/huntercore")
        }
        from(tpaJar.flatMap { it.archiveFile }) {
            into("META-INF/huntercore/bundled-plugins")
            rename { "HunterTPA.jar" }
        }
        from(authJar.flatMap { it.archiveFile }) {
            into("META-INF/huntercore/bundled-plugins")
            rename { "HunterAuth.jar" }
        }
        from(toolsJar.flatMap { it.archiveFile }) {
            into("META-INF/huntercore/bundled-plugins")
            rename { "HunterTools.jar" }
        }
        from(huntEngineArtifact) {
            into("META-INF/huntercore/bundled-plugins")
            rename { "HuntEngine.jar" }
        }
    }
}

tasks.register("printMinecraftVersion") {
    doLast {
        println(providers.gradleProperty("mcVersion").get().trim())
    }
}

val huntercoreWebPanelSource = layout.projectDirectory.dir("huntercore-plugins/hunter-tools/src/main/resources/web-panel")
val preparedHunterCoreWebPanel = layout.buildDirectory.dir("huntercore-web-panel")
val huntercoreWebPanelReadme = """
    HunterCore Web Panel

    Deploy these files to any static web host. Open index.html and fill the HTTPS reverse-proxy URL, for example https://server.example.com.
    The HunterCore backend is HTTP-only and must not be exposed directly to the public Internet.
    The backend-embedded panel at the server root keeps using backend mode and does not show this connection setup.
""".trimIndent() + "\n"
val prepareHunterCoreWebPanel by tasks.registering(Copy::class) {
    group = "huntercore"
    description = "Prepares the standalone HunterCore web panel assets."
    into(preparedHunterCoreWebPanel)
    from(huntercoreWebPanelSource.file("index.html")) {
        filter { line: String -> line.replace("data-panel-mode=\"backend\"", "data-panel-mode=\"frontend\"") }
    }
    from(huntercoreWebPanelSource) {
        include("app.css", "app.js", "panel-bg.jpg")
        into("assets")
    }
    from(resources.text.fromString(huntercoreWebPanelReadme)) {
        rename { "README.txt" }
    }
}

tasks.register<Zip>("packageHunterCoreWebPanel") {
    group = "huntercore"
    description = "Packages the standalone HunterCore web panel for separated frontend deployments."
    dependsOn(prepareHunterCoreWebPanel)
    archiveFileName.set(huntercoreWebPanelZipName)
    destinationDirectory.set(layout.buildDirectory.dir("huntercore-release"))
    from(preparedHunterCoreWebPanel)
}

val huntercoreReleaseAssetJar = layout.buildDirectory.file("huntercore-release/$huntercoreReleaseJarName")

tasks.register("packageHunterCoreRelease") {
    group = "huntercore"
    description = "Builds the paperclip jar, trims bundled native libraries to common server platforms, and copies it to the HunterCore release naming scheme."
    dependsOn(":divinemc-server:createPaperclipJar")
    val releaseJar = layout.projectDirectory.file("divinemc-server/build/libs/$huntercoreReleaseJarName")
    val releaseAssetJar = huntercoreReleaseAssetJar
    outputs.files(releaseJar, releaseAssetJar)
    outputs.upToDateWhen { false }

    doLast {
        val sourceJar = project(":divinemc-server")
            .tasks
            .named<CreatePaperclipJar>("createPaperclipJar")
            .get()
            .outputZip
            .get()
            .asFile
        if (!sourceJar.isFile) {
            error("CreatePaperclipJar did not produce its declared output: $sourceJar")
        }
        val commonNativePrefixes = listOf(
            "linux/amd64/",
            "linux/aarch64/",
            "darwin/x86_64/",
            "darwin/aarch64/",
            "win/amd64/",
            "win/aarch64/",
        )
        val sqliteNativePrefixes = listOf(
            "org/sqlite/native/Linux/x86_64/",
            "org/sqlite/native/Linux/aarch64/",
            "org/sqlite/native/Linux-Musl/x86_64/",
            "org/sqlite/native/Linux-Musl/aarch64/",
            "org/sqlite/native/Mac/x86_64/",
            "org/sqlite/native/Mac/aarch64/",
            "org/sqlite/native/Windows/x86_64/",
            "org/sqlite/native/Windows/aarch64/",
        )
        val trims = listOf(
            EmbeddedLibraryTrim(
                embeddedPath = "META-INF/libraries/com/github/luben/zstd-jni/1.5.7-3/zstd-jni-1.5.7-3.jar",
                libraryListPath = "com/github/luben/zstd-jni/1.5.7-3/zstd-jni-1.5.7-3.jar",
                keepEntry = { entry ->
                    entry.startsWith("META-INF/") ||
                        entry.startsWith("com/github/luben/") ||
                        commonNativePrefixes.any { prefix -> entry.startsWith(prefix) }
                },
            ),
            EmbeddedLibraryTrim(
                embeddedPath = "META-INF/libraries/org/xerial/sqlite-jdbc/3.49.1.0/sqlite-jdbc-3.49.1.0.jar",
                libraryListPath = "org/xerial/sqlite-jdbc/3.49.1.0/sqlite-jdbc-3.49.1.0.jar",
                keepEntry = { entry ->
                    entry.startsWith("META-INF/") ||
                        (entry.startsWith("org/sqlite/") && !entry.startsWith("org/sqlite/native/")) ||
                        sqliteNativePrefixes.any { prefix -> entry.startsWith(prefix) }
                },
            ),
        )
        val output = releaseJar.asFile
        val temporaryOutput = output.resolveSibling("${output.name}.tmp")
        writeCommonNativePaperclip(sourceJar, temporaryOutput, trims)
        Files.move(temporaryOutput.toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING)
        val releaseAsset = releaseAssetJar.get().asFile
        releaseAsset.parentFile.mkdirs()
        Files.copy(output.toPath(), releaseAsset.toPath(), StandardCopyOption.REPLACE_EXISTING)
        val size = output.length()
        val sizeMb = size / 1_000_000.0
        val maximumBytes = huntercoreReleaseMaxBytes.get()
        if (size >= maximumBytes) {
            error(
                "HunterCore release jar is ${"%.2f".format(sizeMb)} MB, " +
                    "expected less than ${"%.2f".format(maximumBytes / 1_000_000.0)} MB",
            )
        }
        if (size >= 110_000_000L) {
            println("HunterCore release jar is large because bundled plugins are embedded: ${"%.2f".format(sizeMb)} MB")
        }
        println("HunterCore release jar: ${output.name} (${"%.2f".format(sizeMb)} MB)")
    }
}

tasks.register<Exec>("verifyHunterCoreRelease") {
    group = "verification"
    description = "Verifies the packaged HunterCore server and web-panel release artifacts."
    dependsOn(
        "packageHunterCoreRelease",
        "packageHunterCoreWebPanel",
        ":divinemc-server:jar",
        ":divinemc-server:createBundlerJar",
        ":divinemc-server:createPaperclipJar",
    )
    inputs.file(layout.projectDirectory.file("scripts/verify-huntercore-release.sh"))
    inputs.file(huntercoreReleaseAssetJar)
    doFirst {
        val paperclip = project(":divinemc-server")
            .tasks
            .named<CreatePaperclipJar>("createPaperclipJar")
            .get()
        val bundler = project(":divinemc-server")
            .tasks
            .named<CreateBundlerJar>("createBundlerJar")
            .get()
        val serverJar = project(":divinemc-server")
            .tasks
            .named<Jar>("jar")
            .get()
            .archiveFile
            .get()
            .asFile
        val webPanel = tasks.named<Zip>("packageHunterCoreWebPanel").get().archiveFile.get().asFile
        commandLine(
            "bash",
            layout.projectDirectory.file("scripts/verify-huntercore-release.sh").asFile.absolutePath,
            huntercoreReleaseAssetJar.get().asFile.absolutePath,
            serverJar.absolutePath,
            paperclip.originalBundlerJar.get().asFile.absolutePath,
            bundler.outputZip.get().asFile.absolutePath,
            webPanel.absolutePath,
        )
        environment("HUNTERCORE_RELEASE_MAX_BYTES", huntercoreReleaseMaxBytes.get().toString())
    }
}
