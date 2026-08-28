package net.kb150.reward_box.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.kb150.reward_box.RewardBox;
import net.kb150.reward_box.init.RewardBoxRegistry;
import net.kb150.reward_box.util.RewardBoxConfigManager;

import java.util.Collection;

@Mod.EventBusSubscriber(modid = RewardBox.MODID)
public class RewardBoxCommand {

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_BOX_IDS = (context, builder) ->
            SharedSuggestionProvider.suggest(RewardBoxConfigManager.getLoadedBoxIds(), builder);

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(
            Commands.literal("rewardbox")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("give")
                    .then(Commands.argument("targets", EntityArgument.players())
                        .then(Commands.argument("box_id", ResourceLocationArgument.id())
                            .suggests(SUGGEST_BOX_IDS)
                            .then(Commands.argument("tier", IntegerArgumentType.integer(1))
                                .executes(ctx -> giveBox(
                                        ctx.getSource(),
                                        EntityArgument.getPlayers(ctx, "targets"),
                                        ResourceLocationArgument.getId(ctx, "box_id").toString(),
                                        IntegerArgumentType.getInteger(ctx, "tier"),
                                        1
                                ))
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1, 64))
                                    .executes(ctx -> giveBox(
                                            ctx.getSource(),
                                            EntityArgument.getPlayers(ctx, "targets"),
                                            ResourceLocationArgument.getId(ctx, "box_id").toString(),
                                            IntegerArgumentType.getInteger(ctx, "tier"),
                                            IntegerArgumentType.getInteger(ctx, "amount")
                                    ))
                                )
                            )
                        )
                    )
                )
        );
    }

    private static int giveBox(CommandSourceStack source, Collection<ServerPlayer> targets, String boxId, int tier, int amount) {
        ItemStack stack = new ItemStack(RewardBoxRegistry.REWARD_BOX_ITEM.get(), amount);
        CompoundTag nbt = stack.getOrCreateTag();
        nbt.putString("BoxId", boxId);
        nbt.putInt("RewardTier", tier);

        for (ServerPlayer player : targets) {
            if (!player.getInventory().add(stack.copy())) {
                player.drop(stack.copy(), false);
            }
        }

        source.sendSuccess(() -> Component.literal("Gave " + amount + "x [" + boxId + " | Tier " + tier + "] to " + targets.size() + " player(s)"), true);
        return targets.size();
    }
}