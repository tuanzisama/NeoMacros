package ink.tuanzi.neomacros.adapter;

import ink.tuanzi.neomacros.port.WorldPort;
import ink.tuanzi.neomacros.port.model.BlockData;
import ink.tuanzi.neomacros.port.model.BlockInteraction;
import ink.tuanzi.neomacros.port.model.BossBarInfoData;
import ink.tuanzi.neomacros.port.model.ChunkData;
import ink.tuanzi.neomacros.port.model.LightData;
import ink.tuanzi.neomacros.port.model.Pos3d;
import ink.tuanzi.neomacros.port.model.RayHit;
import ink.tuanzi.neomacros.port.model.RayOptions;
import ink.tuanzi.neomacros.port.model.ScoreboardData;
import ink.tuanzi.neomacros.port.model.ScoreboardObjectiveData;
import ink.tuanzi.neomacros.port.model.TabListEntryInfoData;
import ink.tuanzi.neomacros.port.model.TabListInfoData;
import ink.tuanzi.neomacros.port.model.TeamData;
import ink.tuanzi.neomacros.port.model.TpsData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * World data services over the vanilla client world. Every query is
 * marshaled onto the game main thread; without a loaded world the queries
 * answer empty (time 0, dimension "", no rain, no blocks).
 */
public final class FabricWorldPort implements WorldPort {

    @Override
    public long time() {
        ClientLevel level = MainThread.run(() -> Minecraft.getInstance().level);
        return level != null ? level.getDefaultClockTime() : 0L;
    }

    @Override
    public String dimension() {
        ClientLevel level = MainThread.run(() -> Minecraft.getInstance().level);
        return level != null ? level.dimension().identifier().toString() : "";
    }

    @Override
    public boolean isRaining() {
        ClientLevel level = MainThread.run(() -> Minecraft.getInstance().level);
        return level != null && level.isRaining();
    }

