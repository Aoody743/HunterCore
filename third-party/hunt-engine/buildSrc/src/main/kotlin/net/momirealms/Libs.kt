package net.momirealms

import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.plugins.JavaPlugin
import org.gradle.kotlin.dsl.DependencyHandlerScope
import org.gradle.kotlin.dsl.exclude

/**
 * 获取依赖版本
 */
fun Project.ver(key: String): String {
    return rootProject.properties[key].toString()
}

fun DependencyHandlerScope.nbt(project: Project, configuration: String = JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME) {
    configuration("net.momirealms:sparrow-nbt:${project.ver("sparrow_nbt_version")}")
    configuration("net.momirealms:sparrow-nbt-adventure:${project.ver("sparrow_nbt_version")}")
    configuration("net.momirealms:sparrow-nbt-codec:${project.ver("sparrow_nbt_version")}")
    configuration("net.momirealms:sparrow-nbt-legacy-codec:${project.ver("sparrow_nbt_version")}")
}

fun DependencyHandlerScope.common(project: Project, configuration: String = JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME) {
    fun v(key: String) = project.rootProject.property(key).toString()
    configuration(project.files("${project.rootProject.rootDir}/libs/boosted-yaml-${v("boosted_yaml_version")}.jar"))
    configuration("com.google.guava:guava:${v("guava_version")}")
    configuration("com.google.code.gson:gson:${v("gson_version")}")
    configuration("it.unimi.dsi:fastutil:${v("fastutil_version")}")
    configuration("org.joml:joml:${v("joml_version")}")
    configuration("org.snakeyaml:snakeyaml-engine:${v("snake_yaml_version")}")
    configuration("org.slf4j:slf4j-api:${v("slf4j_version")}")
    configuration("org.apache.logging.log4j:log4j-core:${v("log4j_version")}")
    configuration("com.github.ben-manes.caffeine:caffeine:${v("caffeine_version")}")
    configuration("commons-io:commons-io:${v("commons_io_version")}")
    configuration("com.mojang:datafixerupper:${v("datafixerupper_version")}")
    configuration("com.mojang:authlib:${v("authlib_version")}")
    configuration(project.files("${project.rootProject.rootDir}/libs/leafpile-${v("leafpile_version")}.jar"))
    configuration("org.ahocorasick:ahocorasick:${v("ahocorasick_version")}")
    configuration("com.bucket4j:bucket4j_jdk17-core:${v("bucket4j_version")}")
    configuration("com.ezylang:EvalEx:${v("evalex_version")}")
    configuration("com.google.jimfs:jimfs:${v("jimfs_version")}")
}

fun DependencyHandlerScope.netty(project: Project, configuration: String = JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME) {
    configuration("io.netty:netty-all:${project.ver("netty_version")}")
    configuration("io.netty:netty-codec-http:${project.ver("netty_version")}")
}

fun DependencyHandlerScope.compression(project: Project, configuration: String = JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME) {
    configuration("com.github.luben:zstd-jni:${project.ver("zstd_version")}")
    configuration("at.yawk.lz4:lz4-java:${project.ver("lz4_version")}")
}

fun DependencyHandlerScope.cloud(project: Project, configuration: String = JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME) {
    configuration("com.mojang:brigadier:${project.ver("mojang_brigadier_version")}")
    configuration("org.incendo:cloud-core:${project.ver("cloud_core_version")}")
    configuration("org.incendo:cloud-minecraft-extras:${project.ver("cloud_minecraft_extras_version")}")
    configuration("org.incendo:cloud-paper:${project.ver("cloud_paper_version")}")
}

fun DependencyHandlerScope.paperServer(project: Project, configuration: String = JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME) {
    configuration("io.papermc.paper:paper-api:${project.ver("paper_version")}")
}

fun DependencyHandlerScope.asm(project: Project, configuration: String = JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME) {
    configuration("org.ow2.asm:asm:${project.ver("asm_version")}")
    configuration("net.bytebuddy:byte-buddy:${project.ver("byte_buddy_version")}")
    configuration("net.bytebuddy:byte-buddy-agent:${project.ver("byte_buddy_version")}")
}

fun DependencyHandlerScope.adventure(project: Project, configuration: String = JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME) {
    configuration("net.kyori:adventure-api:${project.ver("adventure_bundle_version")}")
    configuration("net.kyori:adventure-text-minimessage:${project.ver("adventure_bundle_version")}")
    configuration("net.kyori:adventure-text-serializer-json-legacy-impl:${project.ver("adventure_bundle_version")}")
    configuration("net.kyori:adventure-text-serializer-legacy:${project.ver("adventure_bundle_version")}")
    configuration("net.kyori:adventure-text-serializer-gson:${project.ver("adventure_bundle_version")}").apply {
        (this as? ExternalModuleDependency)?.exclude("com.google.code.gson", "gson")
    }
}

/**
 * Libraries that the upstream Community Edition used to resolve after plugin
 * startup. HuntEngine shades them into its standalone Paper/legacy jars so a
 * production server never downloads dependencies on first boot.
 */
fun DependencyHandlerScope.huntEngineOfflineRuntime(project: Project) {
    val implementationConfiguration = JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME
    val root = project.rootProject
    fun v(key: String) = root.property(key).toString()

    nbt(project, implementationConfiguration)
    common(project, implementationConfiguration)
    netty(project, implementationConfiguration)
    compression(project, implementationConfiguration)
    cloud(project, implementationConfiguration)
    adventure(project, implementationConfiguration)
    asm(project, implementationConfiguration)

    add(implementationConfiguration, "org.ow2.asm:asm-commons:${v("asm_commons_version")}")
    // RelocationHandler still uses this API for pack imports.  The upstream
    // dependency manager fetched it lazily; the HuntEngine distribution must
    // carry it because startup and content imports are offline-deployable.
    add(implementationConfiguration, "me.lucko:jar-relocator:${v("jar_relocator_version")}")
    add(implementationConfiguration, "io.leangen.geantyref:geantyref:${v("geantyref_version")}")
    add(implementationConfiguration, "org.incendo:cloud-services:${v("cloud_services_version")}")
    add(implementationConfiguration, "org.incendo:cloud-bukkit:${v("cloud_bukkit_version")}")
    add(implementationConfiguration, "org.incendo:cloud-brigadier:${v("cloud_brigadier_version")}")
    add(implementationConfiguration, "net.momirealms:sparrow-util:${v("sparrow_util_version")}")
    add(implementationConfiguration, "net.momirealms:sparrow-reflection:${v("sparrow_reflection_version")}")
    // `:bukkit` already shades and relocates AntiGriefLib into its published
    // implementation artifact. Both offline loader variants embed that
    // artifact, so adding the raw dependency again here creates duplicate
    // relocated classes in the final standalone jar.
    add(implementationConfiguration, "net.momirealms:craft-engine-s3:0.21")
    add(implementationConfiguration, "org.bstats:bstats-bukkit:${v("bstats_version")}")
}
