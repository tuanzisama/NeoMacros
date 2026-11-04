package ink.tuanzi.neomacros.docs;

import jdk.javadoc.doclet.Doclet;
import jdk.javadoc.doclet.DocletEnvironment;
import jdk.javadoc.doclet.Reporter;

import javax.lang.model.SourceVersion;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Renders one bilingual Markdown page per module: {@code <name>.md} from the
 * English Javadoc (the normative source) and {@code <name>.zh.md} from the
 * {@code @zh} tag translations, falling back to the English text per member.
 * Generation warnings (missing translations, missing @ApiSince) are collected
 * into {@code warnings.txt}.
 */
public class MDDoclet implements Doclet {

    private Reporter reporter;
    private Path outDir = Path.of("docs-dist");

    @Override
    public void init(Locale locale, Reporter reporter) {
        this.reporter = reporter;
    }

    @Override
    public String getName() {
        return "MDDoclet";
    }

    @Override
    public Set<? extends Doclet.Option> getSupportedOptions() {
        return Set.of(
                new Doclet.Option() {
                    @Override
                    public int getArgumentCount() {
                        return 1;
                    }

                    @Override
                    public String getDescription() {
                        return "output directory for the generated Markdown pages";
                    }

                    @Override
                    public Doclet.Option.Kind getKind() {
                        return Doclet.Option.Kind.STANDARD;
                    }

                    @Override
                    public List<String> getNames() {
                        return List.of("-d");
                    }

                    @Override
                    public String getParameters() {
                        return "directory";
                    }

                    @Override
                    public boolean process(String option, List<String> arguments) {
                        outDir = Path.of(arguments.get(0));
                        return true;
                    }
                },
                DocletSupport.passthroughOption("-tag"));
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean run(DocletEnvironment env) {
        ApiModel model = ModelExtractor.extract(env);
        Path apiDir = outDir.resolve("api");
        Map<String, List<String>> extendedBy = new java.util.LinkedHashMap<>();
        for (ApiModel.ModuleModel module : model.modules()) {
            for (ApiModel.ClassModel clazz : module.classes()) {
                if (!clazz.extendsName().isEmpty()) {
                    extendedBy.computeIfAbsent(clazz.extendsName(), k -> new java.util.ArrayList<>())
                            .add(clazz.name());
                }
            }
        }
        for (ApiModel.ClassModel clazz : model.standaloneClasses()) {
            if (!clazz.extendsName().isEmpty()) {
                extendedBy.computeIfAbsent(clazz.extendsName(), k -> new java.util.ArrayList<>())
                        .add(clazz.name());
            }
        }
        // Build the set of all documented type names for link rendering.
        Set<String> knownTypes = new java.util.TreeSet<>();
        for (ApiModel.ModuleModel module : model.modules()) {
            for (ApiModel.ClassModel clazz : module.classes()) {
                knownTypes.add(clazz.name());
            }
            for (ApiModel.TypeModel type : module.referencedTypes()) {
                knownTypes.add(type.name());
            }
        }
        for (ApiModel.ClassModel clazz : model.standaloneClasses()) {
            knownTypes.add(clazz.name());
        }
        try {
            Files.createDirectories(apiDir);
            for (ApiModel.ModuleModel module : model.modules()) {
                write(apiDir.resolve(module.name() + ".md"),
                        renderModule(module, false, extendedBy, knownTypes));
                write(apiDir.resolve(module.name() + ".zh.md"),
                        renderModule(module, true, extendedBy, knownTypes));
            }
            if (!model.standaloneClasses().isEmpty()) {
                write(apiDir.resolve("types.md"),
                        renderTypesPage(model.standaloneClasses(), extendedBy, knownTypes, false));
                write(apiDir.resolve("types.zh.md"),
                        renderTypesPage(model.standaloneClasses(), extendedBy, knownTypes, true));
            }
            if (!model.events().isEmpty()) {
                write(apiDir.resolve("events-reference.md"),
                        renderEventsReference(model.events(), false));
                write(apiDir.resolve("events-reference.zh.md"),
                        renderEventsReference(model.events(), true));
            }
            write(apiDir.resolve(DocletSupport.WARNINGS_FILE),
                    String.join("\n", model.warnings()) + "\n");
        } catch (IOException e) {
            report("failed to write generated documentation: " + e.getMessage());
            return false;
        }
        model.warnings().forEach(this::report);
        return true;
    }

    /** Renders the standalone types page (Vec3, BlockPos, etc.). */
    private static String renderTypesPage(List<ApiModel.ClassModel> classes,
            Map<String, List<String>> extendedBy, Set<String> knownTypes, boolean zh) {
        StringBuilder sb = new StringBuilder();
        sb.append(zh ? "# \u7c7b\u578b\u5b9a\u4e49\n\n" : "# Type Definitions\n\n");
        sb.append(zh
            ? "\u4ee5\u4e0b\u7c7b\u578b\u7531\u5f15\u64ce\u751f\u6210 guest \u7aef\u771f\u5b9e ES \u7c7b\uff0c\u53ef\u901a\u8fc7 `@neomacros/types` \u5bfc\u5165\u5e76\u4f7f\u7528 `instanceof` \u5224\u65ad\u3002\n\n"
            : "These types are generated as real guest ES classes by the engine. "
            + "Import them from `@neomacros/types` and use `instanceof` for type checks.\n\n");
        for (ApiModel.ClassModel clazz : classes) {
            sb.append("## ").append(clazz.name()).append("\n\n");
            List<String> meta = new java.util.ArrayList<>();
            meta.add(switch (clazz.kind()) {
                case HANDLE -> zh ? "\u53e5\u67c4" : "Handle";
                case DATA -> zh ? "\u6570\u636e" : "Data";
            });
            if (!clazz.wraps().isEmpty()) {
                meta.add(zh ? "\u5305\u88c5\uff1a`" + clazz.wraps() + "`" : "Wraps: `" + clazz.wraps() + "`");
            }
            if (!clazz.extendsName().isEmpty()) {
                meta.add(zh ? "\u7ee7\u627f\uff1a`" + clazz.extendsName() + "`" : "Extends: `" + clazz.extendsName() + "`");
            }
            List<String> children = extendedBy.getOrDefault(clazz.name(), List.of());
            if (!children.isEmpty()) {
                meta.add(zh ? "\u88ab\u7ee7\u627f\uff1a" + joinNames(children, "\u3001")
                        : "Extended by: " + joinNames(children, ", "));
            }
            sb.append(String.join(" \u00b7 ", meta)).append("\n\n");
            sb.append(text(clazz.doc(), clazz.zhDoc(), zh)).append("\n\n");
            renderProperties(sb, clazz.properties(), zh, knownTypes);
            renderMethods(sb, clazz.methods(), zh, knownTypes);
        }
        return sb.toString();
    }

    /** Renders the event catalog page: every @ApiEvent record with payload fields. */
    private static String renderEventsReference(List<ApiModel.EventModel> events, boolean zh) {
        var sb = new StringBuilder();
        sb.append(zh ? "# \u4e8b\u4ef6\u76ee\u5f55\n\n" : "# Event Catalog\n\n");
        sb.append(zh
            ? "\u4ee5\u4e0b\u662f\u6240\u6709\u53ef\u8ba2\u9605\u7684\u4e8b\u4ef6\u53ca\u5176\u8f7d\u8377\u5b57\u6bb5\u3002\u4f7f\u7528 `events.on(name, handler)` \u8ba2\u9605\u3002\n\n"
            : "All subscribable events and their payload fields. Subscribe via `events.on(name, handler)`.\n\n");
        String lastDomain = "";
        for (ApiModel.EventModel event : events) {
            String domain = event.name().contains(":")
                    ? event.name().substring(0, event.name().indexOf(':'))
                    : event.name();
            if (!domain.equals(lastDomain)) {
                if (!lastDomain.isEmpty()) {
                    sb.append("\n");
                }
                sb.append("## ").append(domain).append("\n\n");
                lastDomain = domain;
            }
            String cancellable = event.cancellable()
                    ? (zh ? " \u00b7 \u53ef\u53d6\u6d88" : " · cancellable") : "";
            sb.append("### `").append(event.name()).append("`").append(cancellable).append("\n\n");
            sb.append(text(event.doc(), event.zhDoc(), zh)).append("\n\n");
            if (!event.fields().isEmpty()) {
                sb.append("| Field | Type |\n| --- | --- |\n");
                for (ApiModel.ParameterModel field : event.fields()) {
                    sb.append("| `").append(field.name()).append("` | `").append(field.type()).append("` |\n");
                }
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    /** Renders one module page; {@code zh} selects the translation-fallback view. */
    private static String renderModule(ApiModel.ModuleModel module, boolean zh,
                                       Map<String, List<String>> extendedBy,
                                       Set<String> knownTypes) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(module.name()).append("\n\n");
        sb.append(text(module.doc(), module.zhDoc(), zh)).append("\n\n");

        List<String> meta = new java.util.ArrayList<>();
        if (!module.category().isEmpty()) {
            meta.add("Category: " + categoryLabel(module.category(), zh));
        }
        if (module.since() != null) {
            meta.add("Since: " + module.since());
        }
        if (module.mc() != null) {
            meta.add("MC availability: " + module.mc());
        }
        meta.add(module.global() ? "Exposed as `neo." + module.name() + "` and `@neomacros/"
                + module.name() + "`" : "ES module only: `@neomacros/" + module.name() + "`");
        sb.append(String.join(" · ", meta)).append("\n\n");

        renderClassTree(sb, module.classes(), extendedBy, zh);

        renderProperties(sb, module.properties(), zh, knownTypes);
        renderMethods(sb, module.methods(), zh, knownTypes);
        renderReferencedTypes(sb, module.referencedTypes(), zh);
        for (ApiModel.ClassModel clazz : module.classes()) {
            sb.append("## Class ").append(clazz.name()).append("\n\n");
            sb.append(String.join(" · ", classMeta(clazz, extendedBy, zh))).append("\n\n");
            sb.append(text(clazz.doc(), clazz.zhDoc(), zh)).append("\n\n");
            renderProperties(sb, clazz.properties(), zh, knownTypes);
            renderMethods(sb, clazz.methods(), zh, knownTypes);
        }
        return sb.toString();
    }

    /** Composes the kind/wraps/inheritance meta line of one class section. */
    private static List<String> classMeta(ApiModel.ClassModel clazz,
                                          Map<String, List<String>> extendedBy, boolean zh) {
        List<String> meta = new java.util.ArrayList<>();
        meta.add(switch (clazz.kind()) {
            case HANDLE -> zh ? "句柄" : "Handle";
            case DATA -> zh ? "数据" : "Data";
        });
        if (!clazz.wraps().isEmpty()) {
            meta.add(zh ? "包装：`" + clazz.wraps() + "`" : "Wraps: `" + clazz.wraps() + "`");
        }
        if (!clazz.extendsName().isEmpty()) {
            meta.add(zh ? "继承：`" + clazz.extendsName() + "`" : "Extends: `" + clazz.extendsName() + "`");
        }
        List<String> children = extendedBy.getOrDefault(clazz.name(), List.of());
        if (!children.isEmpty()) {
            meta.add(zh ? "被继承：" + joinNames(children, "、")
                    : "Extended by: " + joinNames(children, ", "));
        }
        return meta;
    }

    /** Backtick-joins type names with the given separator. */
    private static String joinNames(List<String> names, String separator) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) {
                sb.append(separator);
            }
            sb.append("`").append(names.get(i)).append("`");
        }
        return sb.toString();
    }

