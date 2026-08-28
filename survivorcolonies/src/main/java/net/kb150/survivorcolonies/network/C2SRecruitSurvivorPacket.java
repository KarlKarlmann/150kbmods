package net.kb150.survivorcolonies.network;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.api.eventbus.events.colony.citizens.CitizenAddedModEvent;
import net.kb150.survivorcolonies.entity.SurvivorEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class C2SRecruitSurvivorPacket {
    private final int entityId;

    public C2SRecruitSurvivorPacket(int entityId) {
        this.entityId = entityId;
    }

    public C2SRecruitSurvivorPacket(FriendlyByteBuf buf) {
        this.entityId = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(this.entityId);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            Entity entity = player.level().getEntity(this.entityId);
            if (!(entity instanceof SurvivorEntity survivor) || !survivor.isAlive()) return;

            IColony colony = IMinecoloniesAPI.getInstance().getColonyManager()
                    .getIColonyByOwner(player.level(), player.getUUID());

            if (colony == null) {
                player.sendSystemMessage(Component.literal("§cDu benötigst eine Kolonie!"));
                return;
            }

            if (colony.getCitizenManager().getCurrentCitizenCount() >= colony.getCitizenManager().getPotentialMaxCitizens()) {
                player.sendSystemMessage(Component.literal("§cDeine Kolonie hat keinen Platz mehr!"));
                return;
            }

            // Bezahlung prüfen & abziehen
            IItemHandler inv = player.getCapability(ForgeCapabilities.ITEM_HANDLER, null).orElse(null);
            if (inv != null && !player.isCreative()) {
                if (!hasEnoughItems(inv, survivor.getRecruitCost())) {
                    player.sendSystemMessage(Component.literal("§cDir fehlen die benötigten Items!"));
                    return;
                }
                deductItems(inv, survivor.getRecruitCost());
            }

            // 1. Echten MineColonies Bürger anlegen
            ICitizenData newCitizen = colony.getCitizenManager().createAndRegisterCivilianData();

            // 2. NBT ziehen, textureId in den privaten Bereich von CitizenData patchen und zurückschreiben
            CompoundTag nbt = newCitizen.serializeNBT();
            nbt.putInt("texture", survivor.getTextureId());
            newCitizen.deserializeNBT(nbt);

            // 3. Name, Geschlecht & Suffix setzen
            newCitizen.setName(survivor.getSurvivorName());
            newCitizen.setGender(survivor.isFemale());
            newCitizen.setSuffix(survivor.getTextureSuffix());

            // 4. Skill-Map spiegeln (über Level-Differenz anpassen)
            survivor.getSkills().forEach((skillName, targetLevel) -> {
                for (Skill s : Skill.values()) {
                    if (s.name().equalsIgnoreCase(skillName)) {
                        int currentLevel = newCitizen.getCitizenSkillHandler().getLevel(s);
                        newCitizen.getCitizenSkillHandler().incrementLevel(s, targetLevel - currentLevel);
                        break;
                    }
                }
            });

            // 5. Spawnen & Event feuern
            colony.getCitizenManager().spawnOrCreateCitizen(newCitizen, player.level(), survivor.blockPosition());
            IMinecoloniesAPI.getInstance().getEventBus().post(new CitizenAddedModEvent(newCitizen, CitizenAddedModEvent.CitizenAddedSource.HIRED));

            // Proviant ins Bürger-Inventar
            if (!survivor.getExtraItem().isEmpty() && newCitizen.getEntity().isPresent()) {
                newCitizen.getEntity().get().getItemHandlerCitizen().insertItem(0, survivor.getExtraItem().copy(), false);
            }

            player.sendSystemMessage(Component.literal("§a" + survivor.getSurvivorName() + " ist deiner Kolonie beigetreten!"));
            survivor.discard();
        });
        ctx.get().setPacketHandled(true);
    }

    private boolean hasEnoughItems(IItemHandler inv, ItemStack req) {
        int count = 0;
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (ItemStack.isSameItemSameTags(stack, req)) count += stack.getCount();
        }
        return count >= req.getCount();
    }

    private void deductItems(IItemHandler inv, ItemStack req) {
        int toRemove = req.getCount();
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (ItemStack.isSameItemSameTags(stack, req)) {
                int ext = Math.min(stack.getCount(), toRemove);
                inv.extractItem(i, ext, false);
                toRemove -= ext;
                if (toRemove <= 0) break;
            }
        }
    }
}