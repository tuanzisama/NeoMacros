package ink.tuanzi.neomacros.docs;

import com.sun.source.util.DocTrees;
import ink.tuanzi.neomacros.doc.api.ApiEvent;
import ink.tuanzi.neomacros.doc.api.ApiHandle;
import ink.tuanzi.neomacros.doc.api.ApiModule;
import jdk.javadoc.doclet.DocletEnvironment;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Builds the {@link ApiModel} from a doclet environment: picks annotated
 * module classes, handle classes, event records, and referenced record types;
 * collects generation warnings (missing @zh translations, missing @ApiSince)
 * in deterministic (sorted) order.
 */
final class ModelExtractor {

    private ModelExtractor() {
    }

    static ApiModel extract(DocletEnvironment env) {
        DocTrees trees = env.getDocTrees();
        List<ApiModel.ModuleModel> modules = new ArrayList<>();
        List<ApiModel.EventModel> events = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        Set<TypeElement> types = typesIn(env);
        for (TypeElement type : types) {
            ApiModule module = DocletSupport.moduleOf(type);
            if (module != null) {
                modules.add(moduleModel(type, module, trees, warnings));
                continue;
            }
            ApiEvent event = DocletSupport.eventOf(type);
            if (event != null && type.getKind() == ElementKind.RECORD) {
                events.add(eventModel(type, event, trees, warnings));
            }
        }

        modules.sort(Comparator.comparing(ApiModel.ModuleModel::name));
        events.sort(Comparator.comparing(ApiModel.EventModel::name));
        warnings.sort(String::compareTo);
        return new ApiModel(List.copyOf(modules), List.copyOf(events), List.copyOf(warnings));
    }

    /** Collects the class and record types included in this documentation run, deduplicated. */
    private static Set<TypeElement> typesIn(DocletEnvironment env) {
        Map<TypeElement, Boolean> seen = new LinkedHashMap<>();
        for (Element element : env.getIncludedElements()) {
            switch (element.getKind()) {
                case CLASS, RECORD, INTERFACE, ENUM -> seen.put((TypeElement) element, true);
                case PACKAGE -> ElementFilter.typesIn(element.getEnclosedElements())
                        .forEach(t -> seen.put(t, true));
                default -> {
                }
            }
        }
        return seen.keySet();
    }