    /**
     * Renders the inheritance forest of a module's classes as an indented
     * list; roots are classes whose parent is not a class of this module.
     * Skipped entirely when the module declares no classes.
     */
    private static void renderClassTree(StringBuilder sb, List<ApiModel.ClassModel> classes,
                                        Map<String, List<String>> extendedBy, boolean zh) {
        if (classes.isEmpty()) {
            return;
        }
        Set<String> localNames = new java.util.HashSet<>();
        for (ApiModel.ClassModel clazz : classes) {
            localNames.add(clazz.name());
        }
        sb.append(zh ? "**类树**\n\n" : "**Class tree**\n\n");
        for (ApiModel.ClassModel clazz : classes) {
            if (clazz.extendsName().isEmpty() || !localNames.contains(clazz.extendsName())) {
                renderTreeNode(sb, clazz.name(), 0, localNames, extendedBy);
            }
        }
        sb.append("\n");
    }

    private static void renderTreeNode(StringBuilder sb, String name, int depth,
                                       Set<String> localNames, Map<String, List<String>> extendedBy) {
        sb.append("  ".repeat(depth)).append("- `").append(name).append("`\n");
        for (String child : extendedBy.getOrDefault(name, List.of())) {
            if (localNames.contains(child)) {
                renderTreeNode(sb, child, depth + 1, localNames, extendedBy);
            }
        }
    }

