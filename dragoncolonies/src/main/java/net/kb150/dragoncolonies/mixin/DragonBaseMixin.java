package net.kb150.dragoncolonies.mixin;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import net.kb150.dragoncolonies.DragonColonies;
import net.kb150.dragoncolonies.buildings.BuildingDragonRoost;
import net.kb150.dragoncolonies.buildings.modules.DragonStorageModule;
import net.kb150.dragoncolonies.data.WildDragonData;
import net.kb150.dragoncolonies.util.ColonyRelationHelper;
import net.magister.bookofdragons.entity.base.dragon.DragonBase;
import net.magister.liberanimalium.body.state.TransportMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(value = DragonBase.class, remap = false)
public abstract class DragonBaseMixin {

    @Unique
    private boolean dragoncolonies$spawnValidated = false;

    // Signalisiert FAUNA, dass der Kolonie-Drache gezaehmt ist (DragonBase ueberschreibt isTame / m_21824_)
    @Inject(
        method = {"isTame", "m_21824_"},
        at = @At("HEAD"),
        cancellable = true,
        remap = false,
        require = 0
    )
    private void dragoncolonies$isTameForColony(CallbackInfoReturnable<Boolean> cir) {
        DragonBase dragon = (DragonBase) (Object) this;
        if (!dragon.getPassengers().isEmpty() || ColonyRelationHelper.getDragonColonyId(dragon) > 0) {
            cir.setReturnValue(true);
        }
    }

    // Verwichtelt Geister-Duplikate nach Chunk-Reloads, falls der Hort den Lease bereits entzogen hat.
    // In BoD 1.4 wird tickHost pro Tick aufgerufen.
    @Inject(method = {"tickHost", "serverTick", "m_8119_"}, at = @At("HEAD"), remap = false, require = 0)
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

