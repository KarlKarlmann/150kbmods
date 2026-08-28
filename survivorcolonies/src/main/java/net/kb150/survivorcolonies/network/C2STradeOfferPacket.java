package net.kb150.survivorcolonies.network;

import net.kb150.survivorcolonies.entity.SurvivorEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class C2STradeOfferPacket {
    private final int survivorId;
    private final int survivorItemSlot;
    private final ItemStack playerOffer;

    public C2STradeOfferPacket(int survivorId, int survivorItemSlot, ItemStack playerOffer) {
        this.survivorId = survivorId;
        this.survivorItemSlot = survivorItemSlot;
        this.playerOffer = playerOffer;
    }

    public C2STradeOfferPacket(FriendlyByteBuf buf) {
        this.survivorId = buf.readInt();
        this.survivorItemSlot = buf.readInt();
        this.playerOffer = buf.readItem();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(this.survivorId);
        buf.writeInt(this.survivorItemSlot);
        buf.writeItem(this.playerOffer);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;

            Entity entity = player.level().getEntity(this.survivorId);
            if (entity instanceof SurvivorEntity survivor) {
                
                // 1. Hat der NPC das Item noch?
                ItemStack survivorLoot = survivor.getInventory().getItem(this.survivorItemSlot);
                if (survivorLoot.isEmpty()) return;

                // 2. Hat der Spieler die gebotenen Items wirklich im Inventar?
                int remainingToRemove = this.playerOffer.getCount();
                int countInPlayerInventory = 0;
                for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                    ItemStack stack = player.getInventory().getItem(i);
                    if (ItemStack.isSameItemSameTags(stack, this.playerOffer)) {
                        countInPlayerInventory += stack.getCount();
                    }
                }

                if (countInPlayerInventory >= remainingToRemove) {
                    
                    // 3. Dem Spieler seine Items abziehen
                    for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                        ItemStack stack = player.getInventory().getItem(i);
                        if (ItemStack.isSameItemSameTags(stack, this.playerOffer)) {
                            int toTake = Math.min(stack.getCount(), remainingToRemove);
                            stack.shrink(toTake);
                            remainingToRemove -= toTake;
                            if (remainingToRemove <= 0) break;
                        }
                    }

                    // 4. Das Item des NPCs aus dessen Inventar entfernen
                    ItemStack rewardForPlayer = survivor.getInventory().removeItem(this.survivorItemSlot, survivorLoot.getCount());

                    // 5. Gegenseitige Übergabe
                    if (!player.getInventory().add(rewardForPlayer)) {
                        player.drop(rewardForPlayer, false);
                    }
                    
                    ItemStack toSurvivor = this.playerOffer.copy();
                    if (!survivor.tryEquipBetterItem(toSurvivor)) {
                        survivor.getInventory().addItem(toSurvivor);
                    }

                    // 6. Handel abschließen
                    survivor.addTrust(5);
                    player.level().playSound(null, survivor.blockPosition(), 
                        SoundEvents.VILLAGER_TRADE, SoundSource.NEUTRAL, 1.0F, 1.0F);
                }
            }
        });
        context.setPacketHandled(true); 
    }
}