package ink.tuanzi.neomacros.core;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import ink.tuanzi.neomacros.port.arch.NeoMacrosArchRules;

/**
 * Dependency guard for the core module, reusing the shared rules from the
 * ports test fixtures: no game runtime imports, and no reaching into modules
 * that depend on core (engine, adapter).
 */
@AnalyzeClasses(packages = "ink.tuanzi.neomacros.core", importOptions = ImportOption.DoNotIncludeTests.class)
class CoreDependencyGuardTest {

    @ArchTest
    static final ArchRule noGameRuntimeImports =
            NeoMacrosArchRules.noGameDependencies("ink.tuanzi.neomacros.core..");

    @ArchTest
    static final ArchRule noUpwardDependencies =
            NeoMacrosArchRules.doesNotDependOn("ink.tuanzi.neomacros.core..",
                    "ink.tuanzi.neomacros.engine..");
}
