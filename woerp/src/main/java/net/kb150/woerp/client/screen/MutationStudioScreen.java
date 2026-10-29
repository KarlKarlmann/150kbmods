package net.kb150.woerp.client.screen;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.kb150.woerp.Woerp;
import net.kb150.woerp.client.offset.MutationOffsetLoader;
import net.kb150.woerp.client.util.ModelBoneScanner;
import net.kb150.woerp.init.ModEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.client.gui.widget.ForgeSlider;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.io.File;
import java.nio.file.Files;
import java.util.*;

public class MutationStudioScreen extends Screen {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public static boolean isStudioActive = false;
    public static String studioActiveMutation = "mutation_horn";
    public static String studioSelectedBone = "head";
    public static float studioX, studioY, studioZ;
    public static float studioScaleX = 1.0f, studioScaleY = 1.0f, studioScaleZ = 1.0f;
    public static float studioRotX, studioRotY, studioRotZ;

    private static final List<String> MUTATIONS = List.of(
        "mutation_horn", "mutation_eyestalk", "mutation_visage", "mutation_second_face", "mutation_pustules",
        "mutation_crest", "mutation_spur", "mutation_carapace", "mutation_clubfoot", "mutation_maw"
    );

    private final List<EntityType<?>> matchedEntities = new ArrayList<>();
    private final List<String> availableBones = new ArrayList<>();
    private int activeEntityIndex = 0;
    private int activeMutationIndex = 0;
    private int activeBoneIndex = 0;

    private LivingEntity previewEntity;
    private EditBox entitySearchBox, mutationSearchBox, boneSearchBox;
    private boolean entityDropdownOpen, mutationDropdownOpen, boneDropdownOpen;
    private int dropdownScrollOffset;

    private ForgeSlider sliderX, sliderY, sliderZ;
    private ForgeSlider sliderScaleX, sliderScaleY, sliderScaleZ;
    private ForgeSlider sliderRotX, sliderRotY, sliderRotZ;
    
    // Verhindert StackOverflowError: EditBox.setValue() triggert Mojang-intern synchron den Responder-Listener
    private boolean isUpdatingUI = false;
    private boolean linkScale = true, isUpdatingScale = false;

    private float rotationYaw = 180.0f, rotationPitch = 0.0f, zoomFactor = 1.0f;
    private boolean isDraggingEntity = false;

    public MutationStudioScreen() {
        super(Component.literal("Woerp Mutation Studio"));
        isStudioActive = true;
    }

    @Override
    protected void init() {
        super.init();

        matchedEntities.clear();
        for (EntityType<?> t : ForgeRegistries.ENTITY_TYPES) {
            try {
                // Throwable faengt auch ClassNotFound/LinkageErrors unvollstaendiger Mobs ab
                if (t.create(Minecraft.getInstance().level) instanceof LivingEntity) matchedEntities.add(t);
            } catch (Throwable ignored) {}
        }
        matchedEntities.sort(Comparator.comparing(e -> ForgeRegistries.ENTITY_TYPES.getKey(e).getPath()));

        int leftX = 20;

        entitySearchBox = new EditBox(font, leftX, 45, 120, 16, Component.empty());
        if (!matchedEntities.isEmpty()) {
            entitySearchBox.setValue(ForgeRegistries.ENTITY_TYPES.getKey(matchedEntities.get(activeEntityIndex)).getPath());
        }
        entitySearchBox.setResponder(this::onEntitySearchChanged);
        addRenderableWidget(Button.builder(Component.literal("<"), b -> selectEntity(-1)).bounds(145, 45, 16, 16).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> selectEntity(1)).bounds(163, 45, 16, 16).build());
        addRenderableWidget(Button.builder(Component.literal("v"), b -> toggleDropdown(1)).bounds(181, 45, 16, 16).build());

