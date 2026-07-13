package net.kb150.everyonehashats.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.gui.widget.ForgeSlider;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

public class HatStudioScreen extends Screen {

    // Zustandstracker für Live-Overrides
    public static boolean isStudioActive = false;
    public static float studioX, studioY, studioZ;
    public static float studioScaleX = 1.0f, studioScaleY = 1.0f, studioScaleZ = 1.0f;
    public static float studioRotX, studioRotY, studioRotZ;
    public static String studioSelectedBone = "";
    private static java.lang.reflect.Field cachedChildrenField;
    private static boolean childrenFieldResolved = false;
    
    // Sliders
    private ForgeSlider sliderX, sliderY, sliderZ;
    private ForgeSlider sliderScaleX, sliderScaleY, sliderScaleZ;
    private ForgeSlider sliderRotX, sliderRotY, sliderRotZ;

    // Spezielle Button Zustände
    private boolean linkScale = true;
    private boolean isUpdatingScale = false; // Verhindert Rekursionsschleifen beim Verlinken

    // Such- und Auswahllisten
    private final List<EntityType<?>> matchedEntities = new ArrayList<>();
    private final List<ItemStack> matchedHats = new ArrayList<>();
    private final List<String> availableBones = new ArrayList<>();

    private int activeEntityIndex = 0;
    private int activeHatIndex = 0;
    private int activeBoneIndex = 0;

    private LivingEntity previewEntity;
    private EditBox entitySearchBox;
    private EditBox hatSearchBox;
    private EditBox boneSearchBox;

    // UI-Elemente
    private Button btnPrevEntity, btnNextEntity, btnToggleEntityDropdown;
    private Button btnPrevHat, btnNextHat, btnToggleHatDropdown;
    private Button btnPrevBone, btnNextBone, btnToggleBoneDropdown;
    private Button btnSave, btnLinkScale;

    private boolean entityDropdownOpen = false;
    private boolean hatDropdownOpen = false;
    private boolean boneDropdownOpen = false;
    private boolean entityHasNativeRendering = false; // Sperrt den Editor optisch!

    private int dropdownScrollOffset = 0;
    private boolean hasUnsavedChanges = false;

    // Drehung und Zoom per Maus im Viewport (3D Kamera-System)
    private float rotationYaw = 180.0f;
    private float rotationPitch = 0.0f;
    private float zoomFactor = 1.0f;
    private boolean isDraggingEntity = false;

    public HatStudioScreen() {
        super(Component.literal("Everyone Has Hats - Tuning Studio"));
        isStudioActive = true;
    }

