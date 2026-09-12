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
import java.util.UUID;

/**
 * Der Hort-Leitstab.
 * Erlaubt das Einlagern von Drachen in den Drachenhort unter
 * Beibehaltung oder Neuvergabe einer festen RoostDragonID.
 */
public class ItemRoostPointer extends Item {

    public ItemRoostPointer(Properties properties) {
        super(properties.stacksTo(1));
    }

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

            if (!level.isClientSide() && level instanceof ServerLevel serverLevel) {

                CompoundTag tag = stack.getTag();
                if (tag == null || !tag.contains("RoostPos")) {
                    player.sendSystemMessage(Component.translatable("dragoncolonies.message.pointer.unbound"));
                    return InteractionResult.FAIL;
                }

                BlockPos roostPos = NbtUtils.readBlockPos(tag.getCompound("RoostPos"));

                if (!dragon.isTame() || dragon.getOwnerUUID() == null || !dragon.getOwnerUUID().equals(player.getUUID())) {
                    player.sendSystemMessage(Component.translatable("dragoncolonies.message.pointer.not_owner"));
                    return InteractionResult.FAIL;
                }

                IColony colony = IMinecoloniesAPI.getInstance().getColonyManager().getColonyByPosFromWorld(serverLevel, roostPos);
                if (colony == null) {
                    player.sendSystemMessage(Component.translatable("dragoncolonies.message.pointer.roost_missing"));
                    return InteractionResult.FAIL;
                }

                IBuilding building = colony.getServerBuildingManager().getBuilding(roostPos);
                if (!(building instanceof BuildingDragonRoost roost)) {
                    player.sendSystemMessage(Component.translatable("dragoncolonies.message.pointer.invalid_roost"));
                    return InteractionResult.FAIL;
                }

                DragonStorageModule storageModule = roost.getStorageModule();
                if (storageModule == null) return InteractionResult.FAIL;

                dragon.ejectPassengers();

                CompoundTag dragonNbt = new CompoundTag();
                dragon.saveWithoutId(dragonNbt);

                ResourceLocation entityKey = ForgeRegistries.ENTITY_TYPES.getKey(dragon.getType());
                if (entityKey != null) {
                    dragonNbt.putString("id", entityKey.toString());
                }

                dragonNbt.remove("Passengers");

                String dragonDisplayName = dragon.hasCustomName() ? dragon.getCustomName().getString() : dragon.getName().getString();
                dragonNbt.putString("CustomName", dragonDisplayName);

                // RoostDragonID ermitteln: Beibehalten, falls vorhanden, sonst neu anlegen
                UUID roostDragonId;
                if (dragon.getPersistentData().hasUUID("DragonColonies_RoostDragonID")) {
                    roostDragonId = dragon.getPersistentData().getUUID("DragonColonies_RoostDragonID");
                } else {
                    roostDragonId = UUID.randomUUID();
                }

                dragonNbt.putUUID(DragonStorageModule.TAG_ROOST_DRAGON_ID, roostDragonId);

                boolean success;

                if (storageModule.getDragonByRoostId(roostDragonId).isPresent()) {
                    storageModule.updateDragonData(roostDragonId, dragonNbt);
                    success = true;
                } else {
                    if (!storageModule.canStoreMore()) {
                        player.sendSystemMessage(Component.translatable("dragoncolonies.message.pointer.roost_full", storageModule.getCapacity()));
                        return InteractionResult.FAIL;
                    }
                    success = storageModule.addDragon(dragonNbt);
                }

                if (success) {
                    dragon.getPersistentData().remove("DragonColonies_RoostPos");
                    dragon.getPersistentData().remove("DragonColonies_RoostDragonID");
                    dragon.getPersistentData().remove("DragonColonies_GuardDeployed");
                    dragon.getPersistentData().remove("DragonColonies_GuardUUID");

                    dragon.discard();
                    player.sendSystemMessage(Component.translatable("dragoncolonies.message.pointer.success", dragonDisplayName));
                    return InteractionResult.SUCCESS;
                }
            }
            return InteractionResult.CONSUME;
        }

        return InteractionResult.PASS;
    }

    @Override
    public boolean onDroppedByPlayer(ItemStack item, Player player) {
        item.setCount(0);
        if (player.level().isClientSide()) {
            player.sendSystemMessage(Component.translatable("dragoncolonies.message.pointer.destroyed"));
        } else {
            player.level().playSound(null, player.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5F, 1.5F);
        }
        return true;
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        tooltip.add(Component.translatable("item.dragoncolonies.roost_pointer.tooltip.line1"));
        tooltip.add(Component.translatable("item.dragoncolonies.roost_pointer.tooltip.line2"));
        tooltip.add(Component.translatable("item.dragoncolonies.roost_pointer.tooltip.line3"));

        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains("RoostPos")) {
            BlockPos pos = NbtUtils.readBlockPos(tag.getCompound("RoostPos"));
            tooltip.add(Component.translatable("item.dragoncolonies.roost_pointer.tooltip.pos", pos.getX(), pos.getY(), pos.getZ()));
        }
    }
}