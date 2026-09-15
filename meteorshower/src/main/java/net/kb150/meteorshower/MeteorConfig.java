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

import java.util.List;

public class MeteorConfig {
    
    public static double METEORS_PER_100_CHUNKS = 2.0; 
    public static int MIN_METEORS = 5;
    public static int MAX_METEORS = 100;

    // Wie oft spawnt der exklusive Mittelblock (z.B. in jedem 5. Meteor)
    public static int RARE_BLOCK_INTERVAL = 5; 
public static int EVENT_START_DELAY = -1;
    public enum RewardType { BLOCK, ENTITY }

    public record RareReward(RewardType type, String id, String nbt, int weight) {}

    // Pool für das exklusive Zentrum (Gewichtung steuert die Chance)
    public static final List<RareReward> RARE_CORE_REWARDS = List.of(
        // Wertvoller Hauptblock mit hohem Gewicht (z. B. Gewicht 20)
        new RareReward(RewardType.BLOCK, "stones:runestone", null, 20),

        // Dracheneier mit jeweils Gewicht 1 (8 Eier = Gesamtwahrscheinlichkeit 8)
        new RareReward(RewardType.ENTITY, "bookofdragons:dragon_egg", "{DragonType:\"hideous_zippleback\",RequiredActivationTime:1200,TotalHatchTime:2400,CurrentHatchTime:2400}", 1),
        new RareReward(RewardType.ENTITY, "bookofdragons:dragon_egg", "{DragonType:\"monstrous_nightmare\",RequiredActivationTime:1200,TotalHatchTime:2400,CurrentHatchTime:2400}", 1),
        new RareReward(RewardType.ENTITY, "bookofdragons:dragon_egg", "{DragonType:\"deadly_nadder\",RequiredActivationTime:1200,TotalHatchTime:2400,CurrentHatchTime:2400}", 1),
        new RareReward(RewardType.ENTITY, "bookofdragons:dragon_egg", "{DragonType:\"gronckle\",RequiredActivationTime:1200,TotalHatchTime:2400,CurrentHatchTime:2400}", 1),
        new RareReward(RewardType.ENTITY, "bookofdragons:dragon_egg", "{DragonType:\"nightfury\",RequiredActivationTime:1200,TotalHatchTime:2400,CurrentHatchTime:2400}", 1),
        new RareReward(RewardType.ENTITY, "bookofdragons:dragon_egg", "{DragonType:\"skrill\",RequiredActivationTime:1200,TotalHatchTime:2400,CurrentHatchTime:2400}", 1),
        new RareReward(RewardType.ENTITY, "bookofdragons:dragon_egg", "{DragonType:\"whispering_death\",RequiredActivationTime:1200,TotalHatchTime:2400,CurrentHatchTime:2400}", 1)
    );

    public static final List<String> CORE_BLOCKS = List.of(
        "mysticalagriculture:soulium_block",
        "minecraft:ancient_debris",
        "minecraft:gold_block"
    );

    public static final List<String> INNER_SHELL = List.of(
        "minecraft:magma_block",
        "minecraft:obsidian",
        "minecraft:crying_obsidian"
    );

    public static final List<String> OUTER_SHELL = List.of(
        "minecraft:netherrack",
        "minecraft:basalt",
        "minecraft:tuff"
    );

    /**
     * Wählt gewichtet EINE Belohnung aus und setzt sie exakt in die Kratermitte.
     */
    public static void spawnRareCoreReward(ServerLevel level, BlockPos pos, RandomSource random) {
        int totalWeight = RARE_CORE_REWARDS.stream().mapToInt(RareReward::weight).sum();
        if (totalWeight <= 0) return;

        int roll = random.nextInt(totalWeight);
        RareReward selected = RARE_CORE_REWARDS.get(0);

        for (RareReward reward : RARE_CORE_REWARDS) {
            roll -= reward.weight();
            if (roll < 0) {
                selected = reward;
                break;
            }
        }

        if (selected.type() == RewardType.BLOCK) {
            ResourceLocation rl = new ResourceLocation(selected.id());
            Block block = BuiltInRegistries.BLOCK.getOptional(rl).orElse(Blocks.OBSIDIAN);
            level.setBlock(pos, block.defaultBlockState(), 3);
        } else if (selected.type() == RewardType.ENTITY) {
            // Fester Untergrund für das Ei
            level.setBlock(pos, Blocks.MAGMA_BLOCK.defaultBlockState(), 3);
            
            try {
                CompoundTag nbt = selected.nbt() != null ? TagParser.parseTag(selected.nbt()) : new CompoundTag();
                nbt.putString("id", selected.id());

                Entity entity = EntityType.loadEntityRecursive(nbt, level, e -> {
                    e.moveTo(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
                    return e;
                });

                if (entity != null) {
                    level.addFreshEntity(entity);
                }
            } catch (Exception e) {
                MeteorShower.LOGGER.error("Fehler beim Spawnen der seltsamen Kern-Belohnung: " + selected.id(), e);
            }
        }
    }

    public static Block getRandomBlock(List<String> list, RandomSource random) {
        if (list.isEmpty()) return Blocks.OBSIDIAN;
        String randomId = list.get(random.nextInt(list.size()));
        return BuiltInRegistries.BLOCK.getOptional(new ResourceLocation(randomId)).orElse(Blocks.OBSIDIAN);
    }
}