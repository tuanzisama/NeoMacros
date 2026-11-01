package ink.tuanzi.neomacros.fabric.web.v1;

import ink.tuanzi.neomacros.fabric.web.api.NmDesc;
import ink.tuanzi.neomacros.fabric.web.api.NmNullable;

import java.util.List;

/**
 * One script as the panel sees it: identity from the manifest, live state
 * from the module manager, and the current located error if any.
 *
 * @param id          the unique id {@code category/name}
 * @param category    the trigger category (directory)
 * @param name        the manifest name
 * @param version     the manifest version
 * @param author      the manifest author
 * @param lifecycle   the effective load timing
 * @param enabled     the persisted autoload intent
 * @param running     whether the module is currently loaded
 * @param key         the effective key binding, null when unbound
 * @param permissions the declared capabilities
 * @param error       the current located error, null when none
 */
public record MacroView(
        String id,
        MacroCategory category,
        String name,
        String version,
        String author,
        MacroLifecycle lifecycle,
        boolean enabled,
        boolean running,
        @NmNullable String key,
        List<String> permissions,
        @NmNullable LocatedError error) {
}
