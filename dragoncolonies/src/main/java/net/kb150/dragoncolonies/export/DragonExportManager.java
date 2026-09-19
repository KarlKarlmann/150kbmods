package net.kb150.dragoncolonies.export;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.kb150.dragoncolonies.DragonColonies;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Verwaltet die diplomatischen Fraktionen über eine externe JSON-Config.
 * Generiert per Seed-Hashing die täglichen Angebote für spezifische Drachen.
 */
public class DragonExportManager {

    // --- JSON DATENSTRUKTUREN ---

    public static class RewardItem {
        public String item;
        public float multiplier;

        public RewardItem() {}
        public RewardItem(String item, float multiplier) {
            this.item = item;
            this.multiplier = multiplier;
        }
    }

    public static class FactionData {
        public String id;
        public String name;
        public String iconPath;
        public List<RewardItem> rewardPool;
        public List<String> flavorTexts;

        public FactionData() {}

        public FactionData(String id, String name, String iconPath, List<RewardItem> rewardPool, List<String> flavorTexts) {
            this.id = id;
            this.name = name;
            this.iconPath = iconPath;
            this.rewardPool = rewardPool;
            this.flavorTexts = flavorTexts;
        }
    }

    // Wrapper-Klasse für die Config, um eine Anleitung / Kommentare in die JSON zu generieren
    public static class ExportConfig {
        public List<String> _instructions = Arrays.asList(
                "--- DRAGON EXPORT CONFIGURATION ---",
                "defaultRewardPool: A global list of items ANY faction can offer. Checked dynamically.",
                "rewardPool: A specific list of items this faction can offer.",
                "Missing mods/items are safely ignored. Amount = [Dragon Value] * multiplier * random(0.8 to 1.2).",
                "flavorTexts: The lore text shown in the UI. One text will be chosen randomly per offer.",
                "To apply changes, just save this file and restart the game/server."
        );
        public List<RewardItem> defaultRewardPool = new ArrayList<>();
        public Map<String, FactionData> factions = new HashMap<>();
    }

    // --- MANAGER LOGIK ---

    public static final Map<String, FactionData> FACTIONS = new HashMap<>();
    public static final List<RewardItem> DEFAULT_REWARD_POOL = new ArrayList<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void loadConfig() {
        Path configDir = FMLPaths.CONFIGDIR.get().resolve(DragonColonies.MOD_ID);
        Path configFile = configDir.resolve("export_factions.json");

        try {
            if (!Files.exists(configDir)) {
                Files.createDirectories(configDir);
            }

            if (!Files.exists(configFile)) {
                createDefaultConfig(configFile);
            }

            try (Reader reader = Files.newBufferedReader(configFile)) {
                ExportConfig loaded = GSON.fromJson(reader, ExportConfig.class);
                FACTIONS.clear();
                DEFAULT_REWARD_POOL.clear();

                if (loaded != null) {
                    if (loaded.factions != null) {
                        FACTIONS.putAll(loaded.factions);
                    }
                    if (loaded.defaultRewardPool != null) {
                        DEFAULT_REWARD_POOL.addAll(loaded.defaultRewardPool);
                    }
                }
                //DragonColonies.LOGGER.info("[DragonColonies] Successfully loaded {} export factions from JSON.", FACTIONS.size());
            }
        } catch (Exception e) {
            DragonColonies.LOGGER.error("[DragonColonies] Error loading export_factions.json", e);
            createFallbackFactions(new ExportConfig());
        }
    }

    private static void createDefaultConfig(Path configFile) {
        ExportConfig config = new ExportConfig();
        createFallbackFactions(config);
        try (Writer writer = Files.newBufferedWriter(configFile)) {
            GSON.toJson(config, writer);
            DragonColonies.LOGGER.info("[DragonColonies] Default export_factions.json was created.");
        } catch (Exception e) {
            DragonColonies.LOGGER.error("[DragonColonies] Error creating default config", e);
        }
    }

