package ink.tuanzi.neomacros.core.api.bindgen;

import java.util.List;

/**
 * Resolves the permission view for one script load. The trust gate
 * (T-100) supplies the implementation: it hashes the entry source and
 * hands back the view for this load together with the content hash the
 * decision was keyed on, so runtime requests can grant against the same
 * key without re-reading the file.
 */
public interface PermissionViewResolver {

    /** The resolved binding view plus the content hash it was keyed on. */
    record ResolvedPermissions(PermissionView view, String contentHash) {
    }

    /**
     * Resolves the view for one load: the script manifest name and its
     * declared capabilities go in, the binding view and hash come out
     * (hash null when the source could not be read or hashed).
     */
    ResolvedPermissions resolve(String scriptName, List<String> declared);
}
