package net.kb150.survivorcolonies.client;

import net.kb150.survivorcolonies.client.gui.SurvivorRecruitScreen;
import net.kb150.survivorcolonies.entity.SurvivorEntity;
import net.minecraft.client.Minecraft;

/**
 * Isolierte Hilfsklasse.
 * Diese Klasse wird vom Server niemals geladen, da der Aufruf
 * in SurvivorEntity durch isClientSide geschützt ist.
 */
public class ClientHooks {
    public static void openRecruitScreen(SurvivorEntity survivor) {
        Minecraft.getInstance().setScreen(new SurvivorRecruitScreen(survivor));
    }
}