// Stonecutter 控制器管理脚本：`stonecutter active` 行由控制器维护，勿手改。
plugins {
    id("dev.kikugie.stonecutter")
}

if (System.getenv("CI") == "true") stonecutter active null
else stonecutter active file("stonecutter.active") /* [SC] DO NOT EDIT */

// 根级便捷任务（本脚本作用于根项目；根 build.gradle.kts 在中央模式下不求值）
if (project == rootProject) {
    val activeVersion = providers.fileContents(layout.projectDirectory.file("stonecutter.active"))
        .asText.get().trim()

    tasks.register("runFabricClient") {
        group = "run"
        description = "Runs the product node for the active Stonecutter version ($activeVersion)"
        dependsOn(":product:$activeVersion:runClient")
    }
}
