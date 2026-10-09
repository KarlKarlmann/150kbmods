package net.kb150.grubies.item;

import net.kb150.grubies.GrubiesMod;
import net.kb150.grubies.config.GrubiesConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

public class GrubItem extends Item {
    public GrubItem() {
        super(new Item.Properties().food(new FoodProperties.Builder().nutrition(1).saturationMod(0.1f).fast().alwaysEat().build()));
    }

    @Override
    public Component getName(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains("OriginBlock")) {
            ResourceLocation id = ResourceLocation.tryParse(tag.getString("OriginBlock"));
            if (id != null) return Component.translatable("item.grubies.grub.formatted", BuiltInRegistries.BLOCK.get(id).getName());
        }
        return super.getName(stack);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        ItemStack result = super.finishUsingItem(stack, level, entity);

        if (!level.isClientSide() && level instanceof ServerLevel serverLevel && entity instanceof ServerPlayer player) {
            CompoundTag tag = stack.getTag();
            String originBlock = (tag != null && tag.contains("OriginBlock")) ? tag.getString("OriginBlock") : "minecraft:dirt";

            int seed2 = Math.abs(originBlock.hashCode()) % 65536;
            int ampA = (seed2 >> 8) & 0xFF, ampB = seed2 & 0xFF;
            int newDuration = 600 + (seed2 % 1200);

            MobEffectInstance activeA = player.getEffect(GrubiesMod.TRIP_EFFECT_A.get());
            MobEffectInstance activeB = player.getEffect(GrubiesMod.TRIP_EFFECT_B.get());

            if (activeA != null && activeB != null) {
                int seed1 = (activeA.getAmplifier() << 8) | activeB.getAmplifier();
                
                // Restdauer in 15s-Buckets quantisieren, damit der Hash bei gleichem Wurm innerhalb dieses Zeitfensters stabil bleibt
                long timeBucket = activeA.getDuration() / 300;
                long combinedHash = Math.abs((serverLevel.getSeed() ^ seed1 ^ seed2 ^ timeBucket) * 31L);
                
                triggerRandomMobEffect(player, combinedHash, newDuration);

                // Zwingt Vanilla zum sofortigen Re-Sync der Byte-Amplifier ohne Laufzeit-Aggregation
                player.removeEffect(GrubiesMod.TRIP_EFFECT_A.get());
                player.removeEffect(GrubiesMod.TRIP_EFFECT_B.get());
            }

            player.addEffect(new MobEffectInstance(GrubiesMod.TRIP_EFFECT_A.get(), newDuration, ampA, false, true, true));
            player.addEffect(new MobEffectInstance(GrubiesMod.TRIP_EFFECT_B.get(), newDuration, ampB, false, false, false));

            GrubiesMod.debug("EAT", "Player: {} | OriginBlock: {} | NewSeed: {} | ResetDur: {}t", 
                    player.getName().getString(), originBlock, seed2, newDuration);
        }
        return result;
    }

    private void triggerRandomMobEffect(ServerPlayer player, long hash, int duration) {
        List<MobEffect> pool = ForgeRegistries.MOB_EFFECTS.getValues().stream()
            .filter(e -> e != GrubiesMod.TRIP_EFFECT_A.get() && e != GrubiesMod.TRIP_EFFECT_B.get())
            .filter(e -> !GrubiesConfig.isEffectBlacklisted(ForgeRegistries.MOB_EFFECTS.getKey(e)))
            .toList();

        if (pool.isEmpty()) return;

        MobEffect chosen = pool.get((int) (hash % pool.size()));
        int amp = (int) (hash % 3);

        MobEffectInstance cur = player.getEffect(chosen);
        
        // Guenstigkeitsprinzip: Spieler behaelt immer den jeweils hoechsten Wert, ohne künstlichen Stufenanstieg
        int finalAmp = cur != null ? Math.max(cur.getAmplifier(), amp) : amp;
        int finalDur = cur != null ? Math.max(cur.getDuration(), duration) : duration;

        player.addEffect(new MobEffectInstance(chosen, finalDur, finalAmp));
        
        GrubiesMod.debug("EAT", "COMBO EFFECT! Chosen: {} | FinalDur: {}t | FinalAmp: {}", 
                ForgeRegistries.MOB_EFFECTS.getKey(chosen), finalDur, finalAmp);
    }
}