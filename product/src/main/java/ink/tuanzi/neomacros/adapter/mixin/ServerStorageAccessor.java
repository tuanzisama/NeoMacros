package ink.tuanzi.neomacros.adapter.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Exposes the world storage access behind the server instance so world
 * queries can read the loaded save name on singleplayer.
 */
@Mixin(MinecraftServer.class)
public abstract class ServerStorageAccessor {

    @Shadow
    @Final
    protected LevelStorageSource.LevelStorageAccess storageSource;

    /** Reads the loaded save identifier, or null when unavailable. */
    public static String levelId(MinecraftServer server) {
        Object target = server;
        LevelStorageSource.LevelStorageAccess access =
                ((ServerStorageAccessor) target).storageSource;
        return access != null ? access.getLevelId() : null;
    }
}