    private static void createFallbackFactions(ExportConfig config) {
        FACTIONS.clear();
        DEFAULT_REWARD_POOL.clear();

        // Standard-Währungen, die jede Fraktion in den Topf werfen kann
        DEFAULT_REWARD_POOL.add(new RewardItem("minecraft:emerald", 1.0f));
        DEFAULT_REWARD_POOL.add(new RewardItem("minecraft:diamond", 0.3f));
        DEFAULT_REWARD_POOL.add(new RewardItem("minecraft:gold_ingot", 1.5f));
        DEFAULT_REWARD_POOL.add(new RewardItem("somemod:missing_currency_example", 5.0f)); // Wird vom Filter aussortiert!

        FACTIONS.put("amazon", new FactionData("amazon", "Amazons", "minecolonies:textures/entity_icon/raiders/amazon.png",
                Arrays.asList(new RewardItem("minecraft:jungle_log", 8.0f), new RewardItem("minecraft:melon", 12.0f), new RewardItem("minecraft:cocoa_beans", 6.0f)),
                Arrays.asList(
                        "We can put such a mighty beast to good use in the deep jungle. Here is our tribute.",
                        "The jungle calls for a new apex predator. We accept this trade.",
                        "A fine specimen. It will serve the Amazons well."
                )
        ));

        FACTIONS.put("barbarian", new FactionData("barbarian", "Barbarians", "minecolonies:textures/entity_icon/raiders/barbarianchief1.png",
                Arrays.asList(new RewardItem("minecraft:iron_ingot", 2.0f), new RewardItem("minecraft:cooked_beef", 5.0f), new RewardItem("minecraft:coal", 4.0f)),
                Arrays.asList(
                        "A strong dragon for strong warriors! Take this in return, leader.",
                        "Our clan needs fire and fury. This beast will do.",
                        "Hah! The weak fear them, but the Barbarians ride them!"
                )
        ));

        FACTIONS.put("mummy", new FactionData("mummy", "Pharaoh", "minecolonies:textures/entity_icon/raiders/pharao.png",
                Arrays.asList(new RewardItem("minecraft:sand", 16.0f), new RewardItem("minecraft:lapis_lazuli", 1.5f), new RewardItem("minecraft:quartz", 2.0f)),
                Arrays.asList(
                        "The eternal desert will devour this creature... or it shall rule over it.",
                        "A worthy guardian for the ancient tombs. We offer these treasures.",
                        "By the old gods, this dragon shall be painted in gold."
                )
        ));

        FACTIONS.put("pirates", new FactionData("pirates", "Pirates", "minecolonies:textures/entity_icon/raiders/pirate1.png",
                Arrays.asList(new RewardItem("minecraft:gunpowder", 4.0f), new RewardItem("minecraft:tnt", 0.5f), new RewardItem("minecraft:nautilus_shell", 0.2f)),
                Arrays.asList(
                        "Yarrr! This beast will protect our fleet. Here is your share of the loot.",
                        "A flying cannon! Exactly what the captain ordered.",
                        "We plundered this treasure just for you. Hand over the lizard!"
                )
        ));

        FACTIONS.put("norsemen", new FactionData("norsemen", "Norsemen", "minecolonies:textures/entity_icon/raiders/norsemen_chief.png",
                Arrays.asList(new RewardItem("minecraft:iron_block", 0.2f), new RewardItem("minecraft:leather", 4.0f), new RewardItem("minecraft:spruce_log", 8.0f)),
                Arrays.asList(
                        "Cold iron for a fiery heart. A fair agreement between realms.",
                        "The frozen fjords will temper this beast. By Odin, we take it!",
                        "Our longships await. Bring the dragon aboard."
                )
        ));

        FACTIONS.put("drowned", new FactionData("drowned", "Drowned", "minecolonies:textures/entity_icon/raiders/drowned_pirate1.png",
                Arrays.asList(new RewardItem("minecraft:copper_ingot", 3.0f), new RewardItem("minecraft:prismarine_crystals", 2.0f), new RewardItem("minecraft:kelp", 10.0f)),
                Arrays.asList(
                        "From the unfathomable depths we bring these treasures... hand over the beast.",
                        "It will learn to hunt where the light does not reach.",
                        "Gurgle... the tide claims everything... even dragons."
                )
        ));

        config.factions.putAll(FACTIONS);
        config.defaultRewardPool.addAll(DEFAULT_REWARD_POOL);
    }

