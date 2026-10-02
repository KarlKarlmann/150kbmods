package net.kb150.superbcarfare.mixin;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import de.maxhenkel.car.items.ItemCanister;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = VehicleEntity.class, remap = false)
public class VehicleFuelMixin {

    private static final int ENERGY_PER_MB = 500;

    @Inject(method = "m_6096_", at = @At("HEAD"), cancellable = true)
    private void sbcarfare$handleFuelCanister(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (hand != InteractionHand.MAIN_HAND) return;

        ItemStack heldItem = player.getItemInHand(hand);
        
        // Pruefen, ob ein Kanister aus Ultimate Car Mod gehalten wird
        if (!(heldItem.getItem() instanceof ItemCanister)) {
            return;
        }

        VehicleEntity vehicle = (VehicleEntity) (Object) this;

        // --- ABZAPFEN (Extract) via Shift + Rechtsklick ---
        if (player.isShiftKeyDown()) {
            int availableEnergy = vehicle.getEnergy();
            int availableMb = availableEnergy / ENERGY_PER_MB;
            
            if (availableMb <= 0) {
                if (player.level().isClientSide()) {
                    player.displayClientMessage(Component.literal("§cDas Fahrzeug hat keinen abzapfbaren Treibstoff!"), true);
                }
                cir.setReturnValue(InteractionResult.FAIL);
                return;
            }

            CompoundTag comp = heldItem.getOrCreateTag();
            FluidStack currentFluid = FluidStack.EMPTY;
            if (comp.contains("fuel")) {
                currentFluid = FluidStack.loadFluidStackFromNBT(comp.getCompound("fuel"));
            }

            int currentAmount = currentFluid.isEmpty() ? 0 : currentFluid.getAmount();
            int maxCapacity = 10000; // Standard UCM Kanister Kapazitaet
            int spaceLeft = maxCapacity - currentAmount;

            if (spaceLeft <= 0) {
                if (player.level().isClientSide()) {
                    player.displayClientMessage(Component.literal("§cDer Kanister ist bereits voll!"), true);
                }
                cir.setReturnValue(InteractionResult.FAIL);
                return;
            }

            int mbToExtract = Math.min(availableMb, spaceLeft);

            if (mbToExtract > 0) {
                if (!player.level().isClientSide()) {
                    // Wenn der Kanister leer ist, erzeugen wir Bio-Diesel aus dem "Magic Loophole"
                    if (currentFluid.isEmpty() || currentFluid.getFluid() == null) {
                        net.minecraft.world.level.material.Fluid bioDiesel = ForgeRegistries.FLUIDS.getValue(new ResourceLocation("car", "bio_diesel"));
                        if (bioDiesel != null && bioDiesel != net.minecraft.world.level.material.Fluids.EMPTY) {
                            currentFluid = new FluidStack(bioDiesel, mbToExtract);
                        }
                    } else {
                        currentFluid.setAmount(currentAmount + mbToExtract);
                    }

                    comp.put("fuel", currentFluid.writeToNBT(new CompoundTag()));
                    heldItem.setTag(comp);

                    // Fahrzeug Energie abziehen
                    vehicle.setEnergy(Math.max(0, vehicle.getEnergy() - (mbToExtract * ENERGY_PER_MB)));

                    player.level().playSound(null, vehicle.blockPosition(), SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
                    player.displayClientMessage(Component.literal("§eTreibstoff abgezapft! Kanister: " + currentFluid.getAmount() + " mB"), true);
                }
                cir.setReturnValue(InteractionResult.SUCCESS);
                return;
            }
        } 
        // --- TANKEN (Insert) via normalem Rechtsklick ---
        else {
            if (vehicle.getEnergy() >= vehicle.getMaxEnergy()) {
                if (player.level().isClientSide()) {
                    player.displayClientMessage(Component.literal("§aTreibstofftank ist bereits voll!"), true);
                }
                cir.setReturnValue(InteractionResult.SUCCESS);
                return;
            }

            CompoundTag comp = heldItem.getTag();
            if (comp == null || !comp.contains("fuel")) {
                if (player.level().isClientSide()) {
                    player.displayClientMessage(Component.literal("§cDer Kanister ist leer!"), true);
                }
                cir.setReturnValue(InteractionResult.FAIL);
                return;
            }

            FluidStack fluidStack = FluidStack.loadFluidStackFromNBT(comp.getCompound("fuel"));
            if (fluidStack == null || fluidStack.isEmpty()) {
                if (player.level().isClientSide()) {
                    player.displayClientMessage(Component.literal("§cDer Kanister ist leer!"), true);
                }
                cir.setReturnValue(InteractionResult.FAIL);
                return;
            }

            ResourceLocation fluidKey = ForgeRegistries.FLUIDS.getKey(fluidStack.getFluid());
            String fluidId = (fluidKey != null) ? fluidKey.toString() : "";
            if (!fluidId.equals("car:bio_diesel") && !fluidId.equals("car:methanol")) {
                if (player.level().isClientSide()) {
                    player.displayClientMessage(Component.literal("§cFalscher Treibstoff fuer dieses Fahrzeug!"), true);
                }
                cir.setReturnValue(InteractionResult.FAIL);
                return;
            }

            int neededEnergy = vehicle.getMaxEnergy() - vehicle.getEnergy();
            int maxMbNeeded = (neededEnergy + ENERGY_PER_MB - 1) / ENERGY_PER_MB;
            int mbToDrain = Math.min(fluidStack.getAmount(), maxMbNeeded);

            if (mbToDrain > 0) {
                if (!player.level().isClientSide()) {
                    fluidStack.shrink(mbToDrain);
                    if (fluidStack.isEmpty()) {
                        comp.put("fuel", new CompoundTag());
                    } else {
                        comp.put("fuel", fluidStack.writeToNBT(new CompoundTag()));
                    }

                    int energyToAdd = mbToDrain * ENERGY_PER_MB;
                    vehicle.setEnergy(Math.min(vehicle.getMaxEnergy(), vehicle.getEnergy() + energyToAdd));

                    player.level().playSound(null, vehicle.blockPosition(), SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 1.0F, 1.0F);
                    int percent = (int) (((double) vehicle.getEnergy() / vehicle.getMaxEnergy()) * 100);
                    player.displayClientMessage(Component.literal("§aBetankt mit " + fluidStack.getDisplayName().getString() + "! Tankstand: " + percent + "%"), true);
                }
                cir.setReturnValue(InteractionResult.SUCCESS);
            }
        }
    }
}