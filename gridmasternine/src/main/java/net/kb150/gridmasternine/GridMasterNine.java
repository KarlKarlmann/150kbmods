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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;

@Mod("gridmasternine")
@Mod.EventBusSubscriber
public class GridMasterNine {

    private static final String LOAD_DIR = "generated_blueprints";
    private static final String EXPORT_DIR = "blueprints/arnis";
    private static final String RESOURCE_TEMPLATE_PATH = "/data/gridmasternine/template/";
    
    private static final BlockPos START_POS = new BlockPos(0, 64, 0);
    
    private static final int GRID_SPACING = 16;
    private static final int SIZE_X = 8;
    private static final int SIZE_Y = 6;     
    private static final int SAVE_HEIGHT = 5; 
    private static final int SIZE_Z = 8;

    private static final List<String> DO_LAMPS = List.of(
        "domum_ornamentum:vertical_light",
        "domum_ornamentum:crossed_light",
        "domum_ornamentum:framed_light",
        "domum_ornamentum:horizontal_light",
        "domum_ornamentum:fancy_light",
        "domum_ornamentum:four_light",
        "domum_ornamentum:center_light"
    );

    private static final List<String> LIGHT_SOURCES = List.of(
        "minecraft:sea_lantern",
        "minecraft:glowstone",
        "minecraft:ochre_froglight",
        "minecraft:pearlescent_froglight",
        "minecraft:verdant_froglight"
    );

    private static final Map<Integer, List<String>> LEVEL_MATERIAL_POOLS = Map.of(
        1, List.of("minecraft:iron_block", "minecraft:copper_block", "minecraft:coal_block", "minecraft:bone_block"),
        2, List.of("minecraft:gold_block", "minecraft:redstone_block", "minecraft:stripped_dark_oak_wood", "minecraft:raw_iron_block"),
        3, List.of("minecraft:emerald_block", "minecraft:lapis_block", "minecraft:raw_gold_block", "minecraft:amethyst_block"),
        4, List.of("minecraft:diamond_block", "minecraft:obsidian", "minecraft:crying_obsidian", "minecraft:purpur_block"),
        5, List.of("minecraft:netherite_block", "minecraft:gilded_blackstone")
    );

    // Strikt deterministische LinkedHashMap (Gleiche Reihenfolge wie in Python)
    private static final Map<String, String> BUILDING_CATEGORIES;
    private static final List<String> BUILDINGS;

