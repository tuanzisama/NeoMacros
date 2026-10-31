plugins {
    `java-library`
    `java-test-fixtures`
}

repositories {
    mavenCentral()
}

// Version-independent module pinned to the Java 17 baseline: compiled once and
// embedded into every product node. 
java {
    toolchain.languageVersion = JavaLanguageVersion.of(17)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

dependencies {
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.archunit.junit5)

    // Shared dependency-guard rules; other version-independent modules consume
    // them via testFixtures(project(":ports")) so every module declares the
    // same boundaries from one definition.
    testFixturesApi(libs.archunit)
}
