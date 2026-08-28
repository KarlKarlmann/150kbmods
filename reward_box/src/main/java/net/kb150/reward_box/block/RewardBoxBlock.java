package net.kb150.reward_box.block;

import net.kb150.reward_box.block.entity.RewardBoxBlockEntity;
import net.kb150.reward_box.util.RewardBoxConfigManager;
import net.kb150.reward_box.init.RewardBoxRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.Containers;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.material.FluidState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class RewardBoxBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    private static final Logger LOGGER = LogManager.getLogger();

    public RewardBoxBlock() {
        super(BlockBehaviour.Properties.of()
                .strength(2.5F)
                .sound(SoundType.WOOD)
                .noOcclusion()
                .noLootTable());
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof RewardBoxBlockEntity box) {
            String boxId = "reward_box:default";
            int tier = 1;

            if (stack.hasTag()) {
                CompoundTag tag = stack.getTag();
                if (tag.contains("BoxId")) {
                    boxId = tag.getString("BoxId");
                }
                if (tag.contains("RewardTier")) {
                    tier = tag.getInt("RewardTier");
                }
            }

            box.setBoxData(boxId, tier);

            if (placer instanceof Player player) {
                RewardBoxConfigManager.BoxDefinition def = RewardBoxConfigManager.getDefinition(box.getBoxId());
                int lockSeconds = def != null ? def.lockDurationSeconds() : 300;
                box.setOwner(player, lockSeconds);
            }
        }
    }

    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest, FluidState fluid) {
        if (level.getBlockEntity(pos) instanceof RewardBoxBlockEntity rewardBox) {
            // Lock-Prüfung sowohl auf Client als auch Server ausführen, um Desyncs zu vermeiden
            if (rewardBox.isLockedFor(player)) {
                if (!level.isClientSide()) {
                    long remainingSeconds = rewardBox.getRemainingLockSeconds();
                    long minutes = remainingSeconds / 60;
                    long seconds = remainingSeconds % 60;

                    player.displayClientMessage(
                        Component.translatable("message.reward_box.locked", rewardBox.getOwnerDisplayName(), minutes, seconds)
                            .withStyle(ChatFormatting.RED),
                        true
                    );
                }
                return false; // Verhindert den Abbau synchron auf Client & Server
            }
        }
        return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof RewardBoxBlockEntity rewardBox && player instanceof ServerPlayer serverPlayer) {
                
                if (rewardBox.isLockedFor(serverPlayer)) {
                    long remainingSeconds = rewardBox.getRemainingLockSeconds();
                    long minutes = remainingSeconds / 60;
                    long seconds = remainingSeconds % 60;
                    
                    serverPlayer.displayClientMessage(
                        Component.translatable("message.reward_box.locked", rewardBox.getOwnerDisplayName(), minutes, seconds)
                            .withStyle(ChatFormatting.RED), 
                        true
                    );
                    return InteractionResult.CONSUME;
                }

                rewardBox.generateLootNow();
                NetworkHooks.openScreen(serverPlayer, rewardBox, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && player.isCreative()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof RewardBoxBlockEntity rewardBox) {
                rewardBox.clearContent();
                // Verhindert, dass onRemove() gleich danach neuen Loot generiert und droppt -
                // im Kreativmodus soll das Abbauen bewusst keinen Loot erzeugen.
                rewardBox.preventFutureLootGeneration();
                // Markiert den Vorgang als Kreativ-Abbau, damit onRemove() auch das
                // Fallback-Drop-Item (z. B. Iron Nuggets) nicht droppt.
                rewardBox.markCreativeDestroy();
            }
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof RewardBoxBlockEntity rewardBox) {
                if (!level.isClientSide()) {
                    try {
                        // 0. Falls die Kiste noch nie geöffnet wurde, jetzt Loot generieren,
                        //    damit beim Abbauen nicht einfach eine leere Kiste droppt.
                        //    generateLootNow() ist idempotent (lootGenerated-Flag), also unbedenklich
                        //    auch dann aufzurufen, wenn der Loot schon existiert.
                        rewardBox.generateLootNow();

                        // 1. Inhalt der Kiste immer verstreuen, wenn sie zerstört wird.
                        //    Das Lock steuert nur, WER abbauen darf (siehe onDestroyedByPlayer),
                        //    nicht ob der bereits erlaubte Abbau auch den Inhalt droppt.
                        Containers.dropContents(level, pos, rewardBox);

                        // 2. Standard-Fallback: Iron Nuggets in zufälliger Menge (1-8) statt der Box selbst,
                        //    damit man beim Abbau nicht einfach wieder eine leere Kiste bekommt.
                        //    Im Kreativmodus wird kein Fallback-Item gedroppt (siehe playerWillDestroy).
                        if (!rewardBox.isCreativeDestroy()) {
                            Item dropItem = Items.IRON_NUGGET;
                            int dropCount = 1 + level.random.nextInt(8);

                            RewardBoxConfigManager.BoxDefinition def = RewardBoxConfigManager.getDefinition(rewardBox.getBoxId());
                            if (def != null && def.breakDropItem() != null && !def.breakDropItem().trim().isEmpty()) {
                                Item customItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation(def.breakDropItem().trim()));
                                if (customItem != null && customItem != Items.AIR) {
                                    dropItem = customItem;
                                    // Nur eine explizit konfigurierte Anzahl übernehmen (Sentinel -1 = nicht gesetzt)
                                    dropCount = def.breakDropCount() > 0 ? def.breakDropCount() : 1;
                                }
                            }

                            // 3. Drop erzeugen - BoxId/RewardTier NBT nur setzen, wenn tatsächlich wieder
                            //    eine Reward-Box gedroppt wird (z. B. wenn im Config explizit die Box als break_drop_item steht)
                            ItemStack dropStack = new ItemStack(dropItem, dropCount);
                            if (RewardBoxRegistry.REWARD_BOX_ITEM.isPresent() && dropItem == RewardBoxRegistry.REWARD_BOX_ITEM.get()) {
                                CompoundTag tag = dropStack.getOrCreateTag();
                                tag.putString("BoxId", rewardBox.getBoxId());
                                tag.putInt("RewardTier", rewardBox.getTier());
                            }

                            popResource(level, pos, dropStack);
                        }
                    } catch (Exception e) {
                        LOGGER.error("[RewardBox] Fehler beim Entfernen der Kiste an {}", pos, e);
                    }
                }
                level.updateNeighbourForOutputSignal(pos, this);
            }
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RewardBoxBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        // WICHTIG: Ohne diesen Ticker läuft RewardBoxBlockEntity.lidAnimateTick() nie,
        // wodurch chestLidController.tickLid() nie aufgerufen wird - der Openness-Wert
        // bleibt dauerhaft bei 0, selbst wenn triggerEvent() korrekt shouldBeOpen(true) setzt.
        // Nur clientseitig nötig, da die Animation rein visuell ist.
        return level.isClientSide
            ? createTickerHelper(type, RewardBoxRegistry.REWARD_BOX_BE.get(), RewardBoxBlockEntity::lidAnimateTick)
            : null;
    }

    @Override
    public boolean triggerEvent(BlockState state, Level level, BlockPos pos, int id, int param) {
        // WICHTIG: BaseEntityBlock leitet Block-Events standardmäßig NICHT an die BlockEntity weiter.
        // Ohne dieses Override kommt das über level.blockEvent() ausgelöste Event nie bei
        // RewardBoxBlockEntity.triggerEvent() an -> chestLidController.shouldBeOpen() wird nie
        // aufgerufen -> der Deckel öffnet sich nie, obwohl der Container serverseitig geöffnet ist.
        super.triggerEvent(state, level, pos, id, param);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return blockEntity != null && blockEntity.triggerEvent(id, param);
    }
}