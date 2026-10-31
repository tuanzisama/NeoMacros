// Controller script maintained by Stonecutter; do not edit the `stonecutter active` line.
plugins {
    id("dev.kikugie.stonecutter")
}

if (System.getenv("CI") == "true") stonecutter active null
else stonecutter active file("stonecutter.active") /* [SC] DO NOT EDIT */

// Root-level convenience tasks. This script is the only one evaluated for the
// root project: in Stonecutter central mode the root build.gradle.kts is never
// evaluated at all.
if (project == rootProject) {
    val activeVersion = providers.fileContents(layout.projectDirectory.file("stonecutter.active"))
        .asText.get().trim()

    tasks.register("runFabricClient") {
        group = "run"
        description = "Runs the product node for the active Stonecutter version ($activeVersion)"
        dependsOn(":product:$activeVersion:runClient")
    }
}
