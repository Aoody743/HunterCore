version = providers.gradleProperty("huntercoreVersion").get()

dependencies {
    implementation(project(":huntercore-network-common"))
    compileOnly("com.velocitypowered:velocity-api:3.4.0-SNAPSHOT")
    annotationProcessor("com.velocitypowered:velocity-api:3.4.0-SNAPSHOT")
}

tasks.jar {
    archiveBaseName = "HunterCore-Network-Velocity"
    dependsOn(":huntercore-network-common:jar")
    from({ zipTree(project(":huntercore-network-common").tasks.jar.get().archiveFile) })
}