    @Override
    protected void init() {
        super.init();

        matchedEntities.clear();
        for (EntityType<?> type : ForgeRegistries.ENTITY_TYPES) {
            try {
                if (type.create(Minecraft.getInstance().level) instanceof LivingEntity) {
                    matchedEntities.add(type);
                }
            } catch (Exception ignored) {}
        }
        // QOL: Sortiere Entities alphabetisch für synchrone Vor-/Zurück-Pfeile
        matchedEntities.sort(Comparator.comparing(e -> getCleanName(e).toLowerCase(Locale.ROOT)));

        matchedHats.clear();
        for (var item : ForgeRegistries.ITEMS) {
            ResourceLocation key = ForgeRegistries.ITEMS.getKey(item);
            if (key != null && key.getNamespace().equals("simplehats")) {
                String path = key.getPath();
                // FIX: Nur echte Hüte laden, keine Lootbags, Scraps, Icons oder den Display-Stand
                if (!path.equals("special") && !path.contains("hatbag") && 
                    !path.contains("hatscraps") && !path.contains("haticon") && !path.contains("hatdisplay")) {
                    matchedHats.add(new ItemStack(item));
                }
            }
        }
        
        if (matchedHats.isEmpty()) {
            matchedHats.add(ItemStack.EMPTY);
        }
        // QOL: Sortiere Hüte alphabetisch ("Kein Hut" nach ganz oben)
        matchedHats.sort((h1, h2) -> {
            if (h1.isEmpty() && !h2.isEmpty()) return -1;
            if (!h1.isEmpty() && h2.isEmpty()) return 1;
            if (h1.isEmpty() && h2.isEmpty()) return 0;
            return h1.getHoverName().getString().compareToIgnoreCase(h2.getHoverName().getString());
        });

        // Suchboxen
        this.entitySearchBox = new EditBox(font, 20, 45, 120, 16, Component.literal("Entity Suche"));
        this.entitySearchBox.setValue(getCleanName(matchedEntities.get(activeEntityIndex)));
        this.entitySearchBox.setResponder(this::onEntitySearchChanged);

        this.btnPrevEntity = addRenderableWidget(Button.builder(Component.literal("<"), b -> selectPrevEntity()).bounds(145, 45, 16, 16).build());
        this.btnNextEntity = addRenderableWidget(Button.builder(Component.literal(">"), b -> selectNextEntity()).bounds(163, 45, 16, 16).build());
        this.btnToggleEntityDropdown = addRenderableWidget(Button.builder(Component.literal("v"), b -> {
            entityDropdownOpen = !entityDropdownOpen;
            hatDropdownOpen = false;
            boneDropdownOpen = false;
        }).bounds(181, 45, 16, 16).build());

        this.hatSearchBox = new EditBox(font, 20, 68, 120, 16, Component.literal("Hut Suche"));
        this.hatSearchBox.setValue(matchedHats.get(activeHatIndex).isEmpty() ? "Kein Hut" : matchedHats.get(activeHatIndex).getHoverName().getString());
        this.hatSearchBox.setResponder(this::onHatSearchChanged);

        this.btnPrevHat = addRenderableWidget(Button.builder(Component.literal("<"), b -> selectPrevHat()).bounds(145, 68, 16, 16).build());
        this.btnNextHat = addRenderableWidget(Button.builder(Component.literal(">"), b -> selectNextHat()).bounds(163, 68, 16, 16).build());
        this.btnToggleHatDropdown = addRenderableWidget(Button.builder(Component.literal("v"), b -> {
            hatDropdownOpen = !hatDropdownOpen;
            entityDropdownOpen = false;
            boneDropdownOpen = false;
        }).bounds(181, 68, 16, 16).build());

        this.boneSearchBox = new EditBox(font, 20, 91, 120, 16, Component.literal("Bone Suche"));
        this.boneSearchBox.setValue(studioSelectedBone.isEmpty() ? "Standard (Kopf)" : studioSelectedBone);
        this.boneSearchBox.setResponder(this::onBoneSearchChanged);

        this.btnPrevBone = addRenderableWidget(Button.builder(Component.literal("<"), b -> selectPrevBone()).bounds(145, 91, 16, 16).build());
        this.btnNextBone = addRenderableWidget(Button.builder(Component.literal(">"), b -> selectNextBone()).bounds(163, 91, 16, 16).build());
        this.btnToggleBoneDropdown = addRenderableWidget(Button.builder(Component.literal("v"), b -> {
            boneDropdownOpen = !boneDropdownOpen;
            entityDropdownOpen = false;
            hatDropdownOpen = false;
        }).bounds(181, 91, 16, 16).build());

        // NEUES ZWEI-SPALTEN LAYOUT (Platzsparend)
        int leftStart = 115;
        int rightStartTop = 45;
        int rightStartBottom = 115;
        int sliderHeight = 16;
        int rightX = this.width - 160;

        // --- LINKE SEITE (Positionen) ---
        addRenderableWidget(Button.builder(Component.literal("0.0"), b -> {
            studioX = 0; studioY = 0; studioZ = 0; hasUnsavedChanges = true; syncPosSliders();
        }).bounds(20, leftStart, 30, 16).build());
        addRenderableWidget(Button.builder(Component.literal("Snap 0.5"), b -> {
            studioX = Math.round(studioX * 2) / 2.0f; studioY = Math.round(studioY * 2) / 2.0f; studioZ = Math.round(studioZ * 2) / 2.0f;
            hasUnsavedChanges = true; syncPosSliders();
        }).bounds(55, leftStart, 60, 16).build());

        this.sliderX = addRenderableWidget(new ForgeSlider(20, leftStart + 18, 140, sliderHeight, Component.literal("Pos X: "), Component.empty(), -10.0D, 10.0D, studioX, 0.01D, 2, true) {
            @Override protected void applyValue() { studioX = (float)this.getValue(); hasUnsavedChanges = true; }
        });
        this.sliderY = addRenderableWidget(new ForgeSlider(20, leftStart + 36, 140, sliderHeight, Component.literal("Pos Y: "), Component.empty(), -10.0D, 10.0D, studioY, 0.01D, 2, true) {
            @Override protected void applyValue() { studioY = (float)this.getValue(); hasUnsavedChanges = true; }
        });
        this.sliderZ = addRenderableWidget(new ForgeSlider(20, leftStart + 54, 140, sliderHeight, Component.literal("Pos Z: "), Component.empty(), -10.0D, 10.0D, studioZ, 0.01D, 2, true) {
            @Override protected void applyValue() { studioZ = (float)this.getValue(); hasUnsavedChanges = true; }
        });

        // --- RECHTE SEITE OBEN (Skalierungen) ---
        addRenderableWidget(Button.builder(Component.literal("1.0"), b -> {
            studioScaleX = 1; studioScaleY = 1; studioScaleZ = 1; hasUnsavedChanges = true; syncScaleSliders();
        }).bounds(rightX, rightStartTop, 30, 16).build());
        addRenderableWidget(Button.builder(Component.literal("Snap"), b -> {
            studioScaleX = Math.round(studioScaleX * 2) / 2.0f; studioScaleY = Math.round(studioScaleY * 2) / 2.0f; studioScaleZ = Math.round(studioScaleZ * 2) / 2.0f;
            hasUnsavedChanges = true; syncScaleSliders();
        }).bounds(rightX + 35, rightStartTop, 40, 16).build());
        this.btnLinkScale = addRenderableWidget(Button.builder(Component.literal(linkScale ? "Link: ON" : "Link: OFF"), b -> {
            linkScale = !linkScale; b.setMessage(Component.literal(linkScale ? "Link: ON" : "Link: OFF"));
            if(linkScale) { studioScaleY = studioScaleX; studioScaleZ = studioScaleX; syncScaleSliders(); }
        }).bounds(rightX + 80, rightStartTop, 60, 16).build());

        this.sliderScaleX = addRenderableWidget(new ForgeSlider(rightX, rightStartTop + 18, 140, sliderHeight, Component.literal("Scale X: "), Component.empty(), 0.0D, 5.0D, studioScaleX, 0.05D, 2, true) {
            @Override protected void applyValue() {
                if (isUpdatingScale) return;
                studioScaleX = (float)this.getValue(); hasUnsavedChanges = true;
                if(linkScale) { studioScaleY = studioScaleX; studioScaleZ = studioScaleX; syncScaleSliders(); }
            }
        });
        this.sliderScaleY = addRenderableWidget(new ForgeSlider(rightX, rightStartTop + 36, 140, sliderHeight, Component.literal("Scale Y: "), Component.empty(), 0.0D, 5.0D, studioScaleY, 0.05D, 2, true) {
            @Override protected void applyValue() {
                if (isUpdatingScale) return;
                studioScaleY = (float)this.getValue(); hasUnsavedChanges = true;
                if(linkScale) { studioScaleX = studioScaleY; studioScaleZ = studioScaleY; syncScaleSliders(); }
            }
        });
        this.sliderScaleZ = addRenderableWidget(new ForgeSlider(rightX, rightStartTop + 54, 140, sliderHeight, Component.literal("Scale Z: "), Component.empty(), 0.0D, 5.0D, studioScaleZ, 0.05D, 2, true) {
            @Override protected void applyValue() {
                if (isUpdatingScale) return;
                studioScaleZ = (float)this.getValue(); hasUnsavedChanges = true;
                if(linkScale) { studioScaleX = studioScaleZ; studioScaleY = studioScaleZ; syncScaleSliders(); }
            }
        });

        // --- RECHTE SEITE UNTEN (Rotationen) ---
        addRenderableWidget(Button.builder(Component.literal("0°"), b -> {
            studioRotX = 0; studioRotY = 0; studioRotZ = 0; hasUnsavedChanges = true; syncRotSliders();
        }).bounds(rightX, rightStartBottom, 30, 16).build());
        addRenderableWidget(Button.builder(Component.literal("Snap 90°"), b -> {
            studioRotX = Math.round(studioRotX / 90.0f) * 90.0f; studioRotY = Math.round(studioRotY / 90.0f) * 90.0f; studioRotZ = Math.round(studioRotZ / 90.0f) * 90.0f;
            hasUnsavedChanges = true; syncRotSliders();
        }).bounds(rightX + 35, rightStartBottom, 60, 16).build());

        this.sliderRotX = addRenderableWidget(new ForgeSlider(rightX, rightStartBottom + 18, 140, sliderHeight, Component.literal("Rot X: "), Component.empty(), -180.0D, 180.0D, studioRotX, 1.0D, 0, true) {
            @Override protected void applyValue() { studioRotX = (float)this.getValue(); hasUnsavedChanges = true; }
        });
        this.sliderRotY = addRenderableWidget(new ForgeSlider(rightX, rightStartBottom + 36, 140, sliderHeight, Component.literal("Rot Y: "), Component.empty(), -180.0D, 180.0D, studioRotY, 1.0D, 0, true) {
            @Override protected void applyValue() { studioRotY = (float)this.getValue(); hasUnsavedChanges = true; }
        });
        this.sliderRotZ = addRenderableWidget(new ForgeSlider(rightX, rightStartBottom + 54, 140, sliderHeight, Component.literal("Rot Z: "), Component.empty(), -180.0D, 180.0D, studioRotZ, 1.0D, 0, true) {
            @Override protected void applyValue() { studioRotZ = (float)this.getValue(); hasUnsavedChanges = true; }
        });

        // Speicher-Button ganz unten zentriert
        this.btnSave = addRenderableWidget(Button.builder(Component.literal("💾 In Resourcepack sichern"), b -> saveConfigLocal())
            .bounds(this.width / 2 - 100, this.height - 25, 200, 20)
            .build());
            
        loadPreviewEntity();
        
        this.rotationYaw = 180.0f;
        this.rotationPitch = 0.0f;
        this.zoomFactor = 1.0f;
    }

