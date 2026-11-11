package ink.tuanzi.neomacros.core.api.bindgen;

import java.lang.reflect.InvocationTargetException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Value-neutral binding of one script module: a named member map whose
 * members are live property invokers, function invokers, or plain constants.
 * The engine layer renders this into the guest-visible object (proxy with
 * live property reads, promise-wrapped async functions); core stays free of
 * any engine dependency.
 */
public final class ModuleBinding {

    /** One member of a module binding. */
    public sealed interface BindingMember permits FunctionMember, PropertyMember,
            ConstantMember, DeniedMember, GuestTypeMember, EventTypeMember {
    }

    /**
     * Script-facing class constructor member: the engine resolves it to the
     * real guest ES class generated from the cell type's {@code @ApiClass}
     * declaration (constructed on first access, shared per session).
     */
    public static final class GuestTypeMember implements BindingMember {
        private final Class<?> cellClass;

        public GuestTypeMember(Class<?> cellClass) {
            this.cellClass = cellClass;
        }

        /** The annotated cell class backing the guest class. */
        public Class<?> cellClass() {
            return cellClass;
        }
    }

    /**
     * Script-facing event class member: the engine resolves it to the guest
     * ES event class ({@code XxxEvent extends BaseEvent} through the
     * direction middle class) built from the event name; scripts use it for
     * {@code instanceof} dispatch.
     */
    public static final class EventTypeMember implements BindingMember {

        private final String eventName;
        private final boolean cancellable;
        private final ink.tuanzi.neomacros.doc.api.ApiEvent.Direction direction;

        public EventTypeMember(String eventName, boolean cancellable,
                               ink.tuanzi.neomacros.doc.api.ApiEvent.Direction direction) {
            this.eventName = eventName;
            this.cancellable = cancellable;
            this.direction = direction;
        }

        /** The event name backing the guest class ("BaseEvent" yields the base;
         * "InputEvent"/"DisplayEvent"/"StateEvent" yield the direction classes). */
        public String eventName() {
            return eventName;
        }

        /** Whether the event supports cancellation (introspection face). */
        public boolean cancellable() {
            return cancellable;
        }

        /** The traffic direction driving the middle-class inheritance. */
        public ink.tuanzi.neomacros.doc.api.ApiEvent.Direction direction() {
            return direction;
        }
    }

    /**
     * Function member. Invocation converts the argument array per the
     * declared parameter types and returns the raw result (async results are
     * CompletableFuture instances the engine maps to promises).
     */
    public static final class FunctionMember implements BindingMember {
        private final java.lang.reflect.Method method;
        private final Object instance;
        private final boolean async;

        FunctionMember(java.lang.reflect.Method method, Object instance, boolean async) {
            this.method = method;
            this.instance = instance;
            this.async = async;
        }

        /** Invokes the API method with already-converted Java arguments. */
        public Object invoke(Object[] arguments) throws Throwable {
            try {
                return method.invoke(instance, arguments);
            } catch (InvocationTargetException e) {
                throw e.getCause() != null ? e.getCause() : e;
            }
        }

        /** Declared parameter types for host-to-Java argument conversion. */
        public Class<?>[] parameterTypes() {
            return method.getParameterTypes();
        }

        /** Returns whether the result maps to a promise. */
        public boolean isAsync() {
            return async;
        }
    }

    /** Live property member: every read invokes the underlying bean getter. */
    public static final class PropertyMember implements BindingMember {
        private final java.lang.reflect.Method getter;
        private final Object instance;

        PropertyMember(java.lang.reflect.Method getter, Object instance) {
            this.getter = getter;
            this.instance = instance;
        }

        /** Reads the property by invoking the getter. */
        public Object get() throws Throwable {
            try {
                return getter.invoke(instance);
            } catch (InvocationTargetException e) {
                throw e.getCause() != null ? e.getCause() : e;
            }
        }
    }

    /** Plain value member (constants and pre-built objects). */
    public static final class ConstantMember implements BindingMember {
        private final Object value;

        ConstantMember(Object value) {
            this.value = value;
        }

        /** Returns the constant value. */
        public Object value() {
            return value;
        }
    }

    /** Member whose capability is not granted: visible but raising PermissionError. */
    public static final class DeniedMember implements BindingMember {
        private final String permission;

        DeniedMember(String permission) {
            this.permission = permission;
        }

        /** Always raises the permission error (the member stays visible). */
        public Object invoke() {
            throw new PermissionError("permission '" + permission
                    + "' is not declared in this script's meta.permissions");
        }

        /** The required capability key. */
        public String permission() {
            return permission;
        }
    }

    private final String name;
    private final Map<String, BindingMember> members;

    /** Creates a binding from explicit members (root promotion and tests). */
    public static ModuleBinding ofMembers(String name, Map<String, BindingMember> members) {
        return new ModuleBinding(name, members);
    }

    private ModuleBinding(String name, Map<String, BindingMember> members) {
        this.name = name;
        this.members = Map.copyOf(members);
    }

    /** The binding name: a module ({@code neo.<name>}) or a root entry. */
    public String name() {
        return name;
    }

    /** The member map, keyed by script-visible member name. */
    public Map<String, BindingMember> members() {
        return members;
    }

    /** Builds a module binding from a model, an instance, and a permission view. */
    public static ModuleBinding of(ApiModel.ModuleModel model, Object instance, PermissionView view) {
        Map<String, BindingMember> members = new LinkedHashMap<>();
        for (ApiModel.PropertyModel property : model.properties()) {
            members.put(property.name(), new PropertyMember(property.getter(), instance));
        }
        for (ApiModel.MethodModel method : model.methods()) {
            if (method.permission() != null && !view.allows(method.permission())) {
                members.put(method.name(), new DeniedMember(method.permission()));
            } else {
                members.put(method.name(), new FunctionMember(method.method(), instance, method.async()));
            }
        }
        return new ModuleBinding(model.name(), members);
    }
}
