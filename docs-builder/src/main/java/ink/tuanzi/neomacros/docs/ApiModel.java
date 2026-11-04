package ink.tuanzi.neomacros.docs;

import ink.tuanzi.neomacros.doc.api.ApiClassKind;

import java.util.List;
import java.util.Map;

/**
 * Doclet-side model of the script-facing API surface, extracted from
 * doc-api annotations and Javadoc. Both renderers (Markdown and TypeScript)
 * consume the same model, so generated artifacts cannot diverge.
 *
 * @param modules        modules sorted by name
 * @param events         event descriptors sorted by name (nested and standalone)
 * @param standaloneClasses @ApiClass classes not nested in any facade, sorted by name
 * @param warnings       generation warnings (missing @zh translations, missing @ApiSince)
 */
public record ApiModel(List<ModuleModel> modules, List<EventModel> events,
        List<ClassModel> standaloneClasses, List<String> warnings) {

    /** One script-visible ES module. */
    public record ModuleModel(
            String name, String category, boolean global,
            String doc, String zhDoc, String since, String mc,
            List<PropertyModel> properties, List<MethodModel> methods,
            List<ClassModel> classes, List<TypeModel> referencedTypes) {
    }

    /** Record type referenced in a module's signatures, rendered as a TS interface of its components. */
    public record TypeModel(String name, List<ParameterModel> fields) {
    }

    /** Bean-getter member rendered as a script property. */
    public record PropertyModel(
            String name, String type,
            String doc, String zhDoc,
            boolean readOnly, String since, String mc, String permission, boolean experimental) {
    }

    /** Action member rendered as a script method. */
    public record MethodModel(
            String name, List<ParameterModel> parameters, String returnType,
            String doc, Map<String, String> paramDocs, String returnDoc, String zhDoc,
            Map<String, String> zhParamDocs, String zhReturnDoc, String zhExample,
            String since, String mc, String permission, boolean experimental) {
    }

    /** Method parameter. */
    public record ParameterModel(String name, String type) {
    }

    /** Script-facing class declared with {@code @ApiClass} (live handle or immutable data). */
    public record ClassModel(
            String name, ApiClassKind kind, String wraps, String extendsName,
            String doc, String zhDoc,
            List<PropertyModel> properties, List<MethodModel> methods) {
    }

    /** Event descriptor declared with {@code @ApiEvent}. */
    public record EventModel(
            String name, boolean cancellable,
            List<ParameterModel> fields,
            String doc, String zhDoc) {
    }
}
