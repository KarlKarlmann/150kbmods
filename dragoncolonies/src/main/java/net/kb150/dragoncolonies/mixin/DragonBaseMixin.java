package net.kb150.dragoncolonies.mixin;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import net.kb150.dragoncolonies.buildings.BuildingDragonRoost;
import net.kb150.dragoncolonies.buildings.modules.DragonStorageModule;
import net.magister.bookofdragons.entity.base.dragon.DragonBase;
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

/**
 * Mixin für DragonBase.
 * Fälscht isVehicle() für Bürger-Passagiere, fängt Löschungen sicher ab, 
 * kümmert sich um das Zurückholen verwaister Wachendrachen,
 * filtert Geister/Duplikat-Entitäten beim Spawnen heraus und verarbeitet
 * Zuneigungsverlust bei Verhungern.
 */
@Mixin(DragonBase.class)
public abstract class DragonBaseMixin {

    // --- Status-Flag für die Duplikat-Prüfung ---
    @Unique
    private boolean dragoncolonies$spawnValidated = false;

    // --- First-Tick-Validation zur Erkennung von Geister-Drachen ---
    @Inject(method = {"serverTick", "m_8119_"}, at = @At("HEAD"), remap = false, require = 0)
    private void dragoncolonies$validateSpawn(CallbackInfo ci) {
        if (this.dragoncolonies$spawnValidated) return; 
        
        DragonBase dragon = (DragonBase) (Object) this;
        if (dragon.level().isClientSide()) return;

        this.dragoncolonies$spawnValidated = true; // Einmal ausführen und danach nie wieder

        CompoundTag data = dragon.getPersistentData();
        if (data.contains("DragonColonies_RoostPos")) {
            BlockPos roostPos = BlockPos.of(data.getLong("DragonColonies_RoostPos"));
            ServerLevel level = (ServerLevel) dragon.level();
            
            // Kolonie und Hort direkt über die API abfragen (ist unabhängig vom geladenen Chunk)
            IColony colony = IMinecoloniesAPI.getInstance().getColonyManager().getColonyByPosFromWorld(level, roostPos);
            if (colony != null && colony.getServerBuildingManager().getBuilding(roostPos) instanceof BuildingDragonRoost roost) {
                DragonStorageModule storage = roost.getStorageModule();
                
                if (storage != null) {
                    storage.getDragonByUUID(dragon.getUUID()).ifPresent(tag -> {
                        // Geist/Duplikat ODER bereits gefallener Drache (M.I.A.) erkannt!
                        if (!tag.getBoolean("Deployed") || tag.getBoolean("IsDead")) {
                            System.out.println("[DragonColonies] Ungültiger Drache (Geist/Tod) entfernt: " + dragon.getName().getString());
                            
                            // WICHTIG: Das RoostPos-Tag entfernen, damit unser remove-Mixin 
                            // nicht fälschlicherweise den Speicher im Hort überschreibt!
                            data.remove("DragonColonies_RoostPos"); 
                            dragon.discard();
                        }
                    });
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

    /**
     * Verhindert ClassCastExceptions in Book of Dragons.
     */
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

    /**
     * DURCHBRUCH FÜR DIE BEWEGUNG:
     * DragonBaseGoal prüft !this.dragon.m_20160_() (isVehicle()).
     * Indem isVehicle() für Bürger-Passagiere false zurückgibt, laufen alle KI-Flugziele
     * von Book of Dragons ungestört weiter!
     */
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

    // --- HUNGER- & AFFECTION-KONSEQUENZ ---
    @Inject(
        method = {"serverTick", "m_8119_"},
        at = @At("TAIL"),
        remap = false,
        require = 0
    )
    private void dragoncolonies$handleStarvationAffection(CallbackInfo ci) {
        DragonBase dragon = (DragonBase) (Object) this;
        if (dragon.level().isClientSide()) return;

        // Liest das Hungerlevel aus den Synced Data von Book of Dragons
        int foodLevel = dragon.getEntityData().get(DragonBase.getHungerLevelData());

        // Alle 60 Ticks (3 Sekunden) bei Hunger 0 Zuneigung abbauen
        if (foodLevel <= 0 && dragon.tickCount % 60 == 0) {
            
            net.magister.bookofdragons.entity.component.TamingComponent taming = 
                (net.magister.bookofdragons.entity.component.TamingComponent) dragon.componentRegistry.get(net.magister.bookofdragons.entity.component.TamingComponent.class);
            
            if (taming != null && dragon.getOwnerUUID() != null) {
                java.util.UUID ownerUUID = dragon.getOwnerUUID();
                int currentAffection = taming.getAffection(ownerUUID);

                if (currentAffection > 0) {
                    int newAffection = Math.max(0, currentAffection - 5);
                    taming.setAffection(ownerUUID, newAffection);
                    dragon.getEntityData().set(DragonBase.getDebugAffectionData(), newAffection);
                    
                } else if (dragon.isTame()) {
                    // Keine Zuneigung mehr -> Drache wirft alle Reiter ab und bricht die Zähmung
                    dragon.ejectPassengers();
                    dragon.setTame(false);
                    dragon.setOwnerUUID(null);

                    // --- HORT-BINDUNG UND STORAGE ENTFERNEN ---
                    CompoundTag data = dragon.getPersistentData();
                    if (data.contains("DragonColonies_RoostPos")) {
                        BlockPos roostPos = BlockPos.of(data.getLong("DragonColonies_RoostPos"));
                        ServerLevel level = (ServerLevel) dragon.level();
                        IColony colony = IMinecoloniesAPI.getInstance().getColonyManager().getColonyByPosFromWorld(level, roostPos);
                        
                        if (colony != null && colony.getServerBuildingManager().getBuilding(roostPos) instanceof BuildingDragonRoost roost) {
                            DragonStorageModule storage = roost.getStorageModule();
                            if (storage != null) {
                                storage.removeDragon(dragon.getUUID());
                            }
                        }
                        
                        // PersistentData-Tags säubern
                        data.remove("DragonColonies_RoostPos");
                        data.remove("DragonColonies_GuardDeployed");
                        data.remove("DragonColonies_OrphanTicks");
                        data.remove("DragonColonies_GuardUUID");
                    }
                    
                    System.out.println("[DragonColonies] Drache '" + dragon.getName().getString() + "' hat wegen Verhungerns die Bindung verloren und ist wieder wild!");
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
        
        // Nur Drachen mit aktivem Wachteinsatz prüfen
        if (data.contains("DragonColonies_RoostPos") && data.getBoolean("DragonColonies_GuardDeployed")) {
            
            // 1. Ist jemand aufgestiegen? -> Nicht verwaist!
            if (!dragon.getPassengers().isEmpty()) {
                data.remove("DragonColonies_OrphanTicks");
                return;
            }

            // 2. O(1) DIREKT-CHECK: Finden wir die Wache per Hash-Lookup im ServerLevel in 20m Nähe?
            if (data.hasUUID("DragonColonies_GuardUUID") && dragon.level() instanceof ServerLevel serverLevel) {
                Entity guard = serverLevel.getEntity(data.getUUID("DragonColonies_GuardUUID"));
                
                if (guard instanceof AbstractEntityCitizen citizen && citizen.isAlive()) {
                    if (dragon.distanceToSqr(citizen) <= 400.0D) { // 20 Blöcke Radius (20^2 = 400)
                        data.remove("DragonColonies_OrphanTicks");
                        return;
                    }
                }
            }

            // --- AB HIER: DRACHE IST VERWAIST ---
            int orphanTicks = data.getInt("DragonColonies_OrphanTicks") + 1;
            data.putInt("DragonColonies_OrphanTicks", orphanTicks);

            BlockPos roostPos = BlockPos.of(data.getLong("DragonColonies_RoostPos"));
            double distanceToRoost = dragon.distanceToSqr(roostPos.getX() + 0.5, roostPos.getY() + 1.0, roostPos.getZ() + 0.5);

            // Speichern & Despawnen nach 30 Sekunden ODER wenn bereits am Hort
            if (distanceToRoost < 256.0D || orphanTicks > 600) {
                ServerLevel level = (ServerLevel) dragon.level();
                IColony colony = IMinecoloniesAPI.getInstance().getColonyManager().getColonyByPosFromWorld(level, roostPos);
                
                if (colony != null && colony.getServerBuildingManager().getBuilding(roostPos) instanceof BuildingDragonRoost roost) {
                    DragonStorageModule storage = roost.getStorageModule();
                    
                    if (storage != null && storage.getDragonByUUID(dragon.getUUID()).isPresent()) {
                        CompoundTag dragonNbt = new CompoundTag();
                        dragon.saveWithoutId(dragonNbt);
                        
                        ResourceLocation entityKey = ForgeRegistries.ENTITY_TYPES.getKey(dragon.getType());
                        if (entityKey != null) {
                            dragonNbt.putString("id", entityKey.toString());
                        }
                        dragonNbt.remove("Passengers");
                        dragonNbt.putString("CustomName", dragon.hasCustomName() ? dragon.getCustomName().getString() : dragon.getName().getString());
                        
                        data.remove("DragonColonies_RoostPos");
                        data.remove("DragonColonies_GuardDeployed");
                        data.remove("DragonColonies_OrphanTicks");
                        data.remove("DragonColonies_GuardUUID");

                        storage.updateDragonData(dragon.getUUID(), dragonNbt);
                        dragon.discard();
                        return;
                    }
                }
            }

            // Heimflug ansteuern
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
        
        // Wir fangen nun ALLES ab (auch den Tod), solange der Drache zu einem Hort gehört.
        if (data.contains("DragonColonies_RoostPos") && !dragon.level().isClientSide()) {
            BlockPos roostPos = BlockPos.of(data.getLong("DragonColonies_RoostPos"));
            ServerLevel level = (ServerLevel) dragon.level();
            IColony colony = IMinecoloniesAPI.getInstance().getColonyManager().getColonyByPosFromWorld(level, roostPos);
            
            if (colony != null && colony.getServerBuildingManager().getBuilding(roostPos) instanceof BuildingDragonRoost roost) {
                DragonStorageModule storage = roost.getStorageModule();
                
                if (storage != null) {
                    if (storage.getDragonByUUID(dragon.getUUID()).isPresent()) {
                        CompoundTag dragonNbt = new CompoundTag();
                        dragon.saveWithoutId(dragonNbt);
                        
                        ResourceLocation entityKey = ForgeRegistries.ENTITY_TYPES.getKey(dragon.getType());
                        if (entityKey != null) {
                            dragonNbt.putString("id", entityKey.toString());
                        }
                        dragonNbt.remove("Passengers");
                        dragonNbt.putString("CustomName", dragon.hasCustomName() ? dragon.getCustomName().getString() : dragon.getName().getString());
                        
                        // --- M.I.A. Logik ---
                        if (reason == Entity.RemovalReason.KILLED) {
                            dragonNbt.putBoolean("IsDead", true);
                            System.out.println("[DragonColonies] Drache '" + dragon.getName().getString() + "' ist gefallen. Status im Hort auf M.I.A. gesetzt!");
                        } else {
                            System.out.println("[DragonColonies] Failsafe: Drache '" + dragon.getName().getString() + "' hat seine Daten im Hort aktualisiert (Grund: " + reason + ")");
                        }
                        
                        storage.updateDragonData(dragon.getUUID(), dragonNbt);
                        data.remove("DragonColonies_RoostPos"); 
                        data.remove("DragonColonies_GuardUUID");
                    }
                }
            }
        }
    }
}