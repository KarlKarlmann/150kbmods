package net.kb150.reward_box.init;

import net.kb150.reward_box.RewardBox;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class RewardBoxSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, RewardBox.MODID);

    // 1. Das tiefe Rumpeln beim Anklicken (Anticipation)
    public static final RegistryObject<SoundEvent> BOX_CHARGE = registerSoundEvent("box_charge");
    
    // 2. Die Explosion, wenn die Kiste aufspringt (Burst)
    public static final RegistryObject<SoundEvent> BOX_BURST = registerSoundEvent("box_burst");
    
    // 3. Das "Wusch" beim Fliegen der Items (Transfer)
    public static final RegistryObject<SoundEvent> ITEM_SWOOSH = registerSoundEvent("item_swoosh");
    
    // 4. Das befriedigende Einrasten im Raster (Klick/Ping)
    public static final RegistryObject<SoundEvent> ITEM_LAND = registerSoundEvent("item_land");

    private static RegistryObject<SoundEvent> registerSoundEvent(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(RewardBox.MODID, name)));
    }

    public static void register(IEventBus eventBus) {
        SOUNDS.register(eventBus);
    }
}