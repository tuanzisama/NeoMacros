// Stonecutter build script for the 26.x unobfuscated nodes. The legacy
// net.fabricmc.fabric-loom plugin id makes loom 1.19 force
// disableObfuscation=true (finalized), which is exactly the mappings-free
// toolchain these nodes need; the root fabric.loom.disableObfuscation=false
// flag is ignored here by design.
plugins {
    id("net.fabricmc.fabric-loom")
}

import java.util.Properties

apply(from = rootProject.file("product/node-common.gradle.kts"))
// Node coordinates (node-common declares its own copy of this helper; the
// two script classes cannot see each other's members).
val nodeProps = Properties().apply {
    rootProject.file("versions/${project.name}/gradle.properties").inputStream().use { load(it) }
}
fun prop(key: String): String = nodeProps.getProperty(key)


dependencies {
    add("implementation", "net.fabricmc:fabric-loader:${prop("fabric_loader_version")}")
    add("implementation", "net.fabricmc.fabric-api:fabric-api:${prop("fabric_version")}")
}