    private static ApiModel.ModuleModel moduleModel(TypeElement type, ApiModule module,
                                                    DocTrees trees,
                                                    List<String> warnings) {
        Map<String, ApiModel.TypeModel> referenced = new TreeMap<>();
        var comment = trees.getDocCommentTree(type);
        var zhBody = DocletSupport.zhBody(comment);
        String moduleSince = DocletSupport.sinceOf(type);
        if (zhBody.isEmpty()) {
            warnings.add(module.name() + ": module doc has no @zh body");
        }
        if (moduleSince == null) {
            warnings.add(module.name() + ": module is missing @ApiSince");
        }

        List<ApiModel.PropertyModel> properties = new ArrayList<>();
        List<ApiModel.MethodModel> methods = new ArrayList<>();
        List<ApiModel.HandleModel> handles = new ArrayList<>();

        for (Element member : type.getEnclosedElements()) {
            if (member.getKind() == ElementKind.CLASS || member.getKind() == ElementKind.RECORD) {
                TypeElement nested = (TypeElement) member;
                var handle = DocletSupport.handleOf(nested);
                if (handle != null) {
                    handles.add(handleModel(nested, handle, trees, referenced, warnings,
                            module.name()));
                }
                continue;
            }
            if (member.getKind() != ElementKind.METHOD
                    || !member.getModifiers().contains(Modifier.PUBLIC)
                    || DocletSupport.internalOf(member)) {
                continue;
            }
            ExecutableElement method = (ExecutableElement) member;
            var memberComment = trees.getDocCommentTree(method);
            String since = memberSince(method, moduleSince, module.name(), warnings);
            String zhLabel = DocletSupport.propertyName(method);
            if (DocletSupport.isBeanGetter(method)) {
                if (DocletSupport.zhBody(memberComment).isEmpty()) {
                    warnings.add(module.name() + "." + zhLabel + ": property has no @zh docs");
                }
                properties.add(new ApiModel.PropertyModel(
                        zhLabel,
                        DocletSupport.returnTypeOf(method),
                        DocletSupport.docBody(memberComment),
                        DocletSupport.zhBody(memberComment).orElse(""),
                        DocletSupport.readOnlyOf(method),
                        since,
                        DocletSupport.mcOf(method),
                        DocletSupport.permissionOf(method),
                        DocletSupport.experimentalOf(method)));
                collectReferenced(method.getReturnType(), referenced);
            } else {
                if (DocletSupport.zhBody(memberComment).isEmpty()) {
                    warnings.add(module.name() + "." + method.getSimpleName() + ": method has no @zh docs");
                }
                methods.add(new ApiModel.MethodModel(
                        method.getSimpleName().toString(),
                        DocletSupport.parametersOf(method),
                        DocletSupport.returnTypeOf(method),
                        DocletSupport.docBody(memberComment),
                        DocletSupport.paramDocs(memberComment),
                        DocletSupport.returnDoc(memberComment).orElse(""),
                        DocletSupport.zhBody(memberComment).orElse(""),
                        DocletSupport.zhParamDocs(memberComment),
                        DocletSupport.zhReturnDoc(memberComment).orElse(""),
                        DocletSupport.zhExample(memberComment).orElse(""),
                        since,
                        DocletSupport.mcOf(method),
                        DocletSupport.permissionOf(method),
                        DocletSupport.experimentalOf(method)));
                collectReferenced(method.getReturnType(), referenced);
                method.getParameters().forEach(p -> collectReferenced(p.asType(), referenced));
            }
        }

        properties.sort(java.util.Comparator.comparing(ApiModel.PropertyModel::name));
        methods.sort(java.util.Comparator.comparing(ApiModel.MethodModel::name));
        handles.sort(java.util.Comparator.comparing(ApiModel.HandleModel::name));
        List<ApiModel.TypeModel> referencedList = new ArrayList<>(referenced.values());
        referencedList.sort(java.util.Comparator.comparing(ApiModel.TypeModel::name));
        return new ApiModel.ModuleModel(
                module.name(), module.category(), module.global(),
                DocletSupport.docBody(comment),
                zhBody.orElse(""),
                moduleSince, DocletSupport.mcOf(type),
                List.copyOf(properties), List.copyOf(methods), List.copyOf(handles),
                List.copyOf(referencedList));
    }

    private static ApiModel.HandleModel handleModel(TypeElement type,
                                                    ApiHandle handle,
                                                    DocTrees trees,
                                                    Map<String, ApiModel.TypeModel> referenced,
                                                    List<String> warnings, String moduleName) {
        var comment = trees.getDocCommentTree(type);
        var zhBody = DocletSupport.zhBody(comment);
        if (zhBody.isEmpty()) {
            warnings.add(moduleName + "." + handle.value() + ": handle has no @zh docs");
        }
        List<ApiModel.PropertyModel> properties = new ArrayList<>();
        List<ApiModel.MethodModel> methods = new ArrayList<>();
        for (Element member : type.getEnclosedElements()) {
            if (member.getKind() != ElementKind.METHOD
                    || !member.getModifiers().contains(Modifier.PUBLIC)
                    || DocletSupport.internalOf(member)) {
                continue;
            }
            ExecutableElement method = (ExecutableElement) member;
            var memberComment = trees.getDocCommentTree(method);
            if (DocletSupport.isBeanGetter(method)) {
                String prop = DocletSupport.propertyName(method);
                if (DocletSupport.zhBody(memberComment).isEmpty()) {
                    warnings.add(moduleName + "." + handle.value() + "." + prop
                            + ": property has no @zh docs");
                }
                properties.add(new ApiModel.PropertyModel(
                        prop,
                        DocletSupport.returnTypeOf(method),
                        DocletSupport.docBody(memberComment),
                        DocletSupport.zhBody(memberComment).orElse(""),
                        DocletSupport.readOnlyOf(method),
                        DocletSupport.sinceOf(method),
                        DocletSupport.mcOf(method),
                        DocletSupport.permissionOf(method),
                        DocletSupport.experimentalOf(method)));
            } else {
                if (DocletSupport.zhBody(memberComment).isEmpty()) {
                    warnings.add(moduleName + "." + handle.value() + "." + method.getSimpleName()
                            + ": method has no @zh docs");
                }
                methods.add(new ApiModel.MethodModel(
                        method.getSimpleName().toString(),
                        DocletSupport.parametersOf(method),
                        DocletSupport.returnTypeOf(method),
                        DocletSupport.docBody(memberComment),
                        DocletSupport.paramDocs(memberComment),
                        DocletSupport.returnDoc(memberComment).orElse(""),
                        DocletSupport.zhBody(memberComment).orElse(""),
                        DocletSupport.zhParamDocs(memberComment),
                        DocletSupport.zhReturnDoc(memberComment).orElse(""),
                        DocletSupport.zhExample(memberComment).orElse(""),
                        DocletSupport.sinceOf(method),
                        DocletSupport.mcOf(method),
                        DocletSupport.permissionOf(method),
                        DocletSupport.experimentalOf(method)));
            }
        }
        properties.sort(java.util.Comparator.comparing(ApiModel.PropertyModel::name));
        methods.sort(java.util.Comparator.comparing(ApiModel.MethodModel::name));
        return new ApiModel.HandleModel(handle.value(), DocletSupport.docBody(comment),
                zhBody.orElse(""), List.copyOf(properties), List.copyOf(methods));
    }

