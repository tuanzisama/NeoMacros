package ink.tuanzi.neomacros.port;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import ink.tuanzi.neomacros.port.arch.NeoMacrosArchRules;

/**
 * Dependency guard for this module: the port layer stays free of game runtime
 * imports and keeps the dependency direction (core depends on ports, never
 * the reverse). The rules come from the shared fixture so other
 * version-independent modules declare identical boundaries.
 */
@AnalyzeClasses(packages = "ink.tuanzi.neomacros.port", importOptions = ImportOption.DoNotIncludeTests.class)
class PortsDependencyGuardTest {

    @ArchTest
    static final ArchRule noGameRuntimeImports =
            NeoMacrosArchRules.noGameDependencies("ink.tuanzi.neomacros.port..");

    @ArchTest
    static final ArchRule noUpwardDependencies =
            NeoMacrosArchRules.doesNotDependOn("ink.tuanzi.neomacros.port..",
                    "ink.tuanzi.neomacros.core..", "ink.tuanzi.neomacros.engine..",
                    "ink.tuanzi.neomacros.doc.api..");
}