    static {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("baker", "craftsmanship/luxury");
        map.put("blacksmith", "craftsmanship/metallurgy");
        map.put("builder", "fundamentals");
        map.put("residence", "fundamentals");
        map.put("deliveryman", "craftsmanship/storage");
        map.put("farmer", "agriculture/horticulture");
        map.put("fisherman", "agriculture/husbandry");
        map.put("guardtower", "military");
        map.put("lumberjack", "fundamentals");
        map.put("stonemason", "craftsmanship/masonry");
        map.put("townhall", "fundamentals");
        map.put("warehouse", "craftsmanship/storage");
        map.put("shepherd", "agriculture/husbandry");
        map.put("cowboy", "agriculture/husbandry");
        map.put("swineherder", "agriculture/husbandry");
        map.put("chickenherder", "agriculture/husbandry");
        map.put("cook", "fundamentals");
        map.put("kitchen", "fundamentals");
        map.put("smeltery", "craftsmanship/metallurgy");
        map.put("composter", "agriculture/horticulture");
        map.put("library", "education");
        map.put("archery", "military");
        map.put("combatacademy", "military");
        map.put("sawmill", "craftsmanship/carpentry");
        map.put("stonesmeltery", "craftsmanship/masonry");
        map.put("crusher", "craftsmanship/masonry");
        map.put("sifter", "craftsmanship/masonry");
        map.put("florist", "agriculture/horticulture");
        map.put("enchanter", "craftsmanship/luxury");
        map.put("university", "education");
        map.put("hospital", "fundamentals");
        map.put("school", "education");
        map.put("glassblower", "craftsmanship/luxury");
        map.put("dyer", "craftsmanship/luxury");
        map.put("fletcher", "craftsmanship/carpentry");
        map.put("mechanic", "craftsmanship/metallurgy");
        map.put("tavern", "fundamentals");
        map.put("plantation", "agriculture/horticulture");
        map.put("plantationfield", "agriculture/horticulture");
        map.put("rabbithutch", "agriculture/husbandry");
        map.put("concretemixer", "craftsmanship/luxury");
        map.put("beekeeper", "agriculture/husbandry");
        map.put("mysticalsite", "mystic");
        map.put("netherworker", "mystic");

        BUILDING_CATEGORIES = Collections.unmodifiableMap(map);
        BUILDINGS = List.copyOf(map.keySet());
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("arnis")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("loadgrid").executes(GridMasterNine::loadGrid))
                .then(Commands.literal("savegrid").executes(GridMasterNine::saveGrid))
                .then(Commands.literal("removegrid").executes(GridMasterNine::removeGrid))
                .then(Commands.literal("whereami").executes(GridMasterNine::whereAmI))
                .then(Commands.literal("pos").executes(GridMasterNine::whereAmI))
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
                                                        IntegerArgumentType.getInteger(ctx, "toStartLvl"), 
                                                        IntegerArgumentType.getInteger(ctx, "toStartLvl")))
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

    private static int whereAmI(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Vec3 pos = source.getPosition();

        int px = (int) Math.floor(pos.x);
        int py = (int) Math.floor(pos.y);
        int pz = (int) Math.floor(pos.z);

        int relX = px - START_POS.getX();
        int relY = py - START_POS.getY();
        int relZ = pz - START_POS.getZ();

        int bIndex = (relX >= 0) ? (relX / GRID_SPACING) : -1;
        int modX = (relX >= 0) ? (relX % GRID_SPACING) : -1;

        int lvlIndex = (relZ >= 0) ? (relZ / GRID_SPACING) : -1;
        int modZ = (relZ >= 0) ? (relZ % GRID_SPACING) : -1;
        int lvl = lvlIndex + 1;

        if (bIndex >= 0 && bIndex < BUILDINGS.size() && lvl >= 1 && lvl <= 5) {
            String buildingName = BUILDINGS.get(bIndex);
            boolean inX = modX >= 0 && modX < SIZE_X;
            boolean inZ = modZ >= 0 && modZ < SIZE_Z;
            boolean inY = relY >= 0 && relY < SIZE_Y;

            if (inX && inY && inZ) {
                source.sendSuccess(() -> Component.literal(
                    String.format("📍 Raum: §a%s§r (Index %d) | §eLevel %d§r [Relativ: X=%d, Y=%d, Z=%d]",
                        buildingName, bIndex, lvl, modX, relY, modZ)
                ), false);
            } else {
                source.sendSuccess(() -> Component.literal(
                    String.format("⚠️ Zwischenraum nahe §a%s§r (Index %d), Level %d.",
                        buildingName, bIndex, lvl)
                ), false);
            }
            return 1;
        }

        source.sendSuccess(() -> Component.literal(
            String.format("❌ Außerhalb des Grids! (Welt-Pos: X=%d, Y=%d, Z=%d)", px, py, pz)
        ), false);
        return 1;
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

        if (targetStart > 5 || fromLvl < 1 || fromLvl > 5) {
            source.sendFailure(Component.literal("Ungültiger Level-Bereich zum Kopieren."));
            return 0;
        }

        String buildingName = BUILDINGS.get(bIndex);
        BlockPos srcPos = START_POS.offset(bIndex * GRID_SPACING, 0, (fromLvl - 1) * GRID_SPACING);

        // Erzwingt das Laden des Quell-Chunks
        level.getChunk(srcPos);

        // Sicherheitsprüfung: Ist der Quellraum geladen und nicht leer?
        if (level.getBlockState(srcPos.offset(0, 1, 0)).isAir() && level.getBlockState(srcPos.offset(0, 0, 0)).isAir()) {
            source.sendFailure(Component.literal("Quellraum '" + buildingName + "' (Level " + fromLvl + ") scheint leer zu sein! Bitte erst '/arnis loadgrid' ausführen."));
            return 0;
        }

        int copiedCount = 0;

        // Flag 18 (UPDATE_CLIENTS = 2 | UPDATE_KNOWN_SHAPE = 16): Synchronisiert mit Clients, unterdrückt aber Betten-Zerstörung
        int copyFlags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

        for (int tLvl = targetStart; tLvl <= targetEnd; tLvl++) {
            if (tLvl == fromLvl) continue;

            BlockPos dstPos = START_POS.offset(bIndex * GRID_SPACING, 0, (tLvl - 1) * GRID_SPACING);

            // Erzwingt das Laden des Ziel-Chunks
            level.getChunk(dstPos);

            for (int x = 0; x < SIZE_X; x++) {
                for (int y = 0; y < SIZE_Y; y++) {
                    for (int z = 0; z < SIZE_Z; z++) {
                        BlockPos sBlockPos = srcPos.offset(x, y, z);
                        BlockPos dBlockPos = dstPos.offset(x, y, z);

                        BlockState state = level.getBlockState(sBlockPos);
                        BlockEntity srcBe = level.getBlockEntity(sBlockPos);

                        level.setBlock(dBlockPos, state, copyFlags);

                        if (srcBe != null) {
                            CompoundTag nbt = srcBe.saveWithFullMetadata();
                            nbt.putInt("x", dBlockPos.getX());
                            nbt.putInt("y", dBlockPos.getY());
                            nbt.putInt("z", dBlockPos.getZ());

                            BlockEntity targetBe = level.getBlockEntity(dBlockPos);
                            if (targetBe != null) {
                                targetBe.load(nbt);
                                targetBe.setChanged();
                            }
                        }

                        level.sendBlockUpdated(dBlockPos, Blocks.AIR.defaultBlockState(), state, 3);
                    }
                }
            }

            setDOLamp(level, dstPos.offset(2, 4, 2), bIndex, tLvl);
            setDOLamp(level, dstPos.offset(5, 4, 5), bIndex, tLvl);

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

    private static void setDOLamp(ServerLevel level, BlockPos pos, int bIndex, int targetLevel) {
        List<String> materials = LEVEL_MATERIAL_POOLS.get(targetLevel);
        
        List<String[]> combinations = new ArrayList<>();
        for (String mat : materials) {
            for (String light : LIGHT_SOURCES) {
                for (String lamp : DO_LAMPS) {
                    combinations.add(new String[]{lamp, light, mat});
                }
            }
        }
        
        Collections.shuffle(combinations, new Random(1337 + targetLevel));
        
        String[] config = combinations.get(bIndex % combinations.size());
        String lampId = config[0];
        String lightSource = config[1];
        String selectedMaterial = config[2];

        Block doBlock = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(lampId));
        if (doBlock == null) return;

        BlockState state = doBlock.defaultBlockState();
        level.setBlock(pos, state, 3);

        BlockEntity be = level.getBlockEntity(pos);
        if (be != null) {
            CompoundTag nbt = be.saveWithFullMetadata();
            CompoundTag textureData = new CompoundTag();
            textureData.putString("minecraft:block/glowstone", lightSource);
            textureData.putString("minecraft:block/oak_planks", selectedMaterial);
            nbt.put("textureData", textureData);
            
            be.load(nbt);
            be.setChanged();
            level.sendBlockUpdated(pos, state, state, 3);
        }
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

                // 1. CLEANSING
                AABB moduleBox = new AABB(savePos, savePos.offset(SIZE_X, SIZE_Y, SIZE_Z));
                List<ItemEntity> droppedItems = level.getEntitiesOfClass(ItemEntity.class, moduleBox);
                droppedItems.forEach(Entity::discard);

                // 2. NBT-EXPORT
                try {
                    StructureTemplate fullTemplate = new StructureTemplate();
                    fullTemplate.fillFromWorld(level, savePos, new BlockPos(SIZE_X, SIZE_Y, SIZE_Z), false, Blocks.STRUCTURE_VOID);
                    CompoundTag fullNbtTag = fullTemplate.save(new CompoundTag());

                    File nbtDir = new File(LOAD_DIR + "/" + building);
                    if (!nbtDir.exists()) nbtDir.mkdirs();

                    File nbtFile = new File(nbtDir, schematicName + ".nbt");
                    try (FileOutputStream fos = new FileOutputStream(nbtFile)) {
                        NbtIo.writeCompressed(fullNbtTag, fos);
                    }
                } catch (Exception e) {
                    System.err.println("Fehler beim Speichern des NBTs: " + schematicName);
                    e.printStackTrace();
                }

                // 3. BLUEPRINT-EXPORT
                List<BlockPos> tempVoids = new ArrayList<>();
                BlockPos saveEnd = savePos.offset(SIZE_X - 1, SAVE_HEIGHT - 1, SIZE_Z - 1);

                for (BlockPos p : BlockPos.betweenClosed(savePos, saveEnd)) {
                    if (level.getBlockState(p).isAir()) {
                        level.setBlock(p, Blocks.STRUCTURE_VOID.defaultBlockState(), 2);
                        tempVoids.add(p.immutable());
                    }
                }

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
        source.sendSuccess(() -> Component.literal("Cleaned & Exported! " + finalCount + " NBTs + Blueprints gesichert."), true);
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