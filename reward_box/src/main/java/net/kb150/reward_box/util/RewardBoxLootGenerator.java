package net.kb150.reward_box.util;

import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import net.kb150.reward_box.RewardBox;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RewardBoxLootGenerator {

    private static List<Item> cachedAllItems = null;

    private static List<Item> getAllItemsCached() {
        if (cachedAllItems == null) {
            cachedAllItems = ForgeRegistries.ITEMS.getValues().stream()
                    .filter(item -> item != Items.AIR)
                    .toList();
        }
        return cachedAllItems;
    }

    public static void fillChestWithLoot(String boxId, int tier, ServerLevel level, Vec3 pos, NonNullList<ItemStack> container) {
        List<ItemStack> loot = new ArrayList<>();
        RandomSource random = level.getRandom();

        RewardBoxConfigManager.BoxDefinition def = RewardBoxConfigManager.getDefinition(boxId);

        if (def == null) {
            RewardBox.LOGGER.warn("[RewardBox] Keinen Loot-Eintrag für Kiste '{}' gefunden! Verwende Fallback.", boxId);
            loot.add(new ItemStack(Items.EXPERIENCE_BOTTLE, Math.max(1, tier)));
        } else {
            // 1. Garantierte Items aus der JSON (geprüft gegen min_tier UND max_tier)
            for (RewardBoxConfigManager.GuaranteedItemRule rule : def.guaranteedItems()) {
                if (tier >= rule.minTier() && tier <= rule.maxTier()) {
                    Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(rule.itemId()));
                    if (item != null && item != Items.AIR) {
                        int totalCount = rule.count() + (rule.countPerTier() * tier);
                        totalCount = Math.max(1, totalCount);

                        ItemStack stack = new ItemStack(item, totalCount);
                        if (rule.nbt() != null && !rule.nbt().isEmpty()) {
                            try {
                                CompoundTag parsedNbt = TagParser.parseTag(rule.nbt());
                                stack.getOrCreateTag().merge(parsedNbt);
                            } catch (Exception ignored) {}
                        }
                        loot.add(stack);
                    }
                }
            }

            // 2. Vanilla Loot Pools aus der JSON
            List<ResourceLocation> validTables = new ArrayList<>();
            for (RewardBoxConfigManager.LootPoolRule poolRule : def.vanillaLootPools()) {
                if (tier >= poolRule.minTier()) {
                    validTables.addAll(poolRule.tables());
                }
            }

            if (!validTables.isEmpty()) {
                ResourceLocation tableId = validTables.get(random.nextInt(validTables.size()));
                LootParams params = new LootParams.Builder(level)
                        .withParameter(LootContextParams.ORIGIN, pos)
                        .create(LootContextParamSets.CHEST);

                List<ItemStack> vanillaLoot = level.getServer().getLootData().getLootTable(tableId).getRandomItems(params);
                List<ItemStack> modifiable = new ArrayList<>(vanillaLoot);
                Collections.shuffle(modifiable, new java.util.Random(random.nextLong()));

                int halfSize = Math.max(1, modifiable.size() / 2);
                for (int i = 0; i < halfSize; i++) {
                    loot.add(modifiable.get(i));
                }
            }

            // 3. Crazy Items (Eskalation ab gewähltem Tier)
            if (def.crazyModItems() != null && tier >= def.crazyModItems().minTier()) {
                List<Item> allItems = getAllItemsCached();

                int crazyCount = 3 + random.nextInt(tier);
                for (int i = 0; i < crazyCount; i++) {
                    Item randomItem = allItems.get(random.nextInt(allItems.size()));
                    ItemStack crazyStack = new ItemStack(randomItem);

                    int maxSize = crazyStack.getMaxStackSize();
                    if (maxSize > 1) {
                        crazyStack.setCount(1 + random.nextInt(maxSize));
                    }

                    if (def.crazyModItems().enchantScaling() && crazyStack.isEnchantable()) {
                        int crazyEnchantLevel = 30 + random.nextInt(tier * 5);
                        try {
                            crazyStack = EnchantmentHelper.enchantItem(random, crazyStack, crazyEnchantLevel, true);
                        } catch (Exception ignored) {}
                    }

                    loot.add(crazyStack);
                }
            }
        }

        // 4. Inventar-Limitierung auf 27 Slots & Mischen
        if (loot.size() > 27) {
            loot = loot.subList(0, 27);
        }
        Collections.shuffle(loot, new java.util.Random(random.nextLong()));

        // 5. In das Inventar der BlockEntity schreiben
        container.clear();
        for (int i = 0; i < loot.size() && i < container.size(); i++) {
            container.set(i, loot.get(i));
        }
    }
}