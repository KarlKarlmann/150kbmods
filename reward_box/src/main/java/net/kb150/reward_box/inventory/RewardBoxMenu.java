package net.kb150.reward_box.inventory;

import net.kb150.reward_box.init.RewardBoxRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class RewardBoxMenu extends AbstractContainerMenu {
    private final Container container;
    public boolean playAnimation = false;
    public BlockPos blockPos = BlockPos.ZERO;

    public RewardBoxMenu(int id, Inventory playerInv, FriendlyByteBuf extraData) {
        this(id, playerInv, new SimpleContainer(27));
        if (extraData != null) {
            this.blockPos = extraData.readBlockPos(); 
            this.playAnimation = extraData.readBoolean();
        }
    }

    public RewardBoxMenu(int id, Inventory playerInv, Container container) {
        super(RewardBoxRegistry.REWARD_BOX_MENU.get(), id);
        this.container = container;
        checkContainerSize(container, 27);
        container.startOpen(playerInv.player);

        // Lootbox Slots (3x9 Raster)
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(container, col + row * 9, 8 + col * 18, 18 + row * 18));
            }
        }

        // Spieler Inventar (Korrektur: +9 für den Offset der Hotbar, verhindert Duplizierung!)
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }

        // Spieler Hotbar
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(playerInv, col, 8 + col * 18, 142));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return this.container.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack itemstack1 = slot.getItem();
            itemstack = itemstack1.copy();
            if (index < 27) {
                if (!this.moveItemStackTo(itemstack1, 27, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(itemstack1, 0, 27, false)) {
                return ItemStack.EMPTY;
            }

            if (itemstack1.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return itemstack;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.container.stopOpen(player);
    }
}