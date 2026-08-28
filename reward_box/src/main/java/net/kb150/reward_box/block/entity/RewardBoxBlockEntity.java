package net.kb150.reward_box.block.entity;

import net.kb150.reward_box.init.RewardBoxRegistry;
import net.kb150.reward_box.util.RewardBoxLootGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import java.util.UUID;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.ChestLidController;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import net.minecraft.world.level.block.entity.LidBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class RewardBoxBlockEntity extends RandomizableContainerBlockEntity implements LidBlockEntity {
    private NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
    private String boxId = "reward_box:default";
    private int tier = 1;
    private boolean lootGenerated = false;
    private UUID ownerUuid;
    private String ownerName;
    private long lockExpiryGameTime = 0;
    // Transient: wird NICHT gespeichert. Markiert, dass der aktuelle Abbau-Vorgang
    // von einem Kreativ-Spieler ausgeht, damit onRemove() im Block das Item-Drop unterdrücken kann.
    private boolean creativeDestroy = false;
    private final ChestLidController chestLidController = new ChestLidController();
    private final ContainerOpenersCounter openersCounter = new ContainerOpenersCounter() {
        @Override
        protected void onOpen(Level level, BlockPos pos, BlockState state) {
            RewardBoxBlockEntity.playSound(level, pos, state, SoundEvents.CHEST_OPEN);
        }

        @Override
        protected void onClose(Level level, BlockPos pos, BlockState state) {
            RewardBoxBlockEntity.playSound(level, pos, state, SoundEvents.CHEST_CLOSE);
        }

        @Override
        protected void openerCountChanged(Level level, BlockPos pos, BlockState state, int previousCount, int newCount) {
            RewardBoxBlockEntity.this.signalOpenCount(level, pos, state, previousCount, newCount);
        }

        @Override
        protected boolean isOwnContainer(Player player) {
            if (player.containerMenu instanceof ChestMenu menu) {
                return menu.getContainer() == RewardBoxBlockEntity.this;
            }
            return false;
        }
    };

    public RewardBoxBlockEntity(BlockPos pos, BlockState state) {
        super(RewardBoxRegistry.REWARD_BOX_BE.get(), pos, state);
    }

    public static void lidAnimateTick(Level level, BlockPos pos, BlockState state, RewardBoxBlockEntity blockEntity) {
        blockEntity.chestLidController.tickLid();
    }

    @Override
    public float getOpenNess(float partialTicks) {
        return this.chestLidController.getOpenness(partialTicks);
    }

    @Override
    public void startOpen(Player player) {
        if (!this.remove && !player.isSpectator()) {
            this.openersCounter.incrementOpeners(player, this.getLevel(), this.getBlockPos(), this.getBlockState());
        }
    }

    @Override
    public void stopOpen(Player player) {
        if (!this.remove && !player.isSpectator()) {
            this.openersCounter.decrementOpeners(player, this.getLevel(), this.getBlockPos(), this.getBlockState());
        }
    }

    @Override
    public boolean triggerEvent(int id, int type) {
        if (id == 1) {
            this.chestLidController.shouldBeOpen(type > 0);
            return true;
        }
        return super.triggerEvent(id, type);
    }

    private static void playSound(Level level, BlockPos pos, BlockState state, SoundEvent sound) {
        level.playSound(null, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, sound, SoundSource.BLOCKS, 0.5F, level.random.nextFloat() * 0.1F + 0.9F);
    }

    private void signalOpenCount(Level level, BlockPos pos, BlockState state, int previousCount, int newCount) {
        level.blockEvent(pos, state.getBlock(), 1, newCount);
    }

    public void setBoxData(String boxId, int tier) {
        this.boxId = boxId;
        this.tier = tier;
        this.setChanged();
        if (this.level != null && !this.level.isClientSide) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    public String getBoxId() {
        return this.boxId;
    }

    public int getTier() {
        return this.tier;
    }

    public void generateLootNow() {
        if (!this.lootGenerated && this.level instanceof ServerLevel serverLevel) {
            this.lootGenerated = true;
            RewardBoxLootGenerator.fillChestWithLoot(this.boxId, this.tier, serverLevel, Vec3.atCenterOf(this.worldPosition), this.items);
            this.setChanged();
        }
    }

    /**
     * Markiert den Loot als "bereits generiert", ohne tatsächlich Loot zu erzeugen.
     * Wird beim Abbau im Kreativmodus nach clearContent() verwendet, damit
     * generateLootNow() (z. B. beim Zerstören) den Loot nicht nachträglich wieder befüllt.
     */
    public void preventFutureLootGeneration() {
        this.lootGenerated = true;
        this.setChanged();
    }

    public void markCreativeDestroy() {
        this.creativeDestroy = true;
    }

    public boolean isCreativeDestroy() {
        return this.creativeDestroy;
    }
    
    public void setOwner(Player player, int lockDurationSeconds) {
        if (lockDurationSeconds > 0) {
            this.ownerUuid = player.getUUID();
            this.ownerName = player.getScoreboardName();
            if (this.level != null) {
                this.lockExpiryGameTime = this.level.getGameTime() + (lockDurationSeconds * 20L);
            }
            this.setChanged();
        }
    }

    public boolean isLockedFor(Player player) {
        if (this.ownerUuid == null || player.hasPermissions(2)) return false;
        if (player.getUUID().equals(this.ownerUuid)) return false;
        return this.level != null && this.level.getGameTime() < this.lockExpiryGameTime;
    }

    public Component getOwnerDisplayName() {
        if (this.ownerName != null && !this.ownerName.isEmpty()) {
            return Component.literal(this.ownerName);
        }
        return Component.translatable("gui.reward_box.unknown_owner");
    }

    public long getRemainingLockSeconds() {
        if (this.level == null || this.level.getGameTime() >= this.lockExpiryGameTime) return 0;
        return (this.lockExpiryGameTime - this.level.getGameTime()) / 20L;
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return this.items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.reward_box.chest");
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return ChestMenu.threeRows(containerId, inventory, this);
    }

    @Override
    public int getContainerSize() {
        return 27;
    }

    @Override
    public void load(CompoundTag nbt) {
        super.load(nbt);
        this.items = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(nbt, this.items);
        if (nbt.contains("BoxId")) this.boxId = nbt.getString("BoxId");
        if (nbt.contains("RewardTier")) this.tier = nbt.getInt("RewardTier");
        this.lootGenerated = nbt.getBoolean("LootGenerated");
        if (nbt.hasUUID("OwnerUUID")) this.ownerUuid = nbt.getUUID("OwnerUUID");
        if (nbt.contains("OwnerName")) this.ownerName = nbt.getString("OwnerName");
        this.lockExpiryGameTime = nbt.getLong("LockExpiryTime");
    }

    @Override
    protected void saveAdditional(CompoundTag nbt) {
        super.saveAdditional(nbt);
        ContainerHelper.saveAllItems(nbt, this.items);
        nbt.putString("BoxId", this.boxId);
        nbt.putInt("RewardTier", this.tier);
        nbt.putBoolean("LootGenerated", this.lootGenerated);
        if (this.ownerUuid != null) nbt.putUUID("OwnerUUID", this.ownerUuid);
        if (this.ownerName != null) nbt.putString("OwnerName", this.ownerName);
        nbt.putLong("LockExpiryTime", this.lockExpiryGameTime);
    }

    @Override
    public CompoundTag getUpdateTag() {
        // super.getUpdateTag() liefert per Default nur ein LEERES CompoundTag zurück
        // (kein automatischer saveAdditional()-Aufruf in dieser BlockEntity-Basisklasse).
        // saveAdditional() ist hier daher notwendig, u. a. damit BoxId zum Client synct
        // und der Renderer die richtige dynamische Textur wählen kann.
        CompoundTag nbt = super.getUpdateTag();
        this.saveAdditional(nbt);
        return nbt;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}