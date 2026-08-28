package net.kb150.dragoncolonies.client.gui;

import com.minecolonies.core.client.gui.AbstractWindowWorkerModuleBuilding;
import net.kb150.dragoncolonies.DragonColonies;
import net.kb150.dragoncolonies.buildings.BuildingDragonRoost;
import net.minecraft.resources.ResourceLocation;

public class WindowDragonRoost extends AbstractWindowWorkerModuleBuilding<BuildingDragonRoost.View> {

    public WindowDragonRoost(BuildingDragonRoost.View buildingView) {
        super(buildingView, new ResourceLocation(DragonColonies.MOD_ID, "gui/dragonroost.xml"));
    }

    @Override
    public void onOpened() {
        super.onOpened();
        // Minecolonies übernimmt hier automatisch die Anzeige der Arbeiter, Prios und seitlichen Modul-Reiter
    }
}