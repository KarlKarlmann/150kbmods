package net.kb150.dragoncolonies.mixin;

import com.ldtteam.structurize.blueprints.v1.Blueprint;
import com.ldtteam.structurize.storage.StructurePackMeta;
import com.ldtteam.structurize.storage.StructurePacks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.*;

@Mixin(value = StructurePacks.class, remap = false)
public class StructurePacksMixin {

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

    private static String sanitizePath(String path) {
        if (path == null) return "";
        String clean = path.replace('\\', '/');
        while (clean.startsWith("/")) {
            clean = clean.substring(1);
        }
        while (clean.endsWith("/")) {
            clean = clean.substring(0, clean.length() - 1);
        }
        return clean;
    }

    // Liefert eine einzelne Blueprint-Datei aus unserem Pack (Stil-Ordner -> Fallback auf default)
    @Inject(method = "getBlueprint(Ljava/lang/String;Ljava/lang/String;Z)Lcom/ldtteam/structurize/blueprints/v1/Blueprint;", at = @At("HEAD"), cancellable = true, remap = false)
    private static void interceptGetBlueprint(String structurePackId, String subPath, boolean suppressError, CallbackInfoReturnable<Blueprint> cir) {
        if (subPath == null) return;

        StructurePackMeta ourPack = getOurPack();
        if (ourPack == null || ourPack.getName().equalsIgnoreCase(structurePackId)) {
            return;
        }

        String cleanSubPath = sanitizePath(subPath);
        String targetStyle = structurePackId.toLowerCase(Locale.ROOT);

        // 1. Suche zuerst im stylespezifischen Ordner (z.B. "shire/mystic/dragonroost1.blueprint")
        Blueprint bp = StructurePacks.getBlueprint(ourPack.getName(), targetStyle + "/" + cleanSubPath, true);

        // 2. Fallback auf den default-Ordner (z.B. "default/mystic/dragonroost1.blueprint")
        if (bp == null) {
            bp = StructurePacks.getBlueprint(ourPack.getName(), "default/" + cleanSubPath, true);
        }

        if (bp != null) {
            bp.setPackName(structurePackId);
            StructurePackMeta targetPack = StructurePacks.getStructurePack(structurePackId);
            if (targetPack != null) {
                int lastSlash = cleanSubPath.lastIndexOf('/');
                String parentPath = lastSlash > 0 ? cleanSubPath.substring(0, lastSlash) : "";
                bp.setFilePath(targetPack.getPath().resolve(parentPath));
            }
            cir.setReturnValue(bp);
        }
    }

    // Liefert Blueprint-Listen an das Rathaus / Wand-Menü aus
    @Inject(method = "getBlueprints", at = @At("RETURN"), cancellable = true, remap = false)
    private static void interceptGetBlueprints(String structurePackId, String subPath, CallbackInfoReturnable<List<Blueprint>> cir) {
        if (subPath == null) return;

        StructurePackMeta ourPack = getOurPack();
        if (ourPack == null || ourPack.getName().equalsIgnoreCase(structurePackId)) {
            return;
        }

        String cleanPath = sanitizePath(subPath);
        int lastSlash = cleanPath.lastIndexOf('/');
        String category = lastSlash >= 0 ? cleanPath.substring(lastSlash + 1) : cleanPath;
        String targetStyle = structurePackId.toLowerCase(Locale.ROOT);

        // 1. Suche nach Blueprints im spezifischen Stil-Ordner
        List<Blueprint> ourBps = StructurePacks.getBlueprints(ourPack.getName(), targetStyle + "/" + category);

        // 2. Fallback auf default
        if (ourBps == null || ourBps.isEmpty()) {
            ourBps = StructurePacks.getBlueprints(ourPack.getName(), "default/" + category);
        }

        if (ourBps != null && !ourBps.isEmpty()) {
            List<Blueprint> currentList = new ArrayList<>(cir.getReturnValue() != null ? cir.getReturnValue() : Collections.emptyList());
            StructurePackMeta targetPack = StructurePacks.getStructurePack(structurePackId);

            Set<String> existingNames = new HashSet<>();
            for (Blueprint b : currentList) {
                existingNames.add(b.getFileName());
            }

            for (Blueprint bp : ourBps) {
                bp.setPackName(structurePackId);
                if (targetPack != null) {
                    bp.setFilePath(targetPack.getPath().resolve(subPath));
                }
                if (!existingNames.contains(bp.getFileName())) {
                    currentList.add(bp);
                    existingNames.add(bp.getFileName());
                }
            }
            cir.setReturnValue(currentList);
        }
    }
}