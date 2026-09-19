package net.kb150.dragoncolonies.mixin;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import net.kb150.dragoncolonies.buildings.BuildingDragonRoost;
import net.kb150.dragoncolonies.buildings.modules.DragonStorageModule;
import net.magister.bookofdragons.entity.base.dragon.DragonBase;
import net.magister.bookofdragons.entity.component.TamingComponent;
import net.magister.bookofdragons.entity.component.ranged.OmniAttackHandler;
import net.magister.bookofdragons.entity.state.TransportMode;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/**
 * Mixin für DragonBase.
 * Fälscht isVehicle() für Bürger-Passagiere, validiert aufwachende Entitäten anhand
 * der Kombination aus RoostDragonID und EntityUUID gegen den Hort, fängt Tode und Löschungen ab.
 */
@Mixin(DragonBase.class)
public abstract class DragonBaseMixin {

    @Unique
    private boolean dragoncolonies$spawnValidated = false;

    /**
     * Prüft beim Laden eines Chunks, ob dieser Drache noch die legitimierte Entität
     * für seine RoostDragonID ist. Ein altes Duplikat wird sofort verworfen.
     */
    @Inject(method = {"serverTick", "m_8119_"}, at = @At("HEAD"), remap = false, require = 0)
    private void dragoncolonies$validateSpawn(CallbackInfo ci) {
        if (this.dragoncolonies$spawnValidated) return;

        DragonBase dragon = (DragonBase) (Object) this;
        if (dragon.level().isClientSide()) return;

        this.dragoncolonies$spawnValidated = true;

        CompoundTag data = dragon.getPersistentData();
        if (data.contains("DragonColonies_RoostPos") && data.hasUUID("DragonColonies_RoostDragonID")) {
            BlockPos roostPos = BlockPos.of(data.getLong("DragonColonies_RoostPos"));
            UUID roostDragonId = data.getUUID("DragonColonies_RoostDragonID");
            ServerLevel level = (ServerLevel) dragon.level();

            IColony colony = IMinecoloniesAPI.getInstance().getColonyManager().getColonyByPosFromWorld(level, roostPos);
            if (colony != null && colony.getServerBuildingManager().getBuilding(roostPos) instanceof BuildingDragonRoost roost) {
                DragonStorageModule storage = roost.getStorageModule();

                if (storage != null) {
                    boolean isValid = storage.isEntityValidForRoostId(roostDragonId, dragon.getUUID());
                    if (!isValid) {
                        //System.out.println("[DragonColonies] Veraltetes Duplikat/Geist erkannt und entfernt: "
                        //        + dragon.getName().getString() + " (RoostID: " + roostDragonId + ", EntityUUID: " + dragon.getUUID() + ")");

                        data.remove("DragonColonies_RoostPos");
                        data.remove("DragonColonies_RoostDragonID");
                        data.remove("DragonColonies_GuardDeployed");
                        data.remove("DragonColonies_OrphanTicks");
                        data.remove("DragonColonies_GuardUUID");

                        dragon.discard();
                    }
                }
            }
        }
    }

    @Inject(
        method = {"canAddPassenger", "m_7310_"},
        at = @At("HEAD"),
        remap = false,
        cancellable = true,
        require = 0
    )
    private void dragoncolonies$allowCitizenPassenger(Entity passenger, CallbackInfoReturnable<Boolean> cir) {
        if (passenger instanceof AbstractEntityCitizen) {
            DragonBase dragon = (DragonBase) (Object) this;
            if (dragon.getGrowthStage() < 2) {
                cir.setReturnValue(false);
            } else {
                cir.setReturnValue(dragon.getPassengers().size() < 2);
            }
        }
    }

    @Inject(
        method = {"getControllingPassenger", "m_6688_"},
        at = @At("HEAD"),
        cancellable = true,
        remap = false,
        require = 0
    )
    private void dragoncolonies$preventCitizenClassCastCrash(CallbackInfoReturnable<LivingEntity> cir) {
        DragonBase dragon = (DragonBase) (Object) this;
        if (dragon.getFirstPassenger() instanceof AbstractEntityCitizen) {
            cir.setReturnValue(null);
        }
    }

    @Inject(
        method = {"isVehicle", "m_20160_"},
        at = @At("HEAD"),
        cancellable = true,
        remap = false,
        require = 0
    )
    private void dragoncolonies$fakeNotVehicleForCitizenGoal(CallbackInfoReturnable<Boolean> cir) {
        DragonBase dragon = (DragonBase) (Object) this;
        if (dragon.getFirstPassenger() instanceof AbstractEntityCitizen) {
            cir.setReturnValue(false);
        }
    }

