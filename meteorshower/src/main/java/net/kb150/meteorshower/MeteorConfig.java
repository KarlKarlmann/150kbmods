package net.kb150.meteorshower;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Mod.EventBusSubscriber(modid = MeteorShower.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class MeteorConfig {

    public static final ForgeConfigSpec SERVER_SPEC;

    public static final ForgeConfigSpec.DoubleValue METEORS_PER_100_CHUNKS;
    public static final ForgeConfigSpec.IntValue MIN_METEORS;
    public static final ForgeConfigSpec.IntValue MAX_METEORS;
    public static final ForgeConfigSpec.IntValue EVENT_START_DELAY;

    public static final ForgeConfigSpec.IntValue RARE_BLOCK_INTERVAL;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> RARE_REWARDS;

    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> CORE_BLOCKS_CFG;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> INNER_SHELL_CFG;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> OUTER_SHELL_CFG;

    // Gecachte Primitiven verhindern Reflection-Overhead und NullPointer vor dem Laden der Welt-Instanz
    public static double meteorsPer100Chunks = 2.0;
    public static int minMeteors = 5;
    public static int maxMeteors = 100;
    public static int eventStartDelay = -1;
    public static int rareBlockInterval = 5;

    public static final List<String> CORE_BLOCKS = new ArrayList<>();
    public static final List<String> INNER_SHELL = new ArrayList<>();
    public static final List<String> OUTER_SHELL = new ArrayList<>();
    public static final List<RareReward> RARE_CORE_REWARDS = new ArrayList<>();

    public enum RewardType { BLOCK, ENTITY }

    public record RareReward(RewardType type, String id, String nbt, int weight) {}

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.push("Event Settings");
        METEORS_PER_100_CHUNKS = b.comment("Menge an Meteoren pro 100 generierter Chunks in den Regionsdateien.")
                .defineInRange("meteors_per_100_chunks", 2.0, 0.01, 50.0);

        MIN_METEORS = b.comment("Mindestanzahl an Meteoren pro Schauer.")
                .defineInRange("min_meteors", 5, 1, 1000);

        MAX_METEORS = b.comment("Maximale Obergrenze zur Absicherung dedizierter Server gegen Überlastung.")
                .defineInRange("max_meteors", 100, 1, 5000);

        EVENT_START_DELAY = b.comment("Verzögerung in Ticks bis zum automatischen Start. -1 = Start nur manuell per /meteor resetEvent.")
                .defineInRange("event_start_delay", -1, -1, 720000);
        b.pop();

        b.push("Structure");
        RARE_BLOCK_INTERVAL = b.comment("Intervall für seltene Kernblöcke (z. B. 1 = jeder Meteor, 5 = jeder 5. Meteor).")
                .defineInRange("rare_block_interval", 5, 1, 100);

        CORE_BLOCKS_CFG = b.comment("Blöcke für die innere Glutschicht.")
                .defineList("core_blocks", List.of(
                        "mysticalagriculture:soulium_block",
                        "minecraft:ancient_debris",
                        "minecraft:gold_block"
                ), s -> s instanceof String);

        INNER_SHELL_CFG = b.comment("Pufferschicht zwischen Kern und Kraterwand.")
                .defineList("inner_shell", List.of(
                        "minecraft:magma_block",
                        "minecraft:obsidian",
                        "minecraft:crying_obsidian"
                ), s -> s instanceof String);

        OUTER_SHELL_CFG = b.comment("Materialien für Trümmerwurf und Kraterrand.")
                .defineList("outer_shell", List.of(
                        "minecraft:netherrack",
                        "minecraft:basalt",
                        "minecraft:tuff"
                ), s -> s instanceof String);
        b.pop();

        b.push("Rare Rewards");
        RARE_REWARDS = b.comment("Format: TYPE;ID;GEWICHT;NBT (Type = BLOCK oder ENTITY, NBT optional)")
                .defineList("rare_rewards", List.of(
                        "BLOCK;stones:runestone;20;",
                        "ENTITY;bookofdragons:dragon_egg;1;{DragonType:\"hideous_zippleback\",RequiredActivationTime:1200,TotalHatchTime:2400,CurrentHatchTime:2400}",
                        "ENTITY;bookofdragons:dragon_egg;1;{DragonType:\"monstrous_nightmare\",RequiredActivationTime:1200,TotalHatchTime:2400,CurrentHatchTime:2400}",
                        "ENTITY;bookofdragons:dragon_egg;1;{DragonType:\"deadly_nadder\",RequiredActivationTime:1200,TotalHatchTime:2400,CurrentHatchTime:2400}",
                        "ENTITY;bookofdragons:dragon_egg;1;{DragonType:\"gronckle\",RequiredActivationTime:1200,TotalHatchTime:2400,CurrentHatchTime:2400}",
                        "ENTITY;bookofdragons:dragon_egg;1;{DragonType:\"nightfury\",RequiredActivationTime:1200,TotalHatchTime:2400,CurrentHatchTime:2400}",
                        "ENTITY;bookofdragons:dragon_egg;1;{DragonType:\"skrill\",RequiredActivationTime:1200,TotalHatchTime:2400,CurrentHatchTime:2400}",
                        "ENTITY;bookofdragons:dragon_egg;1;{DragonType:\"whispering_death\",RequiredActivationTime:1200,TotalHatchTime:2400,CurrentHatchTime:2400}"
                ), s -> s instanceof String);
        b.pop();

        SERVER_SPEC = b.build();
    }

    @SubscribeEvent
    public static void onLoad(final ModConfigEvent.Loading ev) {
        if (ev.getConfig().getSpec() == SERVER_SPEC) bake();
    }

    @SubscribeEvent
    public static void onReload(final ModConfigEvent.Reloading ev) {
        if (ev.getConfig().getSpec() == SERVER_SPEC) bake();
    }

    public static void bake() {
        meteorsPer100Chunks = METEORS_PER_100_CHUNKS.get();
        minMeteors = MIN_METEORS.get();
        maxMeteors = MAX_METEORS.get();
        eventStartDelay = EVENT_START_DELAY.get();
        rareBlockInterval = RARE_BLOCK_INTERVAL.get();

        CORE_BLOCKS.clear();
        CORE_BLOCKS.addAll(CORE_BLOCKS_CFG.get());
        INNER_SHELL.clear();
        INNER_SHELL.addAll(INNER_SHELL_CFG.get());
        OUTER_SHELL.clear();
        OUTER_SHELL.addAll(OUTER_SHELL_CFG.get());

        RARE_CORE_REWARDS.clear();
        for (String raw : RARE_REWARDS.get()) {
            String[] p = raw.split(";", 4);
            if (p.length < 3) {
                MeteorShower.LOGGER.warn("[Config] Unvollständiger Belohnungseintrag verworfen: '{}'", raw);
                continue;
            }
            try {
                RewardType type = RewardType.valueOf(p[0].trim().toUpperCase(Locale.ROOT));
                String id = p[1].trim();
                int weight = Integer.parseInt(p[2].trim());
                String nbt = (p.length >= 4 && !p[3].trim().isEmpty()) ? p[3].trim() : null;
                RARE_CORE_REWARDS.add(new RareReward(type, id, nbt, weight));
            } catch (Exception e) {
                MeteorShower.LOGGER.error("[Config] Syntaxfehler in Belohnung '{}': {}", raw, e.getMessage());
            }
        }

        MeteorShower.LOGGER.info("[Config] Server-Config synchronisiert: {} Kernbelohnungen aktiv, Seltenheits-Takt: 1/{}",
                RARE_CORE_REWARDS.size(), rareBlockInterval);
    }

    public static void spawnRareCoreReward(ServerLevel level, BlockPos pos, RandomSource random) {
        if (RARE_CORE_REWARDS.isEmpty()) bake();

        int totalWeight = RARE_CORE_REWARDS.stream().mapToInt(RareReward::weight).sum();
        if (totalWeight <= 0) return;

        int roll = random.nextInt(totalWeight);
        RareReward sel = RARE_CORE_REWARDS.get(0);
        for (RareReward r : RARE_CORE_REWARDS) {
            roll -= r.weight();
            if (roll < 0) {
                sel = r;
                break;
            }
        }

        if (sel.type() == RewardType.BLOCK) {
            Block b = BuiltInRegistries.BLOCK.getOptional(new ResourceLocation(sel.id())).orElse(Blocks.OBSIDIAN);
            level.setBlock(pos, b.defaultBlockState(), 3);
            MeteorShower.LOGGER.info("[MeteorShower] Seltener Kraterblock generiert bei {}: {}", pos, sel.id());
        } else if (sel.type() == RewardType.ENTITY) {
            // Fester Hitzeschild unter der Entity verhindert sofortiges Ersticken im Kratergrund
            level.setBlock(pos, Blocks.MAGMA_BLOCK.defaultBlockState(), 3);
            try {
                CompoundTag tag = sel.nbt() != null ? TagParser.parseTag(sel.nbt()) : new CompoundTag();
                tag.putString("id", sel.id());

                Entity ent = EntityType.loadEntityRecursive(tag, level, e -> {
                    e.moveTo(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
                    return e;
                });

                if (ent != null) {
                    level.addFreshEntity(ent);
                    MeteorShower.LOGGER.info("[MeteorShower] Seltene Krater-Entity platziert bei {}: {}", pos, sel.id());
                }
            } catch (Exception e) {
                MeteorShower.LOGGER.error("[MeteorShower] Fehler beim Instanziieren der Entity: " + sel.id(), e);
            }
        }
    }

    public static Block getRandomBlock(List<String> list, RandomSource random) {
        if (list == null || list.isEmpty()) return Blocks.OBSIDIAN;
        return BuiltInRegistries.BLOCK.getOptional(new ResourceLocation(list.get(random.nextInt(list.size())))).orElse(Blocks.OBSIDIAN);
    }
}