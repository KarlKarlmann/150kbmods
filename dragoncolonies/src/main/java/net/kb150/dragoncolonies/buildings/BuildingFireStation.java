package net.kb150.dragoncolonies.buildings;

import com.ldtteam.structurize.blueprints.v1.Blueprint;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.core.colony.buildings.AbstractBuilding;
import com.minecolonies.core.colony.buildings.views.AbstractBuildingView;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Die Feuerwache aus Punkt 5.1 des Architekturberichts.
 * Fungiert als operationelles Zentrum der Brandbekämpfung in der Kolonie.
 */
public class BuildingFireStation extends AbstractBuilding {

    private static final String SCHEMATIC_NAME = "firestation";
    private static final int MAX_LEVEL = 5;

    // Unser neues Modul
    private final net.kb150.dragoncolonies.buildings.modules.FirefighterDispatchModule dispatchModule;

    public BuildingFireStation(@NotNull IColony colony, @NotNull BlockPos pos) {
        super(colony, pos);
        
        // Initialisierung des Dispatch-Moduls
        this.dispatchModule = new net.kb150.dragoncolonies.buildings.modules.FirefighterDispatchModule(this);
    }

    @Override
    public void onColonyTick(IColony colony) {
        super.onColonyTick(colony);
        // Das Modul bei jedem Kolonie-Tick aktualisieren
        if (this.dispatchModule != null) {
            this.dispatchModule.tick();
        }
    }

    @Override
    public @NotNull String getSchematicName() {
        return SCHEMATIC_NAME;
    }

    @Override
    public int getMaxBuildingLevel() {
        return MAX_LEVEL;
    }

    @Override
    public void onUpgradeComplete(@Nullable Blueprint blueprint, int newLevel) {
        super.onUpgradeComplete(blueprint, newLevel);
        // Das FirefighterDispatchModule berechnet seinen Radius ohnehin bei jedem Scan dynamisch 
        // anhand von this.getBuildingLevel(). Wir müssen hier also nichts hart kodieren,
        // aber wir könnten hier ein Event loggen oder eine Meldung an die Kolonie senden,
        // dass sich der Brandschutz verbessert hat.
    }

    /**
     * Die View-Klasse ist zwingend erforderlich, damit der Client (das GUI) 
     * Informationen über das Gebäude abrufen kann, ohne die Server-Tps zu belasten.
     */
    public static class View extends AbstractBuildingView {
        public View(IColonyView colony, @NotNull BlockPos pos) {
            super(colony, pos);
        }
    }
}