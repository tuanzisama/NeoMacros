package ink.tuanzi.neomacros.core.module.manifest;

import com.google.gson.JsonElement;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Locates and parses the {@code neomacro.*} declaration of one script folder.
 * When several manifest formats coexist the fixed priority order is yml,
 * json, json5 and the runner-up files draw a warning. Every format funnels
 * into one plain-map shape before {@link MacroManifest} validation.
 */
public final class ManifestParser {

    /** Manifest file names in resolution priority order. */
    public static final List<String> PRIORITY = List.of("neomacro.yml", "neomacro.json", "neomacro.json5");

    private ManifestParser() {
    }

    /**
     * Parses the manifest of one script folder. Throws
     * {@link ManifestParseException} when no manifest file exists or the
     * declaration is invalid; format warnings (unknown fields, runner-up
     * files) go through {@code warn}.
     */
    public static MacroManifest parse(Path folder, Consumer<String> warn) {
        List<Path> present = new ArrayList<>();
        for (String name : PRIORITY) {
            Path file = folder.resolve(name);
            if (Files.isRegularFile(file)) {
                present.add(file);
            }
        }
        if (present.isEmpty()) {
            throw new ManifestParseException(folder.getFileName() + ": no neomacro.yml/json/json5 found");
        }
        if (present.size() > 1) {
            List<String> names = present.stream().map(p -> p.getFileName().toString()).toList();
            warn.accept(folder.getFileName() + ": multiple manifests (" + String.join(", ", names)
                    + "), using " + present.get(0).getFileName());
        }
        Path file = present.get(0);
        String fileName = file.getFileName().toString();
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ManifestParseException(fileName + ": cannot read manifest: " + e.getMessage(), e);
        }
        Map<String, Object> document = switch (extension(fileName)) {
            case "yml" -> parseYaml(text, fileName);
            case "json" -> parseJson(text, fileName);
            case "json5" -> parseJson5(text, fileName);
            default -> throw new ManifestParseException(fileName + ": unsupported manifest format");
        };
        return MacroManifest.from(document, fileName, warn);
    }

    private static Map<String, Object> parseYaml(String text, String fileName) {
        try {
            Object loaded = new Yaml(new SafeConstructor(new LoaderOptions())).load(text);
            return asDocument(loaded, fileName);
        } catch (RuntimeException e) {
            throw new ManifestParseException(fileName + ": invalid YAML: " + e.getMessage(), e);
        }
    }

    private static Map<String, Object> parseJson(String text, String fileName) {
        try {
            return asDocument(fromGson(com.google.gson.JsonParser.parseString(text)), fileName);
        } catch (RuntimeException e) {
            throw new ManifestParseException(fileName + ": invalid JSON: " + e.getMessage(), e);
        }
    }

    private static Map<String, Object> parseJson5(String text, String fileName) {
        try {
            return Json5Parser.parseObject(text);
        } catch (RuntimeException e) {
            throw new ManifestParseException(fileName + ": invalid JSON5: " + e.getMessage(), e);
        }
    }

    /** Narrows a parsed document to a string-keyed object map. */
    private static Map<String, Object> asDocument(Object value, String fileName) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new ManifestParseException(fileName + ": manifest must be an object/mapping");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() != null) {
                out.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return out;
    }

    /** Converts a Gson tree into plain Java values. */
    private static Object fromGson(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (element.isJsonObject()) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                out.put(entry.getKey(), fromGson(entry.getValue()));
            }
            return out;
        }
        if (element.isJsonArray()) {
            List<Object> out = new ArrayList<>();
            for (JsonElement item : element.getAsJsonArray()) {
                out.add(fromGson(item));
            }
            return out;
        }
        var primitive = element.getAsJsonPrimitive();
        if (primitive.isBoolean()) {
            return primitive.getAsBoolean();
        }
        if (primitive.isNumber()) {
            return primitive.getAsNumber();
        }
        return primitive.getAsString();
    }

    private static String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
    }
}
