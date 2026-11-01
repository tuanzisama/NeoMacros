// Shared build logic for the Stonecutter product nodes: per-node API
// replacements consumed by product/node-common.gradle.kts.
plugins {
    `kotlin-dsl`
}

repositories {
    maven("https://maven.kikugie.dev/releases")
    mavenCentral()
}

dependencies {
    implementation("dev.kikugie:stonecutter:0.8.4")
}
