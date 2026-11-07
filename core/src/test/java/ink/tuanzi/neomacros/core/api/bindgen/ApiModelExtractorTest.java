package ink.tuanzi.neomacros.core.api.bindgen;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Model extraction assertions over the fully annotated fixture module class:
 * module metadata, getter-to-property split, annotation carry-over with
 * class-level fallbacks, internal flagging, and async detection.
 */
class ApiModelExtractorTest {

    @Test
    void fixtureModelCompleteness() {
        ApiModel model = ApiModelExtractor.extract(List.of(BindgenFixture.class));
        assertEquals(1, model.modules().size());

        ApiModel.ModuleModel module = model.modules().get(0);
        assertEquals("fixture", module.name());
        assertEquals("test", module.category());
        assertTrue(module.global());
        assertEquals("0.1.0", module.since());
        assertNull(module.mc(), "class-level ApiMC absent on the fixture");
        assertNull(module.permission());
    }

    @Test
    void beanGetterBecomesPropertyWithCarryOver() {
        ApiModel model = ApiModelExtractor.extract(List.of(BindgenFixture.class));
        assertEquals(1, model.modules().get(0).properties().size());

        ApiModel.PropertyModel property = model.modules().get(0).properties().get(0);
        assertEquals("reads", property.name(), "getReads maps to the reads property");
        assertTrue(property.readOnly());
        assertFalse(property.internal());
        assertEquals("0.1.0", property.since());
        assertNull(property.permission());
    }

    @Test
    void methodsCarryAnnotationsAndAsyncDetection() {
        ApiModel.ModuleModel module = ApiModelExtractor
                .extract(List.of(BindgenFixture.class)).modules().get(0);
        Map<String, ApiModel.MethodModel> byName = new java.util.HashMap<>();
        module.methods().forEach(m -> byName.put(m.name(), m));

        assertEquals(5, module.methods().size(), "greet/fetch/secret/futureOnly/hiddenInternal");

        assertTrue(byName.get("greet").since().equals("0.1.0"));
        assertFalse(byName.get("greet").async());
        assertNull(byName.get("greet").permission());

        assertTrue(byName.get("fetch").async(), "CompletableFuture return maps to async");
        assertNull(byName.get("fetch").mc());

        assertEquals("fs", byName.get("secret").permission());
        assertEquals(">=1.20.6", byName.get("futureOnly").mc());
        assertTrue(byName.get("hiddenInternal").internal(), "internal members stay in the runtime model");
        assertEquals("0.1.0", byName.get("hiddenInternal").since(),
                "class-level since falls back onto internal members too");
    }

    @Test
    void typeOverrideConductionBelongsToTheDocPipeline() {
        // @ApiType drives documentation and TypeScript definitions; the runtime
        // model intentionally does not carry it (bindings map async returns by
        // the CompletableFuture shape). The docs pipeline's model asserts the
        // override itself.
        ApiModel.ModuleModel module = ApiModelExtractor
                .extract(List.of(BindgenFixture.class)).modules().get(0);
        ApiModel.MethodModel fetch = module.methods().stream()
                .filter(m -> m.name().equals("fetch")).findFirst().orElseThrow();
        assertTrue(fetch.async(), "runtime async mapping keyed by return type");
    }

