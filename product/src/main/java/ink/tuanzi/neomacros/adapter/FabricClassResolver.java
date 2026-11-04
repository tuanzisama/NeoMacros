package ink.tuanzi.neomacros.adapter;

import net.fabricmc.loader.api.FabricLoader;

import java.util.List;

/**
 * Best-effort resolution of script-supplied class names into runtime binary
 * names: direct lookup first (unobfuscated nodes and JDK classes resolve
 * as-is), then the loader's mapping resolver across the known namespaces.
 */
public final class FabricClassResolver {

    private FabricClassResolver() {
    }

    /** Resolves one script-supplied binary name to the runtime class name. */
    public static String resolve(String name) throws ClassNotFoundException {
        try {
            Class.forName(name, false, FabricClassResolver.class.getClassLoader());
            return name;
        } catch (ClassNotFoundException ignored) {
            // fall through to the mapping resolver
        }
        var resolver = FabricLoader.getInstance().getMappingResolver();
        for (String namespace : List.of("official", "intermediary", "named")) {
            try {
                String mapped = resolver.mapClassName(namespace, name);
                Class.forName(mapped, false, FabricClassResolver.class.getClassLoader());
                return mapped;
            } catch (ClassNotFoundException | RuntimeException ignored) {
                // namespace not applicable for this name; try the next one
            }
        }
        throw new ClassNotFoundException(name);
    }
}