    @Inject(
        method = {"serverTick", "m_8119_"},
        at = @At("TAIL"),
        remap = false,
        require = 0
    )
    private void dragoncolonies$handleStarvationAffection(CallbackInfo ci) {
        DragonBase dragon = (DragonBase) (Object) this;
        if (dragon.level().isClientSide()) return;

        int foodLevel = dragon.getEntityData().get(DragonBase.getHungerLevelData());

        if (foodLevel <= 0 && dragon.tickCount % 60 == 0) {
            TamingComponent taming = (TamingComponent) dragon.componentRegistry.get(TamingComponent.class);

            if (taming != null && dragon.getOwnerUUID() != null) {
                UUID ownerUUID = dragon.getOwnerUUID();
                int currentAffection = taming.getAffection(ownerUUID);

                if (currentAffection > 0) {
                    int newAffection = Math.max(0, currentAffection - 5);
                    taming.setAffection(ownerUUID, newAffection);
                    dragon.getEntityData().set(DragonBase.getDebugAffectionData(), newAffection);

                } else if (dragon.isTame()) {
                    dragon.ejectPassengers();
                    dragon.setTame(false);
                    dragon.setOwnerUUID(null);

                    CompoundTag data = dragon.getPersistentData();
                    if (data.contains("DragonColonies_RoostPos") && data.hasUUID("DragonColonies_RoostDragonID")) {
                        BlockPos roostPos = BlockPos.of(data.getLong("DragonColonies_RoostPos"));
                        UUID roostDragonId = data.getUUID("DragonColonies_RoostDragonID");
                        ServerLevel level = (ServerLevel) dragon.level();
                        IColony colony = IMinecoloniesAPI.getInstance().getColonyManager().getColonyByPosFromWorld(level, roostPos);

                        if (colony != null && colony.getServerBuildingManager().getBuilding(roostPos) instanceof BuildingDragonRoost roost) {
                            DragonStorageModule storage = roost.getStorageModule();
                            if (storage != null) {
                                storage.removeDragon(roostDragonId);
                            }
                        }

                        data.remove("DragonColonies_RoostPos");
                        data.remove("DragonColonies_RoostDragonID");
                        data.remove("DragonColonies_GuardDeployed");
                        data.remove("DragonColonies_OrphanTicks");
                        data.remove("DragonColonies_GuardUUID");
                    }

                    //System.out.println("[DragonColonies] Drache '" + dragon.getName().getString() + "' hat wegen Verhungerns die Bindung verloren und ist wieder wild!");
                }
            }
        }
    }

    @Inject(
        method = {"serverTick", "m_8119_"},
        at = @At("TAIL"),
        remap = false,
        require = 0
    )
    private void dragoncolonies$handleOrphanedDragons(CallbackInfo ci) {
        DragonBase dragon = (DragonBase) (Object) this;
        if (dragon.level().isClientSide()) return;

        CompoundTag data = dragon.getPersistentData();

        if (data.contains("DragonColonies_RoostPos") && data.getBoolean("DragonColonies_GuardDeployed")) {

            if (!dragon.getPassengers().isEmpty()) {
                data.remove("DragonColonies_OrphanTicks");
                return;
            }

            if (data.hasUUID("DragonColonies_GuardUUID") && dragon.level() instanceof ServerLevel serverLevel) {
                Entity guard = serverLevel.getEntity(data.getUUID("DragonColonies_GuardUUID"));

                if (guard instanceof AbstractEntityCitizen citizen && citizen.isAlive()) {
                    if (dragon.distanceToSqr(citizen) <= 400.0D) {
                        data.remove("DragonColonies_OrphanTicks");
                        return;
                    }
                }
            }

            int orphanTicks = data.getInt("DragonColonies_OrphanTicks") + 1;
            data.putInt("DragonColonies_OrphanTicks", orphanTicks);

            BlockPos roostPos = BlockPos.of(data.getLong("DragonColonies_RoostPos"));
            double distanceToRoost = dragon.distanceToSqr(roostPos.getX() + 0.5, roostPos.getY() + 1.0, roostPos.getZ() + 0.5);

            if ((orphanTicks > 60 && distanceToRoost < 256.0D) || orphanTicks > 1200) {
                ServerLevel level = (ServerLevel) dragon.level();
                IColony colony = IMinecoloniesAPI.getInstance().getColonyManager().getColonyByPosFromWorld(level, roostPos);

                if (colony != null && colony.getServerBuildingManager().getBuilding(roostPos) instanceof BuildingDragonRoost roost) {
                    DragonStorageModule storage = roost.getStorageModule();
                    UUID roostDragonId = data.hasUUID("DragonColonies_RoostDragonID") ? data.getUUID("DragonColonies_RoostDragonID") : null;

                    if (storage != null && roostDragonId != null && storage.getDragonByRoostId(roostDragonId).isPresent()) {
                        CompoundTag dragonNbt = new CompoundTag();
                        dragon.saveWithoutId(dragonNbt);

                        ResourceLocation entityKey = ForgeRegistries.ENTITY_TYPES.getKey(dragon.getType());
                        if (entityKey != null) {
                            dragonNbt.putString("id", entityKey.toString());
                        }
                        dragonNbt.remove("Passengers");
                        dragonNbt.putString("CustomName", dragon.hasCustomName() ? dragon.getCustomName().getString() : dragon.getName().getString());

                        data.remove("DragonColonies_RoostPos");
                        data.remove("DragonColonies_RoostDragonID");
                        data.remove("DragonColonies_GuardDeployed");
                        data.remove("DragonColonies_OrphanTicks");
                        data.remove("DragonColonies_GuardUUID");

                        storage.updateDragonData(roostDragonId, dragonNbt);
                        dragon.discard();
                        return;
                    }
                }
            }

            if (orphanTicks % 20 == 0) {
                dragon.setTarget(null);
                OmniAttackHandler attackHandler = (OmniAttackHandler) dragon.componentRegistry.get(OmniAttackHandler.class);
                if (attackHandler != null && attackHandler.isAttacking()) {
                    attackHandler.handlePlayerFiring(false);
                }

                if (dragon.canFly()) {
                    if (dragon.getTransportMode() != TransportMode.AIRBORNE) {
                        dragon.setTransportMode(TransportMode.AIRBORNE);
                    }
                    var aiMove = dragon.getAIMovement();
                    if (aiMove != null) {
                        aiMove.setWaypoint(new Vec3(roostPos.getX() + 0.5, roostPos.getY() + 15.0, roostPos.getZ() + 0.5), 1.0D);
                    }
                } else {
                    if (dragon.getTransportMode() != TransportMode.GROUNDED) {
                        dragon.setTransportMode(TransportMode.GROUNDED);
                    }
                    dragon.getNavigation().moveTo(roostPos.getX() + 0.5, roostPos.getY() + 1.0, roostPos.getZ() + 0.5, 1.0D);
                }
            }
        }
    }

