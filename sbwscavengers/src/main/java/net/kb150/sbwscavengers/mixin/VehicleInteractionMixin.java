package net.kb150.sbwscavengers.mixin;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import net.kb150.sbwscavengers.VehicleLootManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import net.kb150.sbwscavengers.RepairToolManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Random;

@Mixin(value = VehicleEntity.class, remap = false)
public class VehicleInteractionMixin {

    private static final int CLICKS_REQUIRED_PER_STAGE = 5;
    private static final int REPAIR_COOLDOWN_TICKS = 8;

    /*
     * STREAMING_CHUNK: BLOCKIERT DAS AUSBLUTEN (Self-Hurt)
     */
    @Inject(method = "onHurt", at = @At("HEAD"), cancellable = true, remap = false)
    private void dbd$preventBleedout(float amount, net.minecraft.world.entity.Entity attacker, boolean send, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        VehicleEntity vehicle = (VehicleEntity) (Object) this;
        
        // In SuperbWarfare bedeutet send = false, dass es der interne Ausbluten/Brennen-Schaden ist
        if (!send) {
            // Ein Tag regelt alles: Spawner-Wracks UND geloeschte Fahrzeuge!
            if (vehicle.getPersistentData().getBoolean("SBW_PreventBleedout")) {
                ci.cancel();
            }
        }
    }

    /*
     * STREAMING_CHUNK:Blockiere die automatische Selbstheilung der Basis-Mod...
     */
    @Inject(method = "repairAmount", at = @At("HEAD"), cancellable = true)
    private void dbd$disablePassiveHealing(CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(0.0f);
    }
    
    @Inject(method = "setHealth", at = @At("TAIL"), remap = false)
    private void dbd$clearFlagsOnHeal(float value, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        VehicleEntity vehicle = (VehicleEntity) (Object) this;
        if (vehicle.level().isClientSide()) return;
        
        float selfHurtThreshold = vehicle.getMaxHealth() * vehicle.computed().getSelfHurtPercent();
        if (vehicle.getHealth() > selfHurtThreshold) {
            vehicle.getPersistentData().remove("SBW_PreventBleedout");
        }
    }
    
