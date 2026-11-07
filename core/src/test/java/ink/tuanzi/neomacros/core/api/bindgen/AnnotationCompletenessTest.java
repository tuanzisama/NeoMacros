package ink.tuanzi.neomacros.core.api.bindgen;

import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiEvent;
import ink.tuanzi.neomacros.doc.api.ApiInternal;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiModule;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The explicit-triple annotation gate: every script-facing member must carry
 * all three availability annotations ({@code @ApiSince}, {@code @ApiMC},
 * {@code @ApiLoader}) explicitly. A member marked with one of them but
 * missing another fails the build with the full list, so an unnoticed
 * omission can never silently publish a narrower member as universally
 * available (or vice versa). Class/module-level annotations describe the
 * type itself and never satisfy the members.
 *
 * <p>Scope: API module classes ({@code @ApiModule}), script-facing classes
 * ({@code @ApiClass}), and event payload records ({@code @ApiEvent}).
 * Members outside the script surface stay unmarked: {@code Object}
 * overrides, constructors (the guest factory path), and {@code @ApiInternal}
 * members.</p>
 */
class AnnotationCompletenessTest {

    private static final List<String> MISSING = new ArrayList<>();

    @Test
    void everyScriptFacingMemberCarriesTheExplicitTriple() {
        scan(ApiCompletenessSurface.MODULE_CLASSES, "module");
        scan(ApiCompletenessSurface.CLASSES, "class");
        scan(ApiCompletenessSurface.EVENTS, "event");
        assertTrue(MISSING.isEmpty(),
                "script-facing members missing the explicit annotation triple:\n"
                        + String.join("\n", MISSING));
    }

    private void scan(List<Class<?>> types, String kind) {
        for (Class<?> type : types) {
            check(type, kind + " " + type.getSimpleName());
            for (Method method : type.getDeclaredMethods()) {
                int modifiers = method.getModifiers();
                if (!Modifier.isPublic(modifiers) || Modifier.isStatic(modifiers)) {
                    continue;
                }
                if (method.getDeclaringClass() == Object.class
                        || method.isAnnotationPresent(ApiInternal.class)
                        || isObjectOverride(type, method)) {
                    continue;
                }
                check(method, kind + " " + type.getSimpleName() + "#" + method.getName());
            }
        }
    }

    private void check(java.lang.reflect.AnnotatedElement element, String where) {
        boolean since = element.isAnnotationPresent(ApiSince.class);
        boolean mc = element.isAnnotationPresent(ApiMC.class);
        boolean loader = element.isAnnotationPresent(ApiLoader.class);
        if (!since && !mc && !loader) {
            return; // outside the script surface entirely
        }
        List<String> missing = new ArrayList<>();
        if (!since) {
            missing.add("@ApiSince");
        }
        if (!mc) {
            missing.add("@ApiMC");
        }
        if (!loader) {
            missing.add("@ApiLoader");
        }
        if (!missing.isEmpty()) {
            MISSING.add(where + " -> missing " + String.join(", ", missing));
        }
    }

    /** Whether the method overrides one declared on Object (toString and friends). */
    private static boolean isObjectOverride(Class<?> type, Method method) {
        try {
            Object.class.getMethod(method.getName(), method.getParameterTypes());
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }
}