    @Override
    public Optional<BlockData> blockAt(Pos3d target) {
        return MainThread.run(() -> {
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null) {
                return Optional.empty();
            }
            BlockPos blockPos = BlockPos.containing(target.x(), target.y(), target.z());
            BlockState state = level.getBlockState(blockPos);
            if (state.isAir()) {
                return Optional.empty();
            }
            return Optional.of(new BlockData(blockId(state), toPos3d(blockPos),
                    stateProperties(state), stateExtras(state, blockPos)));
        });
    }

    @Override
    public Optional<RayHit> raycast(Pos3d origin, Pos3d direction, RayOptions options) {
        return MainThread.run(() -> {
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null) {
                return Optional.empty();
            }
            Vec3 from = new Vec3(origin.x(), origin.y(), origin.z());
            Vec3 dir = new Vec3(direction.x(), direction.y(), direction.z());
            if (dir.lengthSqr() == 0) {
                dir = new Vec3(0, 0, 1);
            } else {
                dir = dir.normalize();
            }
            Vec3 to = from.add(dir.scale(options.maxDist()));
            var clip = new ClipContext(from, to,
                    options.blocks() ? ClipContext.Block.COLLIDER : ClipContext.Block.COLLIDER,
                    options.fluids() ? ClipContext.Fluid.ANY : ClipContext.Fluid.NONE,
                    Minecraft.getInstance().player);
            BlockHitResult blockHit = options.blocks() ? level.clip(clip)
                    : new BlockHitResult(from, net.minecraft.core.Direction.UP,
                            net.minecraft.core.BlockPos.containing(from), false);
            double blockDist = blockHit.getType() == net.minecraft.world.phys.HitResult.Type.MISS
                    ? Double.MAX_VALUE
                    : blockHit.getLocation().distanceTo(from);
            EntityHitResult entityHit = options.entities()
                    ? pickEntity(level, from, to, blockDist)
                    : null;
            if (entityHit != null
                    && (blockDist >= Double.MAX_VALUE
                        || entityHit.getLocation().distanceTo(from) < blockDist)) {
                var entity = entityHit.getEntity();
                double dist = entityHit.getLocation().distanceTo(from);
                var snapshot = new ink.tuanzi.neomacros.port.model.EntitySnapshot(
                        entity.getUUID(), BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString(),
                        entity.getName().getString(), toPos3dFrom(entity.position()),
                        entity instanceof net.minecraft.world.entity.LivingEntity living
                                ? living.getHealth() : 0);
                return Optional.of(new RayHit(null, snapshot, toPos3dFrom(entityHit.getLocation()), dist));
            }
            if (blockDist == Double.MAX_VALUE) {
                return Optional.empty();
            }
            BlockPos blockPos = blockHit.getBlockPos();
            BlockState state = level.getBlockState(blockPos);
            var block = new BlockInteraction(blockId(state), toPos3d(blockPos),
                    blockHit.getDirection().getName(), blockHit.isInside());
            return Optional.of(new RayHit(block, null, toPos3dFrom(blockHit.getLocation()), blockDist));
        });
    }

    /** Lifts a generic world position into the neutral position type. */
    private static Pos3d toPos3dFrom(Vec3 pos) {
        return new Pos3d(pos.x, pos.y, pos.z);
    }

    /** Picks the first entity whose bounding box the ray intersects within reach. */
    private static EntityHitResult pickEntity(ClientLevel level, Vec3 from, Vec3 to,
            double blockDist) {
        EntityHitResult best = null;
        double bestDist = Math.min(blockDist, Double.MAX_VALUE);
        double reach = Math.min(blockDist, from.distanceTo(to));
        for (var entity : level.entitiesForRendering()) {
            var box = entity.getBoundingBox().inflate(0.1);
            var hit = box.clip(from, to);
            if (hit.isEmpty()) {
                continue;
            }
            double dist = hit.get().distanceTo(from);
            if (dist <= reach && dist < bestDist) {
                bestDist = dist;
                best = new EntityHitResult(entity, hit.get());
            }
        }
        return best;
    }


    /**
     * Flattens the vanilla state property values into a string map. Values
     * go through {@code Property.getName}, the canonical serialized form
     * (blockstate-JSON spelling): {@code String.valueOf} on the raw enum
     * would print the uppercase constant for the six enums without a
     * {@code toString} override (chest type, attach face, structure mode,
     * note-block instrument, tilt, bell attachment).
     */
    private static Map<String, String> stateProperties(BlockState state) {
        Map<String, String> out = new LinkedHashMap<>();
        state.getProperties().forEach(property ->
                out.put(property.getName(), serializedValue(state, property)));
        return out;
    }

    /**
     * Reads one property in its canonical serialized form. The helper keeps
     * the wildcard capture of {@code property} fixed across the value read
     * and the name lookup (a direct nested call re-captures and fails to
     * unify the two capture variables).
     */
    private static <T extends Comparable<T>> String serializedValue(
            BlockState state, net.minecraft.world.level.block.state.properties.Property<T> property) {
        return property.getName(state.getValue(property));
    }
    /** The namespaced identifier of one block state. */
    private static String blockId(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }

    /** Lifts a block position into the neutral position type. */
    private static Pos3d toPos3d(BlockPos pos) {
        return new Pos3d(pos.getX(), pos.getY(), pos.getZ());
    }

    @Override
    public java.util.Optional<Object> rawByPos(Pos3d pos) {
        return MainThread.run(() -> {
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null) {
                return java.util.Optional.empty();
            }
            var blockPos = new BlockPos((int) Math.floor(pos.x()),
                    (int) Math.floor(pos.y()), (int) Math.floor(pos.z()));
            return java.util.Optional.of(level.getBlockState(blockPos));
        });
    }

    @Override
    public String levelName() {
        return MainThread.run(() -> {
            ServerData serverData = Minecraft.getInstance().getCurrentServer();
            if (serverData != null) {
                return serverData.name != null && !serverData.name.isBlank()
                        ? serverData.name : serverData.ip;
            }
            var integrated = Minecraft.getInstance().getSingleplayerServer();
            if (integrated != null) {
                var access = ((ink.tuanzi.neomacros.adapter.mixin.ServerStorageAccessor) (Object) integrated)
                        .neomacros$storageSource();
                return access != null ? access.getLevelId() : "";
            }
            return "";
        });
    }

    @Override
    public int moonPhase() {
        ClientLevel level = MainThread.run(() -> Minecraft.getInstance().level);
        if (level == null) {
            return 0;
        }
        //? if <26.1 {
        /* return (int) ((level.getDefaultClockTime() / 24000L % 8L + 8L) % 8L); */
        //?}
        //? if >=26.1 {
        return level.environmentAttributes().getDimensionValue(
                net.minecraft.world.attribute.EnvironmentAttributes.MOON_PHASE).index();
        //?}
    }

    @Override
    public java.util.Optional<Pos3d> respawnPos() {
        return MainThread.run(() -> {
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null) {
                return java.util.Optional.empty();
            }
            //? if <1.21.9 {
            /* return java.util.Optional.of(toPos3d(level.getSharedSpawnPos())); */
            //?}
            //? if >=1.21.9 {
            return java.util.Optional.of(toPos3d(level.getRespawnData().pos()));
            //?}
        });
    }

    @Override
    public java.util.Optional<LightData> lightAt(Pos3d pos) {
        return MainThread.run(() -> {
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null) {
                return java.util.Optional.empty();
            }
            BlockPos blockPos = BlockPos.containing(pos.x(), pos.y(), pos.z());
            return java.util.Optional.of(new LightData(
                    level.getBrightness(net.minecraft.world.level.LightLayer.SKY, blockPos),
                    level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, blockPos)));
        });
    }

    @Override
    public Optional<TabListInfoData> tabList() {
        return MainThread.run(() -> {
            ClientLevel level = Minecraft.getInstance().level;
            var connection = Minecraft.getInstance().getConnection();
            if (level == null || connection == null) {
                return Optional.empty();
            }
            List<TabListEntryInfoData> entries = new ArrayList<>();
            for (var info : connection.getListedOnlinePlayers()) {
                var profile = info.getProfile();
                Component tabName = info.getTabListDisplayName();
                var session = info.getChatSession();
                byte[] publicKey = session == null ? null
                        : session.profilePublicKey().data().key().getEncoded();
                var skin = info.getSkin();
                String skinTexture;
                String skinUrl;
                String capeTexture;
                String elytraTexture;
                boolean hasCape;
                boolean slimModel;
                //? if >=1.21.9 {
                var body = skin.body();
                skinTexture = body == null ? null : body.texturePath().toString();
                skinUrl = body instanceof net.minecraft.core.ClientAsset.DownloadedTexture
                        downloaded ? downloaded.url() : null;
                var cape = skin.cape();
                hasCape = cape != null;
                capeTexture = cape == null ? null : cape.texturePath().toString();
                var elytra = skin.elytra();
                elytraTexture = elytra == null ? null : elytra.texturePath().toString();
                slimModel = "SLIM".equals(skin.model().name());
                //?} else {
                /*
                skinTexture = skin.texture().toString();
                skinUrl = skin.textureUrl();
                hasCape = skin.capeTexture() != null;
                capeTexture = skin.capeTexture() == null ? null
                        : skin.capeTexture().toString();
                elytraTexture = skin.elytraTexture() == null ? null
                        : skin.elytraTexture().toString();
                slimModel = "SLIM".equals(skin.model().name());
                */
                //?}
                entries.add(new TabListEntryInfoData(
                        profile.id().toString(),
                        profile.name(),
                        info.getLatency(),
                        info.getGameMode().getName(),
                        tabName != null ? tabName.getString() : null,
                        publicKey, skinTexture, skinUrl, capeTexture, elytraTexture,
                        hasCape, slimModel,
                        teamData(info.getTeam())));
            }
            return Optional.of(new TabListInfoData(
                    ink.tuanzi.neomacros.adapter.TabListTracker.header(),
                    ink.tuanzi.neomacros.adapter.TabListTracker.footer(), entries));
        });
    }

    @Override
    public List<BossBarInfoData> bossBars() {
        return MainThread.run(ink.tuanzi.neomacros.adapter.BossbarHub::snapshot);
    }

    @Override
    public List<TeamData> teams() {
        return MainThread.run(() -> {
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null) {
                return List.of();
            }
            List<TeamData> out = new ArrayList<>();
            for (var team : level.getScoreboard().getPlayerTeams()) {
                out.add(teamData(team));
            }
            return out;
        });
    }

    @Override
    public Optional<TeamData> localPlayerTeam() {
        return MainThread.run(() -> {
            ClientLevel level = Minecraft.getInstance().level;
            var player = Minecraft.getInstance().player;
            if (level == null || player == null) {
                return Optional.empty();
            }
            return Optional.ofNullable(teamData(
                    level.getScoreboard().getPlayersTeam(player.getScoreboardName())));
        });
    }

    @Override
    public Optional<ScoreboardObjectiveData> displayObjective(int slot) {
        return MainThread.run(() -> {
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null) {
                return Optional.empty();
            }
            var slots = net.minecraft.world.scores.DisplaySlot.values();
            if (slot < 0 || slot >= slots.length) {
                return Optional.empty();
            }
            var scoreboard = level.getScoreboard();
            var objective = scoreboard.getDisplayObjective(slots[slot]);
            return objective == null ? Optional.empty()
                    : Optional.of(objectiveData(scoreboard, objective));
        });
    }

    /** Maps one scoreboard team onto its neutral snapshot. */
    private static TeamData teamData(net.minecraft.world.scores.PlayerTeam team) {
        if (team == null) {
            return null;
        }
        var color = FormattingValues.teamColor(team);
        List<String> players = new ArrayList<>(team.getPlayers());
        return new TeamData(team.getName(), team.getDisplayName().getString(),
                FormattingValues.colorName(color), FormattingValues.colorIndex(color),
                FormattingValues.colorValue(color), players);
    }

    /**
     * Builds the full objective snapshot: raw scores (hidden entries
     * included), the team-formatted display texts capped at the fifteen the
     * sidebar draws, and the scoreboard's tracked owners.
     */
    private static ScoreboardObjectiveData objectiveData(
            net.minecraft.world.scores.Scoreboard scoreboard,
            net.minecraft.world.scores.Objective objective) {
        Map<String, Integer> playerScores = new LinkedHashMap<>();
        Map<Integer, String> scoreToDisplayName = new LinkedHashMap<>();
        List<net.minecraft.world.scores.PlayerScoreEntry> visible = new ArrayList<>();
        for (var entry : scoreboard.listPlayerScores(objective)) {
            playerScores.put(entry.owner(), entry.value());
            scoreToDisplayName.put(entry.value(), net.minecraft.world.scores.PlayerTeam
                    .formatNameForTeam(scoreboard.getPlayersTeam(entry.owner()),
                            entry.ownerName()).getString());
            if (!entry.isHidden()) {
                visible.add(entry);
            }
        }
        visible.sort(Comparator
                .comparing(net.minecraft.world.scores.PlayerScoreEntry::value).reversed()
                .thenComparing(net.minecraft.world.scores.PlayerScoreEntry::owner,
                        String.CASE_INSENSITIVE_ORDER));
        List<String> texts = new ArrayList<>();
        for (int i = 0; i < Math.min(15, visible.size()); i++) {
            var entry = visible.get(i);
            texts.add(net.minecraft.world.scores.PlayerTeam.formatNameForTeam(
                    scoreboard.getPlayersTeam(entry.owner()), entry.ownerName())
                    .getString());
        }
        List<String> knownPlayers = new ArrayList<>();
        List<String> knownPlayersDisplayNames = new ArrayList<>();
        for (var holder : scoreboard.getTrackedPlayers()) {
            knownPlayers.add(holder.getScoreboardName());
            Component display = holder.getDisplayName();
            knownPlayersDisplayNames.add(display != null ? display.getString()
                    : holder.getScoreboardName());
        }
        return new ScoreboardObjectiveData(objective.getName(),
                objective.getDisplayName().getString(),
                objective.getCriteria().getName(),
                objective.getRenderType().getSerializedName(),
                playerScores, scoreToDisplayName, texts, knownPlayers,
                knownPlayersDisplayNames);
    }

    @Override
    public Optional<ScoreboardData> scoreboards() {
        return MainThread.run(() -> {
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null) {
                return Optional.empty();
            }
            var scoreboard = level.getScoreboard();
            List<ScoreboardData.ObjectiveData> objectives = new ArrayList<>();
            List<ScoreboardData.ScoreEntryData> scores = new ArrayList<>();
            for (var objective : scoreboard.getObjectives()) {
                String name = objective.getName();
                objectives.add(new ScoreboardData.ObjectiveData(name,
                        objective.getDisplayName().getString(),
                        objective.getCriteria().getName(),
                        objective.getRenderType().getSerializedName()));
                for (var entry : scoreboard.listPlayerScores(objective)) {
                    scores.add(new ScoreboardData.ScoreEntryData(name,
                            entry.owner(), entry.value()));
                }
            }
            return Optional.of(new ScoreboardData(objectives, scores));
        });
    }

    @Override
    public Optional<TpsData> tps() {
        return Optional.ofNullable(ink.tuanzi.neomacros.adapter.TpsTracker.snapshot());
    }

    @Override
    public void playSound(String soundId, Pos3d pos, double volume, double pitch) {
        Identifier id = resolveIdentifier(soundId);
        if (id == null) {
            throw new IllegalArgumentException("invalid sound id: " + soundId);
        }
        var event = BuiltInRegistries.SOUND_EVENT.getValue(id);
        if (event == null) {
            throw new IllegalArgumentException("unknown sound: " + soundId);
        }
        MainThread.run(() -> {
            ClientLevel level = Minecraft.getInstance().level;
            if (pos != null && level != null) {
                level.playLocalSound(pos.x(), pos.y(), pos.z(), event,
                        net.minecraft.sounds.SoundSource.NEUTRAL,
                        (float) volume, (float) pitch, false);
            } else {
                Minecraft.getInstance().getSoundManager().play(
                        net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                                event, (float) pitch, (float) volume));
            }
            return null;
        });
    }

    @Override
    public java.util.concurrent.CompletableFuture<Void> playSoundFile(String path, double volume) {
        return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            java.nio.file.Path file = resolveSoundFile(path);
            try (var stream = javax.sound.sampled.AudioSystem
                    .getAudioInputStream(file.toFile())) {
                javax.sound.sampled.Clip clip = javax.sound.sampled.AudioSystem.getClip();
                clip.open(stream);
                applyPlaybackVolume(clip, volume);
                clip.addLineListener(event -> {
                    if (event.getType() == javax.sound.sampled.LineEvent.Type.STOP) {
                        clip.close();
                    }
                });
                clip.start();
            } catch (javax.sound.sampled.UnsupportedAudioFileException
                    | java.io.IOException e) {
                throw new IllegalStateException("cannot play sound file: " + path, e);
            } catch (javax.sound.sampled.LineUnavailableException e) {
                throw new IllegalStateException("audio line unavailable: " + path, e);
            }
            return null;
        });
    }

    /** Resolves one playback path: absolute as-is, relative to the game directory. */
    private static java.nio.file.Path resolveSoundFile(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("sound file path is required");
        }
        java.nio.file.Path candidate = java.nio.file.Path.of(path);
        return candidate.isAbsolute()
                ? candidate
                : net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir()
                        .resolve(candidate);
    }

    /** Maps a 0..1 volume onto the clip master-gain range when supported. */
    private static void applyPlaybackVolume(javax.sound.sampled.Clip clip, double volume) {
        if (!clip.isControlSupported(javax.sound.sampled.FloatControl.Type.MASTER_GAIN)) {
            return;
        }
        var control = (javax.sound.sampled.FloatControl)
                clip.getControl(javax.sound.sampled.FloatControl.Type.MASTER_GAIN);
        double clamped = Math.max(0, Math.min(1, volume));
        float gain = (float) (control.getMinimum()
                + (control.getMaximum() - control.getMinimum()) * clamped);
        control.setValue(gain);
    }

    @Override
    public void spawnParticle(String particleId, Pos3d pos, int count, Pos3d offset,
            double speed) {
        Identifier id = resolveIdentifier(particleId);
        if (id == null) {
            throw new IllegalArgumentException("invalid particle id: " + particleId);
        }
        var type = BuiltInRegistries.PARTICLE_TYPE.getValue(id);
        if (!(type instanceof net.minecraft.core.particles.SimpleParticleType simple)) {
            throw new IllegalArgumentException(
                    "particle requires data options: " + particleId);
        }
        MainThread.run(() -> {
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null) {
                return null;
            }
            for (int i = 0; i < Math.max(1, count); i++) {
                double dx = (Math.random() * 2 - 1) * offset.x();
                double dy = (Math.random() * 2 - 1) * offset.y();
                double dz = (Math.random() * 2 - 1) * offset.z();
                level.addParticle(simple, pos.x() + dx, pos.y() + dy, pos.z() + dz,
                        speed, speed, speed);
            }
            return null;
        });
    }

    /** Parses a script identifier, defaulting the namespace to minecraft. */
    private static Identifier resolveIdentifier(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.indexOf(':') >= 0
                ? Identifier.tryParse(raw)
                : Identifier.withDefaultNamespace(raw);
    }

    @Override
    public java.util.Optional<ChunkData> chunkAt(Pos3d pos) {
        return MainThread.run(() -> {
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null) {
                return java.util.Optional.empty();
            }
            var chunk = level.getChunkAt(BlockPos.containing(pos.x(), pos.y(), pos.z()));
            Map<String, int[]> heightmaps = new java.util.LinkedHashMap<>();
            for (var entry : chunk.getHeightmaps()) {
                String type = entry.getKey().name().toLowerCase(java.util.Locale.ROOT);
                net.minecraft.world.level.levelgen.Heightmap heightmap = entry.getValue();
                int[] heights = new int[256];
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        heights[x + z * 16] = heightmap.getFirstAvailable(x, z);
                    }
                }
                heightmaps.put(type, heights);
            }
            java.util.List<Pos3d> blockEntities = new ArrayList<>();
            for (BlockPos blockPos : chunk.getBlockEntitiesPos()) {
                blockEntities.add(toPos3d(blockPos));
            }
            return java.util.Optional.of(new ChunkData(chunk.getPos().x(), chunk.getPos().z(),
                    level.getMinY(), level.getMaxY(), chunk.getInhabitedTime(),
                    heightmaps, blockEntities));
        });
    }

    @Override
    public java.util.List<Pos3d> findBlocks(java.util.List<String> blockIds,
            Pos3d center, int range, int limit) {
        return findBlocksMatching(blockIds, null, null, center, range, limit, false);
    }

    @Override
    public java.util.List<Pos3d> findBlocksMatching(java.util.List<String> blockIds,
            String idPattern, java.util.Map<String, Object> stateFilters,
            Pos3d center, int range, int limit, boolean sphere) {
        return MainThread.run(() -> {
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null) {
                return java.util.List.of();
            }
            java.util.List<String> exactIds = new ArrayList<>();
            if (blockIds != null) {
                for (String raw : blockIds) {
                    exactIds.add(raw.indexOf(':') >= 0 ? raw : "minecraft:" + raw);
                }
            }
            if (exactIds.isEmpty() && (idPattern == null || idPattern.isBlank())) {
                return java.util.List.of();
            }
            java.util.regex.Pattern pattern =
                    idPattern != null && !idPattern.isBlank()
                            ? BlockScanMatcher.compileGlob(idPattern) : null;
            int clampedRange = Math.max(0, Math.min(48, range));
            int clampedLimit = Math.max(1, Math.min(1024, limit));
            java.util.List<Pos3d> out = new ArrayList<>();
            int baseX = (int) Math.floor(center.x());
            int baseY = (int) Math.floor(center.y());
            int baseZ = (int) Math.floor(center.z());
            long squaredRange = (long) clampedRange * clampedRange;
            for (int dx = -clampedRange; dx <= clampedRange; dx++) {
                for (int dy = -clampedRange; dy <= clampedRange; dy++) {
                    for (int dz = -clampedRange; dz <= clampedRange; dz++) {
                        if (sphere && (long) dx * dx + (long) dy * dy + (long) dz * dz
                                > squaredRange) {
                            continue;
                        }
                        BlockPos pos = new BlockPos(baseX + dx, baseY + dy, baseZ + dz);
                        BlockState state = level.getBlockState(pos);
                        String id = BuiltInRegistries.BLOCK
                                .getKey(state.getBlock()).toString();
                        boolean idHit = pattern != null
                                ? pattern.matcher(id).matches()
                                : exactIds.contains(id);
                        if (idHit && BlockScanMatcher.stateMatches(state, stateFilters)) {
                            out.add(toPos3d(pos));
                            if (out.size() >= clampedLimit) {
                                return out;
                            }
                        }
                    }
                }
            }
            return out;
        });
    }

    @Override
    public java.util.concurrent.CompletableFuture<java.util.List<Pos3d>> scanBlocks(
            java.util.List<String> blockIds, String idPattern,
            java.util.Map<String, Object> stateFilters, Pos3d center,
            int range, int limit, boolean sphere) {
        java.util.List<String> exactIds = new ArrayList<>();
        if (blockIds != null) {
            for (String raw : blockIds) {
                exactIds.add(raw.indexOf(':') >= 0 ? raw : "minecraft:" + raw);
            }
        }
        return BlockScanScheduler.submit(exactIds, idPattern, stateFilters,
                center, range, limit, sphere);
    }

    /** Neutral physics/light extras read off one block state. */
    private static Map<String, Object> stateExtras(BlockState state, BlockPos pos) {
        Map<String, Object> extras = new LinkedHashMap<>();
        var base = (net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase) state;
        extras.put("luminance", base.getLightEmission());
        extras.put("hardness", base.getDestroySpeed(level(), pos));
        extras.put("opaque", base.canOcclude());
        extras.put("emitsRedstonePower", base.isSignalSource());
        extras.put("hasBlockEntity", base.hasBlockEntity());
        extras.put("hasRandomTicks", base.isRandomlyTicking());
        extras.put("isBurnable", base.ignitedByLava());
        extras.put("replaceable", base.canBeReplaced());
        extras.put("toolRequired", base.requiresCorrectToolForDrops());
        extras.put("pistonBehaviour", base.getPistonPushReaction()
                .name().toLowerCase(java.util.Locale.ROOT));
        extras.put("suffocating", base.isSuffocating(level(), pos));
        // A dry state carries no key: BlockData copies extras through
        // Map.copyOf, which rejects null values (the old unconditional
        // put("fluid", null) failed every blockAt on a dry block).
        if (!base.getFluidState().isEmpty()) {
            extras.put("fluid", net.minecraft.core.registries.BuiltInRegistries.FLUID
                    .getKey(base.getFluidState().getType()).toString());
        }
        extras.putAll(stateFlags(state));
        return extras;
    }

    /**
     * The position-independent physics flags of one block state: the
     * exceeds-cube shape, the comparator output, the legacy movement
     * blocking flag (cobweb/bamboo-sapling excluded, inlined because
     * {@code blocksMotion()} is gone on 26.x), and the legacy solid flag.
     */
    private static Map<String, Object> stateFlags(BlockState state) {
        var base = (net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase) state;
        Map<String, Object> flags = new LinkedHashMap<>();
        flags.put("exceedsCube", base.hasLargeCollisionShape());
        flags.put("hasComparatorOutput", base.hasAnalogOutputSignal());
        var block = state.getBlock();
        flags.put("blocksMovement",
                block != net.minecraft.world.level.block.Blocks.COBWEB
                && block != net.minecraft.world.level.block.Blocks.BAMBOO_SAPLING
                && base.isSolid());
        flags.put("isSolid", base.isSolid());
        return flags;
    }

    @Override
    public java.util.Optional<ink.tuanzi.neomacros.port.model.BlockTypeData> blockType(
            String blockId) {
        Identifier id = resolveIdentifier(blockId);
        if (id == null) {
            return java.util.Optional.empty();
        }
        var block = BuiltInRegistries.BLOCK.getValue(id);
        if (block == null) {
            return java.util.Optional.empty();
        }
        java.util.List<String> tags = new ArrayList<>();
        block.builtInRegistryHolder().tags()
                .forEach(tag -> tags.add(tag.location().toString()));
        BlockState defaultState = block.defaultBlockState();
        java.util.List<Map<String, String>> states = new ArrayList<>();
        for (BlockState state : block.getStateDefinition().getPossibleStates()) {
            states.add(stateProperties(state));
        }
        var stack = block.asItem().getDefaultInstance();
        ink.tuanzi.neomacros.port.model.ItemStackData item =
                stack.isEmpty() ? null : FabricInventoryPort.stackDataOf(stack);
        return java.util.Optional.of(new ink.tuanzi.neomacros.port.model.BlockTypeData(
                BuiltInRegistries.BLOCK.getKey(block).toString(),
                block.getName().getString(), tags,
                block.getExplosionResistance(), block.getJumpFactor(),
                block.getFriction(), block.getSpeedFactor(),
                block.isPossibleToRespawnInThis(defaultState), block.hasDynamicShape(),
                stateProperties(defaultState), states, item));
    }

    @Override
    public java.util.Optional<Boolean> allowsSpawningAt(Pos3d pos, String entityId) {
        return MainThread.run(() -> {
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null) {
                return java.util.Optional.empty();
            }
            BlockPos blockPos = BlockPos.containing(pos.x(), pos.y(), pos.z());
            Identifier type = resolveIdentifier(entityId);
            var entityType = type == null ? null : BuiltInRegistries.ENTITY_TYPE.getValue(type);
            if (entityType == null) {
                return java.util.Optional.of(false);
            }
            return java.util.Optional.of(
                    level.getBlockState(blockPos).isValidSpawn(level, blockPos, entityType));
        });
    }

    @Override
    public java.util.Optional<Boolean> suffocatesAt(Pos3d pos) {
        return MainThread.run(() -> {
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null) {
                return java.util.Optional.empty();
            }
            BlockPos blockPos = BlockPos.containing(pos.x(), pos.y(), pos.z());
            return java.util.Optional.of(
                    level.getBlockState(blockPos).isSuffocating(level, blockPos));
        });
    }

    @Override
    public java.util.Optional<Boolean> chunkContains(int chunkX, int chunkZ,
            java.util.List<String> blockIds, boolean requireAll) {
        return MainThread.run(() -> {
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null) {
                return java.util.Optional.empty();
            }
            var chunk = level.getChunkAt(
                    new BlockPos(chunkX * 16 + 8, 64, chunkZ * 16 + 8));
            java.util.Set<net.minecraft.world.level.block.Block> targets =
                    new java.util.HashSet<>();
            for (String raw : blockIds) {
                Identifier id = resolveIdentifier(raw);
                var block = id == null ? null : BuiltInRegistries.BLOCK.getValue(id);
                if (block == null) {
                    // An unresolvable id can never be present; it fails an
                    // all-of request outright and never matches any-of.
                    if (requireAll) {
                        return java.util.Optional.of(false);
                    }
                    continue;
                }
                targets.add(block);
            }
            if (targets.isEmpty()) {
                return java.util.Optional.of(requireAll);
            }
            if (requireAll) {
                java.util.Set<net.minecraft.world.level.block.Block> remaining =
                        new java.util.HashSet<>(targets);
                for (var section : chunk.getSections()) {
                    if (section.hasOnlyAir()) {
                        continue;
                    }
                    for (var candidate : java.util.List.copyOf(remaining)) {
                        if (section.maybeHas(state -> state.getBlock() == candidate)) {
                            remaining.remove(candidate);
                        }
                    }
                    if (remaining.isEmpty()) {
                        break;
                    }
                }
                return java.util.Optional.of(remaining.isEmpty());
            }
            for (var section : chunk.getSections()) {
                if (section.hasOnlyAir()) {
                    continue;
                }
                if (section.maybeHas(state -> targets.contains(state.getBlock()))) {
                    return java.util.Optional.of(true);
                }
            }
            return java.util.Optional.of(false);
        });
    }

    @Override
    public java.util.Optional<Object> rawBlockEntityByPos(Pos3d pos) {
        return MainThread.run(() -> {
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null) {
                return java.util.Optional.empty();
            }
            var blockPos = new BlockPos((int) Math.floor(pos.x()),
                    (int) Math.floor(pos.y()), (int) Math.floor(pos.z()));
            var blockEntity = level.getBlockEntity(blockPos);
            return blockEntity == null
                    ? java.util.Optional.empty()
                    : java.util.Optional.<Object>of(blockEntity);
        });
    }

    /** The client level for state queries. */
    private static ClientLevel level() {
        return Minecraft.getInstance().level;
    }
}