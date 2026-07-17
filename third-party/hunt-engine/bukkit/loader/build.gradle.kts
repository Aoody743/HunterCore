import net.momirealms.paperServer
import net.momirealms.huntEngineOfflineRuntime

plugins {
    id("de.eldoria.plugin-yml.bukkit") version "0.7.1"
}

repositories {
    maven("https://jitpack.io/")
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.momirealms.net/releases/")
    maven("https://repo.gtemc.net/releases/")
    mavenCentral()
}

dependencies {
    // Platform
    paperServer(project)

    // Keep the legacy artifact offline-deployable as well.
    huntEngineOfflineRuntime(project)

    implementation(project(":core"))
    implementation(project(":bukkit")) {
        exclude(group = "net.momirealms", module = "antigrieflib")
    }
    implementation(project(":bukkit:legacy"))
    implementation(project(":bukkit:compatibility"))
    implementation(project(":bukkit:compatibility:legacy"))
    implementation(project(":common-files"))

    // leafpile
    implementation(files("${rootProject.rootDir}/libs/leafpile-${rootProject.properties["leafpile_version"]}.jar"))

    implementation("net.momirealms:sparrow-util:${rootProject.properties["sparrow_util_version"]}")
    implementation("net.momirealms:craft-engine-nms-helper:${rootProject.properties["nms_helper_version"]}")
    implementation("cn.gtemc:itembridge:${rootProject.properties["itembridge_version"]}")
    implementation("cn.gtemc:levelerbridge:${rootProject.properties["levelerbridge_version"]}")
    implementation(files("${rootProject.rootDir}/libs/jni-internal-lookup-1.9.jar"))
}

bukkit {
    load = net.minecrell.pluginyml.bukkit.BukkitPluginDescription.PluginLoadOrder.STARTUP
    main = "net.momirealms.craftengine.bukkit.plugin.BukkitCraftEnginePlugin"
    version = rootProject.properties["project_version"] as String
    name = "HuntEngine"
    apiVersion = "1.20"
    authors = listOf("XiaoMoMi", "HunterCraft")
    contributors = listOf("https://github.com/Xiao-MoMi/craft-engine/graphs/contributors")
    softDepend = listOf("WorldEdit", "FastAsyncWorldEdit")
    foliaSupported = true
}

artifacts {
    implementation(tasks.shadowJar)
}

val proxyShadowJar = project(":bukkit:proxy").tasks.shadowJar

tasks {
    shadowJar {
        relocation.applyCommon(this)
        dependsOn(proxyShadowJar)
        from(proxyShadowJar.flatMap { it.archiveFile })
        from(proxyShadowJar.flatMap { it.archiveFile }.map { zipTree(it.asFile) }) {
            // Avoid importing the proxy jar's separately relocated runtime
            // libraries.  The outer HuntEngine jar shades those libraries
            // itself; only its bootstrap proxy classes belong here.
            include("net/momirealms/craftengine/proxy/**")
        }
        from(rootProject.file("LICENSE")) { into("META-INF/huntengine") }
        from(rootProject.file("NOTICE")) { into("META-INF/huntengine") }
        from(rootProject.file("UPSTREAM.md")) { into("META-INF/huntengine") }
        from(rootProject.file("CHANGES.md")) { into("META-INF/huntengine") }
        from(rootProject.file("common-files/src/main/resources/THIRD_PARTY_LICENSES")) {
            into("META-INF/huntengine")
        }
        archiveFileName = "HuntEngine-legacy.jar"
        destinationDirectory.set(file("$rootDir/target"))
    }
}