    private void syncPosSliders() {
        if (sliderX != null) sliderX.setValue(studioX);
        if (sliderY != null) sliderY.setValue(studioY);
        if (sliderZ != null) sliderZ.setValue(studioZ);
    }
    
    private void syncRotSliders() {
        if (sliderRotX != null) sliderRotX.setValue(studioRotX);
        if (sliderRotY != null) sliderRotY.setValue(studioRotY);
        if (sliderRotZ != null) sliderRotZ.setValue(studioRotZ);
    }
    
    private void syncScaleSliders() {
        isUpdatingScale = true;
        if (sliderScaleX != null) sliderScaleX.setValue(studioScaleX);
        if (sliderScaleY != null) sliderScaleY.setValue(studioScaleY);
        if (sliderScaleZ != null) sliderScaleZ.setValue(studioScaleZ);
        isUpdatingScale = false;
    }

    private static java.lang.reflect.Field resolveChildrenField() {
        if (childrenFieldResolved) return cachedChildrenField;
        childrenFieldResolved = true;

        for (java.lang.reflect.Field field : ModelPart.class.getDeclaredFields()) {
            if (Map.class.isAssignableFrom(field.getType())) {
                field.setAccessible(true);
                cachedChildrenField = field;
                break;
            }
        }

        if (cachedChildrenField == null) {
            net.kb150.everyonehashats.EveryoneHasHats.LOGGER.error(
                "[HatStudio] Konnte das Children-Feld von ModelPart nicht finden! Bone-Scan wird fehlschlagen.");
        }

        return cachedChildrenField;
    }

