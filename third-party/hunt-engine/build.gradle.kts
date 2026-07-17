import net.momirealms.PublishExtension
import net.momirealms.RelocationExtension

plugins {
    id("java")
}

subprojects {

    apply {
        plugin("java")
        plugin("java-library")
        plugin("com.gradleup.shadow")
        plugin("maven-publish")
    }

    repositories {
        mavenCentral()
        maven("https://oss.sonatype.org/content/repositories/snapshots")
    }

    extensions.create<RelocationExtension>("relocation")
    extensions.create<PublishExtension>("publication")

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release.set(21)
    }

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(25)
        }
        withSourcesJar()
        disableAutoTargetJvm()
    }

    tasks.withType<org.gradle.api.tasks.bundling.AbstractArchiveTask>().configureEach {
        // The bundled server distribution must be reproducible.  Do not let
        // local file timestamps leak into HuntEngine.jar.
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }

    tasks.processResources {
        filteringCharset = "UTF-8"

        filesMatching(arrayListOf("craft-engine.properties")) {
            expand(
                rootProject.properties + mapOf(
                    "proxy_version" to getTimestamp(),
                    "git_version" to versionBanner(),
                    "builder" to builderName()
                )
            )
        }

        filesMatching(arrayListOf("commands.yml", "config.yml")) {
            expand(
                Pair("config_version", rootProject.properties["config_version"]!!)
            )
        }
    }
}

// This source snapshot is deliberately vendored without a nested .git
// directory.  Keep all generated build metadata pinned in gradle.properties
// rather than reading the builder, wall clock, or parent repository state.
fun versionBanner(): String = rootProject.properties["huntengine_upstream_commit"]
    .toString()
    .take(8)

fun builderName(): String = "HunterCraft"

fun getTimestamp(): String = rootProject.properties["huntengine_upstream_commit"]
    .toString()
    .take(8)

tasks.register("assembleHuntEngine") {
    group = "build"
    description = "Build the deterministic standalone target/HuntEngine.jar distribution."
    dependsOn(":bukkit:paper-loader:shadowJar")
}
