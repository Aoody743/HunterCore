pluginManagement {
    plugins {
        kotlin("jvm") version "2.3.10"
    }
    repositories {
        gradlePluginPortal()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://maven.fabricmc.net/")
    }
}

// HuntEngine is an independently buildable HunterCraft distribution of the
// CraftEngine Community Edition. Keep its Gradle project separate from
// HunterCore; HunterCore consumes only the verified target/HuntEngine.jar.
rootProject.name = "hunt-engine"
include(":core")
include(":core:adventure")
include(":common-files")
include(":bukkit")
include(":bukkit:legacy")
include(":bukkit:compatibility")
include(":bukkit:compatibility:legacy")
include(":bukkit:loader")
include(":bukkit:proxy")
include(":bukkit:paper-loader")
