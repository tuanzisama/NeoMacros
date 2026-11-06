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
 * The species handle tree: Mojang-named guest subclasses over the living
 * entity hierarchy, one class per species carrying that species' gap
 * members. Registration is data-driven ({@link #registry()}); the facade
 * consults it when wrapping so queries return the deepest species handle.
 * Intermediate Mojang classes without gap members stay structural.
 *
 * <p>Members read live species state through the entity port; a vanished
 * entity reads back as {@code undefined} like every other handle.</p>
 *
 * @zh 物种句柄树：活实体层级之上的 Mojang 命名 guest 子类，每物种一类、携带该物种的缺口成员。注册数据驱动（{@link #registry()}），门面包装时查表返回最深的物种句柄；无缺口成员的 Mojang 中间类保持结构性存在。成员经实体端口实时读取；实体消失后属性回读为 undefined，与其他句柄一致。
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

    /** Static holder: the JVM loads the tree once. */
    private static final class Registry {
        private static final Map<String, Function<UUID, EntityApi.Entity>> HANDLES =
                build();

        private static Map<String, Function<UUID, EntityApi.Entity>> build() {
            Map<String, Function<UUID, EntityApi.Entity>> map = new LinkedHashMap<>();
            put(map, "minecraft:bat", Bat::new);
            put(map, "minecraft:cat", Cat::new);
            put(map, "minecraft:creeper", Creeper::new);
            put(map, "minecraft:mooshroom", MushroomCow::new);
            put(map, "minecraft:sheep", Sheep::new);
            put(map, "minecraft:tnt", PrimedTnt::new);
            put(map, "minecraft:villager", Villager::new);
            put(map, "minecraft:wolf", Wolf::new);
            put(map, "minecraft:zombie", Zombie::new);
            put(map, "minecraft:donkey", Donkey::new);
            put(map, "minecraft:enderman", Enderman::new);
            put(map, "minecraft:horse", Horse::new);
            put(map, "minecraft:iron_golem", IronGolem::new);
            put(map, "minecraft:llama", Llama::new);
            put(map, "minecraft:phantom", Phantom::new);
            put(map, "minecraft:spider", Spider::new);
            put(map, "minecraft:zombified_piglin", ZombifiedPiglin::new);
            put(map, "minecraft:zombie_villager", ZombieVillager::new);
            put(map, "minecraft:drowned", Drowned::new);
            put(map, "minecraft:warden", Warden::new);
            put(map, "minecraft:dolphin", Dolphin::new);
            put(map, "minecraft:witch", Witch::new);
            put(map, "minecraft:vex", Vex::new);
            put(map, "minecraft:turtle", Turtle::new);
            put(map, "minecraft:snow_golem", SnowGolem::new);
            put(map, "minecraft:shulker", Shulker::new);
            put(map, "minecraft:polar_bear", PolarBear::new);
            put(map, "minecraft:parrot", Parrot::new);
            put(map, "minecraft:panda", Panda::new);
            put(map, "minecraft:goat", Goat::new);
            put(map, "minecraft:frog", Frog::new);
            put(map, "minecraft:fox", Fox::new);
            put(map, "minecraft:bee", Bee::new);
            put(map, "minecraft:axolotl", Axolotl::new);
            put(map, "minecraft:chicken", Chicken::new);
            put(map, "minecraft:ghast", Ghast::new);
            put(map, "minecraft:ocelot", Ocelot::new);
            put(map, "minecraft:rabbit", Rabbit::new);
            put(map, "minecraft:strider", Strider::new);
            put(map, "minecraft:wither", WitherBoss::new);
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
    @ApiClass(name = "Mob", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.Mob")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class Mob extends EntityApi.LivingEntity {

        /** Creates the handle for one mob UUID. */
        public Mob(UUID uuid) {
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
    @ApiClass(name = "Animal", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.animal.Animal")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class Animal extends Mob {

        /** Creates the handle for one animal UUID. */
        public Animal(UUID uuid) {
            super(uuid);
        }
    }

    /**
     * The tameable base (wolves, cats): ownership and sitting state land
     * here with the tameable gap batch.
     *
     * @zh 可驯化基类（狼、猫）：归属与坐下状态随驯化缺口批落地。
     */
    @ApiClass(name = "TamableAnimal", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.TamableAnimal")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class TamableAnimal extends Animal {

        /** Creates the handle for one tameable UUID. */
        public TamableAnimal(UUID uuid) {
            super(uuid);
        }
    }

    /**
     * A sheep: wool color and sheared state.
     *
     * @zh 绵羊：羊毛颜色与剪毛状态。
     */
    @ApiClass(name = "Sheep", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.animal.sheep.Sheep")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class Sheep extends Animal {

        /** Creates the handle for one sheep UUID. */
        public Sheep(UUID uuid) {
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
    }

    /**
     * A wolf: collar color. Anger and begging state ride the tameable gap
     * batch (the vanilla accessors moved behind the wolf AI profile).
     *
     * @zh 狼：项圈颜色。愤怒与乞食状态随驯化缺口批（原版访问器已移入 wolf AI 配置）。
     */
    @ApiClass(name = "Wolf", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.animal.wolf.Wolf")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class Wolf extends TamableAnimal {

        /** Creates the handle for one wolf UUID. */
        public Wolf(UUID uuid) {
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
    }

    /**
     * A cat: collar color and coat variant.
     *
     * @zh 猫：项圈颜色与花色变体。
     */
    @ApiClass(name = "Cat", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.animal.feline.Cat")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class Cat extends TamableAnimal {

        /** Creates the handle for one cat UUID. */
        public Cat(UUID uuid) {
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
    @ApiClass(name = "Villager", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.npc.villager.Villager")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class Villager extends Mob {

        /** Creates the handle for one villager UUID. */
        public Villager(UUID uuid) {
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
    @ApiClass(name = "Creeper", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.monster.Creeper")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class Creeper extends Mob {

        /** Creates the handle for one creeper UUID. */
        public Creeper(UUID uuid) {
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
    }

    /**
     * A zombie: drowning conversion state.
     *
     * @zh 僵尸：溺水转化状态。
     */
    @ApiClass(name = "Zombie", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.monster.zombie.Zombie")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class Zombie extends Mob {

        /** Creates the handle for one zombie UUID. */
        public Zombie(UUID uuid) {
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
    @ApiClass(name = "PrimedTnt", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.item.PrimedTnt")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class PrimedTnt extends EntityApi.Entity {

        /** Creates the handle for one primed TNT UUID. */
        public PrimedTnt(UUID uuid) {
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
    @ApiClass(name = "Bat", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.ambient.Bat")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class Bat extends Mob {

        /** Creates the handle for one bat UUID. */
        public Bat(UUID uuid) {
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
    @ApiClass(name = "MushroomCow", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.animal.cow.MushroomCow")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class MushroomCow extends Animal {

        /** Creates the handle for one mooshroom UUID. */
        public MushroomCow(UUID uuid) {
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
    }

    /**
     * An enderman: the block it currently carries as an id string, or
     * undefined.
     *
     * @zh 末影人：当前搬起的方块 id 字符串；未搬起为 undefined。
     */
    @ApiClass(name = "Enderman", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.monster.Enderman")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class Enderman extends Mob {

        /** Creates the handle for one enderman UUID. */
        public Enderman(UUID uuid) {
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
    }

    /** A phantom: its size tier.
     * @zh 幻翼：体型档位。 */
    @ApiClass(name = "Phantom", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.monster.Phantom")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class Phantom extends Mob {

        /** Creates the handle for one phantom UUID. */
        public Phantom(UUID uuid) {
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
    @ApiClass(name = "Spider", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.monster.spider.Spider")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class Spider extends Mob {

        /** Creates the handle for one spider UUID. */
        public Spider(UUID uuid) {
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
    @ApiClass(name = "AbstractHorse", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.animal.equine.AbstractHorse")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class AbstractHorse extends Animal {

        /** Creates the handle for one horse UUID. */
        public AbstractHorse(UUID uuid) {
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
    }

    /** A horse: coat variant.
     * @zh 马：毛色变体。 */
    @ApiClass(name = "Horse", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.animal.equine.Horse")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class Horse extends AbstractHorse {

        /** Creates the handle for one horse UUID. */
        public Horse(UUID uuid) {
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
    @ApiClass(name = "AbstractChestedHorse", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.animal.equine.AbstractChestedHorse")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class AbstractChestedHorse extends AbstractHorse {

        /** Creates the handle for one chested horse UUID. */
        public AbstractChestedHorse(UUID uuid) {
            super(uuid);
        }
    }

    /** A donkey.
     * @zh 驴。 */
    @ApiClass(name = "Donkey", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.animal.equine.Donkey")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class Donkey extends AbstractChestedHorse {

        /** Creates the handle for one donkey UUID. */
        public Donkey(UUID uuid) {
            super(uuid);
        }
    }

    /** A llama: strength tier and variant.
     * @zh 羊驼：力量档位与变体。 */
    @ApiClass(name = "Llama", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.animal.equine.Llama")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class Llama extends AbstractChestedHorse {

        /** Creates the handle for one llama UUID. */
        public Llama(UUID uuid) {
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
    @ApiClass(name = "IronGolem", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.animal.golem.IronGolem")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class IronGolem extends Animal {

        /** Creates the handle for one iron golem UUID. */
        public IronGolem(UUID uuid) {
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
    @ApiClass(name = "Chicken", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.animal.chicken.Chicken")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class Chicken extends Animal {

        /** Creates the handle for one chicken UUID. */
        public Chicken(UUID uuid) {
            super(uuid);
        }
    }

    /** A ghast: fire charging state.
     * @zh 恶魂：喷火蓄力状态。 */
    @ApiClass(name = "Ghast", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.monster.Ghast")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class Ghast extends Mob {

        /** Creates the handle for one ghast UUID. */
        public Ghast(UUID uuid) {
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
    @ApiClass(name = "Ocelot", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.animal.feline.Ocelot")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class Ocelot extends Animal {

        /** Creates the handle for one ocelot UUID. */
        public Ocelot(UUID uuid) {
            super(uuid);
        }

    }

    /** A rabbit: its variant id.
     * @zh 兔子：变体标识。 */
    @ApiClass(name = "Rabbit", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.animal.rabbit.Rabbit")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class Rabbit extends Animal {

        /** Creates the handle for one rabbit UUID. */
        public Rabbit(UUID uuid) {
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
    @ApiClass(name = "Strider", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.monster.strider.Strider")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class Strider extends Mob {

        /** Creates the handle for one strider UUID. */
        public Strider(UUID uuid) {
            super(uuid);
        }

    }

    /** A wither boss: invulnerable armor phase state.
     * @zh 凋灵：无敌护甲阶段状态。 */
    @ApiClass(name = "WitherBoss", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.boss.wither.WitherBoss")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class WitherBoss extends Mob {

        /** Creates the handle for one wither UUID. */
        public WitherBoss(UUID uuid) {
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
    @ApiClass(name = "Axolotl", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.animal.axolotl.Axolotl")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class Axolotl extends Animal {

        /** Creates the handle for one axolotl UUID. */
        public Axolotl(UUID uuid) {
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
    @ApiClass(name = "Bee", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.animal.bee.Bee")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class Bee extends Animal {

        /** Creates the handle for one bee UUID. */
        public Bee(UUID uuid) {
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
    }

    /** A fox: sitting state.
     * @zh 狐狸：坐下状态。 */
    @ApiClass(name = "Fox", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.animal.fox.Fox")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class Fox extends Animal {

        /** Creates the handle for one fox UUID. */
        public Fox(UUID uuid) {
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
    }

    /** A frog: variant.
     * @zh 青蛙：变体。 */
    @ApiClass(name = "Frog", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.animal.frog.Frog")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class Frog extends Animal {

        /** Creates the handle for one frog UUID. */
        public Frog(UUID uuid) {
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
    }

    /** A goat: horn state and screaming variant.
     * @zh 山羊：角状态与尖叫变体。 */
    @ApiClass(name = "Goat", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.animal.goat.Goat")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class Goat extends Animal {

        /** Creates the handle for one goat UUID. */
        public Goat(UUID uuid) {
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
    @ApiClass(name = "Panda", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.animal.panda.Panda")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class Panda extends Animal {

        /** Creates the handle for one panda UUID. */
        public Panda(UUID uuid) {
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
    }

    /** A parrot: variant.
     * @zh 鹦鹉：变体。 */
    @ApiClass(name = "Parrot", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.animal.parrot.Parrot")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class Parrot extends Animal {

        /** Creates the handle for one parrot UUID. */
        public Parrot(UUID uuid) {
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
    }

    /** A polar bear: standing state.
     * @zh 北极熊：站立状态。 */
    @ApiClass(name = "PolarBear", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.animal.polarbear.PolarBear")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class PolarBear extends Animal {

        /** Creates the handle for one polar bear UUID. */
        public PolarBear(UUID uuid) {
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
    @ApiClass(name = "Shulker", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.monster.Shulker")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class Shulker extends Mob {

        /** Creates the handle for one shulker UUID. */
        public Shulker(UUID uuid) {
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
    }

    /** A snow golem: pumpkin hat state.
     * @zh 雪傀儡：南瓜头状态。 */
    @ApiClass(name = "SnowGolem", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.animal.golem.SnowGolem")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class SnowGolem extends Mob {

        /** Creates the handle for one snow golem UUID. */
        public SnowGolem(UUID uuid) {
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
    }

    /** A turtle: egg-laying state.
     * @zh 海龟：产卵状态。 */
    @ApiClass(name = "Turtle", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.animal.turtle.Turtle")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class Turtle extends Animal {

        /** Creates the handle for one turtle UUID. */
        public Turtle(UUID uuid) {
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
    }

    /** A vex: charging attack state.
     * @zh 恼鬼：冲锋攻击状态。 */
    @ApiClass(name = "Vex", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.monster.Vex")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class Vex extends Mob {

        /** Creates the handle for one vex UUID. */
        public Vex(UUID uuid) {
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
    @ApiClass(name = "Witch", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.monster.Witch")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class Witch extends Mob {

        /** Creates the handle for one witch UUID. */
        public Witch(UUID uuid) {
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
    @ApiClass(name = "Dolphin", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.animal.dolphin.Dolphin")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class Dolphin extends Mob {

        /** Creates the handle for one dolphin UUID. */
        public Dolphin(UUID uuid) {
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
    }

    /** A warden: anger level.
     * @zh 监守者：愤怒等级。 */
    @ApiClass(name = "Warden", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.monster.warden.Warden")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class Warden extends Mob {

        /** Creates the handle for one warden UUID. */
        public Warden(UUID uuid) {
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
    @ApiClass(name = "Drowned", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.monster.zombie.Drowned")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class Drowned extends Zombie {

        /** Creates the handle for one drowned UUID. */
        public Drowned(UUID uuid) {
            super(uuid);
        }
    }

    /** A zombie villager: profession state rides the trade batch.
     * @zh 僵尸村民：职业状态随交易批落地。 */
    @ApiClass(name = "ZombieVillager", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.monster.zombie.ZombieVillager")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class ZombieVillager extends Zombie {

        /** Creates the handle for one zombie villager UUID. */
        public ZombieVillager(UUID uuid) {
            super(uuid);
        }
    }

    /** A zombified piglin.
     * @zh 僵尸猪灵。 */
    @ApiClass(name = "ZombifiedPiglin", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.monster.zombie.ZombifiedPiglin")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class ZombifiedPiglin extends Zombie {

        /** Creates the handle for one zombified piglin UUID. */
        public ZombifiedPiglin(UUID uuid) {
            super(uuid);
        }
    }
}