    @Inject(
        method = {"remove", "m_142687_"},
        at = @At("HEAD"),
        remap = false,
        require = 0
    )
    private void dragoncolonies$handleDragonRemove(Entity.RemovalReason reason, CallbackInfo ci) {
        DragonBase dragon = (DragonBase) (Object) this;
        CompoundTag data = dragon.getPersistentData();

        if (data.contains("DragonColonies_RoostPos") && data.hasUUID("DragonColonies_RoostDragonID") && !dragon.level().isClientSide()) {
            BlockPos roostPos = BlockPos.of(data.getLong("DragonColonies_RoostPos"));
            UUID roostDragonId = data.getUUID("DragonColonies_RoostDragonID");
            ServerLevel level = (ServerLevel) dragon.level();
            IColony colony = IMinecoloniesAPI.getInstance().getColonyManager().getColonyByPosFromWorld(level, roostPos);

            if (colony != null && colony.getServerBuildingManager().getBuilding(roostPos) instanceof BuildingDragonRoost roost) {
                DragonStorageModule storage = roost.getStorageModule();

                if (storage != null && storage.getDragonByRoostId(roostDragonId).isPresent()) {
                    // Prüfen, ob wir überhaupt noch die aktive Entität sind
                    if (!storage.isEntityValidForRoostId(roostDragonId, dragon.getUUID())) {
                        data.remove("DragonColonies_RoostPos");
                        data.remove("DragonColonies_RoostDragonID");
                        data.remove("DragonColonies_GuardUUID");
                        return;
                    }

                    CompoundTag dragonNbt = new CompoundTag();
                    dragon.saveWithoutId(dragonNbt);

                    ResourceLocation entityKey = ForgeRegistries.ENTITY_TYPES.getKey(dragon.getType());
                    if (entityKey != null) {
                        dragonNbt.putString("id", entityKey.toString());
                    }
                    dragonNbt.remove("Passengers");
                    dragonNbt.putString("CustomName", dragon.hasCustomName() ? dragon.getCustomName().getString() : dragon.getName().getString());

                    if (reason == Entity.RemovalReason.KILLED) {
                        dragonNbt.putBoolean(DragonStorageModule.TAG_IS_DEAD, true);
                        //System.out.println("[DragonColonies] Drache '" + dragon.getName().getString() + "' ist gefallen. Status im Hort auf M.I.A. gesetzt!");
                    }

                    storage.updateDragonData(roostDragonId, dragonNbt);
                    data.remove("DragonColonies_RoostPos");
                    data.remove("DragonColonies_RoostDragonID");
                    data.remove("DragonColonies_GuardUUID");
                }
            }
        }
    }
}