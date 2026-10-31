package ink.tuanzi.neomacros.core.api.bindgen;

import java.lang.reflect.InvocationTargetException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Registry of script module bindings: the single source both channels share
 * (the {@code neo.*} global tree and the {@code @neomacros/*} ESM module
 * table resolve to the same binding objects, 05 §2). Facade classes are
 * scanned once; binding trees are built per permission view and trimmed by
 * the current milestone node's {@code @ApiMC} ranges.
 */
public final class BindingRegistry {

    private final String currentMcVersion;
    private final Map<String, ApiModel.ModuleModel> modules = new LinkedHashMap<>();
    private final Map<String, Object> instances = new LinkedHashMap<>();
    private final Map<PermissionView, Map<String, ModuleBinding>> bindingCache = new java.util.IdentityHashMap<>();

    /** Creates the registry for the running milestone node. */
    public BindingRegistry(String currentMcVersion) {
        this.currentMcVersion = currentMcVersion;
    }

    /** Registers one facade class (instantiated with its no-arg constructor). */
    public void register(Class<?> facade) {
        ApiModel.ModuleModel model = ApiModelExtractor.extractModule(facade);
        if (modules.containsKey(model.name())) {
            throw new IllegalStateException("module name collision: " + model.name());
        }
        if (model.mc() != null && !VersionRange.parse(model.mc()).test(currentMcVersion)) {
            return;
        }
        modules.put(model.name(), model);
        try {
            instances.put(model.name(), facade.getDeclaredConstructor().newInstance());
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException(
                    "facade " + facade.getName() + " needs a no-arg constructor", e);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("facade instantiation failed: " + facade.getName(), e);
        }
    }

    /** Registers a batch of facade classes. */
    public void registerAll(List<Class<?>> facades) {
        facades.forEach(this::register);
    }

    /** The extracted model (module metadata; members unfiltered by permission). */
    public ApiModel model() {
        return new ApiModel(List.copyOf(modules.values()));
    }

    /**
     * Builds the {@code neo} global tree for one permission view: module name
     * → {@link ModuleBinding}. Members outside the node's {@code @ApiMC}
     * ranges are omitted; permission-gated members render as denied members.
     */
    public Map<String, Object> neoTree(PermissionView view) {
        Map<String, Object> tree = new LinkedHashMap<>();
        for (Map.Entry<String, ApiModel.ModuleModel> entry : modules.entrySet()) {
            tree.put(entry.getKey(), bindingFor(entry.getValue(), view));
        }
        return tree;
    }

    /**
     * The ESM module binding for one specifier ({@code @neomacros/<name>}),
     * resolving to the same binding object the global tree exposes; empty for
     * unknown or node-filtered modules.
     */
    public Optional<Object> esmModule(String name, PermissionView view) {
        ApiModel.ModuleModel model = modules.get(name);
        if (model == null) {
            return Optional.empty();
        }
        return Optional.of(bindingFor(model, view));
    }

    /**
     * Builds (and caches per permission view) the binding of one module, so
     * the global and ESM channels resolve to the identical binding object.
     */
    private ModuleBinding bindingFor(ApiModel.ModuleModel model, PermissionView view) {
        Map<String, ModuleBinding> perView = bindingCache.computeIfAbsent(
                view, v -> new LinkedHashMap<>());
        return perView.computeIfAbsent(model.name(), name ->
                ModuleBinding.of(trimToNode(model), instances.get(name), view));
    }

    /** Drops members whose {@code @ApiMC} range excludes the current node. */
    private ApiModel.ModuleModel trimToNode(ApiModel.ModuleModel model) {
        List<ApiModel.MethodModel> methods = model.methods().stream()
                .filter(m -> m.mc() == null || VersionRange.parse(m.mc()).test(currentMcVersion))
                .toList();
        List<ApiModel.PropertyModel> properties = model.properties().stream()
                .filter(p -> p.mc() == null || VersionRange.parse(p.mc()).test(currentMcVersion))
                .toList();
        if (methods.size() == model.methods().size()
                && properties.size() == model.properties().size()) {
            return model;
        }
        return new ApiModel.ModuleModel(model.name(), model.category(), model.global(),
                model.since(), model.mc(), model.permission(), properties, methods);
    }

    /** The milestone node version this registry trims against. */
    public String currentMcVersion() {
        return currentMcVersion;
    }
}
