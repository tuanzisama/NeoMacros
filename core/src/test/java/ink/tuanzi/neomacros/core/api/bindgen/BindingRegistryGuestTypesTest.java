package ink.tuanzi.neomacros.core.api.bindgen;

import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiClassKind;
import ink.tuanzi.neomacros.doc.api.ApiModule;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@code types} module: guest-type registration, the {@code neo.types}
 * tree entry, the {@code @neomacros/types} re-export source, and the
 * automatic per-module re-export of module-owned classes.
 */
class BindingRegistryGuestTypesTest {

    //** Test module class owning the sample cell (module name drives the re-export). */
    @ApiModule(name = "sample", category = "test")
    @ApiSince("0.1.0")
    public static class SampleApi {

        /** Annotated cell owned by this module class. */
        @ApiClass(name = "Sample", kind = ApiClassKind.HANDLE)
        public static class Sample {
        }
    }

    /** Foreign cell owned by no module under test. */
    @ApiClass(name = "Other", kind = ApiClassKind.DATA)
    public static class Other {
    }

    @Test
    void typesModuleCarriesRegisteredConstructors() {
        var registry = new BindingRegistry("26.3");
        registry.register(SampleApi.class);
        registry.registerGuestType("Sample", SampleApi.Sample.class);
        registry.registerGuestType("Other", Other.class);

        var tree = registry.neoTree(PermissionView.ALLOW_ALL);
        ModuleBinding types = assertInstanceOf(ModuleBinding.class, tree.get("types"),
                "types module present in the neo tree");
        ModuleBinding.GuestTypeMember sample =
                assertInstanceOf(ModuleBinding.GuestTypeMember.class,
                        types.members().get("Sample"));
        assertEquals(SampleApi.Sample.class, sample.cellClass());
        assertEquals(ModuleBinding.GuestTypeMember.class,
                types.members().get("Other").getClass());

        Optional<String> source = registry.virtualSourceFor(
                "@neomacros/types", PermissionView.ALLOW_ALL);
        assertTrue(source.isPresent(), "types module has a virtual source");
        assertTrue(source.get().contains("export const types = m;"),
                "aggregate export present");
        assertTrue(source.get().contains("export const Sample = m.Sample;"),
                "per-type exports present");
        assertTrue(source.get().contains("export const Other = m.Other;"),
                "second type exported");
    }

    @Test
    void moduleOwnedClassesReExportFromTheirModule() {
        var registry = new BindingRegistry("26.3");
        registry.register(SampleApi.class);
        registry.registerGuestType("Sample", SampleApi.Sample.class);

        Optional<String> source = registry.virtualSourceFor(
                "@neomacros/sample", PermissionView.ALLOW_ALL);
        assertTrue(source.isPresent());
        assertTrue(source.get().contains("export const Sample = globalThis.neo.types.Sample;"),
                "module-owned class re-exports through its module specifier");
    }

    @Test
    void registrationRejectsCollisionsAndMismatchedNames() {
        var registry = new BindingRegistry("26.3");
        registry.register(SampleApi.class);
        assertThrows(IllegalStateException.class,
                () -> registry.registerGuestType("sample", SampleApi.Sample.class),
                "type name colliding with a module name is rejected");
        assertThrows(IllegalArgumentException.class,
                () -> registry.registerGuestType("NotSample", SampleApi.Sample.class),
                "cell class must declare the matching @ApiClass name");
        registry.registerGuestType("Sample", SampleApi.Sample.class);
        assertThrows(IllegalStateException.class,
                () -> registry.registerGuestType("Sample", SampleApi.Sample.class),
                "duplicate type registration is rejected");
    }
}
