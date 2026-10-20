package net.kb150.woerp.block;

import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import org.jetbrains.annotations.Nullable;

public class WoerpStoneBlock extends Block {
    // 10 diskrete Zustaende nutzen die 16-Bit-Palette des Subchunks; 0 Bytes NBT-Speicher
    public static final IntegerProperty PURITY = IntegerProperty.create("purity", 1, 10);

    public WoerpStoneBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(PURITY, 1));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PURITY);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Wuerfelt Reinheit vorm Setzen aus, damit kein unnoetiges Block-Update im Tick feuert
        return this.defaultBlockState().setValue(PURITY, context.getLevel().getRandom().nextInt(10) + 1);
    }
}