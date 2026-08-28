package com.arnis.gridmasternine;

import com.ldtteam.structurize.blueprints.v1.Blueprint;
import com.ldtteam.structurize.blueprints.v1.BlueprintUtil;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Mod("gridmasternine")
@Mod.EventBusSubscriber
public class GridMasterNine {

    private static final String LOAD_DIR = "generated_blueprints";
    private static final String EXPORT_DIR = "blueprints/arnis";
    private static final String RESOURCE_TEMPLATE_PATH = "/data/gridmasternine/template/";
    
    private static final BlockPos START_POS = new BlockPos(0, 64, 0);
    
    private static final int GRID_SPACING = 16;
    private static final int SIZE_X = 8;
    private static final int SIZE_Y = 6;     // Raum- und Deckenhöhe im Grid (Y=0 bis Y=5)
    private static final int SAVE_HEIGHT = 5; // Exportiert NUR Y=0 bis Y=4 (Decke auf Y=5 wird ignoriert!)
    private static final int SIZE_Z = 8;

    private static final Block[] LEVEL_BLOCKS = {
        Blocks.IRON_BLOCK,
        Blocks.GOLD_BLOCK,
        Blocks.EMERALD_BLOCK,
        Blocks.DIAMOND_BLOCK,
        Blocks.NETHERITE_BLOCK
    };

    private static final Map<String, String> BUILDING_CATEGORIES = Map.ofEntries(
        Map.entry("baker", "craftsmanship/luxury"),
        Map.entry("blacksmith", "craftsmanship/metallurgy"),
        Map.entry("builder", "fundamentals"),
        Map.entry("residence", "fundamentals"),
        Map.entry("deliveryman", "craftsmanship/storage"),
        Map.entry("farmer", "agriculture/horticulture"),
        Map.entry("fisherman", "agriculture/husbandry"),
        Map.entry("guardtower", "military"),
        Map.entry("lumberjack", "fundamentals"),
        Map.entry("miner", "fundamentals"),
        Map.entry("stonemason", "craftsmanship/masonry"),
        Map.entry("townhall", "fundamentals"),
        Map.entry("warehouse", "craftsmanship/storage"),
        Map.entry("shepherd", "agriculture/husbandry"),
        Map.entry("cowboy", "agriculture/husbandry"),
        Map.entry("swineherder", "agriculture/husbandry"),
        Map.entry("chickenherder", "agriculture/husbandry"),
        Map.entry("cook", "fundamentals"),
        Map.entry("kitchen", "fundamentals"),
        Map.entry("smeltery", "craftsmanship/metallurgy"),
        Map.entry("composter", "agriculture/horticulture"),
        Map.entry("library", "education"),
        Map.entry("archery", "military"),
        Map.entry("combatacademy", "military"),
        Map.entry("sawmill", "craftsmanship/carpentry"),
        Map.entry("stonesmeltery", "craftsmanship/masonry"),
        Map.entry("crusher", "craftsmanship/masonry"),
        Map.entry("sifter", "craftsmanship/masonry"),
        Map.entry("florist", "agriculture/horticulture"),
        Map.entry("enchanter", "craftsmanship/luxury"),
        Map.entry("university", "education"),
        Map.entry("hospital", "fundamentals"),
        Map.entry("school", "education"),
        Map.entry("glassblower", "craftsmanship/luxury"),
        Map.entry("dyer", "craftsmanship/luxury"),
        Map.entry("fletcher", "craftsmanship/carpentry"),
        Map.entry("mechanic", "craftsmanship/metallurgy"),
        Map.entry("tavern", "fundamentals"),
        Map.entry("plantation", "agriculture/horticulture"),
        Map.entry("plantationfield", "agriculture/horticulture"),
        Map.entry("rabbithutch", "agriculture/husbandry"),
        Map.entry("concretemixer", "craftsmanship/luxury"),
        Map.entry("beekeeper", "agriculture/husbandry"),
        Map.entry("mysticalsite", "mystic"),
        Map.entry("netherworker", "mystic")
    );