    @Test
    void nonAnnotatedClassRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> ApiModelExtractor.extractModule(String.class));
    }

    @Test
    void versionRangeSemantics() {
        assertTrue(VersionRange.parse(">=1.20.6").test("1.20.6"));
        assertTrue(VersionRange.parse(">=1.20.6").test("26.3"));
        assertFalse(VersionRange.parse(">=1.20.6").test("1.18.2"));

        assertTrue(VersionRange.parse("1.18-1.21").test("1.20.1"));
        assertTrue(VersionRange.parse("1.18-1.21").test("1.18"));
        assertFalse(VersionRange.parse("1.18-1.21").test("1.21.5"));

        assertTrue(VersionRange.parse("*").test("1.0"));
        assertTrue(VersionRange.parse(null).test("anything"));
        assertTrue(VersionRange.parse("1.19.4").test("1.19.4"));
        assertFalse(VersionRange.parse("<1.20").test("1.20"));
        assertTrue(VersionRange.parse(">1.19").test("1.19.1"));
    }

    @Test
    void permissionViewsGateCapabilities() {
        PermissionView all = PermissionView.ALLOW_ALL;
        PermissionView none = PermissionView.DENY_ALL;
        PermissionView stub = PermissionView.of(Set.of("fs"));

        assertTrue(all.allows("fs"));
        assertFalse(none.allows("fs"));
        assertTrue(stub.allows("fs"));
        assertFalse(stub.allows("net"));
        assertTrue(PermissionView.of(java.util.Map.of("script", Set.of("command")),
                "script").allows("command"));
        assertFalse(PermissionView.of(java.util.Map.of("script", Set.of("command")),
                "other").allows("command"));
    }

    @Test
    void registryTrimsMembersByNodeRangeAndGuardsCollisions() throws Throwable {
        BindingRegistry modern = new BindingRegistry("26.3");
        modern.register(BindgenFixture.class);
        BindingRegistry legacy = new BindingRegistry("1.18.2");
        legacy.register(BindgenFixture.class);

        assertEquals(1, legacy.model().modules().size(),
                "class-level ApiMC absent: the module stays on old nodes");
        ModuleBinding legacyBinding = (ModuleBinding) legacy.neoTree(PermissionView.ALLOW_ALL).get("fixture");
        assertFalse(legacyBinding.members().containsKey("futureOnly"),
                "member-level @ApiMC trims futureOnly on 1.18.2");

        ModuleBinding modernBinding = (ModuleBinding) modern.neoTree(PermissionView.ALLOW_ALL).get("fixture");
        assertTrue(modernBinding.members().containsKey("futureOnly"));
        assertEquals("modern", ((ModuleBinding.FunctionMember) modernBinding.members()
                .get("futureOnly")).invoke(new Object[0]));

        assertThrows(IllegalStateException.class, () -> modern.register(BindgenFixture.class),
                "duplicate module registration rejected");
    }

    @Test
    void deniedMemberRaisesPermissionErrorNotSilentAccess() throws Throwable {
        BindingRegistry registry = new BindingRegistry("26.3");
        registry.register(BindgenFixture.class);
        ApiModel.ModuleModel model = registry.model().modules().get(0);

        ModuleBinding denied = ModuleBinding.of(model, new BindgenFixture(), PermissionView.DENY_ALL);
        ModuleBinding granted = ModuleBinding.of(model, new BindgenFixture(), PermissionView.of(Set.of("fs")));

        ModuleBinding.DeniedMember secret = (ModuleBinding.DeniedMember) denied.members().get("secret");
        assertThrows(PermissionError.class, secret::invoke,
                "unauthorized invocation raises PermissionError");
        assertEquals("fs", secret.permission());

        ModuleBinding.FunctionMember grantedSecret =
                (ModuleBinding.FunctionMember) granted.members().get("secret");
        assertEquals("s3cret", grantedSecret.invoke(new Object[0]));
    }

    @Test
    void livePropertyInvokesGetterPerRead() throws Throwable {
        BindingRegistry registry = new BindingRegistry("26.3");
        registry.register(BindgenFixture.class);
        ModuleBinding binding = ModuleBinding.of(registry.model().modules().get(0),
                new BindgenFixture(), PermissionView.ALLOW_ALL);

        ModuleBinding.PropertyMember reads = (ModuleBinding.PropertyMember) binding.members().get("reads");
        assertEquals(1, reads.get());
        assertEquals(2, reads.get(), "each read invokes the live getter");
    }

    @Test
    void esmModuleAndGlobalTreeShareTheSameBinding() {
        BindingRegistry registry = new BindingRegistry("26.3");
        registry.register(BindgenFixture.class);
        PermissionView view = PermissionView.ALLOW_ALL;

        Map<String, Object> tree = registry.neoTree(view);
        assertEquals(tree.get("fixture"), registry.esmModule("fixture", view).orElseThrow(),
                "both channels expose the same binding object (05 §2)");
        assertTrue(registry.esmModule("nope", view).isEmpty());
    }
}
