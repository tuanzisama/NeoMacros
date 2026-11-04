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
 * Renders one bilingual Markdown page per module plus one page per
 * documented script-facing type and event: {@code <name>.md} from the
 * English Javadoc (the normative source) and {@code <name>.zh.md} from the
 * {@code @zh} tag translations, falling back to the English text per member.
 * Class and referenced-record pages live under {@code classes/}, event pages
 * under {@code events/}. Generation warnings (missing translations, missing
 * {@code @ApiSince}) are collected into {@code warnings.txt}.
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
        Path classesDir = apiDir.resolve("classes");
        Path eventsDir = apiDir.resolve("events");
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
        // Every documented type name resolves to its own page under classes/.
        Set<String> knownTypes = new java.util.TreeSet<>();
        for (ApiModel.ModuleModel module : model.modules()) {
            for (ApiModel.ClassModel clazz : module.classes()) {
                knownTypes.add(clazz.name());
            }
        }
        for (ApiModel.ClassModel clazz : model.standaloneClasses()) {
            knownTypes.add(clazz.name());
        }
        // Referenced record types, deduplicated by name across modules; a
        // name that already has a class page wins.
        Map<String, ApiModel.TypeModel> referencedTypes = new java.util.TreeMap<>();
        for (ApiModel.ModuleModel module : model.modules()) {
            for (ApiModel.TypeModel type : module.referencedTypes()) {
                if (!knownTypes.contains(type.name())) {
                    referencedTypes.putIfAbsent(type.name(), type);
                }
            }
        }
        knownTypes.addAll(referencedTypes.keySet());
        try {
            Files.createDirectories(apiDir);
            Files.createDirectories(classesDir);
            Files.createDirectories(eventsDir);
            for (ApiModel.ModuleModel module : model.modules()) {
                write(apiDir.resolve(module.name() + ".md"),
                        renderModule(module, false, extendedBy, knownTypes));
                write(apiDir.resolve(module.name() + ".zh.md"),
                        renderModule(module, true, extendedBy, knownTypes));
                for (ApiModel.ClassModel clazz : module.classes()) {
                    write(classesDir.resolve(clazz.name() + ".md"),
                            renderClassPage(clazz, extendedBy, knownTypes, false));
                    write(classesDir.resolve(clazz.name() + ".zh.md"),
                            renderClassPage(clazz, extendedBy, knownTypes, true));
                }
            }
            for (ApiModel.ClassModel clazz : model.standaloneClasses()) {
                write(classesDir.resolve(clazz.name() + ".md"),
                        renderClassPage(clazz, extendedBy, knownTypes, false));
                write(classesDir.resolve(clazz.name() + ".zh.md"),
                        renderClassPage(clazz, extendedBy, knownTypes, true));
            }
            for (ApiModel.TypeModel type : referencedTypes.values()) {
                write(classesDir.resolve(type.name() + ".md"), renderTypePage(type, false));
                write(classesDir.resolve(type.name() + ".zh.md"), renderTypePage(type, true));
            }
            for (ApiModel.EventModel event : model.events()) {
                write(eventsDir.resolve(eventSlug(event.name()) + ".md"),
                        renderEventPage(event, false));
                write(eventsDir.resolve(eventSlug(event.name()) + ".zh.md"),
                        renderEventPage(event, true));
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

    /** File-name slug of one event name (colon becomes a dash, lowercased). */
    private static String eventSlug(String eventName) {
        return eventName.toLowerCase(Locale.ROOT).replace(':', '-');
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

        // Module classes are documented on their own pages under classes/.
        if (!module.classes().isEmpty()) {
            sb.append("**Classes**\n\n");
            List<String> links = new java.util.ArrayList<>();
            for (ApiModel.ClassModel clazz : module.classes()) {
                links.add("[`" + clazz.name() + "`](classes/" + clazz.name() + ".html)");
            }
            sb.append(String.join(" · ", links)).append("\n\n");
        }

        renderProperties(sb, module.properties(), zh, knownTypes, "classes/");
        renderMethods(sb, module.methods(), zh, knownTypes, "classes/");
        return sb.toString();
    }

    /** Renders one script-facing class as its own page under classes/. */
    private static String renderClassPage(ApiModel.ClassModel clazz,
            Map<String, List<String>> extendedBy, Set<String> knownTypes, boolean zh) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(clazz.name()).append("\n\n");
        sb.append(String.join(" · ", classMeta(clazz, extendedBy))).append("\n\n");
        sb.append(text(clazz.doc(), clazz.zhDoc(), zh)).append("\n\n");
        renderProperties(sb, clazz.properties(), zh, knownTypes, "");
        renderMethods(sb, clazz.methods(), zh, knownTypes, "");
        return sb.toString();
    }

    /** Renders one referenced record type as its own page under classes/. */
    private static String renderTypePage(ApiModel.TypeModel type, boolean zh) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(type.name()).append("\n\n");
        sb.append(zh
                ? "\u6a21\u5757\u7b7e\u540d\u4e2d\u5f15\u7528\u7684 record \u7c7b\u578b\u3002\n\n"
                : "Record type referenced in module signatures.\n\n");
        sb.append("| Field | Type |\n");
        sb.append("| --- | --- |\n");
        for (ApiModel.ParameterModel field : type.fields()) {
            sb.append("| `").append(field.name()).append("` | `").append(field.type()).append("` |\n");
        }
        sb.append("\n");
        return sb.toString();
    }

    /** Renders one event as its own page under events/. */
    private static String renderEventPage(ApiModel.EventModel event, boolean zh) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(event.name()).append("\n\n");
        if (event.cancellable()) {
            sb.append("Cancellable\n\n");
        }
        sb.append(text(event.doc(), event.zhDoc(), zh)).append("\n\n");
        if (!event.fields().isEmpty()) {
            sb.append("| Field | Type |\n| --- | --- |\n");
            for (ApiModel.ParameterModel field : event.fields()) {
                sb.append("| `").append(field.name()).append("` | `").append(field.type()).append("` |\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /** Composes the kind/wraps/inheritance meta line of one class page. */
    private static List<String> classMeta(ApiModel.ClassModel clazz,
                                          Map<String, List<String>> extendedBy) {
        List<String> meta = new java.util.ArrayList<>();
        meta.add(switch (clazz.kind()) {
            case HANDLE -> "Handle";
            case DATA -> "Data";
        });
        if (!clazz.wraps().isEmpty()) {
            meta.add("Wraps: `" + clazz.wraps() + "`");
        }
        if (!clazz.extendsName().isEmpty()) {
            meta.add("Extends: [`" + clazz.extendsName() + "`](" + clazz.extendsName() + ".html)");
        }
        List<String> children = extendedBy.getOrDefault(clazz.name(), List.of());
        if (!children.isEmpty()) {
            meta.add("Extended by: " + joinLinks(children));
        }
        return meta;
    }

    /** Link-joins type names pointing at their class pages (same directory). */
    private static String joinLinks(List<String> names) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append("[`").append(names.get(i)).append("`](").append(names.get(i)).append(".html)");
        }
        return sb.toString();
    }

    private static void renderProperties(StringBuilder sb, List<ApiModel.PropertyModel> properties,
            boolean zh, Set<String> knownTypes, String classPrefix) {
        if (properties.isEmpty()) {
            return;
        }
        sb.append("## Properties\n\n");
        sb.append("| Property | Type | Description |\n");
        sb.append("| --- | --- | --- |\n");
        for (ApiModel.PropertyModel property : properties) {
            sb.append("| `").append(property.name()).append("` | ")
                    .append(linkType(property.type(), knownTypes, classPrefix)).append(" | ");
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
            boolean zh, Set<String> knownTypes, String classPrefix) {
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
                            linkType(parameter.type(), knownTypes, classPrefix)
                    ).append(": ").append(method.paramDocs().getOrDefault(parameter.name(),
                            method.zhParamDocs().getOrDefault(parameter.name(), ""))).append("\n");
                }
                sb.append("\n");
            }
            String returns = text(method.returnDoc(), method.zhReturnDoc(), zh);
            if (!returns.isEmpty() || !method.returnType().equals("void")) {
                sb.append("**Returns** ").append(linkType(method.returnType(), knownTypes, classPrefix));
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
     * Wraps known type names in wiki-style links to their pages under
     * {@code classes/}. Unlinked regions stay one code span each so
     * generics like {@code Promise<void>} render verbatim; bare code spans
     * are skipped when nothing links. {@code classPrefix} is empty on the
     * class pages themselves (same directory) and {@code classes/} on
     * module pages.
     */
    private static String linkType(String type, Set<String> knownTypes, String classPrefix) {
        if (type == null || type.isEmpty()) {
            return "`" + type + "`";
        }
        var matcher = java.util.regex.Pattern.compile("[A-Z][A-Za-z0-9]+").matcher(type);
        var out = new StringBuilder();
        int last = 0;
        boolean linked = false;
        while (matcher.find()) {
            String word = matcher.group();
            if (knownTypes.contains(word)) {
                String before = type.substring(last, matcher.start());
                if (!before.isEmpty()) {
                    out.append("`").append(before).append("`");
                }
                out.append("[").append(word).append("](")
                        .append(classPrefix).append(word).append(".html)");
                last = matcher.end();
                linked = true;
            }
        }
        if (!linked) {
            return "`" + type + "`";
        }
        String remaining = type.substring(last);
        if (!remaining.isEmpty()) {
            out.append("`").append(remaining).append("`");
        }
        return out.toString();
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
