import java.util.Locale

pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

if (!file(".git").exists()) {
    val errorText = """
        
        =====================[ ERROR ]=====================
         The HunterCore project directory is not a properly cloned Git repository.
         
         In order to build HunterCore from source you must clone
         the HunterCore repository using Git, not download a code
         zip from GitHub.
         
         Built HunterCore jars are available from GitHub Releases:
         https://github.com/AndyXeCM/HunterCore/releases
         
         See README.md for further information on building and modifying HunterCore.
        ===================================================
    """.trimIndent()
    error(errorText)
}

rootProject.name = "HunterCore"

for (name in listOf("divinemc-api", "divinemc-server", "huntercore-plugins:hunter-tpa", "huntercore-plugins:hunter-auth", "huntercore-plugins:hunter-tools")) {
    val projName = name.lowercase(Locale.ENGLISH)
    include(projName)
    findProject(":$projName")!!.projectDir = file(name.replace(":", "/"))
}

gradle.lifecycle.beforeProject {
    val mcVersion = providers.gradleProperty("mcVersion").get().trim()
    val divinemcChannel = providers.gradleProperty("channel").get().trim()
    val divinemcBuildNumber = providers.environmentVariable("BUILD_NUMBER").orNull?.trim()?.toInt()
    val versionString = if (divinemcBuildNumber == null) {
        "$mcVersion.local-SNAPSHOT"
    } else {
        "$mcVersion.build.$divinemcBuildNumber-${divinemcChannel.lowercase()}"
    }
    version = versionString
}