    /**
     * Prüft, ob ein Item als String existiert und geladen ist (Safe Mod Loading).
     */
    private static boolean isItemValid(String registryName) {
        if (registryName == null || !registryName.contains(":")) return false;
        try {
            ResourceLocation loc = new ResourceLocation(registryName);
            if (!ForgeRegistries.ITEMS.containsKey(loc)) return false; // Mod fehlt!
            Item item = ForgeRegistries.ITEMS.getValue(loc);
            return item != null && item != Items.AIR;
        } catch (Exception e) {
            return false;
        }
    }

    public static CompoundTag generateOffers(ServerLevel level, UUID dragonId, CompoundTag dragonNbt) {
        long day = level.getDayTime() / 24000L;
        // Hash: Seed + Tag + Drachen-ID = Einzigartig für DIESEN Drachen an DIESEM Ingame-Tag
        Random random = new Random(level.getSeed() + day + dragonId.getLeastSignificantBits());

        List<String> keys = new ArrayList<>(FACTIONS.keySet());
        Collections.shuffle(keys, random);

        CompoundTag result = new CompoundTag();
        ListTag offers = new ListTag();

        int baseValue = calculateDragonValue(dragonNbt);
        int offerCount = Math.min(3, keys.size());

        for (int i = 0; i < offerCount; i++) {
            FactionData faction = FACTIONS.get(keys.get(i));
            CompoundTag offerTag = new CompoundTag();
            offerTag.putString("FactionId", faction.id);
            offerTag.putString("FactionName", faction.name);
            offerTag.putString("Icon", faction.iconPath);

            String flavor = "We accept this trade.";
            if (faction.flavorTexts != null && !faction.flavorTexts.isEmpty()) {
                flavor = faction.flavorTexts.get(random.nextInt(faction.flavorTexts.size()));
            }
            offerTag.putString("Text", flavor);

            // GÜLTIGE Items aus beiden Pools (Fraktion + Default) zusammenführen
            List<RewardItem> validPool = new ArrayList<>();
            if (faction.rewardPool != null) {
                for (RewardItem ri : faction.rewardPool) {
                    if (isItemValid(ri.item)) validPool.add(ri);
                }
            }
            for (RewardItem ri : DEFAULT_REWARD_POOL) {
                if (isItemValid(ri.item)) validPool.add(ri);
            }

            // Fallback, falls alle Configs kaputt/leer sind
            if (validPool.isEmpty()) {
                validPool.add(new RewardItem("minecraft:emerald", 1.0f));
            }

            Collections.shuffle(validPool, random);
            
            // 1 bis 3 gültige Items als Angebot generieren
            int itemsCount = Math.min(validPool.size(), random.nextInt(3) + 1);
            ListTag items = new ListTag();

            for (int j = 0; j < itemsCount; j++) {
                RewardItem reward = validPool.get(j);
                float randomizer = 0.8f + (random.nextFloat() * 0.4f);
                int amount = Math.max(1, (int) (baseValue * reward.multiplier * randomizer));

                Item mcItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation(reward.item));
                addStacks(items, mcItem, amount);
            }

            offerTag.put("Items", items);
            offers.add(offerTag);
        }

        result.put("Offers", offers);
        return result;
    }
    
    private static void addStacks(ListTag list, Item item, int total) {
        while (total > 0) {
            int count = Math.min(64, total);
            list.add(new ItemStack(item, count).save(new CompoundTag()));
            total -= count;
        }
    }

    private static int calculateDragonValue(CompoundTag dragonNbt) {
        int score = 10; 
        
        if (dragonNbt.contains("GrowthStage")) {
            int stage = dragonNbt.getInt("GrowthStage");
            score += stage * 5;
        }
        if (dragonNbt.contains("Health")) {
            score += (int) (dragonNbt.getFloat("Health") * 0.5f);
        }
        
        if (dragonNbt.contains("Attributes", net.minecraft.nbt.Tag.TAG_LIST)) {
            ListTag attributes = dragonNbt.getList("Attributes", net.minecraft.nbt.Tag.TAG_COMPOUND);
            for (int i = 0; i < attributes.size(); i++) {
                CompoundTag attr = attributes.getCompound(i);
                if (attr.getString("Name").equals("minecraft:generic.max_health")) {
                    score += (int) (attr.getDouble("Base") * 0.2);
                }
            }
        }
        
        return Math.max(5, score);
    }
}