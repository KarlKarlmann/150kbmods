package net.kb150.dragoncolonies.mixin;

import com.ldtteam.structurize.blueprints.v1.Blueprint;
import com.ldtteam.structurize.storage.StructurePackMeta;
import com.ldtteam.structurize.storage.StructurePacks;
import com.minecolonies.core.client.gui.WindowBuildingBrowser;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Method;
import java.util.*;

@Mixin(value = WindowBuildingBrowser.class, remap = false)
public class WindowBuildingBrowserMixin {

    private static StructurePackMeta getOurPack() {
        for (StructurePackMeta meta : StructurePacks.getPackMetas()) {
            if ("dragoncolonies".equalsIgnoreCase(meta.getName()) 
                    || "dragoncolonies".equalsIgnoreCase(meta.getOwner())
                    || "buildings".equalsIgnoreCase(meta.getName())) {
                return meta;
            }
        }
        return null;
    }

    private static String extractFolder(String path) {
        if (path == null) return "";
        String clean = path.replace('\\', '/');
        while (clean.endsWith("/")) {
            clean = clean.substring(0, clean.length() - 1);
        }
        int lastSlash = clean.lastIndexOf('/');
        return lastSlash >= 0 ? clean.substring(lastSlash + 1) : clean;
    }

    @Inject(method = "discoverBuildings(Lcom/ldtteam/structurize/storage/StructurePackMeta;Ljava/util/List;)Ljava/util/Map;", at = @At("RETURN"), remap = false)
    private void injectDragonColoniesIntoWand(StructurePackMeta currentWandPack, List<Block> browsableBlocks, CallbackInfoReturnable<Map> cir) {
        @SuppressWarnings("rawtypes")
        Map buildings = cir.getReturnValue();
        StructurePackMeta ourPack = getOurPack();

        if (ourPack == null || ourPack.getName().equalsIgnoreCase(currentWandPack.getName())) {
            return;
        }

        try {
            Class<?> buildingInfoClass = Class.forName("com.minecolonies.core.client.gui.WindowBuildingBrowser$BuildingInfo");
            Method createMethod = buildingInfoClass.getDeclaredMethod("create", StructurePackMeta.class, Blueprint.class, boolean.class);
            createMethod.setAccessible(true);

            String activeStyle = currentWandPack.getName().toLowerCase(Locale.ROOT);

            // 1. Alle Kategorien ermitteln (sowohl aus "default" als auch aus dem spezifischen Stil-Ordner)
            Set<String> categories = new LinkedHashSet<>();

            List<StructurePacks.Category> defaultCats = StructurePacks.getCategories(ourPack.getName(), "default");
            if (defaultCats != null) {
                for (StructurePacks.Category c : defaultCats) {
                    String catName = extractFolder(c.subPath);
                    if (!catName.isEmpty()) categories.add(catName);
                }
            }

            List<StructurePacks.Category> styleCats = StructurePacks.getCategories(ourPack.getName(), activeStyle);
            if (styleCats != null) {
                for (StructurePacks.Category c : styleCats) {
                    String catName = extractFolder(c.subPath);
                    if (!catName.isEmpty()) categories.add(catName);
                }
            }

            // 2. Für jede Kategorie Blueprints abfragen (Stil-Ordner hat Vorrang vor default)
            for (String category : categories) {
                List<Blueprint> blueprintsToUse = StructurePacks.getBlueprints(ourPack.getName(), activeStyle + "/" + category);

                // Fallback auf default, wenn dieser Stil keine eigenen Blueprints für diese Kategorie hat
                if (blueprintsToUse == null || blueprintsToUse.isEmpty()) {
                    blueprintsToUse = StructurePacks.getBlueprints(ourPack.getName(), "default/" + category);
                }

                if (blueprintsToUse == null || blueprintsToUse.isEmpty()) continue;

                for (Blueprint bp : blueprintsToUse) {
                    BlockPos anchorPos = bp.getPrimaryBlockOffset();
                    com.ldtteam.structurize.util.BlockInfo info = bp.getBlockInfoAsMap().get(anchorPos);

                    if (info == null || info.getState() == null) continue;
                    Block anchorBlock = info.getState().getBlock();

                    if (browsableBlocks.contains(anchorBlock)) {
                        @SuppressWarnings("unchecked")
                        List<Object> list = (List<Object>) buildings.computeIfAbsent(anchorBlock, k -> new ArrayList<>());

                        Blueprint disguisedBp = createDisguisedBlueprint(bp, currentWandPack, category);
                        Object buildingInfo = createMethod.invoke(null, currentWandPack, disguisedBp, false);
                        list.add(buildingInfo);
                    }
                }
            }
        } catch (Exception e) {
            // Silently fail, um GUI-Abstürze abzufangen
        }
    }

    private Blueprint createDisguisedBlueprint(Blueprint original, StructurePackMeta targetPack, String category) {
        Blueprint fakeBp = new Blueprint(original.getSizeX(), original.getSizeY(), original.getSizeZ());
        fakeBp.setFileName(original.getFileName());
        fakeBp.setPackName(targetPack.getName());
        fakeBp.setFilePath(targetPack.getPath().resolve(category));
        return fakeBp;
    }
}