version = providers.gradleProperty("huntercoreVersion").get()

dependencies {
    implementation(project(":huntercore-network-common"))
    compileOnly("net.md-5:bungeecord-api:1.21-R0.4-SNAPSHOT")
}

tasks.processResources {
    filesMatching("bungee.yml") {
        expand("version" to project.version.toString())
    }
}

tasks.jar {
    archiveBaseName = "HunterCore-Network-Bungee"
    dependsOn(":huntercore-network-common:jar")
    from({ zipTree(project(":huntercore-network-common").tasks.jar.get().archiveFile) })
}
