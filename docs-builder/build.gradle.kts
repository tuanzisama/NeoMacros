plugins {
    `java-library`
}

repositories {
    // Resolves the doc-api annotation jar by coordinates (published via
    // ./gradlew :doc-api:publishToMavenLocal in the parent build): the
    // documentation generator consumes the same annotations the runtime
    // binding builder consumes, from a single source of truth.
    mavenLocal()
    mavenCentral()
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(17)
}

dependencies {
    implementation("ink.tuanzi.neomacros:doc-api:0.1.0")
    // Same version as the main build's catalog; used by NpmTypesPackager.
    implementation("com.google.code.gson:gson:2.11.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    // Path to a TypeScript compiler entry (the bin/tsc JS file); the TS
    // compile test runs it through node, e.g.
    // ./gradlew -p docs-builder check -PtscPath=C:/path/to/typescript/bin/tsc
    systemProperty("tscPath", providers.gradleProperty("tscPath").orElse("").get())
    // Fixture sources and snapshot baselines are read at test runtime, outside
    // any source set; declare them as inputs or edits skip the test task.
    inputs.dir(layout.projectDirectory.dir("src/test/fixtures"))
    inputs.dir(layout.projectDirectory.dir("src/test/snapshots"))
}

// Generates the bilingual API pages and TypeScript definitions from the
// facade sources of the main build's core module. Requires the doc-api
// annotation jar in the local repository (./gradlew :doc-api:publishToMavenLocal
// from the parent build). Outputs build/gen/md/api/*.md (+ warnings.txt) and
// build/gen/ts/types/neomacros.d.ts.
tasks.register<JavaExec>("generateApiDocs") {
    group = "documentation"
    description = "Generates API documentation from the real facade sources (core + ports)"

    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "ink.tuanzi.neomacros.docs.ApiDocsGenerate"

    val repoRoot = projectDir.parentFile.toPath()
    val mdOut = layout.buildDirectory.dir("gen/md")
    val tsOut = layout.buildDirectory.dir("gen/ts")
    args(
        mdOut.get().asFile.absolutePath,
        tsOut.get().asFile.absolutePath,
        repoRoot.resolve("core/src/main/java").toString(),
        repoRoot.resolve("ports/src/main/java").toString(),
    )

    inputs.dir(repoRoot.resolve("core/src/main/java/ink/tuanzi/neomacros/core/api"))
    inputs.dir(repoRoot.resolve("ports/src/main/java/ink/tuanzi/neomacros/port"))
    outputs.dir(mdOut)
    outputs.dir(tsOut)
}
