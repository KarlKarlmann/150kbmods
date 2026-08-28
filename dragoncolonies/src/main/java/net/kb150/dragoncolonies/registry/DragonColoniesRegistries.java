package net.kb150.dragoncolonies.registry;

import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import com.minecolonies.api.colony.jobs.registry.JobEntry;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.api.items.ItemBlockHut;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule;
import com.minecolonies.core.colony.buildings.moduleviews.WorkerBuildingModuleView;
import com.minecolonies.core.colony.jobs.views.DefaultJobView;
import net.kb150.dragoncolonies.DragonColonies;
import net.kb150.dragoncolonies.blocks.BlockHutDragonRoost;
import net.kb150.dragoncolonies.blocks.BlockHutFireStation;
import net.kb150.dragoncolonies.buildings.BuildingDragonRoost;
import net.kb150.dragoncolonies.buildings.BuildingFireStation;
import net.kb150.dragoncolonies.buildings.modules.DragonStorageModule;
import net.kb150.dragoncolonies.client.gui.modules.DragonStorageModuleView;
import net.kb150.dragoncolonies.items.ItemRoostPointer;
import net.kb150.dragoncolonies.jobs.JobBeastmaster;
import net.kb150.dragoncolonies.jobs.JobDragonRider;
import net.kb150.dragoncolonies.jobs.JobFirefighter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import com.minecolonies.api.colony.guardtype.GuardType;
/**
 * Registrierung aller Blöcke, Items, Berufe und Gebäude für DragonColonies.
 */
