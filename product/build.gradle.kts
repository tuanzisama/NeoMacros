// Stonecutter central script for obfuscated 1.x nodes: the modern
// fabric-loom plugin id honors the root fabric.loom.disableObfuscation=false
// flag, giving the mapped (Mojmap) toolchain.
plugins {
    id("fabric-loom")
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
    add("mappings", loom.layered {
        officialMojangMappings()
        // Parchment layers come from parchment_* keys in versions/<mc>/gradle.properties
    })
    add("modImplementation", "net.fabricmc:fabric-loader:${prop("fabric_loader_version")}")
    add("modImplementation", "net.fabricmc.fabric-api:fabric-api:${prop("fabric_version")}")
    // ModMenu: compile-only integration, never nested (optional in production)
    add("modImplementation", "com.terraformersmc:modmenu:${prop("mod_menu_version")}")
}
