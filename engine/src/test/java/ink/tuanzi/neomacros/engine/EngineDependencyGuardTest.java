package ink.tuanzi.neomacros.engine;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import ink.tuanzi.neomacros.port.arch.NeoMacrosArchRules;

/**
 * Dependency guard for the engine module, reusing the shared rules from the
 * ports test fixtures: the GraalJS embedding layer stays free of game runtime
 * imports and does not reach into modules above it.
 */
@AnalyzeClasses(packages = "ink.tuanzi.neomacros.engine", importOptions = ImportOption.DoNotIncludeTests.class)
class EngineDependencyGuardTest {

    @ArchTest
    static final ArchRule noGameRuntimeImports =
            NeoMacrosArchRules.noGameDependencies("ink.tuanzi.neomacros.engine..");

    // Depending on core.runtime (the ScriptEngine SPI) is the engine's purpose;
    // event payload definitions stay core-internal and are off limits here.
    @ArchTest
    static final ArchRule noPayloadDefinitions =
            NeoMacrosArchRules.doesNotDependOn("ink.tuanzi.neomacros.engine..",
                    "ink.tuanzi.neomacros.core.event.def..");
}
