English | [简体中文](README.zh-CN.md)

# product

The Fabric mod itself: the only version-dependent module. Stonecutter creates one virtual node per target version (`:product:<mc>`), all driven by `build.gradle.kts` in this directory.

- `adapter` — implements the [ports](../ports/) interfaces against Fabric/Yarn: hub classes collecting game events (chat, entities, world, sound, input scroll, bossbar, ...), the `Fabric*Port` implementations, mixins, and main-thread dispatch
- `fabric` — mod entrypoint and the in-game screens (script manager, editor)

Version-specific build settings live in `versions/<mc>/gradle.properties`; the 1.x node builds against the mapped (obfuscated) toolchain, the 26.x nodes against the unobfuscated one.

Run a dev client for the active node with `./gradlew :product:runClient`.