        mutationSearchBox = new EditBox(font, leftX, 68, 120, 16, Component.empty());
        mutationSearchBox.setValue(MUTATIONS.get(activeMutationIndex));
        mutationSearchBox.setResponder(this::onMutationSearchChanged);
        addRenderableWidget(Button.builder(Component.literal("<"), b -> selectMutation(-1)).bounds(145, 68, 16, 16).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> selectMutation(1)).bounds(163, 68, 16, 16).build());
        addRenderableWidget(Button.builder(Component.literal("v"), b -> toggleDropdown(2)).bounds(181, 68, 16, 16).build());

        boneSearchBox = new EditBox(font, leftX, 91, 120, 16, Component.empty());
        boneSearchBox.setValue(studioSelectedBone);
        boneSearchBox.setResponder(this::onBoneSearchChanged);
        addRenderableWidget(Button.builder(Component.literal("<"), b -> selectBone(-1)).bounds(145, 91, 16, 16).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> selectBone(1)).bounds(163, 91, 16, 16).build());
        addRenderableWidget(Button.builder(Component.literal("v"), b -> toggleDropdown(3)).bounds(181, 91, 16, 16).build());

        int leftStart = 115, rightStartTop = 45, rightStartBottom = 115, rightX = this.width - 160;

        addRenderableWidget(Button.builder(Component.literal("0.0"), b -> { studioX = studioY = studioZ = 0; syncSliders(); }).bounds(20, leftStart, 30, 16).build());
        addRenderableWidget(Button.builder(Component.literal("Snap 0.5"), b -> {
            studioX = Math.round(studioX * 2) / 2.0f; studioY = Math.round(studioY * 2) / 2.0f; studioZ = Math.round(studioZ * 2) / 2.0f; syncSliders();
        }).bounds(55, leftStart, 60, 16).build());

        sliderX = addRenderableWidget(new ForgeSlider(20, leftStart + 18, 140, 16, Component.literal("Pos X: "), Component.empty(), -5.0D, 5.0D, studioX, 0.01D, 2, true) {
            @Override protected void applyValue() { studioX = (float) getValue(); }
        });
        sliderY = addRenderableWidget(new ForgeSlider(20, leftStart + 36, 140, 16, Component.literal("Pos Y: "), Component.empty(), -5.0D, 5.0D, studioY, 0.01D, 2, true) {
            @Override protected void applyValue() { studioY = (float) getValue(); }
        });
        sliderZ = addRenderableWidget(new ForgeSlider(20, leftStart + 54, 140, 16, Component.literal("Pos Z: "), Component.empty(), -5.0D, 5.0D, studioZ, 0.01D, 2, true) {
            @Override protected void applyValue() { studioZ = (float) getValue(); }
        });

        addRenderableWidget(Button.builder(Component.literal("1.0"), b -> { studioScaleX = studioScaleY = studioScaleZ = 1; syncSliders(); }).bounds(rightX, rightStartTop, 30, 16).build());
        addRenderableWidget(Button.builder(Component.literal("Link"), b -> {
            linkScale = !linkScale; b.setMessage(Component.literal(linkScale ? "Link: ON" : "Link: OFF"));
            if (linkScale) { studioScaleY = studioScaleZ = studioScaleX; syncSliders(); }
        }).bounds(rightX + 35, rightStartTop, 60, 16).build());

        sliderScaleX = addRenderableWidget(new ForgeSlider(rightX, rightStartTop + 18, 140, 16, Component.literal("Scl X: "), Component.empty(), 0.0D, 5.0D, studioScaleX, 0.05D, 2, true) {
            @Override protected void applyValue() {
                if (isUpdatingScale) return;
                studioScaleX = (float) getValue();
                if (linkScale) { studioScaleY = studioScaleZ = studioScaleX; syncSliders(); }
            }
        });
        sliderScaleY = addRenderableWidget(new ForgeSlider(rightX, rightStartTop + 36, 140, 16, Component.literal("Scl Y: "), Component.empty(), 0.0D, 5.0D, studioScaleY, 0.05D, 2, true) {
            @Override protected void applyValue() {
                if (isUpdatingScale) return;
                studioScaleY = (float) getValue();
                if (linkScale) { studioScaleX = studioScaleZ = studioScaleY; syncSliders(); }
            }
        });
        sliderScaleZ = addRenderableWidget(new ForgeSlider(rightX, rightStartTop + 54, 140, 16, Component.literal("Scl Z: "), Component.empty(), 0.0D, 5.0D, studioScaleZ, 0.05D, 2, true) {
            @Override protected void applyValue() {
                if (isUpdatingScale) return;
                studioScaleZ = (float) getValue();
                if (linkScale) { studioScaleX = studioScaleY = studioScaleZ; syncSliders(); }
            }
        });

        addRenderableWidget(Button.builder(Component.literal("0°"), b -> { studioRotX = studioRotY = studioRotZ = 0; syncSliders(); }).bounds(rightX, rightStartBottom, 30, 16).build());
        addRenderableWidget(Button.builder(Component.literal("Snap 90°"), b -> {
            studioRotX = Math.round(studioRotX / 90.0f) * 90.0f;
            studioRotY = Math.round(studioRotY / 90.0f) * 90.0f;
            studioRotZ = Math.round(studioRotZ / 90.0f) * 90.0f;
            syncSliders();
        }).bounds(rightX + 35, rightStartBottom, 60, 16).build());

        sliderRotX = addRenderableWidget(new ForgeSlider(rightX, rightStartBottom + 18, 140, 16, Component.literal("Rot X: "), Component.empty(), -180.0D, 180.0D, studioRotX, 1.0D, 0, true) {
            @Override protected void applyValue() { studioRotX = (float) getValue(); }
        });
        sliderRotY = addRenderableWidget(new ForgeSlider(rightX, rightStartBottom + 36, 140, 16, Component.literal("Rot Y: "), Component.empty(), -180.0D, 180.0D, studioRotY, 1.0D, 0, true) {
            @Override protected void applyValue() { studioRotY = (float) getValue(); }
        });
        sliderRotZ = addRenderableWidget(new ForgeSlider(rightX, rightStartBottom + 54, 140, 16, Component.literal("Rot Z: "), Component.empty(), -180.0D, 180.0D, studioRotZ, 1.0D, 0, true) {
            @Override protected void applyValue() { studioRotZ = (float) getValue(); }
        });

        addRenderableWidget(Button.builder(Component.literal("💾 In Resourcepack sichern"), b -> saveConfigLocal())
            .bounds(this.width / 2 - 100, this.height - 25, 200, 20).build());

        loadPreviewEntity();
    }

    private void toggleDropdown(int id) {
        entityDropdownOpen = (id == 1 && !entityDropdownOpen);
        mutationDropdownOpen = (id == 2 && !mutationDropdownOpen);
        boneDropdownOpen = (id == 3 && !boneDropdownOpen);
        dropdownScrollOffset = 0;
    }

    private void syncSliders() {
        if (sliderX != null) sliderX.setValue(studioX);
        if (sliderY != null) sliderY.setValue(studioY);
        if (sliderZ != null) sliderZ.setValue(studioZ);
        isUpdatingScale = true;
        if (sliderScaleX != null) sliderScaleX.setValue(studioScaleX);
        if (sliderScaleY != null) sliderScaleY.setValue(studioScaleY);
        if (sliderScaleZ != null) sliderScaleZ.setValue(studioScaleZ);
        isUpdatingScale = false;
        if (sliderRotX != null) sliderRotX.setValue(studioRotX);
        if (sliderRotY != null) sliderRotY.setValue(studioRotY);
        if (sliderRotZ != null) sliderRotZ.setValue(studioRotZ);
    }

    private void loadPreviewEntity() {
        if (Minecraft.getInstance().level == null || matchedEntities.isEmpty()) return;

        EntityType<?> type = matchedEntities.get(activeEntityIndex);
        
        // Defensiver Aufruf: Verhindert Komplett-Crash bei Fremd-Mod-Entities mit fehlerhaften Konstruktoren
        LivingEntity created = null;
        try {
            if (type.create(Minecraft.getInstance().level) instanceof LivingEntity living) {
                created = living;
            }
        } catch (Throwable t) {
            Woerp.LOGGER.error("[WOERP-STUDIO] Entity {} konnte auf Client nicht erstellt werden: {}", type, t.getMessage());
        }

        if (created == null) {
            created = (LivingEntity) EntityType.ZOMBIE.create(Minecraft.getInstance().level);
        }
        this.previewEntity = created;
        studioActiveMutation = MUTATIONS.get(activeMutationIndex);

        availableBones.clear();
        EntityRenderer<?> r = Minecraft.getInstance().getEntityRenderDispatcher().renderers.get(previewEntity.getType());
        if (r instanceof LivingEntityRenderer<?, ?> lr) {
            availableBones.addAll(ModelBoneScanner.getSafeBones(lr.getModel()).keySet());
        }
        availableBones.sort(String::compareToIgnoreCase);

        ResourceLocation rl = ForgeRegistries.ENTITY_TYPES.getKey(previewEntity.getType());
        MutationOffsetLoader.MutationOffset off = MutationOffsetLoader.getOffset(rl, studioActiveMutation);

        studioX = off.x; studioY = off.y; studioZ = off.z;
        studioScaleX = off.scaleX; studioScaleY = off.scaleY; studioScaleZ = off.scaleZ;
        studioRotX = off.rotX; studioRotY = off.rotY; studioRotZ = off.rotZ;
        studioSelectedBone = off.bone;

        if (!availableBones.contains(studioSelectedBone)) {
            studioSelectedBone = availableBones.isEmpty() ? "head" : availableBones.get(0);
        }
        activeBoneIndex = Math.max(0, availableBones.indexOf(studioSelectedBone));

        // Schirmt EditBox-Listener vor Rueckkopplungsschleifen ab
        isUpdatingUI = true;
        if (boneSearchBox != null) boneSearchBox.setValue(studioSelectedBone);
        if (mutationSearchBox != null) mutationSearchBox.setValue(studioActiveMutation);
        if (entitySearchBox != null) entitySearchBox.setValue(rl.getPath());
        isUpdatingUI = false;

        previewEntity.removeAllEffects();
        for (RegistryObject<MobEffect> eff : ModEffects.ALL_MUTATIONS) {
            if (eff.getId().getPath().equals(studioActiveMutation)) {
                previewEntity.addEffect(new MobEffectInstance(eff.get(), -1, 0, false, false, false));
                break;
            }
        }

        syncSliders();
    }

    private void saveConfigLocal() {
        if (previewEntity == null) return;
        try {
            ResourceLocation rl = ForgeRegistries.ENTITY_TYPES.getKey(previewEntity.getType());
            File mcDir = Minecraft.getInstance().gameDirectory;
            File packDir = new File(mcDir, "resourcepacks/Woerp_Studio");
            File configDir = new File(packDir, "assets/woerp/offsets/" + rl.getNamespace());
            if (!configDir.exists()) configDir.mkdirs();

            File meta = new File(packDir, "pack.mcmeta");
            if (!meta.exists()) {
                Files.writeString(meta.toPath(), "{\n  \"pack\": {\n    \"pack_format\": 15,\n    \"description\": \"Woerp Mutation Offsets\"\n  }\n}");
            }

            File jsonFile = new File(configDir, rl.getPath() + ".json");
            JsonObject root = jsonFile.exists() ? GSON.fromJson(Files.readString(jsonFile.toPath()), JsonObject.class) : new JsonObject();

            JsonObject mObj = new JsonObject();
            mObj.addProperty("bone", studioSelectedBone);
            mObj.addProperty("x", studioX); mObj.addProperty("y", studioY); mObj.addProperty("z", studioZ);
            mObj.addProperty("scale_x", studioScaleX); mObj.addProperty("scale_y", studioScaleY); mObj.addProperty("scale_z", studioScaleZ);
            mObj.addProperty("rotation_x", studioRotX); mObj.addProperty("rotation_y", studioRotY); mObj.addProperty("rotation_z", studioRotZ);

            root.add(studioActiveMutation, mObj);
            Files.writeString(jsonFile.toPath(), GSON.toJson(root));

            MutationOffsetLoader.updateOffsetAtRuntime(rl, studioActiveMutation, new MutationOffsetLoader.MutationOffset(
                studioSelectedBone, studioX, studioY, studioZ, studioScaleX, studioScaleY, studioScaleZ, studioRotX, studioRotY, studioRotZ
            ));

            activatePack("Woerp_Studio");
        } catch (Exception e) {
            Woerp.LOGGER.error("[WOERP-STUDIO] Fehler beim Sichern:", e);
        }
    }

    private static void activatePack(String name) {
        Minecraft mc = Minecraft.getInstance();
        try {
            mc.getResourcePackRepository().reload();
            List<String> list = new ArrayList<>(mc.options.resourcePacks);
            String id = "file/" + name;
            if (!list.contains(id)) {
                list.add(id);
                mc.options.resourcePacks = list;
                mc.options.save();
                mc.getResourcePackRepository().setSelected(list);
            }
        } catch (Exception ignored) {}
    }

    private void selectEntity(int dir) {
        activeEntityIndex = (activeEntityIndex + dir + matchedEntities.size()) % matchedEntities.size();
        loadPreviewEntity();
    }

    private void selectMutation(int dir) {
        activeMutationIndex = (activeMutationIndex + dir + MUTATIONS.size()) % MUTATIONS.size();
        loadPreviewEntity();
    }

    private void selectBone(int dir) {
        if (availableBones.isEmpty()) return;
        activeBoneIndex = (activeBoneIndex + dir + availableBones.size()) % availableBones.size();
        studioSelectedBone = availableBones.get(activeBoneIndex);
        isUpdatingUI = true;
        boneSearchBox.setValue(studioSelectedBone);
        isUpdatingUI = false;
    }

    private void onEntitySearchChanged(String text) {
        if (isUpdatingUI) return;
        List<EntityType<?>> filter = matchedEntities.stream().filter(e -> ForgeRegistries.ENTITY_TYPES.getKey(e).getPath().contains(text.toLowerCase())).toList();
        if (!filter.isEmpty()) { activeEntityIndex = matchedEntities.indexOf(filter.get(0)); loadPreviewEntity(); }
    }

    private void onMutationSearchChanged(String text) {
        if (isUpdatingUI) return;
        List<String> filter = MUTATIONS.stream().filter(m -> m.contains(text.toLowerCase())).toList();
        if (!filter.isEmpty()) { activeMutationIndex = MUTATIONS.indexOf(filter.get(0)); loadPreviewEntity(); }
    }

    private void onBoneSearchChanged(String text) {
        if (isUpdatingUI) return;
        List<String> filter = availableBones.stream().filter(b -> b.toLowerCase().contains(text.toLowerCase())).toList();
        if (!filter.isEmpty()) { activeBoneIndex = availableBones.indexOf(filter.get(0)); studioSelectedBone = availableBones.get(activeBoneIndex); }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.fill(0, 0, this.width, 34, 0xF20F0F12);
        graphics.drawString(font, "Woerp Mutation Studio", 15, 13, 0xFFE4E4E7);

        entitySearchBox.render(graphics, mouseX, mouseY, partialTick);
        mutationSearchBox.render(graphics, mouseX, mouseY, partialTick);
        boneSearchBox.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawString(font, "Entity:", 20, 35, 0xFFAAAAAA);
        graphics.drawString(font, "Mutation:", 20, 58, 0xFFAAAAAA);
        graphics.drawString(font, "Knochen:", 20, 81, 0xFFAAAAAA);

        if (previewEntity != null) {
            previewEntity.tickCount++;
            renderPreview(graphics, this.width / 2, this.height / 2 + 50, (int) (65 * zoomFactor));
        }

        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 500);
        renderDropdowns(graphics, mouseX, mouseY);
        graphics.pose().popPose();
    }

    private void renderPreview(GuiGraphics graphics, int x, int y, int scale) {
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 50.0D);
        pose.scale(scale, scale, -scale);
        pose.mulPose(Axis.ZP.rotationDegrees(180.0F));
        pose.mulPose(Axis.XP.rotationDegrees(rotationPitch));

        Lighting.setupForEntityInInventory();
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        previewEntity.yBodyRot = previewEntity.yRotO = previewEntity.yHeadRot = previewEntity.yHeadRotO = rotationYaw;
        dispatcher.setRenderShadow(false);
        RenderSystem.runAsFancy(() -> dispatcher.render(previewEntity, 0, 0, 0, 0, 1.0F, pose, graphics.bufferSource(), 15728880));
        graphics.flush();
        dispatcher.setRenderShadow(true);
        pose.popPose();
        Lighting.setupFor3DItems();
    }

    private void renderDropdowns(GuiGraphics graphics, int mouseX, int mouseY) {
        int itemH = 12, maxVisible = 8;
        if (entityDropdownOpen) renderList(graphics, 20, 62, 120, matchedEntities.stream().map(e -> ForgeRegistries.ENTITY_TYPES.getKey(e).getPath()).toList(), itemH, maxVisible, mouseX, mouseY, 1);
        if (mutationDropdownOpen) renderList(graphics, 20, 85, 120, MUTATIONS, itemH, maxVisible, mouseX, mouseY, 2);
        if (boneDropdownOpen) renderList(graphics, 20, 108, 120, availableBones, itemH, maxVisible, mouseX, mouseY, 3);
    }

    private void renderList(GuiGraphics g, int x, int y, int w, List<String> list, int itemH, int maxVis, int mx, int my, int type) {
        int size = Math.min(list.size(), maxVis);
        g.fill(x, y, x + w, y + (size * itemH), 0xF20B0B0C);
        g.renderOutline(x, y, w, size * itemH, 0x44FFFFFF);
        for (int i = 0; i < size; i++) {
            int idx = (i + dropdownScrollOffset) % list.size();
            int iy = y + (i * itemH);
            boolean hov = mx >= x && mx < x + w && my >= iy && my < iy + itemH;
            if (hov) g.fill(x + 1, iy, x + w - 1, iy + itemH, 0x22FFFFFF);
            g.drawString(font, list.get(idx), x + 4, iy + 2, hov ? 0xFFFFFFFF : 0xFF999999);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (entityDropdownOpen && checkListClick(20, 62, 120, matchedEntities.size(), 1, my)) return true;
        if (mutationDropdownOpen && checkListClick(20, 85, 120, MUTATIONS.size(), 2, my)) return true;
        if (boneDropdownOpen && checkListClick(20, 108, 120, availableBones.size(), 3, my)) return true;
        entityDropdownOpen = mutationDropdownOpen = boneDropdownOpen = false;

        if (super.mouseClicked(mx, my, button)) return true;
        if (mx > 170 && mx < this.width - 170 && my > 34) { isDraggingEntity = true; return true; }
        return false;
    }

    private boolean checkListClick(int x, int y, int w, int total, int type, double my) {
        int itemH = 12, size = Math.min(total, 8);
        if (my >= y && my < y + (size * itemH)) {
            int clicked = ((int) ((my - y) / itemH) + dropdownScrollOffset) % total;
            if (type == 1) { activeEntityIndex = clicked; loadPreviewEntity(); }
            if (type == 2) { activeMutationIndex = clicked; loadPreviewEntity(); }
            if (type == 3) {
                activeBoneIndex = clicked;
                studioSelectedBone = availableBones.get(clicked);
                isUpdatingUI = true;
                boneSearchBox.setValue(studioSelectedBone);
                isUpdatingUI = false;
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int b, double dx, double dy) {
        if (isDraggingEntity) {
            rotationYaw += (float) dx * 1.5f;
            rotationPitch = Math.max(-90.0f, Math.min(90.0f, rotationPitch - (float) dy * 1.5f));
            return true;
        }
        return super.mouseDragged(mx, my, b, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int b) {
        isDraggingEntity = false;
        return super.mouseReleased(mx, my, b);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (entityDropdownOpen || mutationDropdownOpen || boneDropdownOpen) {
            dropdownScrollOffset = Math.max(0, dropdownScrollOffset - (int) delta);
            return true;
        }
        zoomFactor = Math.max(0.2f, Math.min(5.0f, zoomFactor + (float) delta * 0.2f));
        return true;
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