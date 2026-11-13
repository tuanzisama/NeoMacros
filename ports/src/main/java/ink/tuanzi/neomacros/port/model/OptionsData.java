package ink.tuanzi.neomacros.port.model;

import java.util.List;

/**
 * Point-in-time copy of the client game-options face: the option values that
 * back the script {@code options} handle together with the window, language,
 * difficulty and resource-pack state. Component names match the option names
 * the write face accepts, so one vocabulary serves reads and writes.
 *
 * <p>Nullable components mark values that may be absent while the option face
 * itself exists: {@code difficultyLocked} without a loaded world,
 * {@code fullscreenResolution} without a configured video mode, and the
 * option strings before the first read.</p>
 *
 * @param fov                     field of view in degrees
 * @param gamma                   brightness (gamma) level
 * @param guiScale                configured GUI scale factor
 * @param renderDistance          render distance in chunks
 * @param biomeBlendRadius        biome blend radius in blocks
 * @param graphicsMode            graphics preset ({@code fast}, {@code fancy}, {@code fabulous}, ...)
 * @param prioritizeChunkUpdates  chunk builder mode ({@code none}, {@code nearby}, {@code player_affected})
 * @param ambientOcclusion        whether ambient occlusion is enabled
 * @param simulationDistance      simulation distance in chunks
 * @param framerateLimit          upper frame-rate limit
 * @param enableVsync             whether vertical sync is enabled
 * @param bobView                 whether view bobbing is enabled
 * @param attackIndicator         attack indicator mode ({@code off}, {@code crosshair}, {@code hotbar})
 * @param cloudStatus             cloud rendering mode ({@code off}, {@code fast}, {@code fancy})
 * @param fullscreen              whether the game runs in fullscreen
 * @param particles               particle rendering mode ({@code minimal}, {@code decreased}, {@code all})
 * @param mipmapLevels            mip-map level count
 * @param entityShadows           whether entity shadows are enabled
 * @param screenEffectScale       screen distortion effect scale
 * @param entityDistanceScaling   entity distance scaling factor
 * @param fovEffectScale          field-of-view effect scale
 * @param showAutosaveIndicator   whether the autosave indicator is shown
 * @param cameraMode              camera mode ordinal ({@code 0} first person, {@code 1} third person back, {@code 2} third person front)
 * @param smoothCamera            whether smooth camera is enabled
 * @param width                   window width in screen pixels
 * @param height                  window height in screen pixels
 * @param language                selected language code
 * @param difficultyLocked        whether the world difficulty is locked; {@code null} without a world
 * @param resourcePacks           available resource-pack identifiers
 * @param enabledResourcePacks    selected resource-pack identifiers
 * @param serverPacksDisabled     whether server-provided resource packs are rejected
 * @param fullscreenResolution    configured fullscreen video mode string; {@code null} when none
 * @param chatVisibility          chat visibility ({@code full}, {@code system}, {@code hidden})
 * @param chatColors              whether chat colors are shown
 * @param chatLinks               whether chat web links open
 * @param chatLinksPrompt         whether opening a chat web link asks first
 * @param chatOpacity             chat background opacity
 * @param textBackgroundOpacity   text background opacity
 * @param chatScale               chat text scale
 * @param chatLineSpacing         spacing between chat lines
 * @param chatDelay               delay before chat messages appear, in seconds
 * @param chatWidth               chat width scale
 * @param chatHeightFocused       chat height scale while chat is focused
 * @param chatHeightUnfocused     chat height scale while chat is unfocused
 * @param narrator                narrator mode ({@code off}, {@code chat}, {@code system}, {@code all})
 * @param autoSuggestions         whether command suggestions are shown while typing
 * @param hideMatchedNames        whether messages from blocked players are hidden
 * @param reducedDebugInfo        whether the debug overlay is reduced
 * @param sensitivity             mouse sensitivity
 * @param invertMouseY            whether the vertical mouse axis is inverted
 * @param mouseWheelSensitivity   mouse wheel sensitivity
 * @param discreteMouseScroll     whether the mouse wheel scrolls in discrete steps
 * @param touchscreen             whether touchscreen mode is on; always {@code false} from 26.2
 * @param rawMouseInput           whether raw mouse input is on; always {@code true} from 26.3
 * @param autoJump                whether auto-jump is enabled
 * @param toggleCrouch            whether sneak uses toggle behaviour
 * @param toggleSprint            whether sprint uses toggle behaviour
 * @param showSubtitles           whether subtitle captions are shown
 * @param backgroundForChatOnly   whether text backgrounds render behind chat only
 * @param darkMojangStudiosBackground whether the dark Mojang Studios background is used
 * @param hideLightningFlash      whether lightning flashes are hidden
 */
