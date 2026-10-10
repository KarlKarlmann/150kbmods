package net.kb150.survivorcolonies.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.minecolonies.api.colony.CitizenNameFile;
import com.minecolonies.core.datalistener.CitizenNameListener;
import com.mojang.logging.LogUtils;
import net.kb150.survivorcolonies.entity.SurvivorEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class SurvivorDataLoader extends SimpleJsonResourceReloadListener {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static final List<Item> WEAPONS = new ArrayList<>();
    private static final List<Item> OFFHAND = new ArrayList<>();
    private static final List<Item> HELMETS = new ArrayList<>();
    private static final List<Item> CHESTPLATES = new ArrayList<>();
    private static final List<Item> LEGGINGS = new ArrayList<>();
    private static final List<Item> BOOTS = new ArrayList<>();
    private static final List<ItemStack> EXTRA_ITEMS = new ArrayList<>();
    private static final List<ItemStack> RECRUIT_COSTS = new ArrayList<>();

    public SurvivorDataLoader() {
        super(GSON, "survivors");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> jsonMap, ResourceManager resourceManager, ProfilerFiller profiler) {
        WEAPONS.clear();
        OFFHAND.clear();
        HELMETS.clear();
        CHESTPLATES.clear();
        LEGGINGS.clear();
        BOOTS.clear();
        EXTRA_ITEMS.clear();
        RECRUIT_COSTS.clear();

        for (Map.Entry<ResourceLocation, JsonElement> entry : jsonMap.entrySet()) {
            try {
                JsonObject json = GsonHelper.convertToJsonObject(entry.getValue(), "survivor data file");

                parseList(json, "weapons", WEAPONS, elem -> getItem(elem.getAsString()));
                parseList(json, "offhand", OFFHAND, elem -> getItem(elem.getAsString()));
                parseList(json, "helmets", HELMETS, elem -> getItem(elem.getAsString()));
                parseList(json, "chestplates", CHESTPLATES, elem -> getItem(elem.getAsString()));
                parseList(json, "leggings", LEGGINGS, elem -> getItem(elem.getAsString()));
                parseList(json, "boots", BOOTS, elem -> getItem(elem.getAsString()));

                parseList(json, "extra_items", EXTRA_ITEMS, this::parseItemStack);
                parseList(json, "recruit_costs", RECRUIT_COSTS, this::parseItemStack);

            } catch (Exception e) {
                LOGGER.error("Fehler beim Lesen der Survivor-Datei: {}", entry.getKey(), e);
            }
        }
        LOGGER.info("Survivor-Ausrüstungspools geladen: {} Waffen, {} Rekrutierungskosten.", WEAPONS.size(), RECRUIT_COSTS.size());
    }

    private <T> void parseList(JsonObject rootJson, String key, List<T> targetList, Function<JsonElement, T> parser) {
        if (!rootJson.has(key)) return;

        boolean shouldReplace = GsonHelper.getAsBoolean(rootJson, "replace", false);
        JsonArray array;

        JsonElement element = rootJson.get(key);
        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            if (obj.has("replace")) {
                shouldReplace = GsonHelper.getAsBoolean(obj, "replace", false);
            }
            array = GsonHelper.getAsJsonArray(obj, "values");
        } else {
            array = element.getAsJsonArray();
        }

        if (shouldReplace) {
            targetList.clear();
        }

        for (JsonElement elem : array) {
            T parsed = parser.apply(elem);
            if (parsed != null && parsed != Items.AIR) {
                targetList.add(parsed);
            }
        }
    }

    private ItemStack parseItemStack(JsonElement elem) {
        if (!elem.isJsonObject()) return ItemStack.EMPTY;
        JsonObject obj = elem.getAsJsonObject();
        Item item = getItem(GsonHelper.getAsString(obj, "item"));
        int count = GsonHelper.getAsInt(obj, "count", 1);
        return item != Items.AIR ? new ItemStack(item, count) : ItemStack.EMPTY;
    }

    private Item getItem(String id) {
        return BuiltInRegistries.ITEM.get(new ResourceLocation(id));
    }

    /**
     * Greift direkt auf das geladene Namensregister von MineColonies zu,
     * um ohne eigene Duplikate echte Kolonie-Namen zu erzeugen.
     */
    public static String generateMinecoloniesName(boolean isFemale, RandomSource random) {
        if (CitizenNameListener.nameFileMap == null || CitizenNameListener.nameFileMap.isEmpty()) {
            return isFemale ? "Überlebende" : "Überlebender";
        }

        // Wählt ein beliebiges geladenes Namens-Preset aus MineColonies (z.B. default, nordic, etc.)
        List<CitizenNameFile> nameFiles = new ArrayList<>(CitizenNameListener.nameFileMap.values());
        CitizenNameFile nameFile = nameFiles.get(random.nextInt(nameFiles.size()));

        List<String> firstNames = isFemale ? nameFile.femalefirstNames : nameFile.maleFirstNames;
        if (firstNames == null || firstNames.isEmpty()) {
            return isFemale ? "Überlebende" : "Überlebender";
        }

        String firstName = firstNames.get(random.nextInt(firstNames.size()));
        if (nameFile.parts <= 1 || nameFile.surnames == null || nameFile.surnames.isEmpty()) {
            return firstName;
        }

        String lastName = nameFile.surnames.get(random.nextInt(nameFile.surnames.size()));

        if (nameFile.order == CitizenNameFile.NameOrder.EASTERN) {
            return lastName + " " + firstName;
        } else if (nameFile.parts == 3) {
            char middle = (char) ('A' + random.nextInt(26));
            return firstName + " " + middle + ". " + lastName;
        } else {
            return firstName + " " + lastName;
        }
    }

    public static void equipRandomly(SurvivorEntity survivor, RandomSource random) {
        boolean isFemale = random.nextBoolean();
        survivor.setFemale(isFemale);

        // Name aus MineColonies Namenspools generieren
        survivor.setSurvivorName(generateMinecoloniesName(isFemale, random));

        if (!WEAPONS.isEmpty()) survivor.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(WEAPONS.get(random.nextInt(WEAPONS.size()))));
        if (!OFFHAND.isEmpty()) survivor.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(OFFHAND.get(random.nextInt(OFFHAND.size()))));
        if (!HELMETS.isEmpty()) survivor.setItemSlot(EquipmentSlot.HEAD, new ItemStack(HELMETS.get(random.nextInt(HELMETS.size()))));
        if (!CHESTPLATES.isEmpty()) survivor.setItemSlot(EquipmentSlot.CHEST, new ItemStack(CHESTPLATES.get(random.nextInt(CHESTPLATES.size()))));
        if (!LEGGINGS.isEmpty()) survivor.setItemSlot(EquipmentSlot.LEGS, new ItemStack(LEGGINGS.get(random.nextInt(LEGGINGS.size()))));
        if (!BOOTS.isEmpty()) survivor.setItemSlot(EquipmentSlot.FEET, new ItemStack(BOOTS.get(random.nextInt(BOOTS.size()))));

        if (!EXTRA_ITEMS.isEmpty()) survivor.setExtraItem(EXTRA_ITEMS.get(random.nextInt(EXTRA_ITEMS.size())).copy());
        if (!RECRUIT_COSTS.isEmpty()) {
            survivor.setRecruitCost(RECRUIT_COSTS.get(random.nextInt(RECRUIT_COSTS.size())).copy());
        } else {
            survivor.setRecruitCost(new ItemStack(Items.EMERALD, 5));
        }
    }
}