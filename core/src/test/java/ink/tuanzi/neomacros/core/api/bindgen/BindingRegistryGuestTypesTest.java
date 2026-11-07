package ink.tuanzi.neomacros.core.api.bindgen;

import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiClassKind;
import ink.tuanzi.neomacros.doc.api.ApiModule;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Root-level guest classes: registration lands each class constructor as a
 * self-named {@code neo.<name>} root entry, and collisions with modules,
 * other classes, or the reserved BaseEvent root are rejected.
 */
class BindingRegistryGuestTypesTest {

    /** Test module class owning the sample cell. */
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
    void guestTypesLandAsRootEntries() {
        var registry = new BindingRegistry("26.3");
        registry.register(SampleApi.class);
        registry.registerGuestType("Sample", SampleApi.Sample.class);
        registry.registerGuestType("Other", Other.class);

        var tree = registry.neoTree(PermissionView.ALLOW_ALL);
        ModuleBinding sample = assertInstanceOf(ModuleBinding.class, tree.get("Sample"),
                "guest classes are root entries, not a types namespace");
        ModuleBinding.GuestTypeMember sampleMember =
                assertInstanceOf(ModuleBinding.GuestTypeMember.class,
                        sample.members().get("Sample"));
        assertEquals(SampleApi.Sample.class, sampleMember.cellClass());
        assertEquals(ModuleBinding.GuestTypeMember.class,
                assertInstanceOf(ModuleBinding.class, tree.get("Other"))
                        .members().get("Other").getClass());
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
        assertThrows(IllegalStateException.class,
                () -> registry.registerGuestType("BaseEvent", Other.class),
                "the reserved BaseEvent root cannot be shadowed by a guest type");
    }
}
