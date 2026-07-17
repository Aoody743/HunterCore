// Protocol-only module shared by the optional proxy companions.
version = providers.gradleProperty("huntercoreVersion").get()

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:6.0.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.0.3")
}

tasks.test {
    useJUnitPlatform()
}