    private static ApiModel.EventModel eventModel(TypeElement type, ApiEvent event,
                                                  DocTrees trees, List<String> warnings) {
        var comment = trees.getDocCommentTree(type);
        var zhBody = DocletSupport.zhBody(comment);
        if (zhBody.isEmpty()) {
            warnings.add(event.name() + ": event has no @zh docs");
        }
        List<ApiModel.ParameterModel> fields = new ArrayList<>();
        for (Element component : type.getEnclosedElements()) {
            if (component.getKind() == ElementKind.RECORD_COMPONENT) {
                VariableElement variable = (VariableElement) component;
                fields.add(new ApiModel.ParameterModel(variable.getSimpleName().toString(),
                        DocletSupport.tsType(variable.asType())));
            }
        }
        fields.sort(java.util.Comparator.comparing(ApiModel.ParameterModel::name));
        return new ApiModel.EventModel(event.name(), event.cancellable(),
                List.copyOf(fields), DocletSupport.docBody(comment), zhBody.orElse(""));
    }

    private static String memberSince(ExecutableElement method, String moduleSince,
                                      String moduleName, List<String> warnings) {
        String since = DocletSupport.sinceOf(method);
        if (since == null) {
            since = moduleSince;
        }
        if (since == null) {
            warnings.add(moduleName + "." + method.getSimpleName() + ": missing @ApiSince");
            return null;
        }
        return since;
    }

    /** Registers a record type referenced by a signature for interface emission. */
    private static void collectReferenced(TypeMirror type, Map<String, ApiModel.TypeModel> referenced) {
        if (type.getKind() != javax.lang.model.type.TypeKind.DECLARED) {
            return;
        }
        TypeElement element = (TypeElement) ((DeclaredType) type).asElement();
        if (element.getKind() != ElementKind.RECORD || DocletSupport.eventOf(element) != null) {
            return;
        }
        List<ApiModel.ParameterModel> fields = new ArrayList<>();
        for (Element component : element.getEnclosedElements()) {
            if (component.getKind() == ElementKind.RECORD_COMPONENT) {
                VariableElement variable = (VariableElement) component;
                fields.add(new ApiModel.ParameterModel(variable.getSimpleName().toString(),
                        DocletSupport.tsType(variable.asType())));
            }
        }
        fields.sort(java.util.Comparator.comparing(ApiModel.ParameterModel::name));
        referenced.putIfAbsent(element.getSimpleName().toString(),
                new ApiModel.TypeModel(element.getSimpleName().toString(), List.copyOf(fields)));
    }
}
