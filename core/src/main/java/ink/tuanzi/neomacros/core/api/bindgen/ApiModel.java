package ink.tuanzi.neomacros.core.api.bindgen;

import java.util.List;

/**
 * Runtime model of the script-facing API surface, extracted from doc-api
 * annotations by reflection at binding build time. The documentation
 * pipeline's model (docs-builder) covers the same surface from source; the
 * two consumers agree on the annotation meanings, so bindings and generated
 * docs cannot drift apart.
 *
 * @param modules modules sorted by name (members sorted per list)
 */
public record ApiModel(List<ModuleModel> modules) {

    /** One script-visible ES module. */
    public record ModuleModel(
            String name, String category, boolean global,
            String since, String mc, String permission,
            List<PropertyModel> properties, List<MethodModel> methods) {
    }

    /** Bean-getter member exposed as a live script property. */
    public record PropertyModel(
            String name, boolean readOnly, boolean internal,
            String since, String mc, String permission, boolean experimental,
            java.lang.reflect.Method getter) {
    }

    /** Action member exposed as a script function; async methods map to promises. */
    public record MethodModel(
            String name, boolean internal, boolean async,
            String since, String mc, String permission, boolean experimental,
            java.lang.reflect.Method method) {
    }
}
