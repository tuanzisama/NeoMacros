plugins {
    `java-library`
    `maven-publish`
}

repositories {
    mavenCentral()
}

// Published as ink.tuanzi.neomacros:doc-api (coordinates from gradle.properties)
// so the runtime binding builder and the documentation generator consume the
// same annotation jar instead of redefining it. publishToMavenLocal covers
// local verification until a release repository is configured.
publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
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
}
