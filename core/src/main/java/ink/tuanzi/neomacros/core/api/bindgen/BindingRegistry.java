package ink.tuanzi.neomacros.core.api.bindgen;

import java.lang.reflect.InvocationTargetException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry of script module bindings: the single source of the {@code neo.*}
 * global tree (modules, root-level guest classes, and root-level event
 * classes). API classes are scanned once; binding trees are built per
 * permission view and trimmed by the current milestone node's {@code @ApiMC}
 * ranges.
 */
public final class BindingRegistry {

    private final String currentMcVersion;
    private final Map<String, ApiModel.ModuleModel> modules = new LinkedHashMap<>();
    private final Map<String, Object> instances = new LinkedHashMap<>();
    private final Map<PermissionView, Map<String, ModuleBinding>> bindingCache = new java.util.IdentityHashMap<>();
    private final Map<String, List<String>> promoted = new LinkedHashMap<>();
    private final Map<String, Class<?>> guestTypes = new LinkedHashMap<>();
    private final Map<String, Boolean> eventTypes = new LinkedHashMap<>();

    /** Creates the registry for the running milestone node. */
    public BindingRegistry(String currentMcVersion) {
        this.currentMcVersion = currentMcVersion;
    }

    /** Registers one API module class (instantiated with its no-arg constructor). */
    public void register(Class<?> moduleClass) {
        ApiModel.ModuleModel model = ApiModelExtractor.extractModule(moduleClass);
        if (modules.containsKey(model.name())) {
            throw new IllegalStateException("module name collision: " + model.name());
        }
        if (model.mc() != null && !VersionRange.parse(model.mc()).test(currentMcVersion)) {
            return;
        }
        modules.put(model.name(), model);
        try {
            instances.put(model.name(), moduleClass.getDeclaredConstructor().newInstance());
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException(
                    "module class " + moduleClass.getName() + " needs a no-arg constructor", e);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("module instantiation failed: " + moduleClass.getName(), e);
        }
    }

    /** Registers a batch of API classes. */
    public void registerAll(List<Class<?>> moduleClasses) {
        moduleClasses.forEach(this::register);
    }

    /**
     * Registers a pre-built API instance (stateful API modules whose
     * constructor needs runtime wiring). Model extraction and collision
     * checks behave exactly like {@link #register(Class)}.
     */
    public void registerInstance(Object moduleClass) {
        ApiModel.ModuleModel model = ApiModelExtractor.extractModule(moduleClass.getClass());
        if (modules.containsKey(model.name())) {
            throw new IllegalStateException("module name collision: " + model.name());
        }
        if (model.mc() != null && !VersionRange.parse(model.mc()).test(currentMcVersion)) {
            return;
        }
        modules.put(model.name(), model);
        instances.put(model.name(), moduleClass);
    }

    /** The extracted model (module metadata; members unfiltered by permission). */
    public ApiModel model() {
        return new ApiModel(List.copyOf(modules.values()));
    }

    /**
     * Promotes members of one module to the root of the global tree, so a
     * module's functions are reachable as {@code neo.<member>} as well as
     * {@code neo.<module>.<member>}. Member names must not collide with any
     * module name.
     */
    public void promoteRootMembers(String moduleName, java.util.Collection<String> memberNames) {
        ApiModel.ModuleModel model = modules.get(moduleName);
        if (model == null) {
            throw new IllegalStateException("unknown module: " + moduleName);
        }
        for (String member : memberNames) {
            if (modules.containsKey(member)) {
                throw new IllegalStateException("root promotion collision: '" + member
                        + "' is also a module name");
            }
        }
        promoted.put(moduleName, List.copyOf(memberNames));
        bindingCache.clear();
    }

    /**
     * Registers one script-facing class constructor at the root of the
     * global tree: {@code neo.<name>} resolves to the real guest ES class
     * built from the cell type's {@code @ApiClass} declaration. Name
     * collisions with modules or other root classes are rejected.
     */
    public void registerGuestType(String name, Class<?> cellClass) {
        if (modules.containsKey(name) || guestTypes.containsKey(name) || name.equals("BaseEvent")) {
            throw new IllegalStateException("guest type collision: " + name);
        }
        var annotation = cellClass.getAnnotation(
                ink.tuanzi.neomacros.doc.api.ApiClass.class);
        if (annotation == null || !annotation.name().equals(name)) {
            throw new IllegalArgumentException("cell class " + cellClass.getName()
                    + " does not declare @ApiClass(\"" + name + "\")");
        }
        guestTypes.put(name, cellClass);
        bindingCache.clear();
    }

