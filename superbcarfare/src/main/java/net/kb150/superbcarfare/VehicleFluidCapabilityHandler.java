package net.kb150.superbcarfare;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.registries.ForgeRegistries;

@Mod.EventBusSubscriber(modid = "superbcarfare", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class VehicleFluidCapabilityHandler {

    // Umrechnung: 1 mB Bio-Diesel = 500 FE Energie fuer das Fahrzeug
    private static final int ENERGY_PER_MB = 500;

    @SubscribeEvent
    public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof VehicleEntity vehicle) {
            event.addCapability(
                    new ResourceLocation("superbcarfare", "fuel_handler"),
                    new ICapabilityProvider() {
                        private final LazyOptional<IFluidHandler> handler = LazyOptional.of(() -> new VehicleFluidAdapter(vehicle));

                        @Override
                        public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
                            if (cap == ForgeCapabilities.FLUID_HANDLER) {
                                return handler.cast();
                            }
                            return LazyOptional.empty();
                        }
                    }
            );
        }
    }

    private static class VehicleFluidAdapter implements IFluidHandler {
        private final VehicleEntity vehicle;

        public VehicleFluidAdapter(VehicleEntity vehicle) {
            this.vehicle = vehicle;
        }

        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return FluidStack.EMPTY;
        }

        @Override
        public int getTankCapacity(int tank) {
            int neededEnergy = vehicle.getMaxEnergy() - vehicle.getEnergy();
            return Math.max(0, neededEnergy / ENERGY_PER_MB);
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            if (stack.isEmpty()) return false;
            ResourceLocation key = ForgeRegistries.FLUIDS.getKey(stack.getFluid());
            if (key == null) return false;
            
            String fluidId = key.toString();
            return fluidId.equals("car:bio_diesel") || fluidId.equals("car:methanol");
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (!isFluidValid(0, resource)) return 0;

            int currentEnergy = vehicle.getEnergy();
            int maxEnergy = vehicle.getMaxEnergy();
            int neededEnergy = maxEnergy - currentEnergy;

            if (neededEnergy <= 0) return 0;

            int maxMbNeeded = (neededEnergy + ENERGY_PER_MB - 1) / ENERGY_PER_MB;
            int mbToDrain = Math.min(resource.getAmount(), maxMbNeeded);

            if (mbToDrain > 0 && action.execute()) {
                int energyToAdd = mbToDrain * ENERGY_PER_MB;
                vehicle.setEnergy(Math.min(maxEnergy, currentEnergy + energyToAdd));
            }

            return mbToDrain;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return FluidStack.EMPTY;
        }
    }
}