    private static final List<String> BUILDINGS = List.copyOf(BUILDING_CATEGORIES.keySet());

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("arnis")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("loadgrid").executes(GridMasterNine::loadGrid))
                .then(Commands.literal("savegrid").executes(GridMasterNine::saveGrid))
                .then(Commands.literal("removegrid").executes(GridMasterNine::removeGrid))
                
                .then(Commands.literal("copyroom")
                        .then(Commands.argument("roomtype", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    BUILDINGS.forEach(builder::suggest);
                                    return builder.buildFuture();
                                })
                                .then(Commands.argument("fromLvl", IntegerArgumentType.integer(1, 5))
                                        .executes(ctx -> executeCopyRoom(ctx, 
                                                StringArgumentType.getString(ctx, "roomtype"),
                                                IntegerArgumentType.getInteger(ctx, "fromLvl"), -1, 5))
                                        .then(Commands.argument("toStartLvl", IntegerArgumentType.integer(1, 5))
                                                .executes(ctx -> executeCopyRoom(ctx, 
                                                        StringArgumentType.getString(ctx, "roomtype"),
                                                        IntegerArgumentType.getInteger(ctx, "fromLvl"), 
                                                        IntegerArgumentType.getInteger(ctx, "toStartLvl"), 5))
                                                .then(Commands.argument("toEndLvl", IntegerArgumentType.integer(1, 5))
                                                        .executes(ctx -> executeCopyRoom(ctx, 
                                                                StringArgumentType.getString(ctx, "roomtype"),
                                                                IntegerArgumentType.getInteger(ctx, "fromLvl"), 
                                                                IntegerArgumentType.getInteger(ctx, "toStartLvl"), 
                                                                IntegerArgumentType.getInteger(ctx, "toEndLvl"))))
                                        )
                                )
                        )
                )
        );
    }

    private static int executeCopyRoom(CommandContext<CommandSourceStack> context, String roomType, int fromLvl, int toStartLvl, int toEndLvl) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();

        int bIndex = BUILDINGS.indexOf(roomType.toLowerCase());
        if (bIndex == -1) {
            source.sendFailure(Component.literal("Gebäude '" + roomType + "' nicht gefunden!"));
            return 0;
        }

        int targetStart = (toStartLvl == -1) ? (fromLvl + 1) : toStartLvl;
        int targetEnd = Math.min(5, Math.max(targetStart, toEndLvl));

        if (targetStart > 5 || (fromLvl == targetStart && fromLvl == targetEnd)) {
            source.sendFailure(Component.literal("Ungültiger Level-Bereich zum Kopieren."));
            return 0;
        }

        String buildingName = BUILDINGS.get(bIndex);
        BlockPos srcPos = START_POS.offset(bIndex * GRID_SPACING, 0, (fromLvl - 1) * GRID_SPACING);
        int copiedCount = 0;

        for (int tLvl = targetStart; tLvl <= targetEnd; tLvl++) {
            if (tLvl == fromLvl) continue;

            BlockPos dstPos = START_POS.offset(bIndex * GRID_SPACING, 0, (tLvl - 1) * GRID_SPACING);

            for (int x = 0; x < SIZE_X; x++) {
                for (int y = 0; y < SIZE_Y; y++) {
                    for (int z = 0; z < SIZE_Z; z++) {
                        BlockPos sBlockPos = srcPos.offset(x, y, z);
                        BlockPos dBlockPos = dstPos.offset(x, y, z);

                        BlockState state = level.getBlockState(sBlockPos);
                        BlockEntity be = level.getBlockEntity(sBlockPos);

                        level.setBlock(dBlockPos, state, 3);

                        if (be != null) {
                            CompoundTag nbt = be.saveWithFullMetadata();
                            BlockEntity targetBe = level.getBlockEntity(dBlockPos);
                            if (targetBe != null) {
                                targetBe.load(nbt);
                                targetBe.setChanged();
                            }
                        }
                    }
                }
            }

            BlockPos levelBlockPos = dstPos.offset(6, 1, 6);
            level.setBlock(levelBlockPos, LEVEL_BLOCKS[tLvl - 1].defaultBlockState(), 3);

            copiedCount++;
        }

        final int finalCount = copiedCount;
        final int finalStart = targetStart;
        final int finalEnd = targetEnd;
        source.sendSuccess(() -> Component.literal(
                String.format("Raum '%s' (Level %d) auf Level %d-%d kopiert! (%d Modul(e) aktualisiert)", 
                        buildingName, fromLvl, finalStart, finalEnd, finalCount)
        ), true);

        return 1;
    }

    private static int loadGrid(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        int count = 0;

        for (int bIndex = 0; bIndex < BUILDINGS.size(); bIndex++) {
            String building = BUILDINGS.get(bIndex);

            for (int lvl = 1; lvl <= 5; lvl++) {
                File nbtFile = new File(LOAD_DIR + "/" + building + "/" + building + lvl + ".nbt");

                if (nbtFile.exists()) {
                    try (FileInputStream fis = new FileInputStream(nbtFile)) {
                        CompoundTag tag = NbtIo.readCompressed(fis);
                        
                        StructureTemplate template = new StructureTemplate();
                        template.load(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(), tag);

                        BlockPos placePos = START_POS.offset(bIndex * GRID_SPACING, 0, (lvl - 1) * GRID_SPACING);

                        template.placeInWorld(
                                level, placePos, placePos, 
                                new StructurePlaceSettings(), level.getRandom(), 2
                        );

                        level.setBlock(placePos.offset(0, -1, -1), Blocks.DIAMOND_BLOCK.defaultBlockState(), 3);
                        count++;
                        
                    } catch (Exception e) {
                        System.err.println("Fehler beim Laden von " + nbtFile.getPath());
                        e.printStackTrace();
                    }
                }
            }
        }

        final int finalCount = count;
        source.sendSuccess(() -> Component.literal("OS-Load abgeschlossen! " + finalCount + " Module platziert."), true);
        return 1;
    }

    private static int saveGrid(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        int count = 0;

        copyTemplateResources();

        for (int bIndex = 0; bIndex < BUILDINGS.size(); bIndex++) {
            String building = BUILDINGS.get(bIndex);
            String categoryPath = BUILDING_CATEGORIES.getOrDefault(building, "fundamentals");

            for (int lvl = 1; lvl <= 5; lvl++) {
                BlockPos savePos = START_POS.offset(bIndex * GRID_SPACING, 0, (lvl - 1) * GRID_SPACING);
                BlockPos hutBlockPos = savePos.offset(0, 1, 0);

                String schematicName = building + lvl;

                // --- DYNAMISCHER STRUCTURE_VOID TRICK ---
                // 1. Alle AIR-Blöcke im Export-Bereich sammeln & temporär auf STRUCTURE_VOID setzen
                List<BlockPos> tempVoids = new ArrayList<>();
                BlockPos saveEnd = savePos.offset(SIZE_X - 1, SAVE_HEIGHT - 1, SIZE_Z - 1);

                for (BlockPos p : BlockPos.betweenClosed(savePos, saveEnd)) {
                    if (level.getBlockState(p).isAir()) {
                        level.setBlock(p, Blocks.STRUCTURE_VOID.defaultBlockState(), 2);
                        tempVoids.add(p.immutable());
                    }
                }

                // 2. Structurize-Blueprint erstellen (erfasst jetzt STRUCTURE_VOID statt AIR)
                Blueprint blueprint = BlueprintUtil.createBlueprint(
                        level,
                        savePos,
                        true,
                        (short) SIZE_X,
                        (short) SAVE_HEIGHT,
                        (short) SIZE_Z,
                        schematicName,
                        Optional.of(hutBlockPos)
                );

                // 3. Sofort wieder alle Blöcke in der Spielwelt auf AIR zurücksetzen
                for (BlockPos p : tempVoids) {
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
                }

                File directory = new File(EXPORT_DIR + "/" + categoryPath);
                if (!directory.exists()) {
                    directory.mkdirs();
                }

                File blueprintFile = new File(directory, schematicName + ".blueprint");

                try (FileOutputStream fos = new FileOutputStream(blueprintFile)) {
                    BlueprintUtil.writeToStream(fos, blueprint);
                    count++;
                } catch (Exception e) {
                    System.err.println("Fehler beim Speichern von " + blueprintFile.getPath());
                    e.printStackTrace();
                }
            }
        }

        final int finalCount = count;
        source.sendSuccess(() -> Component.literal("BuildingPack-Export abgeschlossen! " + finalCount + " Dateien inkl. Assets gespeichert."), true);
        return 1;
    }

    private static void copyTemplateResources() {
        try {
            var resourceUrl = GridMasterNine.class.getResource(RESOURCE_TEMPLATE_PATH);
            if (resourceUrl == null) {
                System.err.println("Template-Ordner unter " + RESOURCE_TEMPLATE_PATH + " nicht gefunden!");
                return;
            }

            java.nio.file.Path sourcePath;
            java.nio.file.FileSystem jarFileSystem = null;

            if (resourceUrl.getProtocol().equals("jar")) {
                String[] jarParts = resourceUrl.toURI().toString().split("!");
                jarFileSystem = java.nio.file.FileSystems.newFileSystem(java.net.URI.create(jarParts[0]), java.util.Map.of());
                sourcePath = jarFileSystem.getPath(jarParts[1]);
            } else {
                sourcePath = java.nio.file.Paths.get(resourceUrl.toURI());
            }

            File targetDir = new File(EXPORT_DIR);

            try (var stream = Files.walk(sourcePath)) {
                stream.forEach(source -> {
                    try {
                        java.nio.file.Path relative = sourcePath.relativize(source);
                        File destination = new File(targetDir, relative.toString());

                        if (Files.isDirectory(source)) {
                            destination.mkdirs();
                        } else {
                            destination.getParentFile().mkdirs();
                            Files.copy(source, destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }

            if (jarFileSystem != null) {
                jarFileSystem.close();
            }

        } catch (Exception e) {
            System.err.println("Fehler beim dynamischen Kopieren des Templates:");
            e.printStackTrace();
        }
    }

    private static int removeGrid(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        int count = 0;

        for (int bIndex = 0; bIndex < BUILDINGS.size(); bIndex++) {
            for (int lvl = 1; lvl <= 5; lvl++) {
                BlockPos placePos = START_POS.offset(bIndex * GRID_SPACING, 0, (lvl - 1) * GRID_SPACING);

                level.setBlock(placePos.offset(0, -1, -1), Blocks.AIR.defaultBlockState(), 3);

                for (int cx = 0; cx < SIZE_X; cx++) {
                    for (int cy = 0; cy < SIZE_Y; cy++) {
                        for (int cz = 0; cz < SIZE_Z; cz++) {
                            BlockPos targetPos = placePos.offset(cx, cy, cz);
                            level.setBlock(targetPos, Blocks.AIR.defaultBlockState(), 3);
                        }
                    }
                }
                count++;
            }
        }

        final int finalCount = count;
        source.sendSuccess(() -> Component.literal("Grid entfernt! " + finalCount + " Module gelöscht."), true);
        return 1;
    }
}