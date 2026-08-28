package net.kb150.dragoncolonies.mixin;

import com.ldtteam.structurize.blueprints.v1.Blueprint;
import com.ldtteam.structurize.storage.StructurePackMeta;
import com.ldtteam.structurize.storage.StructurePacks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = StructurePacks.class, remap = false)
public class StructurePacksMixin {

    private static StructurePackMeta getOurPack() {
        for (StructurePackMeta meta : StructurePacks.getPackMetas()) {
            if ("dragoncolonies".equals(meta.getOwner()) || "dragoncolonies".equals(meta.getName()) || "buildings".equals(meta.getName())) {
                return meta;
            }
        }
        return null;
    }

    // Fängt die Anfrage für eine EINZELNE Blueprint-Datei ab (z.B. vom Bauherrn NPC)
    @Inject(method = "getBlueprint(Ljava/lang/String;Ljava/lang/String;Z)Lcom/ldtteam/structurize/blueprints/v1/Blueprint;", at = @At("HEAD"), cancellable = true, remap = false)
    private static void interceptGetBlueprint(String structurePackId, String subPath, boolean suppressError, CallbackInfoReturnable<Blueprint> cir) {
        if (subPath == null) return;
        
        StructurePackMeta ourPack = getOurPack();
        if (ourPack != null && !ourPack.getName().equals(structurePackId)) {
            // Wir fragen einfach DYNAMISCH unser eigenes Pack, ob wir zufällig eine Datei haben,
            // die exakt in diesem Ordner/Namen liegt (z.B. "military/firestation1.blueprint").
            Blueprint bp = StructurePacks.getBlueprint(ourPack.getName(), subPath, true);
            
            if (bp != null) {
                // Wir haben die Datei! Als den Ziel-Stil tarnen und ausliefern.
                bp.setPackName(structurePackId);
                StructurePackMeta targetPack = StructurePacks.getStructurePack(structurePackId);
                if (targetPack != null) {
                    String cleanPath = subPath.replace('\\', '/');
                    int lastSlash = cleanPath.lastIndexOf('/');
                    String parentPath = lastSlash > 0 ? cleanPath.substring(0, lastSlash) : "";
                    bp.setFilePath(targetPack.getPath().resolve(parentPath));
                }
                cir.setReturnValue(bp);
            }
        }
    }

    // Fängt die Anfrage für LISTEN von Dateien ab (z.B. für das Rathaus)
    @Inject(method = "getBlueprints", at = @At("RETURN"), cancellable = true, remap = false)
    private static void interceptGetBlueprints(String structurePackId, String subPath, CallbackInfoReturnable<List<Blueprint>> cir) {
        if (subPath == null) return;
        
        StructurePackMeta ourPack = getOurPack();
        if (ourPack != null && !ourPack.getName().equals(structurePackId)) {
            
            // DYNAMISCH die Kategorie aus dem angeforderten Pfad filtern (z.B. "acacia/military" -> "military")
            String cleanPath = subPath.replace('\\', '/');
            String category = cleanPath.substring(cleanPath.lastIndexOf('/') + 1);
            
            // Haben wir diesen Ordner auch bei uns im Pack?
            List<Blueprint> ourBps = StructurePacks.getBlueprints(ourPack.getName(), category);
            
            if (ourBps != null && !ourBps.isEmpty()) {
                // Ja! Wir hängen unsere Pläne getarnt an die Liste der Vanilla-Pläne an.
                List<Blueprint> currentList = new ArrayList<>(cir.getReturnValue());
                StructurePackMeta targetPack = StructurePacks.getStructurePack(structurePackId);
                
                for (Blueprint bp : ourBps) {
                    bp.setPackName(structurePackId);
                    if (targetPack != null) {
                        bp.setFilePath(targetPack.getPath().resolve(subPath));
                    }
                    currentList.add(bp);
                }
                cir.setReturnValue(currentList);
            }
        }
    }
}