import io

# --- MDDoclet: standalone types page + cross-references ---
p = 'docs-builder/src/main/java/ink/tuanzi/neomacros/docs/MDDoclet.java'
s = io.open(p, encoding='utf-8').read()

# 1. extendedBy includes standalone classes
old = '''        for (ApiModel.ModuleModel module : model.modules()) {
            for (ApiModel.ClassModel clazz : module.classes()) {
                if (!clazz.extendsName().isEmpty()) {
                    extendedBy.computeIfAbsent(clazz.extendsName(), k -> new java.util.ArrayList<>())
                            .add(clazz.name());
                }
            }
        }'''
new = '''        for (ApiModel.ModuleModel module : model.modules()) {
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
        }'''
assert old in s, 'MDDoclet extendedBy anchor missing'
s = s.replace(old, new)

# 2. emit types.md/types.zh.md after module pages
old = '''            write(apiDir.resolve(DocletSupport.WARNINGS_FILE), String.join("\\n", model.warnings()) + "\\n");'''
new = '''            if (!model.standaloneClasses().isEmpty()) {
                write(apiDir.resolve("types.md"), renderTypesPage(model.standaloneClasses(), extendedBy, false));
                write(apiDir.resolve("types.zh.md"), renderTypesPage(model.standaloneClasses(), extendedBy, true));
            }
            write(apiDir.resolve(DocletSupport.WARNINGS_FILE), String.join("\\n", model.warnings()) + "\\n");'''
assert old in s, 'MDDoclet write anchor missing'
s = s.replace(old, new)

# 3. add renderTypesPage method
old = '''    /** Renders one module page; {@code zh} selects the translation-fallback view. */'''
new = '''    /** Renders the standalone types page (Vec3, BlockPos, etc.). */
    private static String renderTypesPage(List<ApiModel.ClassModel> classes,
            Map<String, List<String>> extendedBy, boolean zh) {
        StringBuilder sb = new StringBuilder();
        sb.append(zh ? "# \\u7c7b\\u578b\\u5b9a\\u4e49\\n\\n" : "# Type Definitions\\n\\n");
        sb.append(zh
            ? "\\u4ee5\\u4e0b\\u7c7b\\u578b\\u7531\\u5f15\\u64ce\\u751f\\u6210 guest \\u7aef\\u771f\\u5b9e ES \\u7c7b\\uff0c\\u53ef\\u901a\\u8fc7 `@neomacros/types` \\u5bfc\\u5165\\u5e76\\u4f7f\\u7528 `instanceof` \\u5224\\u65ad\\u3002\\n\\n"
            : "These types are generated as real guest ES classes by the engine. "
            + "Import them from `@neomacros/types` and use `instanceof` for type checks.\\n\\n");
        java.util.Set<String> allNames = new java.util.HashSet<>();
        for (ApiModel.ClassModel clazz : classes) {
            allNames.add(clazz.name());
        }
        for (ApiModel.ClassModel clazz : classes) {
            sb.append("## ").append(clazz.name()).append("\\n\\n");
            List<String> meta = new java.util.ArrayList<>();
            meta.add(switch (clazz.kind()) {
                case HANDLE -> zh ? "\\u53e5\\u67c4" : "Handle";
                case DATA -> zh ? "\\u6570\\u636e" : "Data";
            });
            if (!clazz.wraps().isEmpty()) {
                meta.add(zh ? "\\u5305\\u88c5\\uff1a`" + clazz.wraps() + "`" : "Wraps: `" + clazz.wraps() + "`");
            }
            if (!clazz.extendsName().isEmpty()) {
                meta.add(zh ? "\\u7ee7\\u627f\\uff1a`" + clazz.extendsName() + "`" : "Extends: `" + clazz.extendsName() + "`");
            }
            List<String> children = extendedBy.getOrDefault(clazz.name(), List.of());
            if (!children.isEmpty()) {
                meta.add(zh ? "\\u88ab\\u7ee7\\u627f\\uff1a" + joinNames(children, "\\u3001")
                        : "Extended by: " + joinNames(children, ", "));
            }
            sb.append(String.join(" \\u00b7 ", meta)).append("\\n\\n");
            sb.append(text(clazz.doc(), clazz.zhDoc(), zh)).append("\\n\\n");
            renderProperties(sb, clazz.properties(), zh);
            renderMethods(sb, clazz.methods(), zh);
        }
        return sb.toString();
    }

    /** Renders one module page; {@code zh} selects the translation-fallback view. */'''
assert old in s, 'MDDoclet renderModule anchor missing'
s = s.replace(old, new, 1)

io.open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('MDDoclet done')

# --- TSDoclet: standalone classes in .d.ts ---
p = 'docs-builder/src/main/java/ink/tuanzi/neomacros/docs/TSDoclet.java'
s = io.open(p, encoding='utf-8').read()

# add standalone class declarations before the neo global
old = '''    /** The neo global aggregate listing modules reachable through the global channel. */
    private static void renderGlobal(StringBuilder sb, ApiModel model) {'''
new = '''    /** Standalone @ApiClass class declarations (types module supplement). */
    private static void renderStandaloneClasses(StringBuilder sb, ApiModel model) {
        if (model.standaloneClasses().isEmpty()) {
            return;
        }
        sb.append("declare module '@neomacros/types' {\\n");
        for (ApiModel.ClassModel clazz : model.standaloneClasses()) {
            sb.append("    ").append(jsdoc(clazz.doc(), null, null)).append("\\n");
            sb.append("    export class ").append(clazz.name());
            if (!clazz.extendsName().isEmpty()) {
                sb.append(" extends ").append(clazz.extendsName());
            }
            sb.append(" {\\n");
            for (ApiModel.PropertyModel property : clazz.properties()) {
                renderProperty(sb, property);
            }
            for (ApiModel.MethodModel method : clazz.methods()) {
                renderMethod(sb, method);
            }
            sb.append("    }\\n");
        }
        sb.append("}\\n\\n");
    }

    /** The neo global aggregate listing modules reachable through the global channel. */
    private static void renderGlobal(StringBuilder sb, ApiModel model) {'''
assert old in s, 'TSDoclet renderGlobal anchor missing'
s = s.replace(old, new)

# call renderStandaloneClasses before renderGlobal
old = '''        renderEvents(sb, model);
        renderGlobal(sb, model);'''
new = '''        renderStandaloneClasses(sb, model);
        renderEvents(sb, model);
        renderGlobal(sb, model);'''
assert old in s
s = s.replace(old, new)

io.open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('TSDoclet done')
