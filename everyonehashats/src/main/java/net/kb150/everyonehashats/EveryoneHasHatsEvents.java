package net.kb150.everyonehashats;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.world.entity.EntityType;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.Random;

@Mod.EventBusSubscriber(modid = EveryoneHasHats.MODID)
public class EveryoneHasHatsEvents {
    private static Set<Item> CACHED_HATS = null;
    private static final Random RANDOM = new Random();
	
	public static boolean isHat(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (CACHED_HATS == null) {
            initializeHatCache();
        }
        return CACHED_HATS.contains(stack.getItem());
    }
    // Scannt alle SimpleHats-Items für das zufällige Spawnen
	private static synchronized void initializeHatCache() {
        if (CACHED_HATS != null) return;
        Set<Item> hats = new HashSet<>();

        for (Item item : ForgeRegistries.ITEMS) {
            ResourceLocation res = ForgeRegistries.ITEMS.getKey(item);
            if (res != null && res.getNamespace().equals("simplehats")) {
                String className = item.getClass().getSimpleName();
                if ((className.equals("HatItem") || className.equals("HatItemDyeable")) && !res.getPath().equals("special")) {
                    hats.add(item);
                }
            }
        }
        CACHED_HATS = Collections.unmodifiableSet(hats);
        EveryoneHasHats.LOGGER.info("[EveryoneHasHats] " + CACHED_HATS.size() + " Hüte im Cache registriert!");
    }

    // INTERAKTION: Hut aufsetzen oder abnehmen
    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof LivingEntity living)) return;
        
        Player player = event.getEntity();
        InteractionHand hand = event.getHand();
        ItemStack heldStack = player.getItemInHand(hand);
        
		ItemStack currentHat = living.getItemBySlot(EquipmentSlot.HEAD);
        boolean isSimpleHat = isHat(heldStack);
        boolean hasSimpleHat = isHat(currentHat);
        
        // SCHLEICHEN + RECHTSKLICK MIT LEERER HAND -> Hut abnehmen
        if (player.isSecondaryUseActive() && heldStack.isEmpty()) {
            if (hasSimpleHat) {
                if (!event.getLevel().isClientSide) {
                    // Gib dem Spieler den Hut zurück oder droppe ihn
                    if (!player.getAbilities().instabuild) {
                        if (!player.getInventory().add(currentHat.copy())) {
                            player.drop(currentHat.copy(), false);
                        }
                    }
                    living.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                    event.getLevel().playSound(null, living.getX(), living.getY(), living.getZ(), 
                        SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.NEUTRAL, 1.0F, 1.2F);
                }
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
                return;
            }
        }
        
        // RECHTSKLICK MIT EINEM HUT -> Aufsetzen / Tauschen
        if (isSimpleHat) {
            if (!event.getLevel().isClientSide) {
                ItemStack hatToEquip = heldStack.copy();
                hatToEquip.setCount(1);
                
                // Tier bekommt den neuen Hut
                living.setItemSlot(EquipmentSlot.HEAD, hatToEquip);
                
                // Falls bereits ein Hut aufgesetzt war, gib ihn dem Spieler zurück
                if (!currentHat.isEmpty()) {
                    if (!player.getAbilities().instabuild) {
                        if (!player.getInventory().add(currentHat.copy())) {
                            player.drop(currentHat.copy(), false);
                        }
                    }
                }
                
                // Verbrauche das gehaltene Item
                if (!player.getAbilities().instabuild) {
                    heldStack.shrink(1);
                }
                
                event.getLevel().playSound(null, living.getX(), living.getY(), living.getZ(), 
                    SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.NEUTRAL, 1.0F, 1.0F);
            }
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
        }
    }
}