    @SuppressWarnings("unchecked")
    private Map<String, ModelPart> getChildrenOf(ModelPart part) {
        java.lang.reflect.Field field = resolveChildrenField();
        if (field == null) return Collections.emptyMap();
        try {
            return (Map<String, ModelPart>) field.get(part);
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private Map<String, ModelPart> getSafeBones(EntityModel<?> model) {
        Map<String, ModelPart> result = new HashMap<>();
        if (model == null) return result;

        Map<String, ModelPart> fieldParts = new HashMap<>();
        Class<?> clazz = model.getClass();

        while (clazz != null && clazz != Object.class) {
            for (java.lang.reflect.Field field : clazz.getDeclaredFields()) {
                if (ModelPart.class.isAssignableFrom(field.getType())) {
                    try {
                        field.setAccessible(true);
                        ModelPart part = (ModelPart) field.get(model);
                        if (part != null) {
                            fieldParts.put(field.getName(), part);
                        }
                    } catch (Exception ignored) {}
                }
            }
            clazz = clazz.getSuperclass();
        }

        Set<ModelPart> allChildren = Collections.newSetFromMap(new IdentityHashMap<>());
        for (ModelPart part : fieldParts.values()) {
            collectAllChildren(part, allChildren);
        }

        Map<ModelPart, String> uniqueParts = new IdentityHashMap<>();
        for (Map.Entry<String, ModelPart> entry : fieldParts.entrySet()) {
            ModelPart part = entry.getValue();
            if (!allChildren.contains(part)) {
                String name = entry.getKey();
                uniqueParts.put(part, name);
                result.put(name, part);
                scanSafeChildren(name, part, result, uniqueParts, 0);
            }
        }
        return result;
    }

    private void collectAllChildren(ModelPart parent, Set<ModelPart> allChildren) {
        for (ModelPart child : getChildrenOf(parent).values()) {
            if (child != null && allChildren.add(child)) {
                collectAllChildren(child, allChildren);
            }
        }
    }

    private void scanSafeChildren(String prefix, ModelPart parent, Map<String, ModelPart> result, Map<ModelPart, String> uniqueParts, int depth) {
        if (depth > 12) return; 
        for (Map.Entry<String, ModelPart> entry : getChildrenOf(parent).entrySet()) {
            ModelPart child = entry.getValue();
            if (child != null && !uniqueParts.containsKey(child)) {
                String childPath = prefix + "/" + entry.getKey();
                uniqueParts.put(child, childPath);
                result.put(childPath, child);
                scanSafeChildren(childPath, child, result, uniqueParts, depth + 1);
            }
        }
    }

    private void loadPreviewEntity() {
        if (Minecraft.getInstance().level != null && !matchedEntities.isEmpty()) {
            EntityType<?> type = matchedEntities.get(activeEntityIndex);
            this.previewEntity = (LivingEntity) type.create(Minecraft.getInstance().level);

            availableBones.clear();
            availableBones.add(""); 

            EntityRenderer<?> renderer = Minecraft.getInstance().getEntityRenderDispatcher().renderers.get(type);
            EntityModel<?> model = null;
            this.entityHasNativeRendering = false;

            boolean isGeckoLoaded = net.minecraftforge.fml.ModList.get().isLoaded("geckolib");
            boolean isGeckoEntity = false;

            if (isGeckoLoaded) {
                try {
                    isGeckoEntity = checkGecko(renderer);
                } catch (Exception ignored) {}
            }

            if (isGeckoEntity) {
                availableBones.addAll(net.kb150.everyonehashats.compat.geckolib.GeckoCompat.getGeckoBones(renderer, this.previewEntity).keySet());
            } else if (renderer instanceof LivingEntityRenderer<?, ?> livingRenderer) {
                model = livingRenderer.getModel();
                
                for (java.lang.reflect.Field field : LivingEntityRenderer.class.getDeclaredFields()) {
                    if (List.class.isAssignableFrom(field.getType())) {
                        try {
                            field.setAccessible(true);
                            List<?> layersList = (List<?>) field.get(livingRenderer);
                            for (Object layer : layersList) {
                                String className = layer.getClass().getSimpleName();
                                if (className.contains("CustomHeadLayer") || className.contains("HumanoidArmorLayer")) {
                                    this.entityHasNativeRendering = true;
                                    break;
                                }
                            }
                            if (this.entityHasNativeRendering) break;
                        } catch (Exception ignored) {}
                    }
                }

                Map<String, ModelPart> scanned = getSafeBones(model);
                availableBones.addAll(scanned.keySet());
            }

            availableBones.sort(String::compareToIgnoreCase);

            ResourceLocation rl = ForgeRegistries.ENTITY_TYPES.getKey(type);
            HatOffsetLoader.HatOffset loaded = HatOffsetLoader.getOffset(rl);

            studioX = loaded.x;
            studioY = loaded.y;
            studioZ = loaded.z;
            studioScaleX = loaded.scaleX;
            studioScaleY = loaded.scaleY;
            studioScaleZ = loaded.scaleZ;
            studioRotX = loaded.rotX;
            studioRotY = loaded.rotY;
            studioRotZ = loaded.rotZ;
            studioSelectedBone = loaded.bone != null ? loaded.bone : "";

            if (!availableBones.contains(studioSelectedBone)) {
                studioSelectedBone = ""; 
            }

            if (studioSelectedBone.isEmpty()) {
                for (String b : availableBones) {
                    String lower = b.toLowerCase();
                    if (lower.equals("head") || lower.equals("bipedhead") || lower.endsWith("/head") || lower.endsWith("/bipedhead") || lower.endsWith("/armor_head")) {
                        studioSelectedBone = b;
                        break;
                    }
                }
            }
            
            activeBoneIndex = availableBones.indexOf(studioSelectedBone);
            if (activeBoneIndex == -1) activeBoneIndex = 0;

            if (this.boneSearchBox != null) {
                this.boneSearchBox.setValue(studioSelectedBone.isEmpty() ? "Standard (Kopf)" : studioSelectedBone);
            }

            if (previewEntity != null && !matchedHats.isEmpty()) {
                previewEntity.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, matchedHats.get(activeHatIndex).copy());
            }

            // UI Werte aktualisieren
            syncPosSliders();
            syncScaleSliders();
            syncRotSliders();

            boolean allowEdits = !this.entityHasNativeRendering;
            if (this.sliderX != null) this.sliderX.active = allowEdits;
            if (this.sliderY != null) this.sliderY.active = allowEdits;
            if (this.sliderZ != null) this.sliderZ.active = allowEdits;
            if (this.sliderScaleX != null) this.sliderScaleX.active = allowEdits;
            if (this.sliderScaleY != null) this.sliderScaleY.active = allowEdits;
            if (this.sliderScaleZ != null) this.sliderScaleZ.active = allowEdits;
            if (this.sliderRotX != null) this.sliderRotX.active = allowEdits;
            if (this.sliderRotY != null) this.sliderRotY.active = allowEdits;
            if (this.sliderRotZ != null) this.sliderRotZ.active = allowEdits;
            
            if (this.btnSave != null) this.btnSave.active = allowEdits;
            if (this.boneSearchBox != null) this.boneSearchBox.setEditable(allowEdits);
            if (this.btnPrevBone != null) this.btnPrevBone.active = allowEdits;
            if (this.btnNextBone != null) this.btnNextBone.active = allowEdits;
            if (this.btnToggleBoneDropdown != null) this.btnToggleBoneDropdown.active = allowEdits;

            hasUnsavedChanges = false;
        }
    }

    private boolean checkGecko(EntityRenderer<?> renderer) {
        return net.kb150.everyonehashats.compat.geckolib.GeckoCompat.isGeckoRenderer(renderer);
    }

    private void saveConfigLocal() {
        if (previewEntity == null || entityHasNativeRendering) return;
        try {
            ResourceLocation rl = ForgeRegistries.ENTITY_TYPES.getKey(previewEntity.getType());
            File mcDir = Minecraft.getInstance().gameDirectory;
            File packDir = new File(mcDir, "resourcepacks/EveryoneHasHats_Studio");
            File configDir = new File(packDir, "assets/everyonehashats/offsets/" + rl.getNamespace());
            
            if (!configDir.exists()) configDir.mkdirs();

            File metaFile = new File(packDir, "pack.mcmeta");
            if (!metaFile.exists()) {
                String mcmeta = "{\n  \"pack\": {\n    \"pack_format\": 15,\n    \"description\": \"Offsets exported live from Hat Studio!\"\n  }\n}";
                Files.writeString(metaFile.toPath(), mcmeta);
            }

            String jsonName = rl.getPath() + ".json";
            File jsonFile = new File(configDir, jsonName);
            
            String jsonContent = String.format(Locale.US,
                "{\n" +
                "  \"bone\": \"%s\",\n" +
                "  \"x\": %.4f,\n" +
                "  \"y\": %.4f,\n" +
                "  \"z\": %.4f,\n" +
                "  \"scale_x\": %.4f,\n" +
                "  \"scale_y\": %.4f,\n" +
                "  \"scale_z\": %.4f,\n" +
                "  \"rotation_x\": %.2f,\n" +
                "  \"rotation_y\": %.2f,\n" +
                "  \"rotation_z\": %.2f\n" +
                "}",
                studioSelectedBone, studioX, studioY, studioZ, studioScaleX, studioScaleY, studioScaleZ, studioRotX, studioRotY, studioRotZ
            );
            Files.writeString(jsonFile.toPath(), jsonContent);

            HatOffsetLoader.updateOffsetAtRuntime(rl, new HatOffsetLoader.HatOffset(
                studioSelectedBone, studioX, studioY, studioZ, studioScaleX, studioScaleY, studioScaleZ, studioRotX, studioRotY, studioRotZ
            ));

            activateStudioPack("EveryoneHasHats_Studio");
            hasUnsavedChanges = false;

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void activateStudioPack(String folderName) {
        Minecraft mc = Minecraft.getInstance();
        try {
            mc.getResourcePackRepository().reload();
            List<String> selected = new ArrayList<>(mc.options.resourcePacks);
            String packId = "file/" + folderName;
            if (!selected.contains(packId)) {
                selected.add(packId);
                mc.options.resourcePacks = selected;
                mc.options.save();
                mc.getResourcePackRepository().setSelected(selected);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void selectPrevEntity() {
        if (activeEntityIndex > 0) activeEntityIndex--;
        else activeEntityIndex = matchedEntities.size() - 1;
        entitySearchBox.setValue(getCleanName(matchedEntities.get(activeEntityIndex)));
        loadPreviewEntity();
    }

    private void selectNextEntity() {
        if (activeEntityIndex < matchedEntities.size() - 1) activeEntityIndex++;
        else activeEntityIndex = 0;
        entitySearchBox.setValue(getCleanName(matchedEntities.get(activeEntityIndex)));
        loadPreviewEntity();
    }

    private void onEntitySearchChanged(String text) {
        List<EntityType<?>> filter = matchedEntities.stream()
            .filter(e -> getCleanName(e).toLowerCase().contains(text.toLowerCase()))
            .collect(Collectors.toList());
        if (!filter.isEmpty() && matchedEntities.contains(filter.get(0))) {
            activeEntityIndex = matchedEntities.indexOf(filter.get(0));
            loadPreviewEntity();
        }
    }

    private void selectPrevHat() {
        if (activeHatIndex > 0) activeHatIndex--;
        else activeHatIndex = matchedHats.size() - 1;
        hatSearchBox.setValue(matchedHats.get(activeHatIndex).isEmpty() ? "Kein Hut" : matchedHats.get(activeHatIndex).getHoverName().getString());
        if (previewEntity != null) {
            previewEntity.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, matchedHats.get(activeHatIndex).copy());
        }
    }

    private void selectNextHat() {
        if (activeHatIndex < matchedHats.size() - 1) activeHatIndex++;
        else activeHatIndex = 0;
        hatSearchBox.setValue(matchedHats.get(activeHatIndex).isEmpty() ? "Kein Hut" : matchedHats.get(activeHatIndex).getHoverName().getString());
        if (previewEntity != null) {
            previewEntity.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, matchedHats.get(activeHatIndex).copy());
        }
    }

    private void onHatSearchChanged(String text) {
        List<ItemStack> filter = matchedHats.stream()
            .filter(h -> h.getHoverName().getString().toLowerCase().contains(text.toLowerCase()))
            .collect(Collectors.toList());
        if (!filter.isEmpty() && matchedHats.contains(filter.get(0))) {
            activeHatIndex = matchedHats.indexOf(filter.get(0));
            if (previewEntity != null) {
                previewEntity.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, matchedHats.get(activeHatIndex).copy());
            }
        }
    }

    private void selectPrevBone() {
        if (availableBones.isEmpty() || entityHasNativeRendering) return;
        if (activeBoneIndex > 0) activeBoneIndex--;
        else activeBoneIndex = availableBones.size() - 1;
        studioSelectedBone = availableBones.get(activeBoneIndex);
        boneSearchBox.setValue(studioSelectedBone.isEmpty() ? "Standard (Kopf)" : studioSelectedBone);
        hasUnsavedChanges = true;
    }

    private void selectNextBone() {
        if (availableBones.isEmpty() || entityHasNativeRendering) return;
        if (activeBoneIndex < availableBones.size() - 1) activeBoneIndex++;
        else activeBoneIndex = 0;
        studioSelectedBone = availableBones.get(activeBoneIndex);
        boneSearchBox.setValue(studioSelectedBone.isEmpty() ? "Standard (Kopf)" : studioSelectedBone);
        hasUnsavedChanges = true;
    }

    private void onBoneSearchChanged(String text) {
        if (entityHasNativeRendering) return;
        if (text.equalsIgnoreCase("standard (kopf)") || text.trim().isEmpty()) {
            studioSelectedBone = "";
            activeBoneIndex = 0;
            hasUnsavedChanges = true;
            return;
        }
        List<String> filter = availableBones.stream()
            .filter(b -> b.toLowerCase().contains(text.toLowerCase()))
            .collect(Collectors.toList());
        if (!filter.isEmpty() && availableBones.contains(filter.get(0))) {
            activeBoneIndex = availableBones.indexOf(filter.get(0));
            studioSelectedBone = availableBones.get(activeBoneIndex);
            hasUnsavedChanges = true;
        }
    }

    private String getCleanName(EntityType<?> type) {
        return ForgeRegistries.ENTITY_TYPES.getKey(type).getPath();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        graphics.fill(0, 0, this.width, 34, 0xF20F0F12);
        graphics.fill(0, 33, this.width, 34, 0x44FFFFFF);
        graphics.drawString(this.font, "Everyone Has Hats - Dynamic Studio", 15, 13, 0xFFE4E4E7);

        this.entitySearchBox.render(graphics, mouseX, mouseY, partialTick);
        this.hatSearchBox.render(graphics, mouseX, mouseY, partialTick);
        this.boneSearchBox.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawString(font, "Entity:", 20, 35, 0xFFAAAAAA);
        graphics.drawString(font, "Hut:", 20, 58, 0xFFAAAAAA);
        graphics.drawString(font, "Ziel-Knochen (Bone):", 20, 81, 0xFFAAAAAA);

        if (previewEntity != null) {
            int entityX = this.width / 2;
            int entityY = this.height / 2 + 50;
            int scale = (int) (65 * zoomFactor);

            previewEntity.tickCount++;
            renderStudioEntity(graphics, entityX, entityY, scale, this.rotationYaw, this.rotationPitch, this.previewEntity);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
        
        if (this.entityHasNativeRendering) {
            graphics.drawCenteredString(font, "⚠ Diese Entity nutzt Vanilla-Helme! Editor für dieses Modell deaktiviert.", this.width / 2, this.height - 45, 0xFFFF5555);
        }

        // QOL Fix: Z-Layering der Dropdown Listen erzwingen!
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 500); // Setzt das Menü vor alles andere
        renderDropdownList(graphics, mouseX, mouseY);
        graphics.pose().popPose();
    }

    private void renderStudioEntity(GuiGraphics graphics, int x, int y, int scale, float yaw, float pitch, LivingEntity entity) {
        PoseStack posestack = graphics.pose();
        posestack.pushPose();
        posestack.translate((double)x, (double)y, 50.0D);
        posestack.scale((float)scale, (float)scale, (float)(-scale));
        posestack.mulPose(Axis.ZP.rotationDegrees(180.0F));
        posestack.mulPose(Axis.XP.rotationDegrees(pitch));

        Lighting.setupForEntityInInventory();
        EntityRenderDispatcher entityrenderdispatcher = Minecraft.getInstance().getEntityRenderDispatcher();

        float oldBodyRot = entity.yBodyRot;
        float oldYRot = entity.yRotO;
        float oldHeadRot = entity.yHeadRot;
        float oldHeadRotO = entity.yHeadRotO;

        entity.yBodyRot = yaw;
        entity.yRotO = yaw;
        entity.yHeadRot = yaw;
        entity.yHeadRotO = yaw;

        entityrenderdispatcher.setRenderShadow(false);
        RenderSystem.runAsFancy(() -> {
            entityrenderdispatcher.render(entity, 0.0D, 0.0D, 0.0D, 0.0F, 1.0F, posestack, graphics.bufferSource(), 15728880);
        });
        graphics.flush();
        entityrenderdispatcher.setRenderShadow(true);

        entity.yBodyRot = oldBodyRot;
        entity.yRotO = oldYRot;
        entity.yHeadRot = oldHeadRot;
        entity.yHeadRotO = oldHeadRotO;

        posestack.popPose();
        Lighting.setupFor3DItems();
    }

    private void renderDropdownList(GuiGraphics graphics, int mouseX, int mouseY) {
        int itemH = 12;
        int maxVisible = 10;

        if (entityDropdownOpen) {
            int dropX = 20, dropY = 62, dropW = 120;
            int size = Math.min(matchedEntities.size(), maxVisible);
            graphics.fill(dropX, dropY, dropX + dropW, dropY + (size * itemH), 0xF20B0B0C);
            graphics.renderOutline(dropX, dropY, dropW, size * itemH, 0x44FFFFFF);

            for (int i = 0; i < size; i++) {
                int idx = (i + dropdownScrollOffset) % matchedEntities.size();
                int y = dropY + (i * itemH);
                boolean hover = mouseX >= dropX && mouseX < dropX + dropW && mouseY >= y && mouseY < y + itemH;
                if (hover) graphics.fill(dropX + 1, y, dropX + dropW - 1, y + itemH, 0x22FFFFFF);
                graphics.drawString(font, getCleanName(matchedEntities.get(idx)), dropX + 4, y + 2, hover ? 0xFFFFFFFF : 0xFF999999);
            }
        }

        if (hatDropdownOpen) {
            int dropX = 20, dropY = 85, dropW = 120;
            int size = Math.min(matchedHats.size(), maxVisible);
            graphics.fill(dropX, dropY, dropX + dropW, dropY + (size * itemH), 0xF20B0B0C);
            graphics.renderOutline(dropX, dropY, dropW, size * itemH, 0x44FFFFFF);

            for (int i = 0; i < size; i++) {
                int idx = (i + dropdownScrollOffset) % matchedHats.size();
                int y = dropY + (i * itemH);
                boolean hover = mouseX >= dropX && mouseX < dropX + dropW && mouseY >= y && mouseY < y + itemH;
                if (hover) graphics.fill(dropX + 1, y, dropX + dropW - 1, y + itemH, 0x22FFFFFF);
                graphics.drawString(font, matchedHats.get(idx).isEmpty() ? "Kein Hut" : matchedHats.get(idx).getHoverName().getString(), dropX + 4, y + 2, hover ? 0xFFFFFFFF : 0xFF999999);
            }
        }

        if (boneDropdownOpen) {
            int dropX = 20, dropY = 108, dropW = 120;
            int size = Math.min(availableBones.size(), maxVisible);
            graphics.fill(dropX, dropY, dropX + dropW, dropY + (size * itemH), 0xF20B0B0C);
            graphics.renderOutline(dropX, dropY, dropW, size * itemH, 0x44FFFFFF);

            for (int i = 0; i < size; i++) {
                int idx = (i + dropdownScrollOffset) % availableBones.size();
                int y = dropY + (i * itemH);
                boolean hover = mouseX >= dropX && mouseX < dropX + dropW && mouseY >= y && mouseY < y + itemH;
                if (hover) graphics.fill(dropX + 1, y, dropX + dropW - 1, y + itemH, 0x22FFFFFF);
                String displayBone = availableBones.get(idx).isEmpty() ? "Standard (Kopf)" : availableBones.get(idx);
                graphics.drawString(font, displayBone, dropX + 4, y + 2, hover ? 0xFFFFFFFF : 0xFF999999);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int itemH = 12;

        if (entityDropdownOpen) {
            int dropX = 20, dropY = 62, dropW = 120;
            int size = Math.min(matchedEntities.size(), 10);
            if (mouseX >= dropX && mouseX < dropX + dropW && mouseY >= dropY && mouseY < dropY + (size * itemH)) {
                int clicked = (int)((mouseY - dropY) / itemH);
                activeEntityIndex = (clicked + dropdownScrollOffset) % matchedEntities.size();
                entitySearchBox.setValue(getCleanName(matchedEntities.get(activeEntityIndex)));
                loadPreviewEntity();
                entityDropdownOpen = false;
                return true;
            }
        }

        if (hatDropdownOpen) {
            int dropX = 20, dropY = 85, dropW = 120;
            int size = Math.min(matchedHats.size(), 10);
            if (mouseX >= dropX && mouseX < dropX + dropW && mouseY >= dropY && mouseY < dropY + (size * itemH)) {
                int clicked = (int)((mouseY - dropY) / itemH);
                activeHatIndex = (clicked + dropdownScrollOffset) % matchedHats.size();
                hatSearchBox.setValue(matchedHats.get(activeHatIndex).isEmpty() ? "Kein Hut" : matchedHats.get(activeHatIndex).getHoverName().getString());
                if (previewEntity != null) {
                    previewEntity.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, matchedHats.get(activeHatIndex).copy());
                }
                hatDropdownOpen = false;
                return true;
            }
        }

        if (boneDropdownOpen && !entityHasNativeRendering) {
            int dropX = 20, dropY = 108, dropW = 120;
            int size = Math.min(availableBones.size(), 10);
            if (mouseX >= dropX && mouseX < dropX + dropW && mouseY >= dropY && mouseY < dropY + (size * itemH)) {
                int clicked = (int)((mouseY - dropY) / itemH);
                activeBoneIndex = (clicked + dropdownScrollOffset) % availableBones.size();
                studioSelectedBone = availableBones.get(activeBoneIndex);
                boneSearchBox.setValue(studioSelectedBone.isEmpty() ? "Standard (Kopf)" : studioSelectedBone);
                hasUnsavedChanges = true;
                boneDropdownOpen = false;
                return true;
            }
        }

        entityDropdownOpen = false;
        hatDropdownOpen = false;
        boneDropdownOpen = false;

        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        // Hitbox für das Dragging verkleinert, damit Slider nicht aus Versehen angeklickt werden
        if (mouseX > 170 && mouseX < this.width - 170 && mouseY > 34) {
            this.isDraggingEntity = true;
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.isDraggingEntity) {
            this.rotationYaw += (float) dragX * 1.5f;
            this.rotationPitch -= (float) dragY * 1.5f;
            this.rotationPitch = Math.max(-90.0f, Math.min(90.0f, this.rotationPitch));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.isDraggingEntity = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (entityDropdownOpen || hatDropdownOpen || boneDropdownOpen) {
            dropdownScrollOffset = Math.max(0, dropdownScrollOffset - (int)delta);
            return true;
        }
        
        if (mouseX > 140 && mouseX < this.width - 140) {
            this.zoomFactor += (float) delta * 0.2f;
            this.zoomFactor = Math.max(0.2f, Math.min(10.0f, this.zoomFactor));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        isStudioActive = false;
        super.onClose();
        Minecraft.getInstance().reloadResourcePacks();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}