    /**
     * Registers one script-facing event class at the root of the global
     * tree: {@code neo.<XxxEvent>} resolves to the guest event class.
     */
    public void registerEventType(String eventName, boolean cancellable) {
        String className = eventNameToClassName(eventName);
        if (modules.containsKey(className) || guestTypes.containsKey(className)) {
            throw new IllegalStateException("event class collision: " + className);
        }
        for (String registered : eventTypes.keySet()) {
            if (eventNameToClassName(registered).equals(className)) {
                throw new IllegalStateException("event class collision: " + className);
            }
        }
        eventTypes.putIfAbsent(eventName, cancellable);
    }

    /** The registered event classes ({@code name -> cancellable}), in registration order. */
    public Map<String, Boolean> eventTypes() {
        return Map.copyOf(eventTypes);
    }

    /** The registered script-facing root classes ({@code neo.<name>}), in registration order. */
    public Map<String, Class<?>> guestTypes() {
        return Map.copyOf(guestTypes);
    }

    /**
     * Builds the {@code neo} global tree for one permission view: module
     * name → {@link ModuleBinding}, plus guest classes and event classes as
     * root entries. Members outside the node's {@code @ApiMC} ranges are
     * omitted; permission-gated members render as denied members.
     */
    public Map<String, Object> neoTree(PermissionView view) {
        Map<String, Object> tree = new LinkedHashMap<>();
        for (Map.Entry<String, ApiModel.ModuleModel> entry : modules.entrySet()) {
            tree.put(entry.getKey(), bindingFor(entry.getValue(), view));
        }
        for (Map.Entry<String, List<String>> promotion : promoted.entrySet()) {
            ModuleBinding binding = bindingFor(modules.get(promotion.getKey()), view);
            for (String member : promotion.getValue()) {
                tree.put(member, rootEntry(member, binding.members().get(member)));
            }
        }
        if (!guestTypes.isEmpty()) {
            for (Map.Entry<String, Class<?>> type : guestTypes.entrySet()) {
                tree.put(type.getKey(), rootEntry(type.getKey(),
                        new ModuleBinding.GuestTypeMember(type.getValue())));
            }
            tree.put("BaseEvent", rootEntry("BaseEvent",
                    new ModuleBinding.EventTypeMember("BaseEvent", false)));
            for (Map.Entry<String, Boolean> type : eventTypes.entrySet()) {
                String className = eventNameToClassName(type.getKey());
                tree.put(className, rootEntry(className,
                        new ModuleBinding.EventTypeMember(type.getKey(), type.getValue())));
            }
        }
        return tree;
    }

    /** Wraps one member as a self-named root entry; the engine renders it as the value itself. */
    private static ModuleBinding rootEntry(String name, ModuleBinding.BindingMember member) {
        return ModuleBinding.ofMembers(name, Map.of(name, member));
    }

    /** The script-facing class name of one event ({@code chat:receive} ->
     * {@code ChatReceiveEvent}); the engine glue derives the same name. */
    public static String eventNameToClassName(String eventName) {
        StringBuilder out = new StringBuilder();
        boolean upperNext = true;
        for (char ch : eventName.toCharArray()) {
            if (ch == ':' || ch == '_' || ch == '-') {
                upperNext = true;
            } else if (upperNext) {
                out.append(Character.toUpperCase(ch));
                upperNext = false;
            } else {
                out.append(ch);
            }
        }
        out.append("Event");
        return out.toString();
    }

    /** The registered API instance for one module name (harness access). */
    public Object instance(String moduleName) {
        return instances.get(moduleName);
    }

    /**
     * Builds (and caches per permission view) the binding of one module.
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
        return new ApiModel.ModuleModel(model.name(), model.category(),
                model.since(), model.mc(), model.permission(), properties, methods);
    }

    /** The milestone node version this registry trims against. */
    public String currentMcVersion() {
        return currentMcVersion;
    }
}
