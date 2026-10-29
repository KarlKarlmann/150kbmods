package net.kb150.woerp.event;

import net.kb150.woerp.Woerp;
import net.kb150.woerp.capability.ChunkRadiationProvider;
import net.kb150.woerp.effect.WoerpMutationEffect;
import net.kb150.woerp.init.ModEffects;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

@Mod.EventBusSubscriber(modid = Woerp.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class MutationSpawnHandler {

    @SubscribeEvent
    public static void onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Mob mob = event.getEntity();

        if (mob instanceof WaterAnimal 
                || mob.getType().getCategory() == MobCategory.WATER_CREATURE 
                || mob.getType().getCategory() == MobCategory.WATER_AMBIENT 
                || mob.getType().getCategory() == MobCategory.UNDERGROUND_WATER_CREATURE) return;

        if (!mob.canChangeDimensions() || mob instanceof WitherBoss) return;

        ChunkPos cPos = new ChunkPos(mob.blockPosition());
        LevelChunk chunk = level.getChunkSource().getChunkNow(cPos.x, cPos.z);
        if (chunk == null) return;

        chunk.getCapability(ChunkRadiationProvider.RADIATION_CAP).ifPresent(cap -> {
            float ambient = cap.getAmbient();
            if (ambient < 50.0f) return;

            int count = (ambient >= 450.0f) ? 3 + (level.random.nextBoolean() ? 1 : 0) :
                        (ambient >= 300.0f) ? 2 + (level.random.nextBoolean() ? 1 : 0) :
                        (ambient >= 150.0f) ? 1 + (level.random.nextBoolean() ? 1 : 0) : 1;

            int amplifier = Math.min(3, (int) (ambient / 125.0f));

            List<RegistryObject<MobEffect>> pool = new ArrayList<>(ModEffects.ALL_MUTATIONS);
            Collections.shuffle(pool, new Random(mob.getUUID().getLeastSignificantBits()));

            for (int i = 0; i < count && i < pool.size(); i++) {
                MobEffectInstance inst = new MobEffectInstance(pool.get(i).get(), -1, amplifier, false, false, false);
                mob.addEffect(inst);

                // ServerEntity broadcastet Mobeverything ausser Effekten beim Spawnen; 
                // ChunkSource-Broadcast stopft dieses Vanilla-Loch fuer Clients, die beim Spawn bereits im Chunk stehen.
                level.getChunkSource().broadcast(mob, new ClientboundUpdateMobEffectPacket(mob.getId(), inst));
            }

            mob.setHealth(mob.getMaxHealth());

            Woerp.LOGGER.info("[WOERP-MUTATION] {} bei {} gespawnt | Ambient: {} | Mutationen: {}",
                    mob.getType().getDescriptionId(), mob.blockPosition().toShortString(), ambient, count);
        });
    }

    // Vanilla sendet beim Initial-Tracking ueber ServerEntity.sendPairingData keine Potion-Effekte an Beobachter.
    // Dieser Hook holt den Paket-Versand nach, sobald ein Spieler den Mob in Render-Distanz laedt.
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getTarget() instanceof Mob mob) || !(event.getEntity() instanceof ServerPlayer player)) return;

        for (MobEffectInstance effect : mob.getActiveEffects()) {
            if (effect.getEffect() instanceof WoerpMutationEffect) {
                player.connection.send(new ClientboundUpdateMobEffectPacket(mob.getId(), effect));
            }
        }
    }
}