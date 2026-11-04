package ink.tuanzi.neomacros.core.api.bindgen;

import ink.tuanzi.neomacros.doc.api.ApiExperimental;
import ink.tuanzi.neomacros.doc.api.ApiInternal;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiModule;
import ink.tuanzi.neomacros.doc.api.ApiPermission;
import ink.tuanzi.neomacros.doc.api.ApiReadOnly;
import ink.tuanzi.neomacros.doc.api.ApiSince;

import javax.lang.model.SourceVersion;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Extracts the runtime {@link ApiModel} from annotated facade classes via
 * reflection: public bean getters become live properties, remaining public
 * methods become functions (async when they return a CompletableFuture), and
 * annotation values carry over with class-level fallbacks.
 */
public final class ApiModelExtractor {

    private ApiModelExtractor() {
    }

    /** Builds the model of one annotated facade class. */
    public static ApiModel.ModuleModel extractModule(Class<?> facade) {
        ApiModule module = facade.getAnnotation(ApiModule.class);
        if (module == null) {
            throw new IllegalArgumentException(facade.getName() + " is not annotated with @ApiModule");
        }
        String classSince = sinceOf(facade);
        String classMc = mcOf(facade);
        String classPermission = permissionOf(facade);

        List<ApiModel.PropertyModel> properties = new ArrayList<>();
        List<ApiModel.MethodModel> methods = new ArrayList<>();
        for (Method method : facade.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers()) || Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            String since = annotationSince(method, classSince);
            String permission = permissionOf(method) != null
                    ? permissionOf(method) : classPermission;
            if (isBeanGetter(method)) {
                properties.add(new ApiModel.PropertyModel(
                        propertyName(method),
                        method.getAnnotation(ApiReadOnly.class) != null,
                        method.getAnnotation(ApiInternal.class) != null,
                        since,
                        mcOf(method),
                        permission,
                        method.getAnnotation(ApiExperimental.class) != null,
                        method));
            } else {
                methods.add(new ApiModel.MethodModel(
                        method.getName(),
                        method.getAnnotation(ApiInternal.class) != null,
                        CompletableFuture.class.isAssignableFrom(method.getReturnType()),
                        since,
                        mcOf(method),
                        permission,
                        method.getAnnotation(ApiExperimental.class) != null,
                        method));
            }
        }
        properties.sort(Comparator.comparing(ApiModel.PropertyModel::name));
        methods.sort(Comparator.comparing(ApiModel.MethodModel::name));
        return new ApiModel.ModuleModel(module.name(), module.category(), module.global(),
                classSince, classMc, classPermission,
                List.copyOf(properties), List.copyOf(methods));
    }

    /** Extracts the full model of the given facade classes, sorted by module name. */
    public static ApiModel extract(List<Class<?>> facades) {
        List<ApiModel.ModuleModel> modules = new ArrayList<>();
        for (Class<?> facade : facades) {
            modules.add(extractModule(facade));
        }
        modules.sort(Comparator.comparing(ApiModel.ModuleModel::name));
        return new ApiModel(List.copyOf(modules));
    }

    /** True when the method is a bean getter (getX/isX, no parameters, non-void). */
    static boolean isBeanGetter(Method method) {
        String name = method.getName();
        boolean prefixed = name.startsWith("get") || name.startsWith("is");
        return prefixed && method.getParameterCount() == 0
                && method.getReturnType() != void.class;
    }

    /** Converts a bean getter name to the script property name. */
    static String propertyName(Method getter) {
        String name = getter.getName();
        int offset = name.startsWith("is") ? 2 : 3;
        String base = name.substring(offset);
        return base.isEmpty() ? base : Character.toLowerCase(base.charAt(0)) + base.substring(1);
    }

    private static String annotationSince(Method method, String classSince) {
        ApiSince since = method.getAnnotation(ApiSince.class);
        return since != null ? since.value() : classSince;
    }

    private static String sinceOf(java.lang.reflect.AnnotatedElement element) {
        ApiSince since = element.getAnnotation(ApiSince.class);
        return since == null ? null : since.value();
    }

    private static String mcOf(java.lang.reflect.AnnotatedElement element) {
        ApiMC mc = element.getAnnotation(ApiMC.class);
        return mc == null ? null : mc.value();
    }

    private static String permissionOf(java.lang.reflect.AnnotatedElement element) {
        ApiPermission permission = element.getAnnotation(ApiPermission.class);
        return permission == null ? null : permission.value();
    }
}
