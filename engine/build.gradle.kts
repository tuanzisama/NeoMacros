plugins {
    `java-library`
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
    api(project(":core"))
    // GraalJS embedding on the stock JVM (interpreter mode without JVMCI):
    // the polyglot API plus the community JS build, kept version-locked.
    implementation("org.graalvm.polyglot:polyglot:24.2.2")
    implementation("org.graalvm.js:js-community:24.2.2")
    testImplementation(testFixtures(project(":ports")))
    testImplementation(libs.archunit.junit5)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
