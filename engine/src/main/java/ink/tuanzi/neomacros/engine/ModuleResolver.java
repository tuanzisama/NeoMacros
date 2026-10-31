package ink.tuanzi.neomacros.engine;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Module source provider consulted by the engine for every import specifier.
 *
 * <p>Virtual modules ({@code @neomacros/*}) come from the binding registry:
 * the resolver hands out generated ES module sources that re-export the
 * frozen {@code neo.*} members, so scripts and the global channel share one
 * binding object. Real modules are plain files in the script directory.</p>
 */
public interface ModuleResolver {

    /** Returns the ES module source backing a virtual specifier, or empty when unknown. */
    Optional<String> virtualSource(String specifier);

    /** Returns the file backing a real module specifier, or empty when unknown. */
    Optional<Path> fileFor(String specifier);
}
