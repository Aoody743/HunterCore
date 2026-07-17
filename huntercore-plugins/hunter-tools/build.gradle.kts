dependencies {
    compileOnly(project(":divinemc-api"))
    // The panel's focused unit tests exercise static helpers on a class that references Bukkit.
    // Keep the API compile-only for the shipped plugin, but provide the local API project at test runtime.
    testRuntimeOnly(project(":divinemc-api"))

    testImplementation("org.junit.jupiter:junit-jupiter:6.0.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.0.3")
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    val pluginVersion = project.version.toString()
    filesMatching("plugin.yml") {
        expand("version" to pluginVersion)
    }
}
