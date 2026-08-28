package net.kb150.dragoncolonies.commands;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.magister.bookofdragons.entity.base.dragon.DragonBase;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import java.util.UUID;

public class CommandAssignDragon {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("dragoncolonies")
                .requires(source -> source.hasPermission(2)) 
                .then(Commands.literal("assign")
                        // Jetzt mit EntityArgument statt reiner Text-UUIDs!
                        .then(Commands.argument("citizen", EntityArgument.entity())
                                .then(Commands.argument("dragon", EntityArgument.entity())
                                        .executes(CommandAssignDragon::executeAssign))))
        );
    }

    private static int executeAssign(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        
        Entity targetEntity = EntityArgument.getEntity(context, "citizen");
        Entity dragonEntity = EntityArgument.getEntity(context, "dragon");
        
        if (!(targetEntity instanceof AbstractEntityCitizen)) {
            source.sendFailure(Component.literal("Das erste Ziel muss eine Minecolonies-Wache sein."));
            return 0;
        }
        
        // ECHTE Book of Dragons Prüfung
        if (!(dragonEntity instanceof DragonBase)) {
            source.sendFailure(Component.literal("Das zweite Ziel muss ein Drache aus Book of Dragons sein!"));
            return 0;
        }
        
        AbstractEntityCitizen citizen = (AbstractEntityCitizen) targetEntity;
        DragonBase dragon = (DragonBase) dragonEntity;
        
        UUID dragonUuid = dragon.getUUID();

        // Schreibt die UUID in die Wache, damit AbstractEntityAIDragonRider sie findet
        CompoundTag citizenData = citizen.getPersistentData();
        citizenData.putUUID("AssignedDragonUUID", dragonUuid);
        citizenData.putBoolean("HasAssignedDragon", true);
        
        source.sendSuccess(() -> Component.literal(
            "Erfolg: Drache '" + dragon.getDisplayName().getString() + 
            "' wurde der Wache '" + citizen.getDisplayName().getString() + "' zugewiesen!"
        ), true);
        
        return 1;
    }
}