    private static void renderProperties(StringBuilder sb, List<ApiModel.PropertyModel> properties,
            boolean zh, Set<String> knownTypes) {
        if (properties.isEmpty()) {
            return;
        }
        sb.append("## Properties\n\n");
        sb.append("| Property | Type | Description |\n");
        sb.append("| --- | --- | --- |\n");
        for (ApiModel.PropertyModel property : properties) {
            sb.append("| `").append(property.name()).append("` | ").append(linkType(property.type(), knownTypes)).append(" | ");
            sb.append(text(property.doc(), property.zhDoc(), zh));
            if (property.readOnly()) {
                sb.append(" (read-only)");
            }
            if (property.experimental()) {
                sb.append(" (experimental)");
            }
            sb.append(" |\n");
        }
        sb.append("\n");
    }

    private static void renderMethods(StringBuilder sb, List<ApiModel.MethodModel> methods,
            boolean zh, Set<String> knownTypes) {
        if (methods.isEmpty()) {
            return;
        }
        sb.append("## Methods\n\n");
        for (ApiModel.MethodModel method : methods) {
            sb.append("### `").append(method.name()).append("(");
            sb.append(joinParameters(method));
            sb.append(")`\n\n");
            sb.append(text(method.doc(), method.zhDoc(), zh)).append("\n\n");
            if (!method.parameters().isEmpty()) {
                sb.append("**Parameters**\n\n");
                for (ApiModel.ParameterModel parameter : method.parameters()) {
                    sb.append("- `").append(parameter.name()).append("` ").append(
                            linkType(parameter.type(), knownTypes)
                    ).append(": ").append(method.paramDocs().getOrDefault(parameter.name(),
                            method.zhParamDocs().getOrDefault(parameter.name(), ""))).append("\n");
                }
                sb.append("\n");
            }
            String returns = text(method.returnDoc(), method.zhReturnDoc(), zh);
            if (!returns.isEmpty() || !method.returnType().equals("void")) {
                sb.append("**Returns** ").append(linkType(method.returnType(), knownTypes));
                if (!returns.isEmpty()) {
                    sb.append(" — ").append(returns);
                }
                sb.append("\n\n");
            }
            List<String> badges = new java.util.ArrayList<>();
            if (method.since() != null) {
                badges.add("Since: " + method.since());
            }
            if (method.mc() != null) {
                badges.add("MC: " + method.mc());
            }
            if (method.permission() != null) {
                badges.add("Permission: `" + method.permission() + "`");
            }
            if (method.experimental()) {
                badges.add("Experimental");
            }
            if (!badges.isEmpty()) {
                sb.append(String.join(" · ", badges)).append("\n\n");
            }
            if (zh && !method.zhExample().isEmpty()) {
                sb.append("**Example**\n\n```js\n").append(method.zhExample()).append("\n```\n\n");
            }
        }
    }