    @Inject(method = "m_6096_", at = @At("HEAD"), cancellable = true)
    private void dbd$handleCustomItemInteractions(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (hand != InteractionHand.MAIN_HAND) return;

        VehicleEntity vehicle = (VehicleEntity) (Object) this;
        ItemStack heldItem = player.getItemInHand(hand);
        ResourceLocation itemKey = ForgeRegistries.ITEMS.getKey(heldItem.getItem());
        String heldItemName = (itemKey != null) ? itemKey.toString() : "";

        /*
         * STREAMING_CHUNK:Lösch-Mechanik mit dem Wassereimer...
         */
        float currentHealth = vehicle.getHealth();
        float maxHealth = vehicle.getMaxHealth();
        float selfHurtThreshold = maxHealth * vehicle.computed().getSelfHurtPercent();
        
        boolean isBurningThreshold = currentHealth <= selfHurtThreshold;
        boolean isSafeFromBleedout = vehicle.getPersistentData().getBoolean("SBW_PreventBleedout");

        if (heldItem.getItem() == net.minecraft.world.item.Items.WATER_BUCKET) {
            if (isBurningThreshold && !isSafeFromBleedout) {
                if (!player.level().isClientSide()) {
                    vehicle.getPersistentData().putBoolean("SBW_PreventBleedout", true);
                    player.level().playSound(null, vehicle.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.0F, 2.0F);
                    
                    if (!player.getAbilities().instabuild) {
                        player.setItemInHand(hand, new ItemStack(net.minecraft.world.item.Items.BUCKET));
                    }
                    player.displayClientMessage(Component.translatable("message.sbwscavengers.extinguished").withStyle(ChatFormatting.AQUA), true);
                }
                cir.setReturnValue(InteractionResult.SUCCESS);
                return;
            }
        }

        /*
         * STREAMING_CHUNK:Pruefe dynamisch gegen alle im Datapack konfigurierten Werkzeuge...
         */
        List<String> availableTools = RepairToolManager.TOOLS;
        boolean isHeldItemATool = availableTools.contains(heldItemName);

        if (isHeldItemATool) {
            if (isBurningThreshold && !isSafeFromBleedout) {
                if (!player.level().isClientSide()) {
                    player.displayClientMessage(Component.translatable("message.sbwscavengers.needs_extinguish").withStyle(ChatFormatting.RED), true);
                }
                cir.setReturnValue(InteractionResult.CONSUME);
                return;
            }

            if (currentHealth >= maxHealth && !vehicle.isWreck()) {
                if (player.level().isClientSide()) {
                    player.displayClientMessage(Component.translatable("message.sbwscavengers.vehicle_intact").withStyle(ChatFormatting.GREEN), true);
                }
                cir.setReturnValue(InteractionResult.SUCCESS);
                return;
            }

            if (player.getAbilities().instabuild) {
                if (!player.level().isClientSide()) {
                    vehicle.setWreck(false);
                    vehicle.setHealth(maxHealth);
                    player.displayClientMessage(Component.translatable("message.sbwscavengers.admin_repair").withStyle(ChatFormatting.LIGHT_PURPLE), true);
                }
                cir.setReturnValue(InteractionResult.SUCCESS);
                return;
            }

            long currentTime = player.level().getGameTime();
            long lastRepairTime = vehicle.getPersistentData().getLong("SBWLastRepairTime");
            if (currentTime - lastRepairTime < REPAIR_COOLDOWN_TICKS) {
                cir.setReturnValue(InteractionResult.SUCCESS);
                return;
            }

            float absoluteHealthSpan = currentHealth + maxHealth;
            int repairStage = (int) Math.floor(absoluteHealthSpan / (maxHealth * 0.10f));
            if (repairStage < 0) repairStage = 0;
            if (repairStage > 19) repairStage = 19;

            long seed = vehicle.getUUID().getMostSignificantBits() ^ (repairStage * 0x9E3779B9L);
            Random seededRandom = new Random(seed);

            /*
             * STREAMING_CHUNK:Waehle das geforderte Werkzeug zufaellig aus der JSON-Liste...
             */
            String requiredToolId = availableTools.get(seededRandom.nextInt(availableTools.size()));

            // Falsches Werkzeug? Bricht korrekterweise VOR den 1/5 Schritten ab.
			if (!heldItemName.equals(requiredToolId)) {
                if (!player.level().isClientSide()) { // <--- HIER IST DER FIX: Server statt Client!
                    int percent = (int) ((absoluteHealthSpan / (2 * maxHealth)) * 100);
                    Item toolItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation(requiredToolId));
                    net.minecraft.network.chat.MutableComponent toolName = (toolItem != null && toolItem != net.minecraft.world.item.Items.AIR) ? toolItem.getDescription().copy() : Component.literal(requiredToolId);
                    
                    player.displayClientMessage(
                        Component.translatable("message.sbwscavengers.need_tool", percent, toolName.withStyle(ChatFormatting.YELLOW)).withStyle(ChatFormatting.RED), 
                        true
                    );
                    vehicle.getPersistentData().putInt("SBWRepairProgress", 0);
                }
                cir.setReturnValue(InteractionResult.SUCCESS);
                return;
            }

            if (player.level().isClientSide()) {
                cir.setReturnValue(InteractionResult.SUCCESS);
                return;
            }

            ResourceLocation entityKey = ForgeRegistries.ENTITY_TYPES.getKey(vehicle.getType());
            String vehiclePath = (entityKey != null) ? entityKey.getPath() : "";
            List<Item> lootPool = VehicleLootManager.getVehicleLootPool(player.level().getServer().getResourceManager(), vehiclePath);

            if (lootPool == null || lootPool.isEmpty()) {
                cir.setReturnValue(InteractionResult.PASS);
                return;
            }

            Item requiredMaterial = lootPool.get(seededRandom.nextInt(lootPool.size()));
            int slot = player.getInventory().findSlotMatchingItem(new ItemStack(requiredMaterial));

            // Material fehlt? Wirft Fehler und bricht VOR den 1/5 Schritten ab.
			if (slot == -1) {
                player.displayClientMessage(
                        Component.translatable("message.sbwscavengers.need_material", requiredMaterial.getDescription().copy().withStyle(ChatFormatting.YELLOW)).withStyle(ChatFormatting.RED),
                        true
                );
                cir.setReturnValue(InteractionResult.SUCCESS); 
                return;
            }

            // Material und Werkzeug vorhanden -> JETZT kommen die 1/5 Schritte!
            vehicle.getPersistentData().putLong("SBWLastRepairTime", currentTime);
            int currentProgress = vehicle.getPersistentData().getInt("SBWRepairProgress");
            currentProgress++;

            // FIX: Das Werkzeug verliert bei JEDEM Klick an Haltbarkeit, nicht nur am Ende!
            heldItem.hurtAndBreak(1, player, (p) -> p.broadcastBreakEvent(hand));

            if (currentProgress >= CLICKS_REQUIRED_PER_STAGE) {
                vehicle.getPersistentData().putInt("SBWRepairProgress", 0);
                player.getInventory().getItem(slot).shrink(1);
                // heldItem.hurtAndBreak(1... wurde hier entfernt, da es jetzt oben passiert!

                float newHealth = currentHealth + (maxHealth * 0.10f);
                if (newHealth > maxHealth) newHealth = maxHealth;

                if (vehicle.isWreck() && newHealth > 0.0f) {
                    vehicle.setWreck(false);
                }
                vehicle.setHealth(newHealth);

                player.level().playSound(null, vehicle.blockPosition(), SoundEvents.IRON_GOLEM_REPAIR, SoundSource.PLAYERS, 1.0F, 0.8F + (seededRandom.nextFloat() * 0.2f));

                int visualPercent = (int) (((newHealth + maxHealth) / (2 * maxHealth)) * 100);
                Component state = vehicle.isWreck() ? 
                        Component.translatable("message.sbwscavengers.state_wreck").withStyle(ChatFormatting.RED) : 
                        Component.translatable("message.sbwscavengers.state_intact").withStyle(ChatFormatting.GREEN);
                
                player.displayClientMessage(Component.translatable("message.sbwscavengers.part_installed", visualPercent, state).withStyle(ChatFormatting.GREEN), true);
            } else {
                vehicle.getPersistentData().putInt("SBWRepairProgress", currentProgress);

                player.level().playSound(null, vehicle.blockPosition(), SoundEvents.IRON_GOLEM_REPAIR, SoundSource.PLAYERS, 0.4F, 1.5F + (seededRandom.nextFloat() * 0.3f));

                int visualPercent = (int) (((currentHealth + maxHealth) / (2 * maxHealth)) * 100);

                StringBuilder bar = new StringBuilder();
                for (int i = 0; i < CLICKS_REQUIRED_PER_STAGE; i++) {
                    bar.append(i < currentProgress ? "■" : "□");
                }

                player.displayClientMessage(Component.translatable("message.sbwscavengers.repair_progress", visualPercent, bar.toString()).withStyle(ChatFormatting.YELLOW), true);
            }

            cir.setReturnValue(InteractionResult.SUCCESS);
        }
    }
}