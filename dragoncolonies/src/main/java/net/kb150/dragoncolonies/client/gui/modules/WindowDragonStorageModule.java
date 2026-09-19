package net.kb150.dragoncolonies.client.gui.modules;

import com.ldtteam.blockui.BOGuiGraphics;
import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.controls.Button;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.views.ScrollingList;
import com.minecolonies.core.client.gui.AbstractModuleWindow;
import net.kb150.dragoncolonies.DragonColonies;
import net.kb150.dragoncolonies.network.DragonColoniesNetwork;
import net.kb150.dragoncolonies.network.message.EmergencyRecallMessage;
import net.kb150.dragoncolonies.network.message.ReleaseDragonMessage;
import net.kb150.dragoncolonies.network.message.RequestRoostPointerMessage;
import net.kb150.dragoncolonies.network.message.RetrieveDragonMessage;
import net.kb150.dragoncolonies.network.message.RequestExportOffersMessage;
import net.kb150.dragoncolonies.network.message.ToggleBreedingStatusMessage;

import net.magister.bookofdragons.client.gui.book.DragonStatGrader;
import net.magister.bookofdragons.entity.base.dragon.DragonBase;
import net.magister.bookofdragons.entity.data.DragonType;
import net.magister.bookofdragons.entity.data.GrowthStage;
import net.magister.bookofdragons.entity.state.GroundStance;
import net.magister.bookofdragons.entity.state.TransportMode;
import net.magister.bookofdragons.entity.stats.PrimaryAttribute;
import net.magister.bookofdragons.entity.stats.SpeciesStatRegistry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class WindowDragonStorageModule extends AbstractModuleWindow<DragonStorageModuleView> {

    private ScrollingList listStored;
    private Text titleText;

    private Text detailName;
    private Text detailSpecies;
    private Text detailStage;
    private Text detailStatusBadge;
    private Text detailHealth;
    private Text detailHunger;
    private Text detailAffection;
    private Text detailStatsGrid1;
    private Text detailStatsGrid2;

    private Button btnRetrieve;
    private Button btnEmergency;
    private Button btnSell;
    private Button btnRelease;
    private Button btnBreedToggle;

    private UUID selectedDragonUUID = null;
    private LivingEntity previewEntity = null;
    private ItemStack saddleItem = ItemStack.EMPTY;
    private ItemStack chestItem = ItemStack.EMPTY;
    private ItemStack favoriteDietItem = ItemStack.EMPTY;
    private ItemStack specialDietItem = ItemStack.EMPTY;

    private float currentHealth = 0;
    private float maxHealth = 0;
    private int hungerPercent = 0;
    private int affectionValue = 0;

    // Speichert den dynamischen Tooltip für die Karteikarten und den Zucht-Button (Manuell verwaltet für BlockUI)
    private final java.util.Map<Button, List<Component>> listHoverTooltips = new java.util.IdentityHashMap<>();
    private final List<Component> breedToggleTooltip = new ArrayList<>();

    public WindowDragonStorageModule(DragonStorageModuleView moduleView) {
        super(moduleView, new ResourceLocation(DragonColonies.MOD_ID, "gui/dragon_storage.xml"));

        this.registerButton("btn_request_pointer", this::onRequestPointerClicked);
        this.registerButton("btn_retrieve", this::onRetrieveClicked);
        this.registerButton("btn_emergency", this::onEmergencyClicked);
        this.registerButton("btn_sell", this::onSellClicked);
        this.registerButton("btn_release", this::onReleaseClicked);
        this.registerButton("btn_breed_toggle", this::onBreedToggleClicked);
    }

    @Override
    public void onOpened() {
        super.onOpened();

        if (this.window != null) {
            this.listStored = this.window.findPaneOfTypeByID("list_stored", ScrollingList.class);
            this.titleText = this.window.findPaneOfTypeByID("title_text", Text.class);

            this.detailName = this.window.findPaneOfTypeByID("detail_name", Text.class);
            this.detailSpecies = this.window.findPaneOfTypeByID("detail_species", Text.class);
            this.detailStage = this.window.findPaneOfTypeByID("detail_stage", Text.class);
            this.detailStatusBadge = this.window.findPaneOfTypeByID("detail_status_badge", Text.class);
            this.detailHealth = this.window.findPaneOfTypeByID("detail_health", Text.class);
            this.detailHunger = this.window.findPaneOfTypeByID("detail_hunger", Text.class);
            this.detailAffection = this.window.findPaneOfTypeByID("detail_affection", Text.class);
            this.detailStatsGrid1 = this.window.findPaneOfTypeByID("detail_stats_grid_1", Text.class);
            this.detailStatsGrid2 = this.window.findPaneOfTypeByID("detail_stats_grid_2", Text.class);

            this.btnRetrieve = this.window.findPaneOfTypeByID("btn_retrieve", Button.class);
            this.btnEmergency = this.window.findPaneOfTypeByID("btn_emergency", Button.class);
            this.btnSell = this.window.findPaneOfTypeByID("btn_sell", Button.class);
            this.btnRelease = this.window.findPaneOfTypeByID("btn_release", Button.class);
            this.btnBreedToggle = this.window.findPaneOfTypeByID("btn_breed_toggle", Button.class);
            
            resetInspector();
        }

        refreshStoredList();
    }

    @Override
    public void onClosed() {
        super.onClosed();
        this.previewEntity = null;
    }

    private void refreshStoredList() {
        if (this.moduleView == null) return;

        List<CompoundTag> storedDragons = this.moduleView.getStoredDragons();
        this.listHoverTooltips.clear(); // Liste leeren, damit sich nichts ansammelt

        if (this.titleText != null) {
            this.titleText.setText(Component.translatable("dragoncolonies.gui.module.dragon_storage.capacity", storedDragons.size(), this.moduleView.getCapacity()));
        }

        if (this.listStored == null) return;

        this.listStored.setDataProvider(new ScrollingList.DataProvider() {
            @Override
            public int getElementCount() {
                return storedDragons.size();
            }

            @Override
            public void updateElement(int index, @NotNull Pane rowPane) {
                try {
                    CompoundTag dragonNbt = storedDragons.get(index);

                    Button actionBtn = rowPane.findPaneOfTypeByID("action_btn", Button.class);
                    Text nameText = rowPane.findPaneOfTypeByID("row_name", Text.class);
                    Text subText = rowPane.findPaneOfTypeByID("row_sub", Text.class);
                    Text rankText = rowPane.findPaneOfTypeByID("row_rank", Text.class);

                    if (nameText != null) nameText.setEnabled(false);
                    if (subText != null) subText.setEnabled(false);
                    if (rankText != null) rankText.setEnabled(false);

                    String baseName = getDragonName(dragonNbt, index);
                    boolean isDeployed = dragonNbt.getBoolean("Deployed");
                    boolean isDead = dragonNbt.getBoolean("IsDead");
                    boolean isEgg = dragonNbt.getBoolean("DragonColonies_IsEgg");
                    boolean allowBreeding = dragonNbt.getBoolean("DragonColonies_AllowBreeding");

                    int currentStage = 2; // Default Adult
                    if (dragonNbt.contains("GrowthStage")) {
                        currentStage = dragonNbt.getInt("GrowthStage");
                    } else if (dragonNbt.contains("AgeTicks") && dragonNbt.getInt("AgeTicks") < 24000) {
                        currentStage = 0;
                    }

                    String displayName = baseName;
                    List<Component> rowTooltip = new ArrayList<>();

                    if (isDead) {
                        displayName += " " + Component.translatable("dragoncolonies.gui.module.dragon_storage.mia").getString();
                    } else if (isDeployed) {
                        displayName += " " + Component.translatable("dragoncolonies.gui.module.dragon_storage.deployed_short").getString();
                    } else if (isEgg) {
                        displayName += " [EGG]";
                    } else if (allowBreeding) {
                        int hunger = dragonNbt.contains("dragonNeeds") ? dragonNbt.getCompound("dragonNeeds").getInt("foodLevel") : 100;
                        float hp = dragonNbt.contains("Health") ? dragonNbt.getFloat("Health") : extractMaxHealth(dragonNbt);
                        float maxHp = extractMaxHealth(dragonNbt);
                        int cd = dragonNbt.getInt("DragonColonies_BreedingCooldown");

                        String breedTag = Component.translatable("dragoncolonies.gui.module.dragon_storage.status.breed").getString();

                        if (hunger < 80 || hp < (maxHp - 5.0f) || cd > 0) {
                            displayName += " §c" + breedTag + "§r"; 
                            rowTooltip.add(Component.translatable("dragoncolonies.gui.module.dragon_storage.status.breed_blocked"));
                            
                            if (cd > 0) rowTooltip.add(Component.translatable("dragoncolonies.gui.module.dragon_storage.status.breed_cd_detail", cd / 20));
                            if (hunger < 80) rowTooltip.add(Component.translatable("dragoncolonies.gui.module.dragon_storage.status.breed_hungry_detail", hunger));
                            if (hp < (maxHp - 5.0f)) rowTooltip.add(Component.translatable("dragoncolonies.gui.module.dragon_storage.status.breed_injured_detail", String.format(Locale.ROOT, "%.0f", hp), String.format(Locale.ROOT, "%.0f", maxHp)));
                        } else {
                            displayName += " §a" + breedTag + "§r"; 
                        }
                    }

                    DragonType type = parseDragonType(dragonNbt);
                    String subInfo = buildPrioritizedSubInfo(dragonNbt, type);

                    if (nameText != null) nameText.setText(Component.literal(displayName));
                    if (subText != null) subText.setText(Component.literal(subInfo));

                    if (rankText != null) {
                        String mainGrade = getSafeGradeString(type, PrimaryAttribute.AGI);
                        rankText.setText(Component.literal(mainGrade.isEmpty() ? "" : "★ " + mainGrade));
                    }

                    final String finalDisplayName = displayName;
                    final boolean finalDeployed = isDeployed;
                    final boolean finalDead = isDead;
                    final boolean finalEgg = isEgg;

                    if (actionBtn != null) {
                        if (!rowTooltip.isEmpty()) {
                            listHoverTooltips.put(actionBtn, rowTooltip);
                        }

                        actionBtn.setHandler(button -> {
                            if (dragonNbt.hasUUID("RoostDragonID")) {
                                selectedDragonUUID = dragonNbt.getUUID("RoostDragonID");
                            } else if (dragonNbt.hasUUID("UUID")) {
                                selectedDragonUUID = dragonNbt.getUUID("UUID");
                            }
                            updateDetailInspector(dragonNbt, finalDisplayName, finalDeployed, finalDead, finalEgg);
                        });
                    }
                } catch (Exception e) {
                    DragonColonies.LOGGER.error("UI Listenelement konnte nicht geladen werden!", e);
                }
            }
        });
    }

    private String getDragonName(CompoundTag tag, int index) {
        if (tag != null && tag.contains("CustomName")) {
            String raw = tag.getString("CustomName");
            if (!raw.isEmpty()) {
                try {
                    Component comp = Component.Serializer.fromJson(raw);
                    if (comp != null && !comp.getString().isEmpty()) return comp.getString();
                } catch (Exception ignored) {}
                return raw;
            }
        }
        DragonType type = parseDragonType(tag);
        return (type != null) ? type.getDisplayName() : Component.translatable("dragoncolonies.gui.module.dragon_storage.dragon_number", index + 1).getString();
    }

    private String buildPrioritizedSubInfo(CompoundTag tag, DragonType type) {
        if (tag == null) return Component.translatable("dragoncolonies.gui.module.dragon_storage.no_data").getString();
        
        int hunger = 100;
        if (tag.contains("dragonNeeds")) {
            hunger = tag.getCompound("dragonNeeds").getInt("foodLevel");
        }
        
        float health = tag.contains("Health") ? tag.getFloat("Health") : 0f;
        float maxHp = extractMaxHealth(tag);
        
        String species = (type != null) ? type.getDisplayName() : Component.translatable("dragoncolonies.gui.module.dragon_storage.unknown").getString();
        
        String hpFormatted = String.format(Locale.ROOT, "%.0f", health);
        String maxHpFormatted = String.format(Locale.ROOT, "%.0f", maxHp);

        return Component.translatable("dragoncolonies.gui.module.dragon_storage.subinfo", hunger, hpFormatted, maxHpFormatted, species).getString();
    }

    private DragonType parseDragonType(CompoundTag tag) {
        if (tag == null) return null;
        String entityIdStr = tag.contains("id") ? tag.getString("id") : tag.getString("DragonType");
        if (!entityIdStr.contains(":")) {
            entityIdStr = "bookofdragons:" + entityIdStr.toLowerCase(Locale.ROOT);
        }
        ResourceLocation entityLoc = new ResourceLocation(entityIdStr);
        for (DragonType dt : DragonType.values()) {
            if (dt.getEntityType() != null) {
                ResourceLocation dtLoc = ForgeRegistries.ENTITY_TYPES.getKey(dt.getEntityType());
                if (dtLoc != null && dtLoc.equals(entityLoc)) return dt;
            }
        }
        return DragonType.fromString(entityLoc.getPath());
    }

    private String getSafeGradeString(DragonType type, PrimaryAttribute attr) {
        if (type == null) return "-";
        try {
            if (SpeciesStatRegistry.getProfile(type.getSerializedName()) != null) {
                return String.valueOf(DragonStatGrader.grade(type, attr));
            }
        } catch (Exception ignored) {}
        return "-";
    }

    private void updateDetailInspector(CompoundTag dragonNbt, String displayName, boolean isDeployed, boolean isDead, boolean isEgg) {
        DragonType type = parseDragonType(dragonNbt);

        this.previewEntity = createPreviewEntity(dragonNbt);

        if (this.detailName != null) this.detailName.setText(Component.literal(displayName));
        if (this.detailSpecies != null) {
            String speciesStr = type != null ? type.getDisplayName() : Component.translatable("dragoncolonies.gui.module.dragon_storage.unknown").getString();
            this.detailSpecies.setText(Component.translatable("dragoncolonies.gui.module.dragon_storage.species", speciesStr));
        }

        int currentStage = 2; // Default Adult
        if (this.detailStage != null) {
            String stageStr = Component.translatable("dragoncolonies.gui.module.dragon_storage.stage.adult").getString();
            if (isEgg) {
                stageStr = "EGG";
                currentStage = 0;
            } else if (dragonNbt.contains("GrowthStage")) {
                currentStage = dragonNbt.getInt("GrowthStage");
                stageStr = GrowthStage.fromStageId(currentStage).getDisplayName();
            } else if (dragonNbt.contains("AgeTicks") && dragonNbt.getInt("AgeTicks") < 24000) {
                stageStr = Component.translatable("dragoncolonies.gui.module.dragon_storage.stage.baby").getString();
                currentStage = 0;
            }
            this.detailStage.setText(Component.translatable("dragoncolonies.gui.module.dragon_storage.stage", stageStr));
        }

        boolean allowBreeding = dragonNbt.getBoolean("DragonColonies_AllowBreeding");
        
        this.hungerPercent = 100;
        if (dragonNbt.contains("dragonNeeds")) {
            this.hungerPercent = dragonNbt.getCompound("dragonNeeds").getInt("foodLevel");
        }
        
        this.currentHealth = dragonNbt.contains("Health") ? dragonNbt.getFloat("Health") : 0f;
        this.maxHealth = extractMaxHealth(dragonNbt);

        if (this.detailStatusBadge != null) {
            if (isDead) {
                this.detailStatusBadge.setText(Component.translatable("dragoncolonies.gui.module.dragon_storage.status.mia"));
            } else if (isDeployed) {
                this.detailStatusBadge.setText(Component.translatable("dragoncolonies.gui.module.dragon_storage.status.deployed"));
            } else if (isEgg) {
                this.detailStatusBadge.setText(Component.literal("[INCUBATING]"));
            } else if (allowBreeding) {
                int cd = dragonNbt.getInt("DragonColonies_BreedingCooldown");
                if (cd > 0) {
                    this.detailStatusBadge.setText(Component.translatable("dragoncolonies.gui.module.dragon_storage.status.breed_cd_detail", cd / 20));
                } else if (this.hungerPercent < 80) {
                    this.detailStatusBadge.setText(Component.translatable("dragoncolonies.gui.module.dragon_storage.status.breed_hungry_detail", this.hungerPercent));
                } else if (this.currentHealth < (this.maxHealth - 5.0f)) {
                    this.detailStatusBadge.setText(Component.translatable("dragoncolonies.gui.module.dragon_storage.status.breed_injured_detail", String.format(Locale.ROOT, "%.0f", this.currentHealth), String.format(Locale.ROOT, "%.0f", this.maxHealth)));
                } else {
                    this.detailStatusBadge.setText(Component.translatable("dragoncolonies.gui.module.dragon_storage.status.breed_ready"));
                }
            } else {
                this.detailStatusBadge.setText(Component.translatable("dragoncolonies.gui.module.dragon_storage.status.ready"));
            }
        }

        if (this.detailHunger != null) {
            this.detailHunger.setText(Component.literal(this.hungerPercent + "%"));
        }

        if (this.detailHealth != null) {
            this.detailHealth.setText(Component.literal(String.format(Locale.ROOT, "%.0f / %.0f", this.currentHealth, this.maxHealth)));
        }

        this.affectionValue = 0; // Standardwert
        if (dragonNbt.contains("AffectionMap")) {
            CompoundTag affectionMap = dragonNbt.getCompound("AffectionMap");
            if (Minecraft.getInstance().player != null) {
                String playerUUID = Minecraft.getInstance().player.getUUID().toString();
                if (affectionMap.contains(playerUUID)) {
                    this.affectionValue = affectionMap.getInt(playerUUID);
                }
            }
        }
        if (this.detailAffection != null) {
            this.detailAffection.setText(Component.literal(this.affectionValue + " / 1000"));
        }

        if (this.detailStatsGrid1 != null && this.detailStatsGrid2 != null) {
            String con = getSafeGradeString(type, PrimaryAttribute.CON);
            String ath = getSafeGradeString(type, PrimaryAttribute.ATH);
            String agi = getSafeGradeString(type, PrimaryAttribute.AGI);
            String prw = getSafeGradeString(type, PrimaryAttribute.PRW);
            String pot = getSafeGradeString(type, PrimaryAttribute.POT);
            String vig = getSafeGradeString(type, PrimaryAttribute.VIG);

            this.detailStatsGrid1.setText(Component.translatable("dragoncolonies.gui.module.dragon_storage.stats1", con, ath, agi));
            this.detailStatsGrid2.setText(Component.translatable("dragoncolonies.gui.module.dragon_storage.stats2", prw, pot, vig));
        }

        boolean hasSaddle = dragonNbt.getBoolean("Saddle") || dragonNbt.getBoolean("HasSaddle");
        boolean hasChest = dragonNbt.getBoolean("Chest") || dragonNbt.getBoolean("HasChest");

        this.saddleItem = hasSaddle ? new ItemStack(Items.SADDLE) : ItemStack.EMPTY;
        this.chestItem = hasChest ? new ItemStack(Items.CHEST) : ItemStack.EMPTY;
        this.favoriteDietItem = getPrimaryDiet(type);
        this.specialDietItem = getSpecialDiet(type);

        if (this.btnBreedToggle != null) {
            this.btnBreedToggle.setText(Component.translatable(allowBreeding ? "dragoncolonies.gui.dragon_storage.btn_breed_on" : "dragoncolonies.gui.dragon_storage.btn_breed_off"));
            
            this.breedToggleTooltip.clear();
            if (isEgg) {
                this.breedToggleTooltip.add(Component.translatable("dragoncolonies.tooltip.breed_fail.is_egg"));
            } else if (currentStage < 2) {
                this.breedToggleTooltip.add(Component.translatable("dragoncolonies.tooltip.breed_fail.too_young"));
            } else if (this.hungerPercent < 80) {
                this.breedToggleTooltip.add(Component.translatable("dragoncolonies.tooltip.breed_fail.hungry"));
            } else if (dragonNbt.getInt("DragonColonies_BreedingCooldown") > 0) {
                this.breedToggleTooltip.add(Component.translatable("dragoncolonies.tooltip.breed_fail.cooldown"));
            } else {
                this.breedToggleTooltip.add(Component.translatable("dragoncolonies.tooltip.breed_success"));
            }
        }

        setButtonsEnabled(selectedDragonUUID != null, isDeployed, isDead, isEgg, currentStage, this.affectionValue);
    }

    private float extractMaxHealth(CompoundTag tag) {
        if (tag.contains("Attributes", Tag.TAG_LIST)) {
            ListTag attributes = tag.getList("Attributes", Tag.TAG_COMPOUND);
            for (int i = 0; i < attributes.size(); i++) {
                CompoundTag attr = attributes.getCompound(i);
                if (attr.getString("Name").equals("minecraft:generic.max_health")) {
                    return (float) attr.getDouble("Base");
                }
            }
        }
        return tag.contains("Health") ? tag.getFloat("Health") : 20.0f;
    }

	private LivingEntity createPreviewEntity(CompoundTag dragonNbt) {
		ClientLevel clientLevel = Minecraft.getInstance().level;
		if (clientLevel == null || dragonNbt == null) return null;
		try {
			CompoundTag copy = dragonNbt.copy();

			// FIX: Reinen Text in valides JSON umwandeln, damit Minecrafts Serializer nicht abstürzt
			if (copy.contains("CustomName")) {
				String rawName = copy.getString("CustomName");
				if (!rawName.startsWith("{")) {
					copy.putString("CustomName", Component.Serializer.toJson(Component.literal(rawName)));
				}
			}

			Entity loaded = EntityType.loadEntityRecursive(copy, clientLevel, e -> e);
			if (loaded instanceof LivingEntity living) {
				if (living instanceof DragonBase dragon) {
					dragon.setTransportMode(TransportMode.GROUNDED);
					dragon.setGroundStance(GroundStance.IDLE);
				}
				return living;
			}
		} catch (Exception e) {
			DragonColonies.LOGGER.error("Fehler beim Erstellen der Drachen-Vorschau", e);
		}
		return null;
	}

    private ItemStack getPrimaryDiet(DragonType type) {
        if (type == null) return ItemStack.EMPTY;
        try {
            var profile = SpeciesStatRegistry.getProfile(type.getSerializedName());
            if (profile != null && !profile.favoriteFoods().isEmpty()) {
                Item item = ForgeRegistries.ITEMS.getValue(profile.favoriteFoods().iterator().next());
                if (item != null) return new ItemStack(item);
            }
        } catch (Exception ignored) {}
        return new ItemStack(Items.COD);
    }

    private ItemStack getSpecialDiet(DragonType type) {
        if (type == null) return ItemStack.EMPTY;
        try {
            var profile = SpeciesStatRegistry.getProfile(type.getSerializedName());
            if (profile != null && !profile.specialFoods().isEmpty()) {
                Item item = ForgeRegistries.ITEMS.getValue(profile.specialFoods().iterator().next());
                if (item != null) return new ItemStack(item);
            }
        } catch (Exception ignored) {}
        return ItemStack.EMPTY;
    }

    @Override
    public void draw(BOGuiGraphics guiGraphics, double mouseX, double mouseY) {
        try {
            super.draw(guiGraphics, mouseX, mouseY);
        } catch (Exception e) {
            DragonColonies.LOGGER.error("Fehler beim Basis-Zeichnen der UI!", e);
        }

        if (this.window == null) return;
        Pane detailContainer = this.window.findPaneByID("detail_container");
        if (detailContainer == null) return;

        try {
            int containerX = this.window.getX() + detailContainer.getX();
            int containerY = this.window.getY() + detailContainer.getY();

            if (this.previewEntity != null) {
                int renderX = containerX + 94;
                int renderY = containerY + 48;
                
                float bbWidth = Math.max(0.8f, this.previewEntity.getBbWidth());
                int scale = (int) (16.0f / bbWidth);

                org.joml.Quaternionf pose = new org.joml.Quaternionf().rotationZ((float) Math.PI);

                float profileAngle = 210.0F;
                this.previewEntity.yBodyRot = profileAngle;
                this.previewEntity.yBodyRotO = profileAngle;
                this.previewEntity.setYRot(profileAngle);
                this.previewEntity.yRotO = profileAngle;
                this.previewEntity.setXRot(0.0F);
                this.previewEntity.xRotO = 0.0F;

                InventoryScreen.renderEntityInInventory(guiGraphics, renderX, renderY, scale, pose, null, this.previewEntity);
            }

            int slotY = containerY + 150;

            renderItemSlot(guiGraphics, this.saddleItem, containerX + 6, slotY, mouseX, mouseY);
            renderItemSlot(guiGraphics, this.chestItem, containerX + 26, slotY, mouseX, mouseY);

            renderItemSlot(guiGraphics, this.favoriteDietItem, containerX + 95, slotY, mouseX, mouseY);
            renderItemSlot(guiGraphics, this.specialDietItem, containerX + 115, slotY, mouseX, mouseY);

            // 1. Tooltip für den Zucht Button unten zeichnen
            if (this.btnBreedToggle != null && this.btnBreedToggle.wasCursorInPane() && !this.breedToggleTooltip.isEmpty()) {
                guiGraphics.pose().pushPose();
                guiGraphics.pose().translate(0.0F, 0.0F, 500.0F);
                guiGraphics.renderComponentTooltip(Minecraft.getInstance().font, this.breedToggleTooltip, (int) mouseX, (int) mouseY);
                guiGraphics.flush(); // ZWINGEND ERFORDERLICH, damit der Tooltip im Vordergrund bleibt
                guiGraphics.pose().popPose();
            }

            // 2. Tooltips für die Listen-Einträge zeichnen
            if (!this.listHoverTooltips.isEmpty()) {
                for (java.util.Map.Entry<Button, List<Component>> entry : this.listHoverTooltips.entrySet()) {
                    if (entry.getKey() != null && entry.getKey().wasCursorInPane() && entry.getValue() != null && !entry.getValue().isEmpty()) {
                        guiGraphics.pose().pushPose();
                        guiGraphics.pose().translate(0.0F, 0.0F, 500.0F);
                        guiGraphics.renderComponentTooltip(Minecraft.getInstance().font, entry.getValue(), (int) mouseX, (int) mouseY);
                        guiGraphics.flush(); // ZWINGEND ERFORDERLICH!
                        guiGraphics.pose().popPose();
                        break;
                    }
                }
            }
        } catch (Exception e) {
            DragonColonies.LOGGER.error("Fehler beim Zeichnen von Custom-Elementen in Storage-GUI", e);
        }
    }

    private void renderItemSlot(BOGuiGraphics guiGraphics, ItemStack stack, int x, int y, double mouseX, double mouseY) {
        guiGraphics.fill(x - 1, y - 1, x + 17, y + 17, 0x88000000);
        guiGraphics.fill(x, y, x + 16, y + 16, 0x44FFFFFF);

        if (stack != null && !stack.isEmpty()) {
            guiGraphics.renderItem(stack, x, y);

            if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
                guiGraphics.pose().pushPose();
                guiGraphics.pose().translate(0.0F, 0.0F, 500.0F);
                guiGraphics.renderTooltip(Minecraft.getInstance().font, stack, (int) mouseX, (int) mouseY);
                guiGraphics.flush(); // <- Dieser Aufruf fehlte! Er zwingt Minecraft, den Tooltip *jetzt* im Vordergrund zu rendern.
                guiGraphics.pose().popPose();
            }
        }
    }

    private void setButtonsEnabled(boolean enabled, boolean isDeployed, boolean isDead, boolean isEgg, int stage, int affection) {
        if (this.btnRetrieve != null) this.btnRetrieve.setEnabled(enabled && !isDeployed && !isDead && !isEgg);
        if (this.btnEmergency != null) this.btnEmergency.setEnabled(enabled && isDeployed && !isDead && !isEgg);
        if (this.btnSell != null) this.btnSell.setEnabled(enabled && !isDeployed && !isDead && !isEgg);
        if (this.btnRelease != null) this.btnRelease.setEnabled(enabled && (!isDeployed || isDead));
        
        // Zucht Toggle Hard-Lock! (Nur klickbar wenn Stage >= 2 und Affection >= 800)
        if (this.btnBreedToggle != null) {
            this.btnBreedToggle.setEnabled(enabled && !isDeployed && !isDead && !isEgg && stage >= 2 && affection >= 800);
        }
    }

    private void onRequestPointerClicked(Button button) {
        if (this.buildingView != null) {
            DragonColoniesNetwork.CHANNEL.sendToServer(new RequestRoostPointerMessage(this.buildingView.getID()));
        }
    }

    private void onRetrieveClicked(Button button) {
        if (selectedDragonUUID != null && this.buildingView != null) {
            DragonColoniesNetwork.CHANNEL.sendToServer(new RetrieveDragonMessage(this.buildingView.getID(), selectedDragonUUID));
            resetInspector();
        }
    }

    private void onEmergencyClicked(Button button) {
        if (selectedDragonUUID != null && this.buildingView != null) {
            DragonColoniesNetwork.CHANNEL.sendToServer(new EmergencyRecallMessage(this.buildingView.getID(), selectedDragonUUID));
            resetInspector();
        }
    }

    private void onBreedToggleClicked(Button button) {
        if (selectedDragonUUID != null && this.buildingView != null) {
            DragonColoniesNetwork.CHANNEL.sendToServer(new ToggleBreedingStatusMessage(this.buildingView.getID(), selectedDragonUUID));
            
            if (this.moduleView != null) {
                for (CompoundTag tag : this.moduleView.getStoredDragons()) {
                    if ((tag.hasUUID("RoostDragonID") && tag.getUUID("RoostDragonID").equals(selectedDragonUUID)) ||
                        (tag.hasUUID("UUID") && tag.getUUID("UUID").equals(selectedDragonUUID))) {
                        
                        boolean current = tag.getBoolean("DragonColonies_AllowBreeding");
                        tag.putBoolean("DragonColonies_AllowBreeding", !current);
                        
                        if (this.btnBreedToggle != null) {
                            this.btnBreedToggle.setText(Component.translatable(!current ? "dragoncolonies.gui.dragon_storage.btn_breed_on" : "dragoncolonies.gui.dragon_storage.btn_breed_off"));
                        }
                        refreshStoredList();
                        break;
                    }
                }
            }
        }
    }

    private void onSellClicked(Button button) {
        if (selectedDragonUUID != null && this.buildingView != null) {
            DragonColoniesNetwork.CHANNEL.sendToServer(new RequestExportOffersMessage(this.buildingView.getID(), selectedDragonUUID));
        }
    }

    private void onReleaseClicked(Button button) {
        if (selectedDragonUUID != null && this.buildingView != null) {
            DragonColoniesNetwork.CHANNEL.sendToServer(new ReleaseDragonMessage(this.buildingView.getID(), selectedDragonUUID));
            resetInspector();
        }
    }

    private void resetInspector() {
        this.selectedDragonUUID = null;
        this.previewEntity = null;
        this.saddleItem = ItemStack.EMPTY;
        this.chestItem = ItemStack.EMPTY;
        this.favoriteDietItem = ItemStack.EMPTY;
        this.specialDietItem = ItemStack.EMPTY;
        this.breedToggleTooltip.clear();
        
        if (this.detailName != null) this.detailName.setText(Component.translatable("dragoncolonies.gui.dragon_storage.select_dragon"));
        if (this.detailSpecies != null) this.detailSpecies.setText(Component.translatable("dragoncolonies.gui.dragon_storage.species_placeholder"));
        if (this.detailStage != null) this.detailStage.setText(Component.translatable("dragoncolonies.gui.dragon_storage.stage_placeholder"));
        if (this.detailStatusBadge != null) this.detailStatusBadge.setText(Component.translatable("dragoncolonies.gui.dragon_storage.status_placeholder"));
        if (this.btnBreedToggle != null) this.btnBreedToggle.setText(Component.literal(""));
        
        setButtonsEnabled(false, false, false, false, 0, 0);
    }
}