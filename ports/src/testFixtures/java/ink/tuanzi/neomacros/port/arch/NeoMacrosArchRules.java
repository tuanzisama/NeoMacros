package ink.tuanzi.neomacros.port.arch;

import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Shared dependency-guard rules for the version-independent modules
 * ({@code doc-api}, {@code ports}, {@code core}, {@code engine}).
 *
 * <p>Modules publish their rules as test fixtures so every build declares the
 * same boundaries from one definition: game runtime packages
 * ({@code net.minecraft}, {@code com.mojang}), Fabric tooling
 * ({@code net.fabricmc}), Mixin tooling ({@code org.spongepowered}) and
 * LWJGL must stay out of the version-independent layer.</p>
 */
public final class NeoMacrosArchRules {

    /** Packages that belong to the game runtime or its tooling ecosystems. */
    private static final String[] GAME_PACKAGES = {
            "net.minecraft..", "com.mojang..", "net.fabricmc..",
            "org.spongepowered..", "org.lwjgl..",
    };

    private NeoMacrosArchRules() {
    }

    /**
     * Rule stating that no class under {@code packagePrefix} may depend on the
     * game runtime or its tooling packages. Run it against the module's own
     * package root with {@code rule.check(JavaImporter...)}, or bind it as an
     * {@code @ArchTest} field.
     */
    public static ArchRule noGameDependencies(String packagePrefix) {
        return noClasses()
                .that().resideInAPackage(packagePrefix)
                .should().dependOnClassesThat().resideInAnyPackage(GAME_PACKAGES)
                .because("version-independent modules reach the game only through port interfaces");
    }

    /**
     * Rule stating that no class under {@code packagePrefix} may depend on the
     * given internal sibling packages, guarding the core-&gt;ports dependency
     * direction (lower layers must not reach upward).
     */
    public static ArchRule doesNotDependOn(String packagePrefix, String... forbiddenPrefixes) {
        return noClasses()
                .that().resideInAPackage(packagePrefix)
                .should().dependOnClassesThat().resideInAnyPackage(forbiddenPrefixes)
                .because("dependency direction must follow the module layering");
    }
}