    private static String joinParameters(ApiModel.MethodModel method) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < method.parameters().size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            ApiModel.ParameterModel parameter = method.parameters().get(i);
            sb.append(parameter.name()).append(": ").append(parameter.type());
        }
        return sb.toString();
    }

    /**
     * Wraps known type names in wiki-style links to their definition anchor
     * on the types page. Unknown types stay as plain backtick code.
     */
    private static String linkType(String type, Set<String> knownTypes) {
        if (type == null || type.isEmpty() || knownTypes.isEmpty()) {
            return "`" + type + "`";
        }
        var matcher = java.util.regex.Pattern.compile("[A-Z][A-Za-z0-9]+").matcher(type);
        var out = new StringBuilder();
        int last = 0;
        while (matcher.find()) {
            String word = matcher.group();
            String before = type.substring(last, matcher.start());
            if (!before.isEmpty()) {
                out.append("`").append(before).append("`");
            }
            if (knownTypes.contains(word)) {
                out.append("[").append(word).append("](types.html#")
                        .append(word.toLowerCase(Locale.ROOT)).append(")");
            } else {
                out.append("`").append(word).append("`");
            }
            last = matcher.end();
        }
        String remaining = type.substring(last);
        if (!remaining.isEmpty()) {
            out.append("`").append(remaining).append("`");
        }
        return out.toString();
    }

    /**
     * Renders referenced port-model record types (Pos3d, EntitySnapshot, ...)
     * as field definition tables after the Methods section.
     */
    private static void renderReferencedTypes(StringBuilder sb,
            List<ApiModel.TypeModel> referencedTypes, boolean zh) {
        if (referencedTypes.isEmpty()) {
            return;
        }
        sb.append("## Referenced Types\n\n");
        sb.append(zh
            ? "\u4ee5\u4e0b\u7c7b\u578b\u5728\u65b9\u6cd5\u7b7e\u540d\u4e2d\u88ab\u5f15\u7528\u3002\n\n"
            : "The following types appear in method signatures on this page.\n\n");
        for (ApiModel.TypeModel type : referencedTypes) {
            sb.append("### ").append(type.name()).append("\n\n");
            sb.append("| Field | Type |\n");
            sb.append("| --- | --- |\n");
            for (ApiModel.ParameterModel field : type.fields()) {
                sb.append("| `").append(field.name()).append("` | `").append(field.type()).append("` |\n");
            }
            sb.append("\n");
        }
    }

    /** Picks the translation for the current view, falling back to English. */
    private static String text(String en, String zh, boolean zhView) {
        return zhView && !zh.isEmpty() ? zh : en;
    }

    /**
     * English display label for a module category on the English page;
     * category values in {@code @ApiModule} are zh-first, so the English
     * view maps the known set and passes anything else through verbatim.
     */
    private static String categoryLabel(String category, boolean zhView) {
        if (zhView) {
            return category;
        }
        return switch (category) {
            case "核心" -> "Core";
            case "界面" -> "UI";
            case "世界" -> "World";
            case "系统" -> "System";
            case "输入" -> "Input";
            default -> category;
        };
    }

    private void report(String message) {
        reporter.print(javax.tools.Diagnostic.Kind.WARNING, message);
    }

    private static void write(Path file, String content) throws IOException {
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }
}
