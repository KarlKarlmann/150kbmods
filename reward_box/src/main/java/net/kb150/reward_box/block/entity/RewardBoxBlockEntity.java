package net.kb150.reward_box.block.entity;

import net.kb150.reward_box.init.RewardBoxRegistry;
import net.kb150.reward_box.init.RewardBoxSounds;
import net.kb150.reward_box.util.RewardBoxLootGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.ChestLidController;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import net.minecraft.world.level.block.entity.LidBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class RewardBoxBlockEntity extends RandomizableContainerBlockEntity implements LidBlockEntity {
    private NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
    private String boxId = "reward_box:default";
    private int tier = 1;
    private boolean lootGenerated = false;
    private UUID ownerUuid;
    private String ownerName;
    private long lockExpiryGameTime = 0;
    private boolean creativeDestroy = false;
    public final ChestLidController chestLidController = new ChestLidController();
    
    // Animations & Server-Security Status
    public int clientAnimationTick = 0;
    public int serverAnimationTick = 0;
    private UUID openingPlayer = null;
    
    // Cached List für 1:1 zerlegten 3D-Loot
    private List<ItemStack> cachedFlattenedItems = null;

    private final ContainerOpenersCounter openersCounter = new ContainerOpenersCounter() {
        @Override
        protected void onOpen(Level level, BlockPos pos, BlockState state) {
            level.playSound(null, pos.getX()+0.5, pos.getY()+0.5, pos.getZ()+0.5, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.5F, level.random.nextFloat() * 0.1F + 0.9F);
        }
        @Override
        protected void onClose(Level level, BlockPos pos, BlockState state) {
            level.playSound(null, pos.getX()+0.5, pos.getY()+0.5, pos.getZ()+0.5, SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS, 0.5F, level.random.nextFloat() * 0.1F + 0.9F);
        }
        @Override
        protected void openerCountChanged(Level level, BlockPos pos, BlockState state, int prev, int count) {
            level.blockEvent(pos, state.getBlock(), 1, count);
        }
        @Override
        protected boolean isOwnContainer(Player player) {
            return player.containerMenu instanceof net.kb150.reward_box.inventory.RewardBoxMenu;
        }
    };

    public RewardBoxBlockEntity(BlockPos pos, BlockState state) {
        super(RewardBoxRegistry.REWARD_BOX_BE.get(), pos, state);
    }

    public void startServerAnimation(ServerPlayer player) {
        // Dynamische Zeitberechnung (identisch mit dem Client!): 
        // 40 Ticks Start + (Anzahl Items * 2) + 60 Ticks Flug- und Landepuffer
        int totalItems = getFlattenedRenderItems().size();
        int duration = 40 + (totalItems * 2) + 60; 
        
        this.serverAnimationTick = duration; 
        this.openingPlayer = player.getUUID();
        this.setChanged();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, RewardBoxBlockEntity be) {
        if (be.serverAnimationTick > 0) {
            be.serverAnimationTick--;
            // Wenn der Countdown abgelaufen ist (letztes Item ist gelandet!), geben wir das Menü frei!
            if (be.serverAnimationTick == 0 && be.openingPlayer != null) {
                Player player = level.getPlayerByUUID(be.openingPlayer);
                if (player instanceof ServerPlayer sp) {
                    NetworkHooks.openScreen(sp, be, buf -> {
                        buf.writeBlockPos(pos);
                        buf.writeBoolean(false);
                    });
                }
                be.openingPlayer = null;
            }
        }
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, RewardBoxBlockEntity be) {
        be.chestLidController.tickLid();
        
        if (be.clientAnimationTick > 0) {
            be.clientAnimationTick++;
            int t = be.clientAnimationTick;
            List<ItemStack> flattened = be.getFlattenedRenderItems();

            // 1. Anticipation Sound & FIX FÜR DAS DECKEL-KLAPPERN
            if (t == 1) {
                be.chestLidController.shouldBeOpen(false); // Verhindert das Zucken des Deckels!
                level.playLocalSound(pos.getX()+0.5, pos.getY()+0.5, pos.getZ()+0.5, RewardBoxSounds.BOX_CHARGE.get(), SoundSource.BLOCKS, 1.0f, 0.8f, false);
            }
            
            // 2. The Burst! (Deckel reißt auf)
            if (t == 40) {
                be.chestLidController.shouldBeOpen(true);
                level.playLocalSound(pos.getX()+0.5, pos.getY()+0.5, pos.getZ()+0.5, RewardBoxSounds.BOX_BURST.get(), SoundSource.BLOCKS, 1.0f, 1.0f, false);
                be.spawnBurstParticles();
            }

            // 3. Maschinenpistolen-Wusch & exaktes Klingeling bei Rückkehr
            for (int i = 0; i < flattened.size(); i++) {
                int itemStartTick = 40 + (i * 2); // Alle 2 Ticks schießt ein Item raus!
                
                // Abschuss
                if (t == itemStartTick) {
                    level.playLocalSound(pos.getX()+0.5, pos.getY()+0.5, pos.getZ()+0.5, RewardBoxSounds.ITEM_SWOOSH.get(), SoundSource.BLOCKS, 0.4f, 0.8f + (level.random.nextFloat() * 0.4f), false);
                }
                
                // Wiedereintritt in die Kiste (exakt 50 Ticks nach Abschuss)
                if (t == itemStartTick + 50) { 
                    float pitchProgress = (float)i / Math.max(1, flattened.size());
                    float pitch = 1.0f + (pitchProgress * 1.0f); 
                    level.playLocalSound(pos.getX()+0.5, pos.getY()+0.5, pos.getZ()+0.5, RewardBoxSounds.ITEM_LAND.get(), SoundSource.BLOCKS, 0.7f, Math.min(pitch, 2.0f), false);
                }
            }

            // 4. Ende der Animation (Dynamisch + Puffer für sanftes Schließen)
            int duration = 40 + (flattened.size() * 2) + 60; // 50 Ticks Flugzeit + 10 Ticks Gnadenfrist
            if (t > duration) {
                be.chestLidController.shouldBeOpen(false);
                be.clientAnimationTick = 0;
            }
        }
    }

    public List<ItemStack> getFlattenedRenderItems() {
        if (this.cachedFlattenedItems == null) {
            this.cachedFlattenedItems = new ArrayList<>();
            
            // Erst die Stacks nach Seltenheit sortieren
            List<ItemStack> baseSorted = new ArrayList<>();
            for (ItemStack stack : this.items) {
                if (!stack.isEmpty()) baseSorted.add(stack);
            }
            baseSorted.sort(Comparator.comparingInt(a -> a.getRarity().ordinal()));
            
            // Dann JEDES Item zerlegen (1 Stack = X Items für den Rendereffekt)
            for (ItemStack stack : baseSorted) {
                int count = stack.getCount();
                for (int i = 0; i < count; i++) {
                    ItemStack single = stack.copy();
                    single.setCount(1);
                    this.cachedFlattenedItems.add(single);
                }
            }
        }
        return this.cachedFlattenedItems;
    }

    @Override
    public boolean triggerEvent(int id, int type) {
        if (id == 1) {
            this.chestLidController.shouldBeOpen(type > 0);
            return true;
        } else if (id == 2) {
            // GLOBALES EVENT: Startet die Kisten-Wackel-Animation
            if (this.level != null && this.level.isClientSide) {
                this.clientAnimationTick = 1;
            }
            return true;
        }
        return super.triggerEvent(id, type);
    }

    public void spawnBurstParticles() {
        if (this.level == null) return;
        double cx = this.getBlockPos().getX() + 0.5D;
        double cy = this.getBlockPos().getY() + 0.8D;
        double cz = this.getBlockPos().getZ() + 0.5D;
        net.minecraft.util.RandomSource random = this.level.getRandom();
        for (int i = 0; i < 40; i++) {
            this.level.addParticle(net.minecraft.core.particles.ParticleTypes.END_ROD, cx, cy, cz, (random.nextDouble()-0.5)*0.6, random.nextDouble()*0.6+0.2, (random.nextDouble()-0.5)*0.6);
        }
    }

    @Override public float getOpenNess(float partialTicks) { return this.chestLidController.getOpenness(partialTicks); }
    @Override public void startOpen(Player player) { if (!this.remove && !player.isSpectator()) this.openersCounter.incrementOpeners(player, this.getLevel(), this.getBlockPos(), this.getBlockState()); }
    @Override public void stopOpen(Player player) { if (!this.remove && !player.isSpectator()) this.openersCounter.decrementOpeners(player, this.getLevel(), this.getBlockPos(), this.getBlockState()); }

    public void setBoxData(String id, int t) { this.boxId = id; this.tier = t; this.setChanged(); if (this.level != null && !this.level.isClientSide) this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), Block.UPDATE_ALL); }
    public String getBoxId() { return this.boxId; }
    public int getTier() { return this.tier; }

    public boolean generateLootNow() {
        if (!this.lootGenerated && this.level instanceof ServerLevel serverLevel) {
            this.lootGenerated = true;
            RewardBoxLootGenerator.fillChestWithLoot(this.boxId, this.tier, serverLevel, Vec3.atCenterOf(this.worldPosition), this.items);
            this.setChanged();
            serverLevel.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), Block.UPDATE_ALL);
            return true; 
        }
        return false;
    }

    public void preventFutureLootGeneration() { this.lootGenerated = true; this.setChanged(); }
    public void markCreativeDestroy() { this.creativeDestroy = true; }
    public boolean isCreativeDestroy() { return this.creativeDestroy; }
    
    public void setOwner(Player player, int lockSec) {
        if (lockSec > 0) {
            this.ownerUuid = player.getUUID();
            this.ownerName = player.getScoreboardName();
            if (this.level != null) this.lockExpiryGameTime = this.level.getGameTime() + (lockSec * 20L);
            this.setChanged();
        }
    }

    public boolean isLockedFor(Player player) {
        if (this.ownerUuid == null || player.hasPermissions(2) || player.getUUID().equals(this.ownerUuid)) return false;
        return this.level != null && this.level.getGameTime() < this.lockExpiryGameTime;
    }
    
    public Component getOwnerDisplayName() { return (this.ownerName != null && !this.ownerName.isEmpty()) ? Component.literal(this.ownerName) : Component.translatable("gui.reward_box.unknown_owner"); }
    public long getRemainingLockSeconds() { return (this.level == null || this.level.getGameTime() >= this.lockExpiryGameTime) ? 0 : (this.lockExpiryGameTime - this.level.getGameTime()) / 20L; }
    
    @Override protected NonNullList<ItemStack> getItems() { return this.items; }
    @Override protected void setItems(NonNullList<ItemStack> i) { this.items = i; this.cachedFlattenedItems = null; }
    @Override protected Component getDefaultName() { return Component.translatable("container.reward_box.chest"); }
    @Override protected AbstractContainerMenu createMenu(int id, Inventory inv) { return new net.kb150.reward_box.inventory.RewardBoxMenu(id, inv, this); }
    @Override public int getContainerSize() { return 27; }

    @Override
    public void load(CompoundTag nbt) {
        super.load(nbt);
        this.items = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(nbt, this.items);
        this.cachedFlattenedItems = null; // WICHTIG: Cache invalidieren bei NBT Sync!
        
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
    
    @Override public CompoundTag getUpdateTag() { CompoundTag nbt = super.getUpdateTag(); this.saveAdditional(nbt); return nbt; }
    @Override public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}