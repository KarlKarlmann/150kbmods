package net.kb150.superbwarfarespawner;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;

public class VehicleMarkerEntity extends Mob {

    private boolean isTransforming = false;

    public VehicleMarkerEntity(EntityType<? extends Mob> type, Level level) {
        super(type, level);
        WorldVehicleSpawner.LOGGER.info("[DEBUG-MARKER] [{}] KREIERT! Pos: [{}, {}, {}] Thread: {}", 
                this.getId(), this.getX(), this.getY(), this.getZ(), Thread.currentThread().getName());
    }

    @Override
    public void tick() {
        super.tick();

        WorldVehicleSpawner.LOGGER.info("[TICK-TRACE] [{}] TICK START. Pos: [{}, {}, {}]", this.getId(), this.getX(), this.getY(), this.getZ());

        if (this.level().isClientSide()) {
            WorldVehicleSpawner.LOGGER.info("[TICK-TRACE] [{}] Abbruch: Ist Client-Side.", this.getId());
            return;
        }
        if (!this.isAlive()) {
            WorldVehicleSpawner.LOGGER.info("[TICK-TRACE] [{}] Abbruch: Ist nicht Alive.", this.getId());
            return;
        }
        if (this.isTransforming) {
            WorldVehicleSpawner.LOGGER.info("[TICK-TRACE] [{}] Abbruch: isTransforming ist bereits true.", this.getId());
            return;
        }

        this.isTransforming = true;
        WorldVehicleSpawner.LOGGER.info("[TICK-TRACE] [{}] Starte Raum-Check...", this.getId());

        try {
            if (this.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                boolean hasSpace = true;
                BlockPos pos = this.blockPosition();

                for (int x = -1; x <= 1; x++) {
                    for (int z = -1; z <= 1; z++) {
                        if (serverLevel.getBlockState(pos.offset(x, 1, z)).blocksMotion()) {
                            WorldVehicleSpawner.LOGGER.info("[TICK-TRACE] [{}] Blockiert bei Offset [x:{}, z:{}].", this.getId(), x, z);
                            hasSpace = false;
                            break;
                        }
                    }
                    if (!hasSpace) break;
                }

                if (hasSpace) {
                    WorldVehicleSpawner.LOGGER.info("[TICK-TRACE] [{}] Platz OK. Verwandle...", this.getId());
                    WorldVehicleSpawner.transformMarkerToVehicle(serverLevel, this);
                } else {
                    WorldVehicleSpawner.LOGGER.info("[TICK-TRACE] [{}] Zu eng. Verwandlung abgebrochen.", this.getId());
                }
            }
        } catch (Exception e) {
            WorldVehicleSpawner.LOGGER.error("[TICK-TRACE] [{}] Exception gefangen:", this.getId(), e);
        } finally {
            WorldVehicleSpawner.LOGGER.info("[TICK-TRACE] [{}] Rufe discard() auf.", this.getId());
            this.discard();
        }
    }
}