                if (storage != null && !storage.isEntityValidForRoostId(roostDragonId, dragon.getUUID())) {
                    DragonColonies.debug("NAVIGATION", "[VALIDATE-DISCARD] Discarding invalid dragon instance {}", dragon.getUUID());
                    dragon.ejectPassengers();
                    ColonyRelationHelper.cleanColonyData(dragon);
                    dragon.discard();
                }
            }
        }
    }

    // Erlaubt MineColonies-Wachen das Aufsitzen auf erwachsenen Drachen.
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
            cir.setReturnValue(dragon.getGrowthStage() >= 2 && dragon.getPassengers().size() < 2);
        }
    }

    // Verhindert ClassCastExceptions in BoD, wenn eine Wache statt eines Spielers aufsitzt.
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

    // Verhindert, dass BoD die autonome Pfadfindung stoppt, wenn eine Wache aufsitzt.
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

    // Steigflug und Uebergabe an WildDragonData (wird aktiv, sobald DragonColonies_FleeingTicks gesetzt ist).
    @Inject(
        method = {"tickHost", "serverTick", "m_8119_"},
        at = @At("TAIL"),
        remap = false,
        require = 0
    )
    private void dragoncolonies$tickWildFleeing(CallbackInfo ci) {
        DragonBase dragon = (DragonBase) (Object) this;
        if (dragon.level().isClientSide()) return;

        CompoundTag data = dragon.getPersistentData();
        if (!data.contains("DragonColonies_FleeingTicks")) return;

        int fleeTicks = data.getInt("DragonColonies_FleeingTicks") + 1;
        data.putInt("DragonColonies_FleeingTicks", fleeTicks);

        ServerLevel level = (ServerLevel) dragon.level();

        // Ascent ceiling reached or flight timeout elapsed: Handoff to lazy SavedData
        if (dragon.getY() >= 190.0 || fleeTicks >= 200) {
            CompoundTag dragonNbt = new CompoundTag();
            dragon.saveWithoutId(dragonNbt);

            ResourceLocation entityKey = ForgeRegistries.ENTITY_TYPES.getKey(dragon.getType());
            if (entityKey != null) {
                dragonNbt.putString("id", entityKey.toString());
            }

            CompoundTag persistent = dragonNbt.getCompound("ForgeData");
            ColonyRelationHelper.cleanColonyData(persistent);

            double angle = level.random.nextDouble() * 2 * Math.PI;
            double dist = 500.0 + level.random.nextDouble() * 4500.0;
            int targetX = (int) (dragon.getX() + Math.cos(angle) * dist);
            int targetZ = (int) (dragon.getZ() + Math.sin(angle) * dist);
            long chunkKey = ChunkPos.asLong(targetX >> 4, targetZ >> 4);

            WildDragonData.get(level).addDragon(chunkKey, targetX, targetZ, dragonNbt);

            level.sendParticles(ParticleTypes.CLOUD, dragon.getX(), dragon.getY(), dragon.getZ(), 24, 1.8, 0.8, 1.8, 0.04);
            DragonColonies.debug("AI", "[ESCAPE-RELOCATE] Dragon '{}' ascended into clouds. Relocated to chunk [{}, {}]",
                    dragon.getName().getString(), targetX >> 4, targetZ >> 4);

            dragon.discard();
            return;
        }

        if (fleeTicks % 20 == 0 && dragon.canFly()) {
            if (dragon.getTransportMode() != TransportMode.AIRBORNE) {
                dragon.setTransportMode(TransportMode.AIRBORNE);
            }
            dragon.setDeltaMovement(dragon.getDeltaMovement().add(0, 0.25D, 0));
        }
    }

    @Unique
    private static void dragoncolonies$triggerWildFlee(DragonBase dragon, ServerLevel level) {
        dragon.getPersistentData().putInt("DragonColonies_FleeingTicks", 1);
        dragon.setTarget(null);

        if (dragon.canFly()) {
            dragon.setTransportMode(TransportMode.AIRBORNE);
            double angle = level.random.nextDouble() * 2 * Math.PI;
            dragon.setDeltaMovement(Math.cos(angle) * 0.8D, 1.2D, Math.sin(angle) * 0.8D);
        }
    }

    // Leitet herrenlose Drachen zurueck zum Hort, wenn die zugeordnete Wache stirbt.
    @Inject(
        method = {"tickHost", "serverTick", "m_8119_"},
        at = @At("TAIL"),
        remap = false,
        require = 0
    )
    private void dragoncolonies$handleOrphanedDragons(CallbackInfo ci) {
        DragonBase dragon = (DragonBase) (Object) this;
        if (dragon.level().isClientSide()) return;

        CompoundTag data = dragon.getPersistentData();
        if (!data.contains("DragonColonies_RoostPos") || !data.getBoolean("DragonColonies_GuardDeployed")) {
            return;
        }

        if (!dragon.getPassengers().isEmpty()) {
            data.remove("DragonColonies_OrphanTicks");
            return;
        }

        boolean guardAlive = false;
        if (data.hasUUID("DragonColonies_GuardUUID") && dragon.level() instanceof ServerLevel serverLevel) {
            Entity guard = serverLevel.getEntity(data.getUUID("DragonColonies_GuardUUID"));
            if (guard instanceof AbstractEntityCitizen citizen && citizen.isAlive() && !citizen.isRemoved()) {
                guardAlive = true;
            }
        }

        if (guardAlive) {
            data.remove("DragonColonies_OrphanTicks");
            return;
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
                if (storage != null) {
                    DragonColonies.debug("NAVIGATION", "[WATCHDOG-STORE] Orphaned dragon {} returning to roost", dragon.getName().getString());
                    storage.storeDragon(dragon);
                    return;
                }
            }
        }

        if (orphanTicks % 20 == 0) {
            dragon.setTarget(null);
            dragon.dropTrigger(); // Unterbricht laufende Angriffe in FAUNA

            if (dragon.canFly()) {
                if (dragon.getTransportMode() != TransportMode.AIRBORNE) {
                    dragon.setTransportMode(TransportMode.AIRBORNE);
                }
            } else {
                if (dragon.getTransportMode() != TransportMode.GROUNDED) {
                    dragon.setTransportMode(TransportMode.GROUNDED);
                }
                dragon.getNavigation().moveTo(roostPos.getX() + 0.5, roostPos.getY() + 1.0, roostPos.getZ() + 0.5, 1.0D);
            }
        }
    }

    // Verhindert, dass der Drache Kolonisten der eigenen Kolonie anvisiert.
    @Inject(method = {"setTarget", "m_6710_"}, at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void dragoncolonies$neverTargetColonyCitizen(@org.jetbrains.annotations.Nullable LivingEntity target, CallbackInfo ci) {
        if (target == null) return;

        DragonBase dragon = (DragonBase) (Object) this;

        // 1. EISERNE REGEL: Niemals den eigenen Reiter/Passagier anvisieren!
        if (dragon.hasPassenger(target) || dragon.getPassengers().contains(target)) {
            ci.cancel();
            return;
        }

        // 2. Kolonie-Check fuer Buerger
        if (target instanceof AbstractEntityCitizen citizen) {
            if (ColonyRelationHelper.isSameColony(citizen, dragon)) {
                DragonColonies.debug("COMBAT", "[TARGET-BLOCKED] Drache {} darf Kolonist {} nicht anvisieren",
                        dragon.getName().getString(), citizen.getName().getString());
                ci.cancel();
            }
        }
    }

    // Synchronisiert Statuswerte beim Entfernen/Toeten der Entity zurueck in das Hort-Modul.
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
                    if (!storage.isEntityValidForRoostId(roostDragonId, dragon.getUUID())) {
                        ColonyRelationHelper.cleanColonyData(dragon);
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
                    }

                    storage.updateDragonData(roostDragonId, dragonNbt);
                    ColonyRelationHelper.cleanColonyData(dragon);
                }
            }
        }
    }
}