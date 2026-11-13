package ink.tuanzi.neomacros.core.api.entity;

import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiClassKind;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiReadOnly;
import ink.tuanzi.neomacros.doc.api.ApiSince;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * The species handle tree: guest subclasses over the living entity
 * hierarchy, one class per species carrying that species' gap members;
 * every class name carries the Entity suffix. Registration is data-driven ({@link #registry()}); the API
 * consults it when wrapping so queries return the deepest species handle.
 * Intermediate Mojang classes without gap members stay structural.
 *
 * <p>Members read live species state through the entity port; a vanished
 * entity reads back as {@code undefined} like every other handle.</p>
 *
 * @zh 物种句柄树：活实体层级之上的 guest 子类，每物种一类、携带该物种的缺口成员；类名一律带 Entity 后缀。注册数据驱动（{@link #registry()}），包装时查表返回最深的物种句柄；无缺口成员的 Mojang 中间类保持结构性存在。成员经实体端口实时读取；实体消失后属性回读为 undefined，与其他句柄一致。
 */
public final class SpeciesEntities {

    private SpeciesEntities() {
    }

    /**
     * The species registry: entity type id → deepest handle factory.
     *
     * @zh 物种注册表：实体类型 id → 最深句柄工厂。
     */
    public static Map<String, Function<UUID, EntityApi.Entity>> registry() {
        return Registry.HANDLES;
    }

    /**
     * Reads one pose entry from a species state map as a rotation triple,
     * or null when the entry is absent or not a triple.
     *
     * @zh 从物种状态映射读取一个姿态条目为旋转三元组；条目缺席或形状不符时为 null。
     */
    private static ink.tuanzi.neomacros.core.api.types.Pos3D poseOf(
            Map<String, Object> state, String key) {
        Object value = state.get(key);
        return value instanceof ink.tuanzi.neomacros.port.model.Pos3d pose
                ? new ink.tuanzi.neomacros.core.api.types.Pos3D(
                        pose.x(), pose.y(), pose.z())
                : null;
    }

    /** Static holder: the JVM loads the tree once. */
    private static final class Registry {
        private static final Map<String, Function<UUID, EntityApi.Entity>> HANDLES =
                build();

        private static Map<String, Function<UUID, EntityApi.Entity>> build() {
            Map<String, Function<UUID, EntityApi.Entity>> map = new LinkedHashMap<>();
            put(map, "minecraft:bat", BatEntity::new);
            put(map, "minecraft:cat", CatEntity::new);
            put(map, "minecraft:creeper", CreeperEntity::new);
            put(map, "minecraft:mooshroom", MushroomCowEntity::new);
            put(map, "minecraft:sheep", SheepEntity::new);
            put(map, "minecraft:tnt", PrimedTntEntity::new);
            put(map, "minecraft:villager", VillagerEntity::new);
            put(map, "minecraft:wolf", WolfEntity::new);
            put(map, "minecraft:zombie", ZombieEntity::new);
            put(map, "minecraft:donkey", DonkeyEntity::new);
            put(map, "minecraft:enderman", EndermanEntity::new);
            put(map, "minecraft:horse", HorseEntity::new);
            put(map, "minecraft:iron_golem", IronGolemEntity::new);
            put(map, "minecraft:llama", LlamaEntity::new);
            put(map, "minecraft:phantom", PhantomEntity::new);
            put(map, "minecraft:spider", SpiderEntity::new);
            put(map, "minecraft:zombified_piglin", ZombifiedPiglinEntity::new);
            put(map, "minecraft:zombie_villager", ZombieVillagerEntity::new);
            put(map, "minecraft:drowned", DrownedEntity::new);
            put(map, "minecraft:warden", WardenEntity::new);
            put(map, "minecraft:dolphin", DolphinEntity::new);
            put(map, "minecraft:witch", WitchEntity::new);
            put(map, "minecraft:vex", VexEntity::new);
            put(map, "minecraft:turtle", TurtleEntity::new);
            put(map, "minecraft:snow_golem", SnowGolemEntity::new);
            put(map, "minecraft:shulker", ShulkerEntity::new);
            put(map, "minecraft:polar_bear", PolarBearEntity::new);
            put(map, "minecraft:parrot", ParrotEntity::new);
            put(map, "minecraft:panda", PandaEntity::new);
            put(map, "minecraft:goat", GoatEntity::new);
            put(map, "minecraft:frog", FrogEntity::new);
            put(map, "minecraft:fox", FoxEntity::new);
            put(map, "minecraft:bee", BeeEntity::new);
            put(map, "minecraft:axolotl", AxolotlEntity::new);
            put(map, "minecraft:chicken", ChickenEntity::new);
            put(map, "minecraft:ghast", GhastEntity::new);
            put(map, "minecraft:ocelot", OcelotEntity::new);
            put(map, "minecraft:rabbit", RabbitEntity::new);
            put(map, "minecraft:strider", StriderEntity::new);
            put(map, "minecraft:wither", WitherBossEntity::new);
            put(map, "minecraft:allay", AllayEntity::new);
            put(map, "minecraft:cow", CowEntity::new);
            put(map, "minecraft:armadillo", ArmadilloEntity::new);
            put(map, "minecraft:camel", CamelEntity::new);
            put(map, "minecraft:sniffer", SnifferEntity::new);
            put(map, "minecraft:salmon", SalmonEntity::new);
            put(map, "minecraft:cod", CodEntity::new);
            put(map, "minecraft:pufferfish", PufferfishEntity::new);
            put(map, "minecraft:tropical_fish", TropicalFishEntity::new);
            put(map, "minecraft:piglin", PiglinEntity::new);
            put(map, "minecraft:piglin_brute", PiglinBruteEntity::new);
            put(map, "minecraft:pillager", PillagerEntity::new);
            put(map, "minecraft:evoker", EvokerEntity::new);
            put(map, "minecraft:wandering_trader", WanderingTraderEntity::new);
            put(map, "minecraft:ender_dragon", EnderDragonEntity::new);
            put(map, "minecraft:breeze", BreezeEntity::new);
            put(map, "minecraft:creaking", CreakingEntity::new);
            put(map, "minecraft:armor_stand", ArmorStandEntity::new);
            put(map, "minecraft:falling_block", FallingBlockEntity::new);
            put(map, "minecraft:item_frame", ItemFrameEntity::new);
            put(map, "minecraft:area_effect_cloud", AreaEffectCloudEntity::new);
            put(map, "minecraft:pig", PigEntity::new);
            put(map, "minecraft:arrow", ArrowEntity::new);
            put(map, "minecraft:spectral_arrow", ArrowEntity::new);
            put(map, "minecraft:trident", ThrownTridentEntity::new);
            put(map, "minecraft:wither_skull", WitherSkullEntity::new);
            put(map, "minecraft:fishing_bobber", FishingHookEntity::new);
            put(map, "minecraft:boat", BoatEntity::new);
            put(map, "minecraft:chest_boat", ChestBoatEntity::new);
            for (String wood : new String[] {"oak", "spruce", "birch", "jungle", "acacia",
                    "dark_oak", "mangrove", "cherry", "pale_oak"}) {
                put(map, "minecraft:" + wood + "_boat", BoatEntity::new);
                put(map, "minecraft:" + wood + "_chest_boat", ChestBoatEntity::new);
            }
            put(map, "minecraft:bamboo_raft", BoatEntity::new);
            put(map, "minecraft:bamboo_chest_raft", ChestBoatEntity::new);
            put(map, "minecraft:minecart", AbstractMinecartEntity::new);
            put(map, "minecraft:chest_minecart", AbstractMinecartEntity::new);
            put(map, "minecraft:hopper_minecart", AbstractMinecartEntity::new);
            put(map, "minecraft:command_block_minecart", AbstractMinecartEntity::new);
            put(map, "minecraft:spawner_minecart", AbstractMinecartEntity::new);
            put(map, "minecraft:furnace_minecart", MinecartFurnaceEntity::new);
            put(map, "minecraft:tnt_minecart", MinecartTntEntity::new);
            put(map, "minecraft:end_crystal", EndCrystalEntity::new);
            put(map, "minecraft:interaction", InteractionEntity::new);
            put(map, "minecraft:painting", PaintingEntity::new);
            put(map, "minecraft:block_display", BlockDisplayEntity::new);
            put(map, "minecraft:item_display", ItemDisplayEntity::new);
            put(map, "minecraft:text_display", TextDisplayEntity::new);
            put(map, "minecraft:slime", SlimeEntity::new);
            put(map, "minecraft:magma_cube", SlimeEntity::new);
            put(map, "minecraft:guardian", GuardianEntity::new);
            put(map, "minecraft:elder_guardian", GuardianEntity::new);
            put(map, "minecraft:vindicator", VindicatorEntity::new);
            return map;
        }

        private static void put(Map<String, Function<UUID, EntityApi.Entity>> map,
                String typeId, Function<UUID, EntityApi.Entity> factory) {
            map.putIfAbsent(typeId, factory);
        }
    }

    /**
     * The mob base: artificial-intelligence toggle and current attack
     * target, shared by every hostile and tameable species.
     *
     * @zh 生物基类：AI 开关与当前攻击目标，为敌对与可驯物种共享。
     */
    @ApiClass(name = "MobEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.Mob")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class MobEntity extends EntityApi.LivingEntity {

        /** Creates the handle for one mob UUID. */
        public MobEntity(UUID uuid) {
            super(uuid);
        }

        /**
         * Whether the mob has no AI (spawned with the NoAI tag).
         *
         * @zh 该生物是否无 AI（NoAI 标签生成）。
         */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isNoAi() {
            return speciesBool("noAi");
        }

        /**
         * The current attack target, or {@code undefined} when idle.
         *
         * @zh 当前攻击目标；空闲时为 undefined。
         */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public EntityApi.Entity getTarget() {
            Object uuid = speciesState().get("target");
            return uuid instanceof String id ? EntityApi.wrap(UUID.fromString(id)) : null;
        }
    }

    /**
     * Breedable animal base (cows, sheep, cats, wolves, ...).
     *
     * @zh 可繁殖动物基类（牛、羊、猫、狼等）。
     */
    @ApiClass(name = "AnimalEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.animal.Animal")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class AnimalEntity extends MobEntity {

        /** Creates the handle for one animal UUID. */
        public AnimalEntity(UUID uuid) {
            super(uuid);
        }
    }

    /**
     * The tameable base (wolves, cats): ownership and sitting state land
     * here with the tameable gap batch.
     *
     * @zh 可驯化基类（狼、猫）：归属与坐下状态随驯化缺口批落地。
     */
    @ApiClass(name = "TamableAnimalEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.TamableAnimal")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class TamableAnimalEntity extends AnimalEntity {

        /** Creates the handle for one tameable UUID. */
        public TamableAnimalEntity(UUID uuid) {
            super(uuid);
        }

        /**
         * The owning player handle, or undefined when untamed or absent.
         *
         * @zh 主人玩家的实体句柄；未驯服或主人不在场时为 undefined。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public EntityApi.Entity getOwner() {
            return speciesEntity("owner");
        }

        /**
         * Whether the animal has been tamed.
         *
         * @zh 动物是否已被驯服。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isTamed() {
            return speciesBool("tamed");
        }

        /**
         * Whether the animal is sitting (ordered to stay).
         *
         * @zh 动物是否处于坐下（原地待命）状态。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isSitting() {
            return speciesBool("sitting");
        }
    }

    /**
     * A sheep: wool color and sheared state.
     *
     * @zh 绵羊：羊毛颜色与剪毛状态。
     */
    @ApiClass(name = "SheepEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.animal.sheep.Sheep")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class SheepEntity extends AnimalEntity {

        /** Creates the handle for one sheep UUID. */
        public SheepEntity(UUID uuid) {
            super(uuid);
        }

        /**
         * The wool color identifier, e.g. {@code minecraft:white}.
         *
         * @zh 羊毛颜色标识，如 {@code minecraft:white}。
         */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public String getColor() {
            return speciesString("color");
        }

        /**
         * Whether the wool has been sheared off.
         *
         * @zh 羊毛是否已被剪掉。
         */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isSheared() {
            return speciesBool("sheared");
        }

        /**
         * Whether the sheep is currently shearable (grown wool, not a baby).
         *
         * @zh 羊当前是否可剪（毛已长成且非幼年）。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isShearable() {
            return speciesBool("shearable");
        }

        /**
         * Whether the sheep is a rainbow "jeb_" sheep (name-driven).
         *
         * @zh 羊是否为 "jeb_" 彩虹羊（由自定义名称驱动）。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isJeb() {
            return speciesBool("jeb");
        }
    }

    /**
     * A wolf: collar color. Anger and begging state ride the tameable gap
     * batch (the vanilla accessors moved behind the wolf AI profile).
     *
     * @zh 狼：项圈颜色。愤怒与乞食状态随驯化缺口批（原版访问器已移入 wolf AI 配置）。
     */
    @ApiClass(name = "WolfEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.animal.wolf.Wolf")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class WolfEntity extends TamableAnimalEntity {

        /** Creates the handle for one wolf UUID. */
        public WolfEntity(UUID uuid) {
            super(uuid);
        }

        /**
         * The collar color identifier, e.g. {@code minecraft:red}.
         *
         * @zh 项圈颜色标识，如 {@code minecraft:red}。
         */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public String getCollarColor() {
            return speciesString("collarColor");
        }

        /**
         * Whether the wolf is currently angry at a player.
         *
         * @zh 狼当前是否处于愤怒状态。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isAngry() {
            return speciesBool("angry");
        }

        /**
         * Whether the wolf is shaking water off (wet state).
         *
         * @zh 狼是否处于甩水（潮湿）状态。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isWet() {
            return speciesBool("wet");
        }

        /**
         * The worn wolf armor item identifier, or undefined when bare.
         *
         * @zh 狼所穿护甲的物品标识；未穿戴时为 undefined。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public String getArmor() {
            return speciesString("armor");
        }
    }

    /**
     * A cat: collar color and coat variant.
     *
     * @zh 猫：项圈颜色与花色变体。
     */
    @ApiClass(name = "CatEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.animal.feline.Cat")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class CatEntity extends TamableAnimalEntity {

        /** Creates the handle for one cat UUID. */
        public CatEntity(UUID uuid) {
            super(uuid);
        }

        /**
         * The collar color identifier, e.g. {@code minecraft:red}.
         *
         * @zh 项圈颜色标识，如 {@code minecraft:red}。
         */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public String getCollarColor() {
            return speciesString("collarColor");
        }

        /**
         * The coat variant identifier, e.g. {@code minecraft:black}.
         *
         * @zh 花色变体标识，如 {@code minecraft:black}。
         */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public String getVariant() {
            return speciesString("variant");
        }
    }

    /**
     * A villager: profession, trade level, and biome variant.
     *
     * @zh 村民：职业、交易等级与生物群系变体。
     */
    @ApiClass(name = "VillagerEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.npc.villager.Villager")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class VillagerEntity extends MobEntity {

        /** Creates the handle for one villager UUID. */
        public VillagerEntity(UUID uuid) {
            super(uuid);
        }

        /**
         * The profession identifier, e.g. {@code minecraft:farmer}.
         *
         * @zh 职业标识，如 {@code minecraft:farmer}。
         */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public String getProfession() {
            return speciesString("profession");
        }

        /**
         * The trade level (1-5); {@code undefined} for nitwits.
         *
         * @zh 交易等级（1-5）；傻子村民为 undefined。
         */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Integer getLevel() {
            Object level = speciesState().get("level");
            return level instanceof Integer value ? value : null;
        }

        /**
         * The biome variant identifier, e.g. {@code minecraft:plains}.
         *
         * @zh 生物群系变体标识，如 {@code minecraft:plains}。
         */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public String getVariant() {
            return speciesString("variant");
        }
    }

    /**
     * A creeper: charge and ignition state. The fuse countdown accessor is
     * not exposed by the vanilla client classes; it rides the species gap
     * follow-up.
     *
     * @zh 苦力怕：充能与点燃状态。原版客户端类未暴露引信倒计时访问器，随物种缺口后随批。
     */
    @ApiClass(name = "CreeperEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.monster.Creeper")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class CreeperEntity extends MobEntity {

        /** Creates the handle for one creeper UUID. */
        public CreeperEntity(UUID uuid) {
            super(uuid);
        }

        /**
         * Whether the creeper is charged by a lightning strike.
         *
         * @zh 苦力怕是否被闪电充能。
         */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isPowered() {
            return speciesBool("powered");
        }

        /**
         * Whether the fuse has been lit by a flint and steel.
         *
         * @zh 引信是否被打火石点燃。
         */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isIgnited() {
            return speciesBool("ignited");
        }

        /**
         * The swell direction: 1 while building up, -1 while easing off,
         * 0 when idle.
         *
         * @zh 膨胀方向：1 为鼓起，-1 为回落，0 为空闲。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Integer getSwellDir() {
            return speciesInt("swellDir");
        }

        /**
         * The current swell progress from 0 to 1 (client-side animation).
         *
         * @zh 当前膨胀进度（0-1，客户端动画值）。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Double getSwell() {
            return speciesDouble("swell");
        }
    }

    /**
     * A zombie: drowning conversion state.
     *
     * @zh 僵尸：溺水转化状态。
     */
    @ApiClass(name = "ZombieEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.monster.zombie.Zombie")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class ZombieEntity extends MobEntity {

        /** Creates the handle for one zombie UUID. */
        public ZombieEntity(UUID uuid) {
            super(uuid);
        }

        /**
         * Whether the zombie is converting into a drowned underwater.
         *
         * @zh 僵尸是否正在水下转化为溺尸。
         */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isUnderWaterConverting() {
            return speciesBool("underWaterConverting");
        }
    }

    /**
     * A primed TNT block entity: fuse countdown.
     *
     * @zh 已点燃的 TNT 实体：引信倒计时。
     */
    @ApiClass(name = "PrimedTntEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.item.PrimedTnt")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class PrimedTntEntity extends EntityApi.Entity {

        /** Creates the handle for one primed TNT UUID. */
        public PrimedTntEntity(UUID uuid) {
            super(uuid);
        }

        /**
         * The remaining fuse ticks before the explosion.
         *
         * @zh 距爆炸的剩余引信 tick。
         */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Integer getFuse() {
            Object fuse = speciesState().get("fuse");
            return fuse instanceof Integer value ? value : null;
        }
    }

    /**
     * A bat: roosting state.
     *
     * @zh 蝙蝠：栖息状态。
     */
    @ApiClass(name = "BatEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.ambient.Bat")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class BatEntity extends MobEntity {

        /** Creates the handle for one bat UUID. */
        public BatEntity(UUID uuid) {
            super(uuid);
        }

        /**
         * Whether the bat is hanging upside down.
         *
         * @zh 蝙蝠是否正倒挂栖息。
         */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isResting() {
            return speciesBool("resting");
        }
    }

    /**
     * A mushroom cow (mooshroom): brown or red variant.
     *
     * @zh 蘑菇牛（哞菇）：棕色或红色变体。
     */
    @ApiClass(name = "MushroomCowEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.animal.cow.MushroomCow")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class MushroomCowEntity extends AnimalEntity {

        /** Creates the handle for one mooshroom UUID. */
        public MushroomCowEntity(UUID uuid) {
            super(uuid);
        }

        /**
         * The variant identifier ({@code minecraft:red} or
         * {@code minecraft:brown}).
         *
         * @zh 变体标识（{@code minecraft:red} 或 {@code minecraft:brown}）。
         */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public String getVariant() {
            return speciesString("variant");
        }

        /**
         * Whether the mooshroom is currently shearable.
         *
         * @zh 哞菇当前是否可剪。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isShearable() {
            return speciesBool("shearable");
        }
    }

    /**
     * An enderman: the block it currently carries as an id string, or
     * undefined.
     *
     * @zh 末影人：当前搬起的方块 id 字符串；未搬起为 undefined。
     */
    @ApiClass(name = "EndermanEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.monster.Enderman")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class EndermanEntity extends MobEntity {

        /** Creates the handle for one enderman UUID. */
        public EndermanEntity(UUID uuid) {
            super(uuid);
        }

        /** The carried block id, or undefined when carrying nothing.
         * @zh 所搬方块 id；未搬起为 undefined。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public String getCarriedBlock() {
            return speciesString("carriedBlock");
        }

        /** Whether the enderman is screaming (agitated state).
         * @zh 末影人是否处于嘶鸣（被激怒）状态。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isScreaming() {
            return speciesBool("screaming");
        }
    }

    /** A phantom: its size tier.
     * @zh 幻翼：体型档位。 */
    @ApiClass(name = "PhantomEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.monster.Phantom")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class PhantomEntity extends MobEntity {

        /** Creates the handle for one phantom UUID. */
        public PhantomEntity(UUID uuid) {
            super(uuid);
        }

        /** The phantom size tier (0 by default).
         * @zh 幻翼体型档位（默认 0）。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Integer getSize() {
            return speciesInt("size");
        }
    }

    /** A spider: wall-climbing state.
     * @zh 蜘蛛：爬墙状态。 */
    @ApiClass(name = "SpiderEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.monster.spider.Spider")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class SpiderEntity extends MobEntity {

        /** Creates the handle for one spider UUID. */
        public SpiderEntity(UUID uuid) {
            super(uuid);
        }

        /** Whether the spider is currently climbing a wall.
         * @zh 蜘蛛是否正在爬墙。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isClimbing() {
            return speciesBool("climbing");
        }
    }

    /** The horse base: taming, eating and breeding states plus the saddle
     * inventory size.
     * @zh 马基类：驯服/进食/繁殖状态与鞍上背包容量。 */
    @ApiClass(name = "AbstractHorseEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.animal.equine.AbstractHorse")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class AbstractHorseEntity extends AnimalEntity {

        /** Creates the handle for one horse UUID. */
        public AbstractHorseEntity(UUID uuid) {
            super(uuid);
        }

        /** Whether the horse is tamed.
         * @zh 该马是否已被驯服。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isTamed() {
            return speciesBool("tamed");
        }

        /** Whether the horse is currently eating.
         * @zh 该马是否正在进食。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isEating() {
            return speciesBool("eating");
        }

        /** Whether the horse has bred after taming.
         * @zh 驯服后是否已繁殖过。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isBred() {
            return speciesBool("bred");
        }

        /** The saddle inventory slot count.
         * @zh 鞍上背包槽位数。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Integer getInventorySize() {
            return speciesInt("inventorySize");
        }

        /** Whether a saddle is currently equipped.
         * @zh 当前是否已装备鞍。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isSaddled() {
            return speciesBool("saddled");
        }

        /** The owning player handle, or undefined when ownerless.
         * @zh 主人玩家的实体句柄；无主时为 undefined。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public EntityApi.Entity getOwner() {
            return speciesEntity("owner");
        }
    }

    /** A horse: coat variant.
     * @zh 马：毛色变体。 */
    @ApiClass(name = "HorseEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.animal.equine.Horse")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class HorseEntity extends AbstractHorseEntity {

        /** Creates the handle for one horse UUID. */
        public HorseEntity(UUID uuid) {
            super(uuid);
        }

        /** The coat variant identifier.
         * @zh 毛色变体标识。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public String getVariant() {
            return speciesString("variant");
        }
    }

    /** The chested-horse base (donkeys, mules, llamas); the chest state
     * accessor is not public and rides the follow-up batch.
     * @zh 驮箱马基类（驴、骡、羊驼）；原版未公开箱子状态访问器，随后随批。 */
    @ApiClass(name = "AbstractChestedHorseEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.animal.equine.AbstractChestedHorse")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class AbstractChestedHorseEntity extends AbstractHorseEntity {

        /** Creates the handle for one chested horse UUID. */
        public AbstractChestedHorseEntity(UUID uuid) {
            super(uuid);
        }
    }

    /** A donkey.
     * @zh 驴。 */
    @ApiClass(name = "DonkeyEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.animal.equine.Donkey")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class DonkeyEntity extends AbstractChestedHorseEntity {

        /** Creates the handle for one donkey UUID. */
        public DonkeyEntity(UUID uuid) {
            super(uuid);
        }
    }

    /** A llama: strength tier and variant.
     * @zh 羊驼：力量档位与变体。 */
    @ApiClass(name = "LlamaEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.animal.equine.Llama")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class LlamaEntity extends AbstractChestedHorseEntity {

        /** Creates the handle for one llama UUID. */
        public LlamaEntity(UUID uuid) {
            super(uuid);
        }

        /** The strength tier (1-5).
         * @zh 力量档位（1-5）。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Integer getStrength() {
            return speciesInt("strength");
        }

        /** The variant identifier.
         * @zh 变体标识。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public String getVariant() {
            return speciesString("llamaVariant");
        }
    }

    /** An iron golem: whether a player built it.
     * @zh 铁傀儡：是否为玩家建造。 */
    @ApiClass(name = "IronGolemEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.animal.golem.IronGolem")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class IronGolemEntity extends AnimalEntity {

        /** Creates the handle for one iron golem UUID. */
        public IronGolemEntity(UUID uuid) {
            super(uuid);
        }

        /** Whether the golem was created by a player.
         * @zh 该傀儡是否由玩家建造。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isPlayerCreated() {
            return speciesBool("playerCreated");
        }
    }

    /** A chicken.
     * @zh 鸡。 */
    @ApiClass(name = "ChickenEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.animal.chicken.Chicken")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class ChickenEntity extends AnimalEntity {

        /** Creates the handle for one chicken UUID. */
        public ChickenEntity(UUID uuid) {
            super(uuid);
        }
    }

    /** A ghast: fire charging state.
     * @zh 恶魂：喷火蓄力状态。 */
    @ApiClass(name = "GhastEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.monster.Ghast")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class GhastEntity extends MobEntity {

        /** Creates the handle for one ghast UUID. */
        public GhastEntity(UUID uuid) {
            super(uuid);
        }

        /** Whether the ghast is charging a fireball.
         * @zh 恶魂是否正在蓄力火球。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isCharging() {
            return speciesBool("charging");
        }
    }

    /** An ocelot: trusting state.
     * @zh 豹猫：信任状态。 */
    @ApiClass(name = "OcelotEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.animal.feline.Ocelot")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class OcelotEntity extends AnimalEntity {

        /** Creates the handle for one ocelot UUID. */
        public OcelotEntity(UUID uuid) {
            super(uuid);
        }

    }

    /** A rabbit: its variant id.
     * @zh 兔子：变体标识。 */
    @ApiClass(name = "RabbitEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.animal.rabbit.Rabbit")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class RabbitEntity extends AnimalEntity {

        /** Creates the handle for one rabbit UUID. */
        public RabbitEntity(UUID uuid) {
            super(uuid);
        }

        /** The variant identifier (e.g. minecraft:brown, minecraft:killer_bunny).
         * @zh 变体标识（如 minecraft:brown、minecraft:killer_bunny）。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public String getVariant() {
            return speciesString("variant");
        }
    }

    /** A strider: saddle and shivering state.
     * @zh 炽足兽：鞍与颤抖状态。 */
    @ApiClass(name = "StriderEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.monster.strider.Strider")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class StriderEntity extends MobEntity {

        /** Creates the handle for one strider UUID. */
        public StriderEntity(UUID uuid) {
            super(uuid);
        }

        /** Whether a saddle is currently equipped.
         * @zh 当前是否已装备鞍。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isSaddled() {
            return speciesBool("saddled");
        }

        /** Whether the strider is shivering outside the lava it needs.
         * @zh 炽足兽是否因离开熔岩而颤抖。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isShivering() {
            return speciesBool("shivering");
        }
    }

    /** A wither boss: invulnerable armor phase state.
     * @zh 凋灵：无敌护甲阶段状态。 */
    @ApiClass(name = "WitherBossEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.boss.wither.WitherBoss")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class WitherBossEntity extends MobEntity {

        /** Creates the handle for one wither UUID. */
        public WitherBossEntity(UUID uuid) {
            super(uuid);
        }

        /** The remaining invulnerability armor ticks (0 after the entry
         * phase).
         * @zh 剩余无敌护甲 tick（入场阶段结束后为 0）。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Integer getInvulnerableTicks() {
            return speciesInt("invulnerableTicks");
        }
    }

    /** An axolotl: variant, playing-dead state, and bucket origin.
     * @zh 六角恐龙：变体、装死状态与桶装来源。 */
    @ApiClass(name = "AxolotlEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.animal.axolotl.Axolotl")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class AxolotlEntity extends AnimalEntity {

        /** Creates the handle for one axolotl UUID. */
        public AxolotlEntity(UUID uuid) {
            super(uuid);
        }

        /** The variant identifier.
         * @zh 变体标识。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public String getVariant() {
            return speciesString("variant");
        }

        /** Whether the axolotl is playing dead.
         * @zh 六角恐龙是否正在装死。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isPlayingDead() {
            return speciesBool("playingDead");
        }

        /** Whether the axolotl came from a bucket.
         * @zh 六角恐龙是否来自桶装。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isFromBucket() {
            return speciesBool("fromBucket");
        }
    }

    /** A bee: nectar and sting state.
     * @zh 蜜蜂：花粉与蜇刺状态。 */
    @ApiClass(name = "BeeEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.animal.bee.Bee")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class BeeEntity extends AnimalEntity {

        /** Creates the handle for one bee UUID. */
        public BeeEntity(UUID uuid) {
            super(uuid);
        }

        /** Whether the bee is carrying nectar.
         * @zh 蜜蜂是否携带花粉。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean hasNectar() {
            return speciesBool("nectar");
        }

        /** Whether the bee has stung a player.
         * @zh 蜜蜂是否已蜇过玩家。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean hasStung() {
            return speciesBool("stung");
        }

        /** Whether the bee is currently angry at a player.
         * @zh 蜜蜂当前是否对玩家处于愤怒状态。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isAngry() {
            return speciesBool("angry");
        }
    }

    /** A fox: coat variant and body-language states.
     * @zh 狐狸：毛色变体与肢体状态。 */
    @ApiClass(name = "FoxEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.animal.fox.Fox")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class FoxEntity extends AnimalEntity {

        /** Creates the handle for one fox UUID. */
        public FoxEntity(UUID uuid) {
            super(uuid);
        }

        /** Whether the fox is sitting.
         * @zh 狐狸是否正在坐下。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isSitting() {
            return speciesBool("sitting");
        }

        /** The coat variant identifier ({@code minecraft:red} or {@code minecraft:snow}).
         * @zh 毛色变体标识（{@code minecraft:red} 或 {@code minecraft:snow}）。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public String getVariant() {
            return speciesString("variant");
        }

        /** Whether the fox is pouncing (leaping at prey).
         * @zh 狐狸是否正在扑杀（跃向猎物）。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isPouncing() {
            return speciesBool("pouncing");
        }

        /** Whether the fox is asleep curled up.
         * @zh 狐狸是否正在蜷缩睡觉。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isAsleep() {
            return speciesBool("sleeping");
        }

        /** Whether the fox has faceplanted while diving for prey.
         * @zh 狐狸是否在扑猎时头朝下扎进雪里。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isFaceplanted() {
            return speciesBool("faceplanted");
        }

        /** Whether the fox is crouching toward prey.
         * @zh 狐狸是否正压低身子潜近猎物。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isCrouching() {
            return speciesBool("crouching");
        }
    }

    /** A frog: variant.
     * @zh 青蛙：变体。 */
    @ApiClass(name = "FrogEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.animal.frog.Frog")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class FrogEntity extends AnimalEntity {

        /** Creates the handle for one frog UUID. */
        public FrogEntity(UUID uuid) {
            super(uuid);
        }

        /** The variant identifier.
         * @zh 变体标识。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public String getVariant() {
            return speciesString("variant");
        }

        /** Whether the frog is currently croaking.
         * @zh 青蛙当前是否正在鸣叫。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isCroaking() {
            return speciesBool("croaking");
        }

        /** The entity currently targeted by the tongue, or undefined.
         * @zh 舌头当前瞄准的实体；无目标时为 undefined。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public EntityApi.Entity getTongueTarget() {
            return speciesEntity("tongueTarget");
        }
    }

    /** A goat: horn state and screaming variant.
     * @zh 山羊：角状态与尖叫变体。 */
    @ApiClass(name = "GoatEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.animal.goat.Goat")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class GoatEntity extends AnimalEntity {

        /** Creates the handle for one goat UUID. */
        public GoatEntity(UUID uuid) {
            super(uuid);
        }

        /** Whether the left horn is present.
         * @zh 是否有左角。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean hasLeftHorn() {
            return speciesBool("leftHorn");
        }

        /** Whether the right horn is present.
         * @zh 是否有右角。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean hasRightHorn() {
            return speciesBool("rightHorn");
        }

        /** Whether this is a screaming goat.
         * @zh 是否为尖叫山羊。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isScreamingGoat() {
            return speciesBool("screaming");
        }
    }

    /** A panda: gene pair and behavior states.
     * @zh 熊猫：基因对与行为状态。 */
    @ApiClass(name = "PandaEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.animal.panda.Panda")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class PandaEntity extends AnimalEntity {

        /** Creates the handle for one panda UUID. */
        public PandaEntity(UUID uuid) {
            super(uuid);
        }

        /** The main gene identifier.
         * @zh 主基因标识。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public String getMainGene() {
            return speciesString("mainGene");
        }

        /** The hidden gene identifier.
         * @zh 隐性基因标识。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public String getHiddenGene() {
            return speciesString("hiddenGene");
        }

        /** Whether the panda is sneezing.
         * @zh 熊猫是否正在打喷嚏。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isSneezing() {
            return speciesBool("sneezing");
        }

        /** Whether the panda is lying on its back.
         * @zh 熊猫是否正四脚朝天。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isOnBack() {
            return speciesBool("onBack");
        }

        /** Whether the panda is eating.
         * @zh 熊猫是否正在进食。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isEating() {
            return speciesBool("eating");
        }

        /** Whether the panda is the lazy variant.
         * @zh 熊猫是否为懒性变体。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isLazy() {
            return speciesBool("lazy");
        }

        /** Whether the panda is sitting.
         * @zh 熊猫是否正在坐下。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isSitting() {
            return speciesBool("sitting");
        }

        /** Whether the panda looks worried (near a thunderstorm).
         * @zh 熊猫是否处于担忧状态（雷暴临近）。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isWorried() {
            return speciesBool("worried");
        }

        /** Whether the panda is being playful.
         * @zh 熊猫是否处于顽皮状态。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isPlayful() {
            return speciesBool("playful");
        }

        /** Whether the panda is rolling around.
         * @zh 熊猫是否正在打滚。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isRolling() {
            return speciesBool("rolling");
        }

        /** Whether the panda shows the brown (rare) look.
         * @zh 熊猫是否为棕色（罕见）外观。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isBrown() {
            return speciesBool("brown");
        }

        /** Whether the panda is the weak variant (lower health).
         * @zh 熊猫是否为虚弱变体（更低生命）。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isWeak() {
            return speciesBool("weak");
        }

        /** Whether the panda is scared and hiding.
         * @zh 熊猫是否因受惊而躲避。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isScared() {
            return speciesBool("scared");
        }

        /** Whether the main gene is recessive; only brown and weak are.
         * @zh 主基因是否为隐性；仅 brown 与 weak 为隐性。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isMainGeneRecessive() {
            return speciesBool("mainGeneRecessive");
        }

        /** Whether the hidden gene is recessive; only brown and weak are.
         * @zh 隐性基因是否为隐性；仅 brown 与 weak 为隐性。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isHiddenGeneRecessive() {
            return speciesBool("hiddenGeneRecessive");
        }
    }

    /** A parrot: variant and party state.
     * @zh 鹦鹉：变体与起舞状态。 */
    @ApiClass(name = "ParrotEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.animal.parrot.Parrot")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class ParrotEntity extends AnimalEntity {

        /** Creates the handle for one parrot UUID. */
        public ParrotEntity(UUID uuid) {
            super(uuid);
        }

        /** The variant identifier.
         * @zh 变体标识。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public String getVariant() {
            return speciesString("variant");
        }

        /** Whether the parrot is dancing to a nearby jukebox.
         * @zh 鹦鹉是否正跟着唱片机起舞。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isPartying() {
            return speciesBool("partying");
        }
    }

    /** A polar bear: standing state.
     * @zh 北极熊：站立状态。 */
    @ApiClass(name = "PolarBearEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.animal.polarbear.PolarBear")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class PolarBearEntity extends AnimalEntity {

        /** Creates the handle for one polar bear UUID. */
        public PolarBearEntity(UUID uuid) {
            super(uuid);
        }

        /** Whether the polar bear is standing on its hind legs.
         * @zh 北极熊是否以后腿站立。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isStanding() {
            return speciesBool("standing");
        }
    }

    /** A shulker: shell color.
     * @zh 潜影贝：壳颜色。 */
    @ApiClass(name = "ShulkerEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.monster.Shulker")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class ShulkerEntity extends MobEntity {

        /** Creates the handle for one shulker UUID. */
        public ShulkerEntity(UUID uuid) {
            super(uuid);
        }

        /** The shell color identifier, or undefined for the default shell.
         * @zh 壳颜色标识；默认壳为 undefined。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public String getColor() {
            return speciesString("color");
        }

        /** The face direction the shulker is attached to (e.g. {@code north}).
         * @zh 潜影贝附着面朝向（如 {@code north}）。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public String getAttachFace() {
            return speciesString("attachFace");
        }

        /** The current shell opening amount from 0 (closed) to 1.
         * @zh 壳当前张开程度（0 为闭合，1 为全开）。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Double getPeek() {
            return speciesDouble("peek");
        }
    }

    /** A snow golem: pumpkin hat state.
     * @zh 雪傀儡：南瓜头状态。 */
    @ApiClass(name = "SnowGolemEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.animal.golem.SnowGolem")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class SnowGolemEntity extends MobEntity {

        /** Creates the handle for one snow golem UUID. */
        public SnowGolemEntity(UUID uuid) {
            super(uuid);
        }

        /** Whether the pumpkin hat is still on.
         * @zh 是否仍戴着南瓜头。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean hasPumpkin() {
            return speciesBool("pumpkin");
        }

        /** Whether the snow golem is currently shearable.
         * @zh 雪傀儡当前是否可剪。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isShearable() {
            return speciesBool("shearable");
        }
    }

    /** A turtle: egg-laying state.
     * @zh 海龟：产卵状态。 */
    @ApiClass(name = "TurtleEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.animal.turtle.Turtle")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class TurtleEntity extends AnimalEntity {

        /** Creates the handle for one turtle UUID. */
        public TurtleEntity(UUID uuid) {
            super(uuid);
        }

        /** Whether the turtle is carrying eggs.
         * @zh 海龟是否携带卵。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean hasEgg() {
            return speciesBool("hasEgg");
        }

        /** The home beach position the turtle returns to ({@code x, y, z}).
         * @zh 海龟回归的家沙滩坐标（{@code x, y, z}）。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Object getHomePos() {
            return speciesState().get("homePos");
        }
    }

    /** A vex: charging attack state.
     * @zh 恼鬼：冲锋攻击状态。 */
    @ApiClass(name = "VexEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.monster.Vex")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class VexEntity extends MobEntity {

        /** Creates the handle for one vex UUID. */
        public VexEntity(UUID uuid) {
            super(uuid);
        }

        /** Whether the vex is charging its attack.
         * @zh 恼鬼是否正在蓄力攻击。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isCharging() {
            return speciesBool("charging");
        }
    }

    /** A witch: potion-drinking state.
     * @zh 女巫：喝药状态。 */
    @ApiClass(name = "WitchEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.monster.Witch")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class WitchEntity extends AbstractIllagerEntity {

        /** Creates the handle for one witch UUID. */
        public WitchEntity(UUID uuid) {
            super(uuid);
        }

        /** Whether the witch is drinking a potion.
         * @zh 女巫是否正在喝药。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isDrinkingPotion() {
            return speciesBool("drinkingPotion");
        }
    }

    /** A dolphin: moisture level.
     * @zh 海豚：湿润度。 */
    @ApiClass(name = "DolphinEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.animal.dolphin.Dolphin")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class DolphinEntity extends MobEntity {

        /** Creates the handle for one dolphin UUID. */
        public DolphinEntity(UUID uuid) {
            super(uuid);
        }

        /** The moisture level (max 2400; dries out on land).
         * @zh 湿润度（上限 2400；离水后下降）。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Integer getMoistness() {
            return speciesInt("moistness");
        }

        /** Whether the dolphin currently has a fish in its mouth.
         * @zh 海豚当前是否叼着鱼。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean hasFish() {
            return speciesBool("hasFish");
        }
    }

    /** A warden: anger level.
     * @zh 监守者：愤怒等级。 */
    @ApiClass(name = "WardenEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.monster.warden.Warden")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class WardenEntity extends MobEntity {

        /** Creates the handle for one warden UUID. */
        public WardenEntity(UUID uuid) {
            super(uuid);
        }

        /** The anger level identifier (calmed, agitated, angry or furious).
         * @zh 愤怒等级标识（calmed/agitated/angry/furious）。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public String getAngerLevel() {
            return speciesString("angerLevel");
        }
    }

    /** A drowned.
     * @zh 溺尸。 */
    @ApiClass(name = "DrownedEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.monster.zombie.Drowned")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class DrownedEntity extends ZombieEntity {

        /** Creates the handle for one drowned UUID. */
        public DrownedEntity(UUID uuid) {
            super(uuid);
        }
    }

    /** A zombie villager: trade identity and conversion state.
     * @zh 僵尸村民：交易身份与转化状态。 */
    @ApiClass(name = "ZombieVillagerEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.monster.zombie.ZombieVillager")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class ZombieVillagerEntity extends ZombieEntity {

        /** Creates the handle for one zombie villager UUID. */
        public ZombieVillagerEntity(UUID uuid) {
            super(uuid);
        }

        /**
         * The profession identifier this villager would regain on curing,
         * e.g. {@code minecraft:farmer}.
         *
         * @zh 治愈后恢复的职业标识，如 {@code minecraft:farmer}。
         */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public String getProfession() {
            return speciesString("profession");
        }

        /**
         * The trade level (1-5) this villager would regain on curing.
         *
         * @zh 治愈后恢复的交易等级（1-5）。
         */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Integer getLevel() {
            Object level = speciesState().get("level");
            return level instanceof Integer value ? value : null;
        }

        /**
         * The biome variant identifier this villager would regain on curing,
         * e.g. {@code minecraft:plains}.
         *
         * @zh 治愈后恢复的生物群系变体标识，如 {@code minecraft:plains}。
         */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public String getVariant() {
            return speciesString("variant");
        }

        /**
         * Whether the zombie villager is converting back into a villager.
         *
         * @zh 僵尸村民是否正在转化回村民。
         */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isConverting() {
            return speciesBool("converting");
        }
    }

    /** A zombified piglin.
     * @zh 僵尸猪灵。 */
    @ApiClass(name = "ZombifiedPiglinEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.monster.zombie.ZombifiedPiglin")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class ZombifiedPiglinEntity extends ZombieEntity {

        /** Creates the handle for one zombified piglin UUID. */
        public ZombifiedPiglinEntity(UUID uuid) {
            super(uuid);
        }
    }

    /** An allay: jukebox dance and held-item state.
     * @zh 悦灵：唱片机起舞与持物状态。 */
    @ApiClass(name = "AllayEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.animal.allay.Allay")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class AllayEntity extends MobEntity {

        /** Creates the handle for one allay UUID. */
        public AllayEntity(UUID uuid) {
            super(uuid);
        }

        /**
         * Whether the allay is dancing to a nearby jukebox.
         *
         * @zh 悦灵是否正随附近唱片机起舞。
         */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isDancing() {
            return speciesBool("dancing");
        }

        /**
         * Whether the dancing allay is doing its spin flourish.
         *
         * @zh 起舞的悦灵是否正在转圈。
         */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isSpinning() {
            return speciesBool("spinning");
        }

        /**
         * Whether the allay is currently holding an item.
         *
         * @zh 悦灵当前是否持有物品。
         */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean hasItemInHand() {
            return speciesBool("hasItemInHand");
        }

        /**
         * Whether the allay is flapping its wings this tick.
         *
         * @zh 悦灵本刻是否正在扇动翅膀。
         */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isFlapping() {
            return speciesBool("flapping");
        }
    }

    /** A cow: coat variant.
     * @zh 牛：毛色变体。 */
    @ApiClass(name = "CowEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.animal.cow.Cow")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class CowEntity extends AnimalEntity {

        /** Creates the handle for one cow UUID. */
        public CowEntity(UUID uuid) {
            super(uuid);
        }

        /**
         * The coat variant identifier, e.g. {@code minecraft:temperate}.
         *
         * @zh 毛色变体标识，如 {@code minecraft:temperate}。
         */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public String getVariant() {
            return speciesString("variant");
        }
    }

    /** An armadillo: shell roll state.
     * @zh 犰狳：缩壳状态。 */
    @ApiClass(name = "ArmadilloEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.animal.armadillo.Armadillo")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class ArmadilloEntity extends AnimalEntity {

        /** Creates the handle for one armadillo UUID. */
        public ArmadilloEntity(UUID uuid) {
            super(uuid);
        }

        /**
         * The roll state identifier, one of {@code minecraft:idle},
         * {@code minecraft:rolling}, {@code minecraft:scared}, or
         * {@code minecraft:unrolling}.
         *
         * @zh 缩壳状态标识，取值 {@code minecraft:idle}、
         * {@code minecraft:rolling}、{@code minecraft:scared} 或
         * {@code minecraft:unrolling}。
         */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public String getState() {
            return speciesString("state");
        }

        /**
         * Whether the armadillo is scared into its shell.
         *
         * @zh 犰狳是否受惊缩壳。
         */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isScared() {
            return speciesBool("scared");
        }
    }

    /** A camel: dash and sit pose, over the horse inventory base.
     * @zh 骆驼：冲刺与坐姿，继承马匹背包基类。 */
    @ApiClass(name = "CamelEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.animal.camel.Camel")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class CamelEntity extends AbstractHorseEntity {

        /** Creates the handle for one camel UUID. */
        public CamelEntity(UUID uuid) {
            super(uuid);
        }

        /**
         * Whether the camel is dashing.
         *
         * @zh 骆驼是否正在冲刺。
         */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isDashing() {
            return speciesBool("dashing");
        }

        /**
         * Whether the camel is sitting.
         *
         * @zh 骆驼是否正在坐下。
         */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isSitting() {
            return speciesBool("sitting");
        }

        /**
         * Whether the camel is transitioning between sit poses.
         *
         * @zh 骆驼是否正在坐姿切换中。
         */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isInPoseTransition() {
            return speciesBool("poseTransition");
        }

        /**
         * The tick timestamp of the last pose change.
         *
         * @zh 上次坐姿切换的游戏刻时间戳。
         */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Integer getPoseTime() {
            return speciesInt("poseTime");
        }
    }

    /** A sniffer: sniffing and digging state.
     * @zh 嗅探兽：嗅探与挖刨状态。 */
    @ApiClass(name = "SnifferEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.animal.sniffer.Sniffer")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class SnifferEntity extends AnimalEntity {

        /** Creates the handle for one sniffer UUID. */
        public SnifferEntity(UUID uuid) {
            super(uuid);
        }

        /**
         * Whether the sniffer is searching the ground for seeds.
         *
         * @zh 嗅探兽是否正在嗅探地面寻找种子。
         */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isSearching() {
            return speciesBool("searching");
        }

        /**
         * Whether the sniffer is tempted by a player-held item.
         *
         * @zh 嗅探兽是否被玩家手持物品引诱。
         */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isTempted() {
            return speciesBool("tempted");
        }

        /**
         * Whether the sniffer can sniff right now.
         *
         * @zh 嗅探兽当前能否嗅探。
         */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean canSniff() {
            return speciesBool("canSniff");
        }
    }

    /** The fish base: bucket-birth state.
     * @zh 鱼类基类：桶装来源状态。 */
    @ApiClass(name = "AbstractFishEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.animal.fish.AbstractFish")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class AbstractFishEntity extends AnimalEntity {

        /** Creates the handle for one fish UUID. */
        public AbstractFishEntity(UUID uuid) {
            super(uuid);
        }

        /**
         * Whether the fish was released from a water bucket.
         *
         * @zh 鱼是否来自放出的水桶。
         */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isFromBucket() {
            return speciesBool("fromBucket");
        }
    }

    /** The schooling fish base: school membership.
     * @zh 群游鱼类基类：鱼群成员关系。 */
    @ApiClass(name = "AbstractSchoolingFishEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.animal.fish.AbstractSchoolingFish")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class AbstractSchoolingFishEntity extends AbstractFishEntity {

        /** Creates the handle for one schooling fish UUID. */
        public AbstractSchoolingFishEntity(UUID uuid) {
            super(uuid);
        }

        /**
         * Whether this fish leads a school with at least one follower.
         *
         * @zh 该鱼是否为带领至少一条跟随者的鱼群首领。
         */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean hasFollowers() {
            return speciesBool("hasFollowers");
        }

        /**
         * Whether this fish follows a school leader.
         *
         * @zh 该鱼是否跟随某个鱼群首领。
         */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isFollower() {
            return speciesBool("follower");
        }

        /**
         * Whether other fish may follow this one.
         *
         * @zh 其他鱼是否可跟随该鱼。
         */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean canBeFollowed() {
            return speciesBool("canBeFollowed");
        }
    }

    /** A salmon: size variant.
     * @zh 鲑鱼：体型变体。 */
    @ApiClass(name = "SalmonEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.animal.fish.Salmon")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class SalmonEntity extends AbstractSchoolingFishEntity {

        /** Creates the handle for one salmon UUID. */
        public SalmonEntity(UUID uuid) {
            super(uuid);
        }

        /**
         * The size variant identifier, one of {@code minecraft:small},
         * {@code minecraft:medium}, or {@code minecraft:large}.
         *
         * @zh 体型变体标识，取值 {@code minecraft:small}、
         * {@code minecraft:medium} 或 {@code minecraft:large}。
         */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public String getVariant() {
            return speciesString("variant");
        }
    }

    /** A cod.
     * @zh 鳕鱼。 */
    @ApiClass(name = "CodEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.animal.fish.Cod")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class CodEntity extends AbstractSchoolingFishEntity {

        /** Creates the handle for one cod UUID. */
        public CodEntity(UUID uuid) {
            super(uuid);
        }
    }

    /** A pufferfish: puff inflation level (0-2).
     * @zh 河豚：鼓胀等级（0-2）。 */
    @ApiClass(name = "PufferfishEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.animal.fish.Pufferfish")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class PufferfishEntity extends AbstractFishEntity {

        /** Creates the handle for one pufferfish UUID. */
        public PufferfishEntity(UUID uuid) {
            super(uuid);
        }

        /**
         * The puff state, 0 deflated through 2 fully inflated.
         *
         * @zh 鼓胀状态，0 为收起，2 为完全鼓起。
         */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Integer getPuffState() {
            return speciesInt("puffState");
        }
    }

    /** A tropical fish: shape pattern and colors.
     * @zh 热带鱼：形态花纹与配色。 */
    @ApiClass(name = "TropicalFishEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.animal.fish.TropicalFish")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class TropicalFishEntity extends AbstractFishEntity {

        /** Creates the handle for one tropical fish UUID. */
        public TropicalFishEntity(UUID uuid) {
            super(uuid);
        }

        /**
         * The shape pattern identifier, e.g. {@code minecraft:kob}.
         *
         * @zh 形态花纹标识，如 {@code minecraft:kob}。
         */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public String getPattern() {
            return speciesString("pattern");
        }

        /**
         * The base body color identifier, e.g. {@code minecraft:white}.
         *
         * @zh 身体底色标识，如 {@code minecraft:white}。
         */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public String getBaseColor() {
            return speciesString("baseColor");
        }

        /**
         * The pattern highlight color identifier, e.g. {@code minecraft:blue}.
         *
         * @zh 花纹高光色标识，如 {@code minecraft:blue}。
         */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public String getPatternColor() {
            return speciesString("patternColor");
        }
    }

    /** A piglin: barterer growth and zombification state.
     * @zh 猪灵：以物易物者的成长与僵尸化状态。 */
    @ApiClass(name = "PiglinEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.monster.piglin.Piglin")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class PiglinEntity extends MobEntity {

        /** Creates the handle for one piglin UUID. */
        public PiglinEntity(UUID uuid) {
            super(uuid);
        }

        /**
         * Whether the piglin is converting into a zombified piglin.
         *
         * @zh 猪灵是否正在转化为僵尸猪灵。
         */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isConverting() {
            return speciesBool("converting");
        }

        /**
         * Whether the piglin has grown out of its baby stage.
         *
         * @zh 猪灵是否已长成成年。
         */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isAdult() {
            return speciesBool("adult");
        }
    }

    /** A piglin brute: aggressive growth and zombification state.
     * @zh 猪灵蛮兵：好战者的成长与僵尸化状态。 */
    @ApiClass(name = "PiglinBruteEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.monster.piglin.PiglinBrute")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class PiglinBruteEntity extends MobEntity {

        /** Creates the handle for one piglin brute UUID. */
        public PiglinBruteEntity(UUID uuid) {
            super(uuid);
        }

        /**
         * Whether the brute is converting into a zombified piglin.
         *
         * @zh 蛮兵是否正在转化为僵尸猪灵。
         */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isConverting() {
            return speciesBool("converting");
        }

        /**
         * Whether the brute has grown out of its baby stage.
         *
         * @zh 蛮兵是否已长成成年。
         */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isAdult() {
            return speciesBool("adult");
        }
    }

    /** A pillager: crossbow charging state.
     * @zh 掠夺者：弩充能状态。 */
    @ApiClass(name = "PillagerEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.monster.illager.Pillager")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class PillagerEntity extends AbstractIllagerEntity {

        /** Creates the handle for one pillager UUID. */
        public PillagerEntity(UUID uuid) {
            super(uuid);
        }

        /**
         * Whether the pillager is charging its crossbow.
         *
         * @zh 掠夺者是否正在给弩上弦。
         */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isChargingCrossbow() {
            return speciesBool("chargingCrossbow");
        }
    }

    /** An evoker: spell casting state (inherits the spellcaster surface).
     * @zh 唤魔者：施法状态（继承施法者成员面）。 */
    @ApiClass(name = "EvokerEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.monster.illager.Evoker")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class EvokerEntity extends SpellcasterIllagerEntity {

        /** Creates the handle for one evoker UUID. */
        public EvokerEntity(UUID uuid) {
            super(uuid);
        }
    }

    /** A wandering trader: despawn countdown.
     * @zh 流浪商人：滞留倒计时。 */
    @ApiClass(name = "WanderingTraderEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class WanderingTraderEntity extends MobEntity {

        /** Creates the handle for one wandering trader UUID. */
        public WanderingTraderEntity(UUID uuid) {
            super(uuid);
        }

        /**
         * The delay in ticks before the trader despawns; zero while trading.
         *
         * @zh 商人消失前的延迟刻数；交易期间为零。
         */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Integer getDespawnDelay() {
            return speciesInt("despawnDelay");
        }
    }

    /** The ender dragon: fight phase and death countdown.
     * @zh 末影龙：战斗阶段与死亡倒计时。 */
    @ApiClass(name = "EnderDragonEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.boss.enderdragon.EnderDragon")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class EnderDragonEntity extends MobEntity {

        /** Creates the handle for one ender dragon UUID. */
        public EnderDragonEntity(UUID uuid) {
            super(uuid);
        }

        /**
         * The fight phase index synced from the dragon fight.
         *
         * @zh 龙战同步来的阶段序号。
         */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Integer getPhase() {
            return speciesInt("phase");
        }

        /**
         * The death animation countdown; above zero only while dying.
         *
         * @zh 死亡动画倒计时；仅濒死时大于零。
         */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Integer getDeathTime() {
            return speciesInt("deathTime");
        }
    }

    /** A breeze: slide attack state.
     * @zh 旋风人：滑行攻击状态。 */
    @ApiClass(name = "BreezeEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.monster.breeze.Breeze")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class BreezeEntity extends MobEntity {

        /** Creates the handle for one breeze UUID. */
        public BreezeEntity(UUID uuid) {
            super(uuid);
        }

        /**
         * Whether the breeze is sliding toward its target.
         *
         * @zh 旋风人是否正在滑行突进。
         */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isSliding() {
            return speciesBool("sliding");
        }
    }

    /** A creaking: heart binding and mobility state.
     * @zh 嘎枝：心脏绑定与移动状态。 */
    @ApiClass(name = "CreakingEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.monster.creaking.Creaking")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class CreakingEntity extends MobEntity {

        /** Creates the handle for one creaking UUID. */
        public CreakingEntity(UUID uuid) {
            super(uuid);
        }

        /**
         * Whether the creaking is active and stalking.
         *
         * @zh 嘎枝是否处于活跃游荡状态。
         */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isActive() {
            return speciesBool("active");
        }

        /**
         * Whether the creaking is tearing down after its heart broke.
         *
         * @zh 嘎枝是否在心脏被破坏后的消散中。
         */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isTearingDown() {
            return speciesBool("tearingDown");
        }

        /**
         * Whether the creaking is bound to a creaking heart.
         *
         * @zh 嘎枝是否绑定在嘎枝心脏上。
         */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isHeartBound() {
            return speciesBool("heartBound");
        }

        /**
         * Whether the creaking may move right now.
         *
         * @zh 嘎枝当前是否可以移动。
         */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean canMove() {
            return speciesBool("canMove");
        }
    }

    /**
     * An armor stand: the marker and small pose flags.
     *
     * @zh 盔甲架：隐形判定标记与小尺寸旗标。
     */
    @ApiClass(name = "ArmorStandEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.decoration.ArmorStand")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class ArmorStandEntity extends MobEntity {

        /** Creates the handle for one armor stand UUID. */
        public ArmorStandEntity(UUID uuid) {
            super(uuid);
        }

        /**
         * Whether the stand is a hitbox-less marker.
         *
         * @zh 盔甲架是否为无碰撞判定标记。
         */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isMarker() {
            return speciesBool("marker");
        }

        /**
         * Whether the stand uses the small pose.
         *
         * @zh 盔甲架是否为小尺寸。
         */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isSmall() {
            return speciesBool("small");
        }

        /**
         * Whether the stand is invisible (the vanilla invisible flag).
         *
         * @zh 盔甲架是否隐形（原版隐形标记）。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isInvisible() {
            return speciesBool("invisible");
        }

        /**
         * Whether the stand displays arms.
         *
         * @zh 盔甲架是否显示手臂。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean showArms() {
            return speciesBool("showArms");
        }

        /**
         * Whether the stand displays its base plate.
         *
         * @zh 盔甲架是否显示底座。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean showBasePlate() {
            return speciesBool("showBasePlate");
        }

        /**
         * The body pose rotation triple (x, y, z) in degrees.
         *
         * @zh 身体姿态旋转三元组（x、y、z，单位度）。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public ink.tuanzi.neomacros.core.api.types.Pos3D getBodyPose() {
            return poseOf(speciesState(), "bodyPose");
        }

        /**
         * The head pose rotation triple (x, y, z) in degrees.
         *
         * @zh 头部姿态旋转三元组（x、y、z，单位度）。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public ink.tuanzi.neomacros.core.api.types.Pos3D getHeadPose() {
            return poseOf(speciesState(), "headPose");
        }

        /**
         * The left arm pose rotation triple (x, y, z) in degrees.
         *
         * @zh 左臂姿态旋转三元组（x、y、z，单位度）。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public ink.tuanzi.neomacros.core.api.types.Pos3D getLeftArmPose() {
            return poseOf(speciesState(), "leftArmPose");
        }

        /**
         * The right arm pose rotation triple (x, y, z) in degrees.
         *
         * @zh 右臂姿态旋转三元组（x、y、z，单位度）。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public ink.tuanzi.neomacros.core.api.types.Pos3D getRightArmPose() {
            return poseOf(speciesState(), "rightArmPose");
        }

        /**
         * The left leg pose rotation triple (x, y, z) in degrees.
         *
         * @zh 左腿姿态旋转三元组（x、y、z，单位度）。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public ink.tuanzi.neomacros.core.api.types.Pos3D getLeftLegPose() {
            return poseOf(speciesState(), "leftLegPose");
        }

        /**
         * The right leg pose rotation triple (x, y, z) in degrees.
         *
         * @zh 右腿姿态旋转三元组（x、y、z，单位度）。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public ink.tuanzi.neomacros.core.api.types.Pos3D getRightLegPose() {
            return poseOf(speciesState(), "rightLegPose");
        }
    }

    /**
     * A falling block: the block it carries.
     *
     * @zh 下落方块：其所携带的方块。
     */
    @ApiClass(name = "FallingBlockEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.item.FallingBlockEntity")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class FallingBlockEntity extends EntityApi.Entity {

        /** Creates the handle for one falling block UUID. */
        public FallingBlockEntity(UUID uuid) {
            super(uuid);
        }

        /**
         * The identifier of the block this entity carries.
         *
         * @zh 该实体携带的方块标识。
         */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public String getBlock() {
            return speciesString("block");
        }
    }

    /**
     * An item frame: the displayed stack and its rotation step.
     *
     * @zh 物品展示框：所展示的物品堆与旋转步进。
     */
    @ApiClass(name = "ItemFrameEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.decoration.ItemFrame")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class ItemFrameEntity extends EntityApi.Entity {

        /** Creates the handle for one item frame UUID. */
        public ItemFrameEntity(UUID uuid) {
            super(uuid);
        }

        /**
         * The displayed stack; empty air when the frame is empty.
         *
         * @zh 展示的物品堆；空框为 minecraft:air。
         */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public ink.tuanzi.neomacros.core.api.types.ItemStack getItem() {
            Object data = speciesState().get("item");
            return data instanceof ink.tuanzi.neomacros.port.model.ItemStackData stack
                    ? ink.tuanzi.neomacros.core.api.types.ItemStack.of(stack) : null;
        }

        /**
         * The rotation step of the displayed stack (0-3).
         *
         * @zh 展示物品的旋转步进（0-3）。
         */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Integer getFrameRotation() {
            return speciesInt("rotation");
        }
    }

    /**
     * An area effect cloud: radius and waiting state.
     *
     * @zh 药水效果云：半径与潜伏状态。
     */
    @ApiClass(name = "AreaEffectCloudEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.AreaEffectCloud")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class AreaEffectCloudEntity extends EntityApi.Entity {

        /** Creates the handle for one effect cloud UUID. */
        public AreaEffectCloudEntity(UUID uuid) {
            super(uuid);
        }

        /**
         * The current cloud radius in blocks.
         *
         * @zh 效果云当前半径（格）。
         */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Double getRadius() {
            Object value = speciesState().get("radius");
            return value instanceof Number n ? n.doubleValue() : null;
        }

        /**
         * Whether the cloud is waiting before the effect starts.
         *
         * @zh 效果云是否处于生效前的潜伏中。
         */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isWaiting() {
            return speciesBool("waiting");
        }

        /**
         * The remaining wait time in ticks.
         *
         * @zh 潜伏剩余刻数。
         */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Integer getWaitTime() {
            return speciesInt("waitTime");
        }

        /**
         * The cloud particle type identifier, e.g. {@code minecraft:effect}.
         *
         * @zh 效果云粒子类型标识，如 {@code minecraft:effect}。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public String getParticle() {
            return speciesString("particle");
        }
    }

    /** Projectile base: shared state for arrows, tridents, and skulls.
     * @zh 弹射物基类：箭、三叉戟与凋灵之首的共享状态。 */
    @ApiClass(name = "ProjectileEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.projectile.Projectile")
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class ProjectileEntity extends EntityApi.Entity {

        /** Creates the handle for one projectile UUID. */
        public ProjectileEntity(UUID uuid) {
            super(uuid);
        }
    }

    /** Abstract arrow base: crit and piercing state.
     * @zh 箭类基类：暴击与穿透状态。 */
    @ApiClass(name = "AbstractArrowEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.projectile.arrow.AbstractArrow")
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class AbstractArrowEntity extends ProjectileEntity {

        /** Creates the handle for one arrow UUID. */
        public AbstractArrowEntity(UUID uuid) {
            super(uuid);
        }

        /** Whether the arrow is a critical hit arrow.
         * @zh 是否为暴击箭。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isCritical() {
            return speciesBool("critical");
        }

        /** The piercing level (0 for non-piercing shots).
         * @zh 穿透等级（普通射击为 0）。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Integer getPierceLevel() {
            return speciesInt("pierceLevel");
        }
    }

    /** An arrow: the tip potion color.
     * @zh 箭：药水箭尖颜色。 */
    @ApiClass(name = "ArrowEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.projectile.arrow.Arrow")
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class ArrowEntity extends AbstractArrowEntity {

        /** Creates the handle for one arrow UUID. */
        public ArrowEntity(UUID uuid) {
            super(uuid);
        }

        /** The potion arrow color as packed RGB, or undefined for plain arrows.
         * @zh 药水箭颜色（RGB 打包整数）；普通箭为 undefined。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Integer getColor() {
            return speciesInt("color");
        }
    }

    /** A thrown trident: enchantment foil state.
     * @zh 掷出的三叉戟：附魔光泽状态。 */
    @ApiClass(name = "ThrownTridentEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.projectile.arrow.ThrownTrident")
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class ThrownTridentEntity extends AbstractArrowEntity {

        /** Creates the handle for one trident UUID. */
        public ThrownTridentEntity(UUID uuid) {
            super(uuid);
        }

        /** Whether the trident renders with enchantment foil.
         * @zh 三叉戟是否带附魔光泽。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isFoil() {
            return speciesBool("foil");
        }
    }

    /** A wither skull: charged (blue) state.
     * @zh 凋灵之首：充能（蓝色）状态。 */
    @ApiClass(name = "WitherSkullEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull")
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class WitherSkullEntity extends ProjectileEntity {

        /** Creates the handle for one wither skull UUID. */
        public WitherSkullEntity(UUID uuid) {
            super(uuid);
        }

        /** Whether this is the charged blue skull variant.
         * @zh 是否为充能的蓝色凋灵之首。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isCharged() {
            return speciesBool("charged");
        }
    }

    /** A fishing bobber: hook state and open-water flag.
     * @zh 浮漂：钩住状态与开阔水域标记。 */
    @ApiClass(name = "FishingHookEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.projectile.FishingHook")
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class FishingHookEntity extends ProjectileEntity {

        /** Creates the handle for one fishing hook UUID. */
        public FishingHookEntity(UUID uuid) {
            super(uuid);
        }

        /** The entity currently hooked on the line, or undefined.
         * @zh 当前被钩住的实体；无钩住目标时为 undefined。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public EntityApi.Entity getHookedEntity() {
            return speciesEntity("hooked");
        }

        /** Whether the bobber floats in open water.
         * @zh 浮漂是否处于开阔水域。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isOpenWater() {
            return speciesBool("openWater");
        }

        /** The angling player handle, or undefined.
         * @zh 垂钓玩家的实体句柄；无主时为 undefined。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public EntityApi.Entity getAngler() {
            return speciesEntity("angler");
        }
    }

    /** A boat: submerged state.
     * @zh 船：水下浸没状态。 */
    @ApiClass(name = "BoatEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.vehicle.boat.AbstractBoat")
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class BoatEntity extends EntityApi.Entity {

        /** Creates the handle for one boat UUID. */
        public BoatEntity(UUID uuid) {
            super(uuid);
        }

        /** Whether the boat hull is fully under water.
         * @zh 船体是否完全没入水下。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isUnderWater() {
            return speciesBool("underWater");
        }
    }

    /** A chest boat (discriminated also by the {@code chest_boat} type id).
     * @zh 运输船（也可经 {@code chest_boat} 类型标识判别）。 */
    @ApiClass(name = "ChestBoatEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.vehicle.boat.AbstractChestBoat")
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class ChestBoatEntity extends BoatEntity {

        /** Creates the handle for one chest boat UUID. */
        public ChestBoatEntity(UUID uuid) {
            super(uuid);
        }
    }

    /** Minecart base.
     * @zh 矿车基类。 */
    @ApiClass(name = "AbstractMinecartEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.vehicle.minecart.AbstractMinecart")
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class AbstractMinecartEntity extends EntityApi.Entity {

        /** Creates the handle for one minecart UUID. */
        public AbstractMinecartEntity(UUID uuid) {
            super(uuid);
        }
    }

    /** A furnace minecart (fuel state has no client accessor).
     * @zh 动力矿车（燃料状态无客户端访问器）。 */
    @ApiClass(name = "MinecartFurnaceEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.vehicle.minecart.MinecartFurnace")
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class MinecartFurnaceEntity extends AbstractMinecartEntity {

        /** Creates the handle for one furnace minecart UUID. */
        public MinecartFurnaceEntity(UUID uuid) {
            super(uuid);
        }
    }

    /** A TNT minecart: fuse countdown.
     * @zh TNT 矿车：引信倒计时。 */
    @ApiClass(name = "MinecartTntEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.vehicle.minecart.MinecartTNT")
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class MinecartTntEntity extends AbstractMinecartEntity {

        /** Creates the handle for one TNT minecart UUID. */
        public MinecartTntEntity(UUID uuid) {
            super(uuid);
        }

        /** The remaining fuse ticks, or undefined when not primed.
         * @zh 剩余引信 tick；未点燃时为 undefined。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Integer getFuse() {
            return speciesInt("fuse");
        }

        /** Whether the TNT cart is primed and about to explode.
         * @zh TNT 矿车是否已点燃即将爆炸。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isPrimed() {
            return speciesBool("primed");
        }
    }

    /** An end crystal: beam target and base state.
     * @zh 末影水晶：光束目标与底座状态。 */
    @ApiClass(name = "EndCrystalEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.boss.enderdragon.EndCrystal")
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class EndCrystalEntity extends EntityApi.Entity {

        /** Creates the handle for one end crystal UUID. */
        public EndCrystalEntity(UUID uuid) {
            super(uuid);
        }

        /** The position the healing beam points at, or undefined.
         * @zh 治愈光束指向的坐标；无目标时为 undefined。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Object getBeamTarget() {
            return speciesState().get("beamTarget");
        }

        /** Whether the bedrock base is rendered.
         * @zh 是否渲染基岩底座。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isShowingBottom() {
            return speciesBool("showBottom");
        }
    }

    /** An interaction entity: hitbox size and response state.
     * @zh 交互实体：碰撞箱尺寸与响应状态。 */
    @ApiClass(name = "InteractionEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.Interaction")
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class InteractionEntity extends EntityApi.Entity {

        /** Creates the handle for one interaction UUID. */
        public InteractionEntity(UUID uuid) {
            super(uuid);
        }

        /** The hitbox width in blocks.
         * @zh 碰撞箱宽度（格）。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Double getWidth() {
            return speciesDouble("width");
        }

        /** The hitbox height in blocks.
         * @zh 碰撞箱高度（格）。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Double getHeight() {
            return speciesDouble("height");
        }

        /** Whether the entity responds to interactions.
         * @zh 该实体是否响应交互。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isResponsive() {
            return speciesBool("response");
        }

        /** The last attacker entity handle, or undefined.
         * @zh 最近攻击者的实体句柄；无记录时为 undefined。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public EntityApi.Entity getLastAttacker() {
            return speciesEntity("lastAttacker");
        }

        /** The last interacting entity handle, or undefined.
         * @zh 最近交互者的实体句柄；无记录时为 undefined。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public EntityApi.Entity getLastInteracted() {
            return speciesEntity("lastInteracted");
        }
    }

    /** Hanging decoration base (paintings, item frames).
     * @zh 悬挂装饰基类（画与物品展示框）。 */
    @ApiClass(name = "HangingEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.decoration.HangingEntity")
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class HangingEntity extends EntityApi.Entity {

        /** Creates the handle for one hanging decoration UUID. */
        public HangingEntity(UUID uuid) {
            super(uuid);
        }
    }

    /** A painting: artwork id and dimensions.
     * @zh 画：画作标识与尺寸。 */
    @ApiClass(name = "PaintingEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.decoration.painting.Painting")
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class PaintingEntity extends HangingEntity {

        /** Creates the handle for one painting UUID. */
        public PaintingEntity(UUID uuid) {
            super(uuid);
        }

        /** The artwork identifier, e.g. {@code minecraft:wanderer}.
         * @zh 画作标识，如 {@code minecraft:wanderer}。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public String getArtId() {
            return speciesString("artId");
        }

        /** The artwork width in blocks.
         * @zh 画作宽度（格）。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Integer getWidth() {
            return speciesInt("width");
        }

        /** The artwork height in blocks.
         * @zh 画作高度（格）。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Integer getHeight() {
            return speciesInt("height");
        }
    }

    /** Display entity base: billboard, brightness, scaling, and shadow
     * settings shared by block/item/text displays.
     * @zh 展示实体基类：方块/物品/文本展示共享的公告板、亮度、缩放与阴影设置。 */
    @ApiClass(name = "DisplayEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.Display")
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class DisplayEntity extends EntityApi.Entity {

        /** Creates the handle for one display UUID. */
        public DisplayEntity(UUID uuid) {
            super(uuid);
        }

        /** The billboard mode ({@code fixed}, {@code vertical}, {@code horizontal},
         * or {@code center}).
         * @zh 公告板模式（{@code fixed}、{@code vertical}、{@code horizontal} 或 {@code center}）。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public String getBillboard() {
            return speciesString("billboard");
        }

        /** The packed light override, or undefined when following the world.
         * @zh 光照覆盖值（打包格式）；跟随世界光照时为 undefined。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Integer getBrightness() {
            return speciesInt("brightness");
        }

        /** The display width scale multiplier.
         * @zh 显示宽度缩放倍率。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Double getDisplayWidth() {
            return speciesDouble("displayWidth");
        }

        /** The display height scale multiplier.
         * @zh 显示高度缩放倍率。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Double getDisplayHeight() {
            return speciesDouble("displayHeight");
        }

        /** The glow color override as packed RGB, or undefined.
         * @zh 发光颜色覆盖（RGB 打包整数）；未覆盖时为 undefined。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Integer getGlowColorOverride() {
            return speciesInt("glowColorOverride");
        }

        /** The view range multiplier.
         * @zh 可见距离倍率。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Double getViewRange() {
            return speciesDouble("viewRange");
        }

        /** The shadow radius in blocks.
         * @zh 阴影半径（格）。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Double getShadowRadius() {
            return speciesDouble("shadowRadius");
        }

        /** The shadow strength from 0 to 1.
         * @zh 阴影强度（0-1）。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Double getShadowStrength() {
            return speciesDouble("shadowStrength");
        }

        /** The culling (visibility) box at the current position, sized by
         * the display width and height; undefined once the entity is gone.
         * @zh 当前位置的剔除（可见性）包围盒，按显示宽高取尺寸；实体消失后为 undefined。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public EntityApi.BoundingBox getVisibilityBoundingBox() {
            Object data = speciesState().get("visibilityBoundingBox");
            return data instanceof ink.tuanzi.neomacros.port.model.BoundingBoxData box
                    ? new EntityApi.BoundingBox(
                            new ink.tuanzi.neomacros.core.api.types.Pos3D(
                                    box.min().x(), box.min().y(), box.min().z()),
                            new ink.tuanzi.neomacros.core.api.types.Pos3D(
                                    box.max().x(), box.max().y(), box.max().z()))
                    : null;
        }
    }

    /** A block display: the rendered block id.
     * @zh 方块展示实体：所渲染的方块标识。 */
    @ApiClass(name = "BlockDisplayEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.Display$BlockDisplay")
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class BlockDisplayEntity extends DisplayEntity {

        /** Creates the handle for one block display UUID. */
        public BlockDisplayEntity(UUID uuid) {
            super(uuid);
        }

        /** The rendered block identifier.
         * @zh 渲染方块的标识。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public String getBlock() {
            return speciesString("block");
        }

        /** The full block state (identifier plus vanilla properties) the
         * display renders; undefined once the entity is gone.
         * @zh 展示的完整方块状态（标识符加原版属性）；实体消失后为 undefined。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public ink.tuanzi.neomacros.core.api.types.BlockState getBlockState() {
            Object data = speciesState().get("blockState");
            return data instanceof ink.tuanzi.neomacros.port.model.BlockData state
                    ? new ink.tuanzi.neomacros.core.api.types.BlockState(
                            state.id(), state.properties())
                    : null;
        }
    }

    /** An item display: the rendered stack.
     * @zh 物品展示实体：所渲染的物品堆。 */
    @ApiClass(name = "ItemDisplayEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.Display$ItemDisplay")
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class ItemDisplayEntity extends DisplayEntity {

        /** Creates the handle for one item display UUID. */
        public ItemDisplayEntity(UUID uuid) {
            super(uuid);
        }

        /** The rendered stack.
         * @zh 渲染的物品堆。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public ink.tuanzi.neomacros.core.api.types.ItemStack getItem() {
            Object data = speciesState().get("item");
            return data instanceof ink.tuanzi.neomacros.port.model.ItemStackData stack
                    ? ink.tuanzi.neomacros.core.api.types.ItemStack.of(stack) : null;
        }

        /** The serialized item display transform (e.g. {@code fixed},
         * {@code gui}, {@code ground}).
         * @zh 序列化的物品展示变换（如 {@code fixed}、{@code gui}、{@code ground}）。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public String getTransform() {
            return speciesString("transform");
        }
    }

    /** A text display: the rendered text and background.
     * @zh 文本展示实体：所渲染的文本与背景。 */
    @ApiClass(name = "TextDisplayEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.Display$TextDisplay")
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class TextDisplayEntity extends DisplayEntity {

        /** Creates the handle for one text display UUID. */
        public TextDisplayEntity(UUID uuid) {
            super(uuid);
        }

        /** The rendered plain text.
         * @zh 渲染的纯文本内容。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public String getText() {
            return speciesString("text");
        }

        /** The background color as packed ARGB.
         * @zh 背景颜色（ARGB 打包整数）。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Integer getBackgroundColor() {
            return speciesInt("backgroundColor");
        }

        /** The maximum line width in font pixels before wrapping
         * (the vanilla default is 200).
         * @zh 换行前的最大行宽（字体像素，原版默认 200）。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Integer getLineWidth() {
            return speciesInt("lineWidth");
        }

        /** The text opacity: 0 transparent, 255 opaque, -1 the opaque
         * default; affects the text, not the background.
         * @zh 文本不透明度：0 透明、255 不透明、-1 为不透明默认值；只影响文本，不影响背景。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Integer getTextOpacity() {
            return speciesInt("textOpacity");
        }

        /** Whether the text is drawn with a drop shadow.
         * @zh 文本是否带投影绘制。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean hasShadow() {
            return speciesBool("shadow");
        }

        /** Whether text and background use the see-through rendering mode.
         * @zh 文本与背景是否使用透视渲染模式。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean hasSeeThrough() {
            return speciesBool("seeThrough");
        }

        /** Whether the default background box is used.
         * @zh 是否使用默认背景框。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean hasDefaultBackground() {
            return speciesBool("defaultBackground");
        }

        /** The text alignment ({@code left}, {@code center} or
         * {@code right}); left wins when both alignment bits are set.
         * @zh 文本对齐方式（{@code left}、{@code center} 或 {@code right}）；两个对齐位同时设置时 left 优先。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public String getAlignment() {
            return speciesString("alignment");
        }
    }

    /** A slime (or magma cube): its size tier.
     * @zh 史莱姆（含岩浆怪）：体型档位。 */
    @ApiClass(name = "SlimeEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.monster.cubemob.Slime")
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class SlimeEntity extends MobEntity {

        /** Creates the handle for one slime UUID. */
        public SlimeEntity(UUID uuid) {
            super(uuid);
        }

        /** The size tier (1 for small, up to the natural maximum of 4).
         * @zh 体型档位（小为 1，自然最大为 4）。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Integer getSize() {
            return speciesInt("size");
        }
    }

    /** A guardian: the laser attack target.
     * @zh 守卫者：激光攻击目标。 */
    @ApiClass(name = "GuardianEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.monster.Guardian")
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class GuardianEntity extends MobEntity {

        /** Creates the handle for one guardian UUID. */
        public GuardianEntity(UUID uuid) {
            super(uuid);
        }

        /** The entity currently targeted by the laser, or undefined.
         * @zh 激光当前瞄准的实体；无目标时为 undefined。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public EntityApi.Entity getActiveTarget() {
            return speciesEntity("activeTarget");
        }
    }

    /** Illager base: the arm pose shared by pillagers, vindicators,
     * evokers, and witches.
     * @zh 灾厄村民基类：掠夺者、卫道士、唤魔者与女巫共享的手臂姿态。 */
    @ApiClass(name = "AbstractIllagerEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.monster.illager.AbstractIllager")
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class AbstractIllagerEntity extends MobEntity {

        /** Creates the handle for one illager UUID. */
        public AbstractIllagerEntity(UUID uuid) {
            super(uuid);
        }

        /** The current arm pose (e.g. {@code crossed}, {@code attacking},
         * {@code spellcasting}, {@code bow_and_arrow}, {@code celebrating},
         * {@code neutral}).
         * @zh 当前手臂姿态（如 {@code crossed}、{@code attacking}、{@code spellcasting}、{@code bow_and_arrow}、{@code celebrating}、{@code neutral}）。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public String getArmPose() {
            return speciesString("armPose");
        }
    }

    /** A spellcasting illager: spell casting state.
     * @zh 施法者灾厄村民：施法状态。 */
    @ApiClass(name = "SpellcasterIllagerEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.monster.illager.SpellcasterIllager")
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class SpellcasterIllagerEntity extends AbstractIllagerEntity {

        /** Creates the handle for one spellcaster UUID. */
        public SpellcasterIllagerEntity(UUID uuid) {
            super(uuid);
        }

        /** Whether a spell is currently being cast.
         * @zh 当前是否正在施法。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isCastingSpell() {
            return speciesBool("castingSpell");
        }
    }

    /** A vindicator (the {@code johnny} flag has no client accessor).
     * @zh 卫道士（{@code johnny} 标记无客户端访问器）。 */
    @ApiClass(name = "VindicatorEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.monster.illager.Vindicator")
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class VindicatorEntity extends AbstractIllagerEntity {

        /** Creates the handle for one vindicator UUID. */
        public VindicatorEntity(UUID uuid) {
            super(uuid);
        }
    }

    /** A pig: saddle state.
     * @zh 猪：鞍状态。 */
    @ApiClass(name = "PigEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.animal.pig.Pig")
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class PigEntity extends AnimalEntity {

        /** Creates the handle for one pig UUID. */
        public PigEntity(UUID uuid) {
            super(uuid);
        }

        /** Whether a saddle is currently equipped.
         * @zh 当前是否已装备鞍。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isSaddled() {
            return speciesBool("saddled");
        }
    }
}