public class DragonColoniesRegistries {

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, DragonColonies.MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, DragonColonies.MOD_ID);

    public static final DeferredRegister<JobEntry> JOBS = DeferredRegister.create(
            new ResourceLocation("minecolonies", "jobs"), 
            DragonColonies.MOD_ID
    );

    public static final DeferredRegister<BuildingEntry> BUILDINGS = DeferredRegister.create(
            new ResourceLocation("minecolonies", "buildings"), 
            DragonColonies.MOD_ID
    );

    public static final RegistryObject<BlockHutFireStation> BLOCK_HUT_FIRE_STATION = BLOCKS.register("blockhutfirestation", BlockHutFireStation::new);
    public static final RegistryObject<BlockHutDragonRoost> BLOCK_HUT_DRAGON_ROOST = BLOCKS.register("blockhutdragonroost", BlockHutDragonRoost::new);

    public static final RegistryObject<Item> ITEM_HUT_FIRE_STATION = ITEMS.register("blockhutfirestation", 
        () -> new ItemBlockHut(BLOCK_HUT_FIRE_STATION.get(), new Item.Properties()));

    public static final RegistryObject<Item> ITEM_HUT_DRAGON_ROOST = ITEMS.register("blockhutdragonroost", 
        () -> new ItemBlockHut(BLOCK_HUT_DRAGON_ROOST.get(), new Item.Properties()));

    public static final RegistryObject<Item> ROOST_POINTER = ITEMS.register("roost_pointer",
            () -> new ItemRoostPointer(new Item.Properties()));

    public static final RegistryObject<JobEntry> FIREFIGHTER_JOB = JOBS.register("firefighter",
            () -> new JobEntry.Builder()
                    .setRegistryName(new ResourceLocation(DragonColonies.MOD_ID, "firefighter"))
                    .setJobProducer(JobFirefighter::new)
                    .setJobViewProducer(() -> DefaultJobView::new)
                    .createJobEntry()
    );

    public static final RegistryObject<JobEntry> BEASTMASTER_JOB = JOBS.register("beastmaster",
            () -> new JobEntry.Builder()
                    .setRegistryName(new ResourceLocation(DragonColonies.MOD_ID, "beastmaster"))
                    .setJobProducer(JobBeastmaster::new)
                    .setJobViewProducer(() -> DefaultJobView::new)
                    .createJobEntry()
    );

    public static final RegistryObject<JobEntry> DRAGONRIDER_JOB = JOBS.register("dragonrider",
            () -> new JobEntry.Builder()
                    .setRegistryName(new ResourceLocation(DragonColonies.MOD_ID, "dragonrider"))
                    .setJobProducer(JobDragonRider::new)
                    .setJobViewProducer(() -> DefaultJobView::new)
                    .createJobEntry()
    );

    public static final BuildingEntry.ModuleProducer<WorkerBuildingModule, WorkerBuildingModuleView> BEASTMASTER_WORK = 
            new BuildingEntry.ModuleProducer<>(
                    "beastmaster_work",
                    () -> new WorkerBuildingModule(BEASTMASTER_JOB.get(), Skill.Adaptability, Skill.Strength, true, b -> 1),
                    () -> WorkerBuildingModuleView::new
            );

    public static final BuildingEntry.ModuleProducer<WorkerBuildingModule, WorkerBuildingModuleView> DRAGONRIDER_WORK = 
            new BuildingEntry.ModuleProducer<>(
                    "dragonrider_work",
                    () -> new WorkerBuildingModule(DRAGONRIDER_JOB.get(), Skill.Agility, Skill.Adaptability, true, b -> Math.max(1, b.getBuildingLevel() - 1)),
                    () -> WorkerBuildingModuleView::new
            );

    public static final BuildingEntry.ModuleProducer<DragonStorageModule, DragonStorageModuleView> DRAGON_STORAGE = 
            new BuildingEntry.ModuleProducer<>(
                    "dragon_storage",
                    DragonStorageModule::new,
                    () -> DragonStorageModuleView::new
            );

    public static final RegistryObject<BuildingEntry> FIRE_STATION_BUILDING = BUILDINGS.register("firestation",
            () -> new BuildingEntry.Builder()
                    .setRegistryName(new ResourceLocation(DragonColonies.MOD_ID, "firestation"))
                    .setBuildingBlock(BLOCK_HUT_FIRE_STATION.get())
                    .setBuildingProducer(BuildingFireStation::new)
                    .setBuildingViewProducer(() -> BuildingFireStation.View::new)
                    .createBuildingEntry()
    );

    public static final RegistryObject<BuildingEntry> DRAGON_ROOST_BUILDING = BUILDINGS.register("dragonroost",
            () -> new BuildingEntry.Builder()
                    .setBuildingBlock(BLOCK_HUT_DRAGON_ROOST.get())
                    .setBuildingProducer(BuildingDragonRoost::new)
                    .setBuildingViewProducer(() -> BuildingDragonRoost.View::new)
                    .setRegistryName(new ResourceLocation(DragonColonies.MOD_ID, "dragonroost"))
                    .addBuildingModuleProducer(BEASTMASTER_WORK)
                    .addBuildingModuleProducer(DRAGONRIDER_WORK)
                    .addBuildingModuleProducer(DRAGON_STORAGE)
                    .addBuildingModuleProducer(BuildingModules.GUARD_ENTITY_LIST)
                    .addBuildingModuleProducer(BuildingModules.GUARD_SETTINGS)
                    .addBuildingModuleProducer(BuildingModules.MIN_STOCK)
                    .addBuildingModuleProducer(BuildingModules.STATS_MODULE)
                    .createBuildingEntry()
    );
	// Ganz oben zu deinen anderen Registern:
	public static final DeferredRegister<GuardType> GUARD_TYPES = DeferredRegister.create(
			new ResourceLocation("minecolonies", "guardtypes"), 
			DragonColonies.MOD_ID
	);

	// Dann die GuardType Registrierung:
	public static final RegistryObject<GuardType> DRAGONRIDER_GUARD = GUARD_TYPES.register("dragonrider",
			() -> new GuardType.Builder()
					.setJobEntry(DRAGONRIDER_JOB)
					.setJobTranslationKey("com.minecolonies.job.dragonrider")
					.setButtonTranslationKey("com.minecolonies.gui.button.dragonrider")
					.setPrimarySkill(Skill.Agility) // Wie in deinem Modul definiert
					.setSecondarySkill(Skill.Adaptability)
					.setWorkerSoundName("minecolonies:worker.knight") // Fallback-Sound
					.setClazz(JobDragonRider.class)
					.setRegistryName(new ResourceLocation(DragonColonies.MOD_ID, "dragonrider"))
					.createGuardType()
	);

	// Vergiss nicht, GUARD_TYPES in deiner register()-Methode am Ende an den EventBus zu übergeben:
	// GUARD_TYPES.register(modEventBus);
		public static void register(IEventBus modEventBus) {
			BLOCKS.register(modEventBus);
			ITEMS.register(modEventBus);
			JOBS.register(modEventBus);
			BUILDINGS.register(modEventBus);
			GUARD_TYPES.register(modEventBus);
		}
}