public record OptionsData(
        int fov,
        double gamma,
        int guiScale,
        int renderDistance,
        int biomeBlendRadius,
        String graphicsMode,
        String prioritizeChunkUpdates,
        boolean ambientOcclusion,
        int simulationDistance,
        int framerateLimit,
        boolean enableVsync,
        boolean bobView,
        String attackIndicator,
        String cloudStatus,
        boolean fullscreen,
        String particles,
        int mipmapLevels,
        boolean entityShadows,
        double screenEffectScale,
        double entityDistanceScaling,
        double fovEffectScale,
        boolean showAutosaveIndicator,
        int cameraMode,
        boolean smoothCamera,
        int width,
        int height,
        String language,
        Boolean difficultyLocked,
        List<String> resourcePacks,
        List<String> enabledResourcePacks,
        boolean serverPacksDisabled,
        String fullscreenResolution,
        String chatVisibility,
        boolean chatColors,
        boolean chatLinks,
        boolean chatLinksPrompt,
        double chatOpacity,
        double textBackgroundOpacity,
        double chatScale,
        double chatLineSpacing,
        double chatDelay,
        double chatWidth,
        double chatHeightFocused,
        double chatHeightUnfocused,
        String narrator,
        boolean autoSuggestions,
        boolean hideMatchedNames,
        boolean reducedDebugInfo,
        double sensitivity,
        boolean invertMouseY,
        double mouseWheelSensitivity,
        boolean discreteMouseScroll,
        boolean touchscreen,
        boolean rawMouseInput,
        boolean autoJump,
        boolean toggleCrouch,
        boolean toggleSprint,
        boolean showSubtitles,
        boolean backgroundForChatOnly,
        boolean darkMojangStudiosBackground,
        boolean hideLightningFlash) {

    /**
     * Returns the value of one component by its option name, or {@code null}
     * when no component carries that name. Write verification and stub
     * adapters address components through this name.
     *
     * @param name component (option) name
     * @return the component value, or null when unknown
     */
    public Object option(String name) {
        for (java.lang.reflect.RecordComponent component : components()) {
            if (component.getName().equals(name)) {
                try {
                    return component.getAccessor().invoke(this);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException("option read failed: " + name, e);
                }
            }
        }
        return null;
    }

    /**
     * Returns a copy of this face with one named component replaced by the
     * given value; numbers coerce onto the component's primitive type.
     * Stub adapters apply recorded writes through this method.
     *
     * @param name  component (option) name
     * @param value replacement value
     * @return the updated copy
     * @throws IllegalArgumentException when no component carries the name or
     *                                  the value cannot be coerced
     */
    public OptionsData withOption(String name, Object value) {
        java.lang.reflect.RecordComponent[] parts = components();
        Class<?>[] types = new Class<?>[parts.length];
        Object[] arguments = new Object[parts.length];
        boolean found = false;
        for (int i = 0; i < parts.length; i++) {
            types[i] = parts[i].getType();
            if (parts[i].getName().equals(name)) {
                arguments[i] = coerce(parts[i].getType(), value, name);
                found = true;
            } else {
                try {
                    arguments[i] = parts[i].getAccessor().invoke(this);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException("option read failed: " + parts[i].getName(), e);
                }
            }
        }
        if (!found) {
            throw new IllegalArgumentException("unknown option: " + name);
        }
        try {
            return (OptionsData) OptionsData.class.getDeclaredConstructor(types)
                    .newInstance(arguments);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("option copy failed: " + name, e);
        }
    }

    /** The record components of this face. */
    private static java.lang.reflect.RecordComponent[] components() {
        return OptionsData.class.getRecordComponents();
    }

    /** Coerces one incoming value onto a component type. */
    private static Object coerce(Class<?> type, Object value, String name) {
        if (value == null) {
            if (type.isPrimitive()) {
                throw new IllegalArgumentException("option '" + name + "' requires a value");
            }
            return null;
        }
        if (type == int.class && value instanceof Number number) {
            return number.intValue();
        }
        if (type == double.class && value instanceof Number number) {
            return number.doubleValue();
        }
        if (type == boolean.class && value instanceof Boolean flag) {
            return flag;
        }
        if (type == String.class && value instanceof String text) {
            return text;
        }
        if (type == Boolean.class && value instanceof Boolean flag) {
            return flag;
        }
        if (List.class.isAssignableFrom(type) && value instanceof List<?> list) {
            return List.copyOf(list);
        }
        if (type.isInstance(value)) {
            return value;
        }
        throw new IllegalArgumentException(
                "option '" + name + "' rejects value " + value + " for " + type.getSimpleName());
    }
}
