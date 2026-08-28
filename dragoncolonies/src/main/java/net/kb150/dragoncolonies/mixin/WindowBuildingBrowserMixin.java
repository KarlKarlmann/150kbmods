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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Mixin(value = WindowBuildingBrowser.class, remap = false)
public class WindowBuildingBrowserMixin {

    private static StructurePackMeta getOurPack() {
        for (StructurePackMeta meta : StructurePacks.getPackMetas()) {
            if ("dragoncolonies".equals(meta.getOwner()) || "dragoncolonies".equals(meta.getName()) || "buildings".equals(meta.getName())) {
                return meta;
            }
        }
        return null;
    }

    @Inject(method = "discoverBuildings(Lcom/ldtteam/structurize/storage/StructurePackMeta;Ljava/util/List;)Ljava/util/Map;", at = @At("RETURN"), remap = false)
    private void injectDragonColoniesIntoWand(StructurePackMeta currentWandPack, List<Block> browsableBlocks, CallbackInfoReturnable<Map> cir) {
        @SuppressWarnings("rawtypes")
        Map buildings = cir.getReturnValue();
        StructurePackMeta ourPack = getOurPack();

        // Wenn unser Pack nicht da ist oder das Baumenü gerade unser Pack direkt scannt: abbrechen.
        if (ourPack == null || ourPack.getName().equals(currentWandPack.getName())) {
            return;
        }

        try {
            Class<?> buildingInfoClass = Class.forName("com.minecolonies.core.client.gui.WindowBuildingBrowser$BuildingInfo");
            Method createMethod = buildingInfoClass.getDeclaredMethod("create", StructurePackMeta.class, Blueprint.class, boolean.class);
            createMethod.setAccessible(true);

            // 1. DYNAMISCH alle Ordner in deinem Pack abfragen (z.B. "military", "mystic", "brot")
            List<StructurePacks.Category> categories = StructurePacks.getCategories(ourPack.getName(), "");
            if (categories == null) return;

            for (StructurePacks.Category category : categories) {
                String catName = category.subPath; 
                
                // 2. DYNAMISCH alle Blueprints aus diesem Ordner abfragen
                List<Blueprint> ourBlueprints = StructurePacks.getBlueprints(ourPack.getName(), catName);
                if (ourBlueprints == null) continue;

                for (Blueprint bp : ourBlueprints) {
                    // 3. DYNAMISCH den Anker-Block des Blueprints auslesen (z.B. Feuerwachen-Block)
                    BlockPos anchorPos = bp.getPrimaryBlockOffset();
                    com.ldtteam.structurize.util.BlockInfo info = bp.getBlockInfoAsMap().get(anchorPos);
                    
                    if (info == null || info.getState() == null) continue;
                    Block anchorBlock = info.getState().getBlock(); // Hier verwenden wir jetzt den unverschleierten MojMap-Namen

                    // 4. Prüfen, ob das Baumenü sich für diesen Block interessiert
                    if (browsableBlocks.contains(anchorBlock)) {
                        @SuppressWarnings("unchecked")
                        List<Object> list = (List<Object>) buildings.computeIfAbsent(anchorBlock, k -> new ArrayList<>());

                        // 5. Tarnen und einschleusen!
                        Blueprint disguisedBp = createDisguisedBlueprint(bp, currentWandPack, catName);
                        Object buildingInfo = createMethod.invoke(null, currentWandPack, disguisedBp, false);
                        list.add(buildingInfo);
                    }
                }
            }
        } catch (Exception e) {
            // Silently fail, um GUI-Abstürze zu verhindern
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