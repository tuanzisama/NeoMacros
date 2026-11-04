package ink.tuanzi.neomacros.docs;

import com.sun.source.doctree.DocCommentTree;
import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.ParamTree;
import com.sun.source.doctree.ReturnTree;
import com.sun.source.doctree.TextTree;
import com.sun.source.doctree.UnknownBlockTagTree;
import com.sun.source.doctree.UnknownInlineTagTree;
import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiEvent;
import ink.tuanzi.neomacros.doc.api.ApiExperimental;
import ink.tuanzi.neomacros.doc.api.ApiInternal;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiModule;
import ink.tuanzi.neomacros.doc.api.ApiPermission;
import ink.tuanzi.neomacros.doc.api.ApiReadOnly;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.doc.api.ApiType;
import jdk.javadoc.doclet.Doclet;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Shared helpers for the doclets: Javadoc flattening, bilingual tag
 * extraction, annotation reads, and the Java-to-TypeScript type mapping.
 */
final class DocletSupport {

    private DocletSupport() {
    }

    /** Name of the warnings artifact written next to the Markdown files. */
    static final String WARNINGS_FILE = "warnings.txt";

    /**
     * Flattens the English comment body (first sentence plus remaining body,
     * block tags excluded) to normalized plain text.
     */
    static String flattenBody(DocCommentTree tree) {
        if (tree == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        appendNodes(tree.getFirstSentence(), sb);
        if (sb.length() > 0) {
            sb.append(' ');
        }
        appendNodes(tree.getBody(), sb);
        return normalize(sb.toString());
    }

    /** Collapses whitespace runs so multi-line sources render as one line. */
    static String normalize(String text) {
        return text.replaceAll("\\s+", " ").trim();
    }

    /** Flattens a list of doc tree nodes to plain text. */
    static String flattenNodes(List<? extends DocTree> nodes) {
        StringBuilder sb = new StringBuilder();
        appendNodes(nodes, sb);
        return sb.toString();
    }

    private static void appendNodes(List<? extends DocTree> nodes, StringBuilder sb) {
        for (DocTree node : nodes) {
            if (node instanceof UnknownBlockTagTree blockTag) {
                appendNodes(blockTag.getContent(), sb);
            } else if (node instanceof UnknownInlineTagTree inlineTag) {
                appendNodes(inlineTag.getContent(), sb);
            } else {
                appendNode(node, sb);
            }
        }
    }

    private static void appendNode(DocTree node, StringBuilder sb) {
        switch (node.getKind()) {
            case TEXT -> sb.append(((TextTree) node).getBody());
            case CODE -> sb.append(((com.sun.source.doctree.LiteralTree) node).getBody().getBody());
            case LITERAL -> sb.append(((com.sun.source.doctree.LiteralTree) node).getBody().getBody());
            case ERRONEOUS -> sb.append(((com.sun.source.doctree.ErroneousTree) node).getBody());
            default -> {
                // Structural nodes (lists, links, entities) contribute nothing to the
                // plain-text view; the fixture sources use plain paragraphs only.
            }
        }
    }

    /** Extracts the English body text of the comment. */
    static String docBody(DocCommentTree tree) {
        return flattenBody(tree);
    }

    /**
     * Returns the text of a custom block tag, or empty when absent. The name
     * is given without the leading {@code @} (matching {@code getTagName()}).
     */
    static Optional<String> customTag(DocCommentTree tree, String tagName) {
        if (tree == null) {
            return Optional.empty();
        }
        return tree.getBlockTags().stream()
                .filter(UnknownBlockTagTree.class::isInstance)
                .map(t -> (UnknownBlockTagTree) t)
                .filter(t -> tagName.contentEquals(t.getTagName()))
                .map(t -> normalize(flattenNodes(t.getContent())))
                .filter(s -> !s.isEmpty())
                .findFirst();
    }

    /** Returns the @zh Chinese body text, or empty when absent. */
    static Optional<String> zhBody(DocCommentTree tree) {
        return customTag(tree, "zh");
    }

    /** Returns the @zh-example code text, or empty when absent. */
    static Optional<String> zhExample(DocCommentTree tree) {
        return customTag(tree, "zh-example");
    }

    /** Returns @param name-to-description entries. */
    static Map<String, String> paramDocs(DocCommentTree tree) {
        Map<String, String> out = new LinkedHashMap<>();
        if (tree == null) {
            return out;
        }
        for (DocTree tag : tree.getBlockTags()) {
            if (tag.getKind() == DocTree.Kind.PARAM) {
                ParamTree param = (ParamTree) tag;
                out.put(param.getName().getName().toString(), normalize(flattenNodes(param.getDescription())));
            }
        }
        return out;
    }

    /** Returns @zh-param name-to-description entries; the name is the first whitespace token. */
    static Map<String, String> zhParamDocs(DocCommentTree tree) {
        Map<String, String> out = new LinkedHashMap<>();
        if (tree == null) {
            return out;
        }
        for (DocTree tag : tree.getBlockTags()) {
            if (tag instanceof UnknownBlockTagTree blockTag
                    && "zh-param".contentEquals(blockTag.getTagName())) {
                String text = normalize(flattenNodes(blockTag.getContent()));
                int split = text.indexOf(' ');
                if (split > 0) {
                    out.put(text.substring(0, split), text.substring(split + 1).trim());
                }
            }
        }
        return out;
    }

    /** Returns the @return description, or empty when absent. */
    static Optional<String> returnDoc(DocCommentTree tree) {
        if (tree == null) {
            return Optional.empty();
        }
        return tree.getBlockTags().stream()
                .filter(t -> t.getKind() == DocTree.Kind.RETURN)
                .map(t -> normalize(flattenNodes(((ReturnTree) t).getDescription())))
                .filter(s -> !s.isEmpty())
                .findFirst();
    }

    /** Returns the @zh-return description, or empty when absent. */
    static Optional<String> zhReturnDoc(DocCommentTree tree) {
        return customTag(tree, "zh-return");
    }

    /** Extracts the module declaration from an annotated class, or null when the class is not a module. */
    static ApiModule moduleOf(TypeElement type) {
        return type.getAnnotation(ApiModule.class);
    }

    /** Extracts the class declaration from an annotated type, or null when absent. */
    static ApiClass classOf(TypeElement type) {
        return type.getAnnotation(ApiClass.class);
    }

    /** Returns the {@code @ApiSince} value of a type or method element, or null when absent. */
    static String sinceOf(Element element) {
        ApiSince since = element.getAnnotation(ApiSince.class);
        return since == null ? null : since.value();
    }

    /** Returns the {@code @ApiLoader} value of a type or method element, or null when absent. */
    static String loaderOf(javax.lang.model.element.Element element) {
        ApiLoader loader = element.getAnnotation(ApiLoader.class);
        return loader == null ? null : loader.value();
    }

    /** Returns the {@code @ApiMC} range of a type or method element, or null when absent. */
    static String mcOf(Element element) {
        ApiMC mc = element.getAnnotation(ApiMC.class);
        return mc == null ? null : mc.value();
    }

    /** Returns the capability key required by {@code @ApiPermission}, or null when unrestricted. */
    static String permissionOf(Element element) {
        ApiPermission permission = element.getAnnotation(ApiPermission.class);
        return permission == null ? null : permission.value();
    }

    /** Returns the explicit TypeScript type of the member or parameter, or empty when inferred. */
    static Optional<String> typeOverrideOf(Element element) {
        ApiType override = element.getAnnotation(ApiType.class);
        return override == null ? Optional.empty() : Optional.of(override.value());
    }

    /** Returns true when the member carries the experimental badge. */
    static boolean experimentalOf(Element element) {
        return element.getAnnotation(ApiExperimental.class) != null;
    }

    /** Returns true when the member is internal surface and must be hidden from documentation. */
    static boolean internalOf(Element element) {
        return element.getAnnotation(ApiInternal.class) != null;
    }

    /** Returns true when the member is marked read-only in the TypeScript definitions. */
    static boolean readOnlyOf(Element element) {
        return element.getAnnotation(ApiReadOnly.class) != null;
    }

    /** Returns the event descriptor carried by a record class, or null when absent. */
    static ApiEvent eventOf(TypeElement type) {
        return type.getAnnotation(ApiEvent.class);
    }

    /** True when the method is a bean getter (getX/isX with no parameters and a non-void return). */
    static boolean isBeanGetter(ExecutableElement method) {
        String name = method.getSimpleName().toString();
        boolean prefix = name.startsWith("get") || name.startsWith("is");
        return prefix && method.getParameters().isEmpty()
                && method.getReturnType().getKind() != TypeKind.VOID;
    }

    /** Converts a bean getter name to the script property name ({@code getName} to {@code name}). */
    static String propertyName(ExecutableElement getter) {
        String name = getter.getSimpleName().toString();
        int offset = 0;
        if (name.startsWith("is") && name.length() > 2) {
            offset = 2;
        } else if (name.startsWith("get") && name.length() > 3) {
            offset = 3;
        }
        String base = name.substring(offset);
        return base.isEmpty() ? base : Character.toLowerCase(base.charAt(0)) + base.substring(1);
    }

    /** Maps a Java signature type to the script-visible TypeScript type. */
    static String tsType(TypeMirror type) {
        return switch (type.getKind()) {
            case BOOLEAN -> "boolean";
            case BYTE, SHORT, INT, LONG, FLOAT, DOUBLE -> "number";
            case CHAR -> "string";
            case VOID -> "void";
            case DECLARED -> tsDeclaredType((DeclaredType) type);
            default -> "unknown";
        };
    }

    private static String tsDeclaredType(DeclaredType type) {
        TypeElement element = (TypeElement) type.asElement();
        Optional<String> override = typeOverrideOf(element);
        if (override.isPresent()) {
            return override.get();
        }
        ApiClass apiClass = classOf(element);
        if (apiClass != null) {
            return apiClass.name();
        }
        if (element.getKind() == ElementKind.RECORD) {
            return element.getSimpleName().toString();
        }
        List<? extends TypeMirror> args = type.getTypeArguments();
        return switch (element.getQualifiedName().toString()) {
            case "java.lang.String", "java.lang.CharSequence", "java.util.UUID",
                    "java.lang.Character" -> "string";
            case "java.lang.Void" -> "void";
            case "java.lang.Boolean" -> "boolean";
            case "java.lang.Byte", "java.lang.Short", "java.lang.Integer",
                    "java.lang.Long", "java.lang.Float", "java.lang.Double" -> "number";
            case "java.lang.Object" -> "unknown";
            case "java.util.List", "java.util.Collection", "java.util.Set" ->
                    "Array<" + (args.isEmpty() ? "unknown" : tsType(args.get(0))) + ">";
            case "java.util.Map" -> {
                String key = args.isEmpty() ? "unknown" : tsType(args.get(0));
                String value = args.size() < 2 ? "unknown" : tsType(args.get(1));
                yield "Record<" + key + ", " + value + ">";
            }
            case "java.util.Optional" ->
                    (args.isEmpty() ? "unknown" : tsType(args.get(0))) + " | undefined";
            case "java.util.concurrent.CompletableFuture" ->
                    "Promise<" + (args.isEmpty() ? "void" : tsType(args.get(0))) + ">";
            default -> element.getSimpleName().toString();
        };
    }

    /** Returns the parameters of the method as name/type pairs, honoring @ApiType overrides. */
    static List<ApiModel.ParameterModel> parametersOf(ExecutableElement method) {
        return method.getParameters().stream()
                .map(p -> new ApiModel.ParameterModel(p.getSimpleName().toString(),
                        typeOverrideOf(p).orElseGet(() -> tsType(p.asType()))))
                .toList();
    }

    /** Returns the script-visible return type of the method, honoring an @ApiType override. */
    static String returnTypeOf(ExecutableElement method) {
        return typeOverrideOf(method).orElseGet(() -> tsType(method.getReturnType()));
    }

    /** Accepts a single-argument option without consuming its value (e.g. {@code -tag}). */
    static Doclet.Option passthroughOption(String name) {
        return new Doclet.Option() {
            @Override
            public int getArgumentCount() {
                return 1;
            }

            @Override
            public String getDescription() {
                return "declares a custom tag; consumed by the doclet without action";
            }

            @Override
            public Doclet.Option.Kind getKind() {
                return Doclet.Option.Kind.STANDARD;
            }

            @Override
            public List<String> getNames() {
                return List.of(name);
            }

            @Override
            public String getParameters() {
                return "specification";
            }

            @Override
            public boolean process(String option, List<String> arguments) {
                return true;
            }
        };
    }
}
