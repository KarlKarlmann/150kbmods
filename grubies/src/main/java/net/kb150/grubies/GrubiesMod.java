package net.kb150.grubies;

import com.mojang.serialization.Codec;
import net.kb150.grubies.client.ClientSetup;
import net.kb150.grubies.config.GrubiesConfig;
import net.kb150.grubies.effect.GrubTripEffect;
import net.kb150.grubies.item.GrubItem;
import net.kb150.grubies.loot.GrubLootModifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(GrubiesMod.MODID)
public class GrubiesMod {
    public static final String MODID = "grubies";
    public static final Logger LOGGER = LogManager.getLogger(MODID);

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<MobEffect> MOB_EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, MODID);
    public static final DeferredRegister<Codec<? extends IGlobalLootModifier>> LOOT_MODIFIERS =
            DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, MODID);

    public static final RegistryObject<Item> GRUB = ITEMS.register("grub", GrubItem::new);
    
    // Zweigeteilter MobEffect zur verlustfreien Übertragung von 16-Bit Seeds via Vanilla-Amplifier
    public static final RegistryObject<MobEffect> TRIP_EFFECT_A = MOB_EFFECTS.register("trip_a", GrubTripEffect::new);
    public static final RegistryObject<MobEffect> TRIP_EFFECT_B = MOB_EFFECTS.register("trip_b", GrubTripEffect::new);
    
    public static final RegistryObject<Codec<? extends IGlobalLootModifier>> GRUB_LOOT_MODIFIER =
            LOOT_MODIFIERS.register("grub_loot", () -> GrubLootModifier.CODEC.get());

    public static boolean isLogging(String channel) {
        return GrubiesConfig.isLogging(channel);
    }

    public static void debug(String channel, String message, Object... args) {
        if (isLogging(channel)) {
            LOGGER.warn("[" + channel + "] " + message, args);
        }
    }

    public static void debugStack(String channel, String message) {
        if (isLogging(channel)) {
            LOGGER.warn("[" + channel + "] " + message, new Throwable(channel + "-Trace"));
        }
    }

    public GrubiesMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, GrubiesConfig.SPEC);
        bus.addListener(GrubiesConfig::onConfigLoad);

        ITEMS.register(bus);
        MOB_EFFECTS.register(bus);
        LOOT_MODIFIERS.register(bus);

        MinecraftForge.EVENT_BUS.register(this);

        if (FMLEnvironment.dist.isClient()) {
            ClientSetup.init(bus);
        }

        LOGGER.info("[Grubies] Initialized core registers with dual MobEffect 16-bit seed engine.");
    }
}