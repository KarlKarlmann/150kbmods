package net.kb150.dragoncolonies.items;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import net.kb150.dragoncolonies.buildings.BuildingDragonRoost;
import net.kb150.dragoncolonies.buildings.modules.DragonStorageModule;
import net.magister.bookofdragons.entity.base.dragon.DragonBase;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Der Hort-Leitstab. 
 * Ein temporäres Werkzeug zum Rufen eines Drachens in die Obhut des Drachenhorts.
 */
public class ItemRoostPointer extends Item {

    public ItemRoostPointer(Properties properties) {
        super(properties.stacksTo(1));
    }

    /**
     * Erstellt einen neuen Leitstab, der an einen konkreten Drachenhort gebunden ist.
     */
    public static ItemStack createBoundPointer(BlockPos roostPos) {
        ItemStack stack = new ItemStack(net.kb150.dragoncolonies.registry.DragonColoniesRegistries.ROOST_POINTER.get());
        CompoundTag tag = stack.getOrCreateTag();
        tag.put("RoostPos", NbtUtils.writeBlockPos(roostPos));
        return stack;
    }

    @Override
    public @NotNull InteractionResult interactLivingEntity(@NotNull ItemStack stack, @NotNull Player player, @NotNull LivingEntity target, @NotNull InteractionHand hand) {
        Level level = player.level();

        if (target instanceof DragonBase dragon) {

            // Nur auf dem Server ausführen
            if (!level.isClientSide() && level instanceof ServerLevel serverLevel) {

                // 1. NBT-Daten des Ziel-Horts auslesen
                CompoundTag tag = stack.getTag();
                if (tag == null || !tag.contains("RoostPos")) {
                    player.sendSystemMessage(Component.literal("§c[DragonColonies] Dieser Leitstab ist an keinen Drachenhort gebunden!"));
                    return InteractionResult.FAIL;
                }

                BlockPos roostPos = NbtUtils.readBlockPos(tag.getCompound("RoostPos"));

                if (!dragon.isTame() || dragon.getOwnerUUID() == null || !dragon.getOwnerUUID().equals(player.getUUID())) {
                    player.sendSystemMessage(Component.literal("§c[DragonColonies] Dieser Drache gehört dir nicht!"));
                    return InteractionResult.FAIL;
                }

                IColony colony = IMinecoloniesAPI.getInstance().getColonyManager().getColonyByPosFromWorld(serverLevel, roostPos);
                if (colony == null) {
                    player.sendSystemMessage(Component.literal("§c[DragonColonies] Der Drachenhort existiert nicht mehr!"));
                    return InteractionResult.FAIL;
                }

                IBuilding building = colony.getServerBuildingManager().getBuilding(roostPos);
                if (!(building instanceof BuildingDragonRoost roost)) {
                    player.sendSystemMessage(Component.literal("§c[DragonColonies] Ungültiger Drachenhort!"));
                    return InteractionResult.FAIL;
                }

                DragonStorageModule storageModule = roost.getStorageModule();
                
                // Vor dem Speichern alle Passagiere absetzen
                dragon.ejectPassengers();

                // Drachen-Daten vollständig serialisieren
                CompoundTag dragonNbt = new CompoundTag();
                dragon.saveWithoutId(dragonNbt);

                // ZWINGEND: Entity-ID "id" mitspeichern, damit loadEntityRecursive die Klasse wiederfindet
                ResourceLocation entityKey = ForgeRegistries.ENTITY_TYPES.getKey(dragon.getType());
                if (entityKey != null) {
                    dragonNbt.putString("id", entityKey.toString());
                }

                // Rest-Passagierdaten entfernen
                dragonNbt.remove("Passengers");

                // Lesbaren Namen für das UI sichern
                String dragonDisplayName = dragon.hasCustomName() ? dragon.getCustomName().getString() : dragon.getName().getString();
                dragonNbt.putString("CustomName", dragonDisplayName);

                boolean success = false;

                // Prüfen, ob der Drache bereits im Hort registriert ist
                if (storageModule.getDragonByUUID(dragon.getUUID()).isPresent()) {
                    // Er ist "Auf Reisen" (oder aus einem Bug noch da), also updaten wir nur
                    storageModule.updateDragonData(dragon.getUUID(), dragonNbt);
                    success = true;
                } else {
                    // Er ist komplett neu für diesen Hort. Haben wir noch Platz?
                    if (!storageModule.canStoreMore()) {
                        player.sendSystemMessage(Component.literal("§c[DragonColonies] Der Drachenhort ist voll! (Kapazität: " + storageModule.getCapacity() + ")"));
                        return InteractionResult.FAIL;
                    }
                    
                    // Neu hinzufügen
                    success = storageModule.addDragon(dragonNbt);
                }

                if (success) {
                    // Wenn der Drache auch noch das Failsafe-Tag für den Chunk-Unload hatte, 
                    // löschen wir es sicherheitshalber aus der physischen Entität, bevor wir sie discarden.
                    dragon.getPersistentData().remove("DragonColonies_RoostPos");
                    
                    // Drache aus der Welt entfernen
                    dragon.discard();
                    player.sendSystemMessage(Component.literal("§a[DragonColonies] " + dragonDisplayName + " wurde sicher im Drachenhort eingelagert!"));
                    return InteractionResult.SUCCESS;
                }
            }
            return InteractionResult.CONSUME;
        }

        return InteractionResult.PASS;
    }

    @Override
    public boolean onDroppedByPlayer(ItemStack item, Player player) {
        // Sobald der Spieler Q drückt oder den Stab wegwirft, löscht er sich im Flug
        item.setCount(0);
        if (player.level().isClientSide()) {
            player.sendSystemMessage(Component.literal("§7[DragonColonies] Hort-Leitstab hat sich aufgelöst."));
        } else {
            player.level().playSound(null, player.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5F, 1.5F);
        }
        return true;
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        tooltip.add(Component.literal("§7Klicke auf einen deiner Drachen, um ihn"));
        tooltip.add(Component.literal("§7in die Obhut des Drachenhorts zu übergeben."));
        tooltip.add(Component.literal("§8(Löscht sich beim Wegwerfen automatisch)"));

        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains("RoostPos")) {
            BlockPos pos = NbtUtils.readBlockPos(tag.getCompound("RoostPos"));
            tooltip.add(Component.literal("§eHort bei: §fX: " + pos.getX() + " Y: " + pos.getY() + " Z: " + pos.getZ()));
        }
    }
}