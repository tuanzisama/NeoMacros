// Shared node build logic, applied by build.gradle.kts (obfuscated nodes,
// modern fabric-loom plugin id) and unobf.gradle.kts (26.x nodes, legacy
// plugin id that loom hard-forces into unobfuscated mode). This file must
// not carry a plugins block and must not reference loom classes: applied
// scripts get no generated accessors and no plugin classpath.
import org.gradle.language.jvm.tasks.ProcessResources
import java.util.Properties

val mc = project.name // branch "product": the node's project name is the Minecraft version

// ModMenu is an optional integration: compiled against here and on the dev
// classpath, but never nested, so production loads it only when installed.
repositories {
    maven("https://maven.terraformersmc.com/releases")
    maven("https://maven.shedaniel.me/")
}

val nodeProps = Properties().apply {
    val f = rootProject.file("versions/$mc/gradle.properties")
    require(f.exists()) { "Missing node properties file versions/$mc/gradle.properties" }
    f.inputStream().use { load(it) }
}
fun prop(key: String): String =
    nodeProps.getProperty(key) ?: error("versions/$mc/gradle.properties is missing key: $key")

// Dependency floor for fabric.mod.json, decoupled from the loader version
// loom compiles and runs the dev client with.
val loaderMin = nodeProps.getProperty("fabric_loader_min") ?: prop("fabric_loader_version")

val modId = providers.gradleProperty("mod_id").get()

configure<org.gradle.api.plugins.BasePluginExtension> {
    // Gradle appends "-$version", producing e.g. neomacros-26.3-fabric-0.1.0.jar
    archivesName.set("$modId-$mc-fabric")
}

configure<org.gradle.api.plugins.JavaPluginExtension> {
    toolchain.languageVersion.set(JavaLanguageVersion.of(prop("java_version").toInt()))
}

dependencies.add("minecraft", "com.mojang:minecraft:$mc")

// Version-independent modules: compiled against here and embedded as
// jar-in-jar so every node ships the same prebuilt core.
for (m in listOf(":doc-api", ":ports", ":core", ":engine")) {
    dependencies.add("implementation", project(m))
    dependencies.add("include", project(m))
}

// External runtime dependency of :core (manifest YAML parsing) that must ride
// along in the mod jar; the version comes from the shared version catalog.
val libsCatalog = extensions.getByType<org.gradle.api.artifacts.VersionCatalogsExtension>().named("libs")
dependencies.add("include", libsCatalog.findLibrary("snakeyaml").get().get().toString())

// Graal runtime closure of the engine, embedded explicitly because loom's
// include() does not carry transitive dependencies: the dev client gets Graal
// from the compile classpath, while a production launcher only sees nested
// jars. Keep the version locked with engine/build.gradle.kts.
for (g in listOf(
    "org.graalvm.polyglot:polyglot:24.2.2",
    "org.graalvm.js:js-language:24.2.2",
    "org.graalvm.regex:regex:24.2.2",
    "org.graalvm.truffle:truffle-api:24.2.2",
    "org.graalvm.truffle:truffle-runtime:24.2.2",
    "org.graalvm.truffle:truffle-compiler:24.2.2",
    "org.graalvm.sdk:collections:24.2.2",
    "org.graalvm.sdk:nativeimage:24.2.2",
    "org.graalvm.sdk:word:24.2.2",
    "org.graalvm.sdk:jniutils:24.2.2",
    "org.graalvm.shadowed:icu4j:24.2.2",
    "org.graalvm.shadowed:xz:24.2.2",
)) {
    dependencies.add("include", g)
}

// The adapter and fabric entrypoint sources live in this branch's own tree
// (src/main/java) so each node compiles its Stonecutter-preprocessed mirror;
// external srcDirs would bypass the preprocessing pipeline.

tasks.withType<ProcessResources>().configureEach {
    val expandProps = mapOf(
        "version" to providers.gradleProperty("version").get(),
        "mod_id" to modId,
        "mc_range" to prop("fabric_minecraft_version_range"),
        "loader_min" to loaderMin,
    )
    inputs.properties(expandProps)
    filesMatching("fabric.mod.json") {
        expand(expandProps)
    }
}

tasks.withType<org.gradle.api.tasks.compile.JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

dependencies.add("testImplementation", "org.junit.jupiter:junit-jupiter:5.10.2")
dependencies.add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher:1.10.2")

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

// Per-node API rewrites shared with every node (buildSrc/NeomacrosNode.kt).
configure<dev.kikugie.stonecutter.build.StonecutterBuildExtension> {
    neomacrosReplacements()
}
