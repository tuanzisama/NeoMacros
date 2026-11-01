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
        try {
            Files.createDirectories(apiDir);
            for (ApiModel.ModuleModel module : model.modules()) {
                write(apiDir.resolve(module.name() + ".md"), renderModule(module, false));
                write(apiDir.resolve(module.name() + ".zh.md"), renderModule(module, true));
            }
            write(apiDir.resolve(DocletSupport.WARNINGS_FILE), String.join("\n", model.warnings()) + "\n");
        } catch (IOException e) {
            report("failed to write generated documentation: " + e.getMessage());
            return false;
        }
        model.warnings().forEach(this::report);
        return true;
    }

    /** Renders one module page; {@code zh} selects the translation-fallback view. */
    private static String renderModule(ApiModel.ModuleModel module, boolean zh) {
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

        renderProperties(sb, module.properties(), zh);
        renderMethods(sb, module.methods(), zh);
        for (ApiModel.HandleModel handle : module.handles()) {
            sb.append("## Handle ").append(handle.name()).append("\n\n");
            sb.append(text(handle.doc(), handle.zhDoc(), zh)).append("\n\n");
            renderProperties(sb, handle.properties(), zh);
            renderMethods(sb, handle.methods(), zh);
        }
        return sb.toString();
    }

    private static void renderProperties(StringBuilder sb, List<ApiModel.PropertyModel> properties, boolean zh) {
        if (properties.isEmpty()) {
            return;
        }
        sb.append("## Properties\n\n");
        sb.append("| Property | Type | Description |\n");
        sb.append("| --- | --- | --- |\n");
        for (ApiModel.PropertyModel property : properties) {
            sb.append("| `").append(property.name()).append("` | `").append(property.type()).append("` | ");
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

    private static void renderMethods(StringBuilder sb, List<ApiModel.MethodModel> methods, boolean zh) {
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
                    sb.append("- `").append(parameter.name()).append("` (`").append(parameter.type())
                            .append("`): ").append(method.paramDocs().getOrDefault(parameter.name(),
                            method.zhParamDocs().getOrDefault(parameter.name(), ""))).append("\n");
                }
                sb.append("\n");
            }
            String returns = text(method.returnDoc(), method.zhReturnDoc(), zh);
            if (!returns.isEmpty() || !method.returnType().equals("void")) {
                sb.append("**Returns** `").append(method.returnType()).append("`");
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
