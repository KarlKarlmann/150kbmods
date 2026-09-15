package net.kb150.meteorshower.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.kb150.meteorshower.MeteorShower;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.*;

@Mod.EventBusSubscriber(
        modid = MeteorShower.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT
)
public class MeteorRenderer {

    private static final ResourceLocation TEX_METEOR_1 = new ResourceLocation(MeteorShower.MOD_ID, "textures/entity/meteor1.png");
    private static final ResourceLocation TEX_METEOR_2 = new ResourceLocation(MeteorShower.MOD_ID, "textures/entity/meteor2.png");
    private static final ResourceLocation TEX_METEOR_3 = new ResourceLocation(MeteorShower.MOD_ID, "textures/entity/meteor3.png");
    
    private static final ResourceLocation TEX_SMOKE_1 = new ResourceLocation(MeteorShower.MOD_ID, "textures/entity/smoke1.png");
    private static final ResourceLocation TEX_SMOKE_2 = new ResourceLocation(MeteorShower.MOD_ID, "textures/entity/smoke2.png");
    private static final ResourceLocation TEX_SMOKE_3 = new ResourceLocation(MeteorShower.MOD_ID, "textures/entity/smoke3.png");
    private static final ResourceLocation TEX_SMOKE_4 = new ResourceLocation(MeteorShower.MOD_ID, "textures/entity/smoke4.png");

    private static final ResourceLocation[] SMOKE_TEXTURES = {TEX_SMOKE_1, TEX_SMOKE_2, TEX_SMOKE_3, TEX_SMOKE_4};
    private static final ResourceLocation[] FIRE_TEXTURES = {TEX_METEOR_1, TEX_METEOR_2, TEX_METEOR_3};

    private static final Set<MeteorClientHandler.ClientMeteor> PROCESSED_METEORS = new HashSet<>();
    private static final Map<MeteorClientHandler.ClientMeteor, Vec3> LAST_SMOKE_POS = new HashMap<>();

    private static final List<ClientParticle> ACTIVE_SMOKE_NODES = new ArrayList<>();
    private static final List<ClientParticle> ACTIVE_FIRE_NODES = new ArrayList<>();

    private static final int LIGHT_FULL_BRIGHT = LightTexture.pack(15, 15);
    private static final int LIGHT_SKY_ONLY = LightTexture.pack(0, 15);

    public static boolean renderedInDhThisFrame = false;

    static {
        if (ModList.get().isLoaded("distanthorizons")) {
            try {
                Class.forName("net.kb150.meteorshower.client.compat.DhCompat").getMethod("init").invoke(null);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static void addImpact(Vec3 pos, Vec3 incomingDir) {
        Random rand = new Random();
        
        for (int i = 0; i < 30; i++) {
            double angle = rand.nextDouble() * Math.PI * 2.0;
            double speed = 1.5 + rand.nextDouble() * 2.5;
            Vec3 vel = new Vec3(Math.cos(angle) * speed, rand.nextDouble() * 0.2, Math.sin(angle) * speed);
            ResourceLocation tex = SMOKE_TEXTURES[rand.nextInt(SMOKE_TEXTURES.length)];
            ACTIVE_SMOKE_NODES.add(new ClientParticle(pos, vel, 60 + rand.nextInt(20), tex, rand.nextFloat() * 6.28F, 30.0F, 0.85));
        }

        for (int i = 0; i < 50; i++) {
            Vec3 ejectaDir = incomingDir.scale(0.7).add(
                    new Vec3(rand.nextGaussian() * 0.4, rand.nextDouble() * 0.6, rand.nextGaussian() * 0.4)
            ).normalize();
            
            double speed = 3.0 + rand.nextDouble() * 5.0;
            Vec3 vel = ejectaDir.scale(speed);
            
            ResourceLocation tex = FIRE_TEXTURES[rand.nextInt(FIRE_TEXTURES.length)];
            ACTIVE_FIRE_NODES.add(new ClientParticle(pos, vel, 25 + rand.nextInt(20), tex, rand.nextFloat() * 6.28F, 20.0F + rand.nextFloat() * 15.0F, 0.92));
        }

        for (int i = 0; i < 60; i++) {
            Vec3 vel = new Vec3(rand.nextGaussian() * 0.3, 0.5 + rand.nextDouble() * 1.5, rand.nextGaussian() * 0.3);
            ResourceLocation tex = SMOKE_TEXTURES[rand.nextInt(SMOKE_TEXTURES.length)];
            ACTIVE_SMOKE_NODES.add(new ClientParticle(pos, vel, 100 + rand.nextInt(60), tex, rand.nextFloat() * 6.28F, 40.0F + rand.nextFloat() * 40.0F, 0.96));
        }
    }

    private static void updateParticles() {
        updateParticleList(ACTIVE_SMOKE_NODES);
        updateParticleList(ACTIVE_FIRE_NODES);
    }

    private static void updateParticleList(List<ClientParticle> list) {
        Iterator<ClientParticle> it = list.iterator();
        while (it.hasNext()) {
            ClientParticle p = it.next();
            p.age++;
            if (p.age >= p.maxAge) {
                it.remove();
            } else {
                p.worldPos = p.worldPos.add(p.velocity);
                p.velocity = p.velocity.scale(p.drag);
            }
        }
    }

    private static void tickMeteorLogic(float partialTick) {
        List<MeteorClientHandler.ClientMeteor> activeMeteors = MeteorClientHandler.getActiveMeteors();

        for (MeteorClientHandler.ClientMeteor meteor : activeMeteors) {
            Vec3 cometPos = meteor.getInterpolatedPosition(partialTick);
            Vec3 travelDir = meteor.endPos.subtract(meteor.startPos).normalize();
            
            // Impact auslösen (Einmalig)
            if ((meteor.totalTicks - meteor.currentTicks) <= 2 && PROCESSED_METEORS.add(meteor)) {
                addImpact(meteor.endPos, travelDir);
            }

            // Bow Shock Spawning (Ablation)
            Vec3 spawnCenter = cometPos.add(travelDir.scale(4.5));
            Vec3 lastPos = LAST_SMOKE_POS.get(meteor);
            
            if (lastPos == null || lastPos.distanceToSqr(cometPos) > 1.0D) {
                for (int s = 0; s < 2; s++) {
                    ResourceLocation randomSmoke = SMOKE_TEXTURES[(int)(Math.random() * SMOKE_TEXTURES.length)];
                    float randRot = (float)(Math.random() * Math.PI * 2);
                    float randSize = 15.0F + (float)(Math.random() * 10.0F);
                    int lifespan = 800 + (int)(Math.random() * 400); 
                    
                    Vec3 drift = new Vec3((Math.random() - 0.5) * 0.12, 0.02 + (Math.random() * 0.05), (Math.random() - 0.5) * 0.12);
                    Vec3 particlePos = spawnCenter.add((Math.random() - 0.5) * 3.5, (Math.random() - 0.5) * 3.5, (Math.random() - 0.5) * 3.5);

                    ACTIVE_SMOKE_NODES.add(new ClientParticle(particlePos, drift, lifespan, randomSmoke, randRot, randSize, 0.995));
                }

                for (int f = 0; f < 3; f++) {
                    ResourceLocation randomFire = FIRE_TEXTURES[(int)(Math.random() * FIRE_TEXTURES.length)];
                    float randRot = (float)(Math.random() * Math.PI * 2);
                    float randSize = 8.0F + (float)(Math.random() * 6.0F);
                    int lifespan = 8 + (int)(Math.random() * 12);
                    
                    Vec3 outwardVel = new Vec3((Math.random() - 0.5) * 0.4, (Math.random() - 0.5) * 0.4, (Math.random() - 0.5) * 0.4);
                    Vec3 particlePos = spawnCenter.add((Math.random() - 0.5) * 2.5, (Math.random() - 0.5) * 2.5, (Math.random() - 0.5) * 2.5);

                    ACTIVE_FIRE_NODES.add(new ClientParticle(particlePos, outwardVel, lifespan, randomFire, randRot, randSize, 0.90));
                }
                LAST_SMOKE_POS.put(meteor, cometPos);
            }
        }
    }

    public static void renderMeteorsForDH(org.joml.Matrix4f projMat, org.joml.Matrix4f mvMat, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        if (MeteorClientHandler.getActiveMeteors().isEmpty() && ACTIVE_SMOKE_NODES.isEmpty() && ACTIVE_FIRE_NODES.isEmpty()) {
            return;
        }

        // Signalisiert dem Vanilla Event, dass dieser Frame bereits durch DH gerendert wurde
        renderedInDhThisFrame = true;

        Camera camera = mc.gameRenderer.getMainCamera();
        
        // Sehr große Sichtweite für DH nutzen, damit die Meteore nicht herunterskaliert werden
        float farPlane = 200000.0F;

        org.joml.Matrix4f oldProj = RenderSystem.getProjectionMatrix();
        com.mojang.blaze3d.vertex.VertexSorting oldSort = RenderSystem.getVertexSorting();
        
        RenderSystem.setProjectionMatrix(projMat, com.mojang.blaze3d.vertex.VertexSorting.DISTANCE_TO_ORIGIN);
        
        PoseStack mvStack = RenderSystem.getModelViewStack();
        mvStack.pushPose();
        mvStack.setIdentity();
        mvStack.last().pose().mul(mvMat); // <-- Gefixter JOML Aufruf
        RenderSystem.applyModelViewMatrix();
        
        // Renderpass im DH-Framebuffer (schreibt korrekte Tiefendaten gegenüber den LODs)
        doRender(camera, partialTick, farPlane, true);
        
        mvStack.popPose();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(oldProj, oldSort);
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) return;

        // Logik-Update (Rauch verwehen & neue Spawnen) wird jeden Frame genau einmal gemacht
        updateParticles();
        tickMeteorLogic(event.getPartialTick());
        
        List<MeteorClientHandler.ClientMeteor> activeMeteors = MeteorClientHandler.getActiveMeteors();
        LAST_SMOKE_POS.keySet().retainAll(activeMeteors);

        // DH hat bereits im eigenen Render-Pass gerendert? Dann überspringen wir das Vanilla-Rendering!
        if (renderedInDhThisFrame) {
            renderedInDhThisFrame = false;
            return;
        }

        if (activeMeteors.isEmpty() && ACTIVE_SMOKE_NODES.isEmpty() && ACTIVE_FIRE_NODES.isEmpty()) {
            PROCESSED_METEORS.clear();
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        float farPlane = mc.gameRenderer.getRenderDistance();
        
        // Fallback: Standard Vanilla Rendering (mit Distance-Scaling)
        doRender(event.getCamera(), event.getPartialTick(), farPlane, false);
    }

    private static void doRender(Camera camera, float partialTick, float farPlane, boolean isDH) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 cameraPos = camera.getPosition();
        
        RenderSystem.enableDepthTest();
        // WICHTIG: Im DH-Modus MÜSSEN wir die Tiefe schreiben, da DH sonst 
        // den leeren Himmel (Depth 1.0) für näher hält und den Meteor ignoriert!
        RenderSystem.depthMask(isDH); 
        RenderSystem.enableBlend();
        
        // Shader mit Lightmap-Support
        RenderSystem.setShader(GameRenderer::getPositionColorTexLightmapShader);

        Vector3f right = camera.getLeftVector().mul(-1.0F, new org.joml.Vector3f());
        Vector3f up = camera.getUpVector();

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        long time = mc.level != null ? mc.level.getGameTime() : 0;
        float smoothTime = time + partialTick;

        // Partikel nach Distanz sortieren (Hinten nach Vorne), damit
        // die Transparenz bei aktiviertem DepthMask fehlerfrei funktioniert!
        List<ClientParticle> sortedSmoke = new ArrayList<>(ACTIVE_SMOKE_NODES);
        sortedSmoke.sort((p1, p2) -> Double.compare(p2.worldPos.distanceToSqr(cameraPos), p1.worldPos.distanceToSqr(cameraPos)));

        List<ClientParticle> sortedFire = new ArrayList<>(ACTIVE_FIRE_NODES);
        sortedFire.sort((p1, p2) -> Double.compare(p2.worldPos.distanceToSqr(cameraPos), p1.worldPos.distanceToSqr(cameraPos)));

        List<MeteorClientHandler.ClientMeteor> sortedMeteors = new ArrayList<>(MeteorClientHandler.getActiveMeteors());
        sortedMeteors.sort((m1, m2) -> Double.compare(m2.getInterpolatedPosition(partialTick).distanceToSqr(cameraPos), m1.getInterpolatedPosition(partialTick).distanceToSqr(cameraPos)));

        // ========================================================
        // 1. RAUCH RENDERN (Reagiert auf Tageslicht)
        // ========================================================
        // KORREKTUR: Separates Alpha-Blending, damit der DH-Framebuffer den Alpha-Wert für den Himmel speichert!
        RenderSystem.blendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA
        );
        RenderSystem.setShaderTexture(0, TEX_SMOKE_1);
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP);

        for (ClientParticle smoke : sortedSmoke) {
            float progress = (float) smoke.age / smoke.maxAge;
            Vec3 relativePos = smoke.worldPos.subtract(cameraPos);
            double dist = relativePos.length();
            
            float fogFactor = isDH ? 1.0f : (float) Math.max(0.0, 1.0 - (dist / 8000.0));
            float currentSize = smoke.baseSize + (progress * 50.0F);
            float alpha = (1.0F - progress) * 0.6F * fogFactor;
            float color = Math.min(1.0F, 0.3F + progress * 0.7F);
            
            switchTexture(tesselator, buffer, smoke.texture);
            renderQuad(buffer, relativePos, right, up, currentSize, color, color, color, alpha, smoke.rotation + (progress * 1.5F), LIGHT_SKY_ONLY, farPlane, isDH);
        }

        // ========================================================
        // 2. FEUER & METEORE (Leuchtend)
        // ========================================================
        // KORREKTUR: Separates Alpha-Blending für das leuchtende Feuer!
        RenderSystem.blendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE
        );
        switchTexture(tesselator, buffer, TEX_METEOR_1);

        for (ClientParticle fire : sortedFire) {
            float progress = (float) fire.age / fire.maxAge;
            Vec3 relativePos = fire.worldPos.subtract(cameraPos);
            double dist = relativePos.length();
            float fogFactor = isDH ? 1.0f : (float) Math.max(0.0, 1.0 - (dist / 8000.0));

            float currentSize = fire.baseSize * (1.0F - progress * 0.5F);
            float alpha = (1.0F - progress) * fogFactor;
            
            switchTexture(tesselator, buffer, fire.texture);
            renderQuad(buffer, relativePos, right, up, currentSize, 1.0F, 0.7F, 0.3F, alpha, fire.rotation + (progress * 2.0F), LIGHT_FULL_BRIGHT, farPlane, isDH);
        }

        // B. Meteor Köpfe & Glow
        for (MeteorClientHandler.ClientMeteor meteor : sortedMeteors) {
            Vec3 cometPos = meteor.getInterpolatedPosition(partialTick);
            Vec3 relativeCometPos = cometPos.subtract(cameraPos);
            
            double dist = relativeCometPos.length();
            float fogFactor = isDH ? 1.0f : (float) Math.max(0.0, 1.0 - (dist / 8000.0));

            // Kern Rendern
            int spriteCount = 18;
            for (int i = 0; i < spriteCount; i++) {
                double offsetX = Math.sin(smoothTime * 0.2 + i * 1.5) * 3.5;
                double offsetY = Math.cos(smoothTime * 0.15 + i * 2.1) * 3.5;
                double offsetZ = Math.sin(smoothTime * 0.1 + i * 0.8) * 3.5;
                
                Vec3 relativePos = relativeCometPos.add(offsetX, offsetY, offsetZ);

                float size = 10.0F + (i % 3) * 5.0F;
                float rot = smoothTime * 0.05F * (i % 2 == 0 ? 1 : -1) + i;
                
                ResourceLocation tex = FIRE_TEXTURES[i % 3];
                switchTexture(tesselator, buffer, tex);
                renderQuad(buffer, relativePos, right, up, size, 1.0F, 0.85F, 0.6F, 0.8F * fogFactor, rot, LIGHT_FULL_BRIGHT, farPlane, isDH);
            }
            
            // Glow
            switchTexture(tesselator, buffer, TEX_METEOR_3);
            renderQuad(buffer, relativeCometPos, right, up, 35.0F, 1.0F, 0.5F, 0.1F, 0.6F * fogFactor, 0.0F, LIGHT_FULL_BRIGHT, farPlane, isDH);
        }

        tesselator.end();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    }

    private static void switchTexture(Tesselator tesselator, BufferBuilder buffer, ResourceLocation newTexture) {
        tesselator.end();
        RenderSystem.setShaderTexture(0, newTexture);
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP);
    }

    private static void renderQuad(BufferBuilder buffer, Vec3 relativePos, Vector3f right, Vector3f up, float size, float r, float g, float b, float a, float rotationAngle, int lightmap, float farPlane, boolean isDH) {
        double dist = relativePos.length();
        float maxDist = farPlane * 0.9F;

        // Wenn Vanilla Minecraft rendert, stauchen wir die Meteore an die farPlane (damit sie nicht weggeschnitten werden)
        // Im DH-Rendermodus bleiben sie exakt in ihren tiefen echten 3D-Koordinaten!
        if (!isDH && dist > maxDist) {
            size = (float) (size * (maxDist / dist));
            relativePos = relativePos.normalize().scale(maxDist);
        }

        float px = (float) relativePos.x; float py = (float) relativePos.y; float pz = (float) relativePos.z;
        float cos = (float) Math.cos(rotationAngle); float sin = (float) Math.sin(rotationAngle);

        float rx = (right.x() * cos + up.x() * sin) * size; float ry = (right.y() * cos + up.y() * sin) * size; float rz = (right.z() * cos + up.z() * sin) * size;
        float ux = (-right.x() * sin + up.x() * cos) * size; float uy = (-right.y() * sin + up.y() * cos) * size; float uz = (-right.z() * sin + up.z() * cos) * size;

        buffer.vertex(px - rx - ux, py - ry - uy, pz - rz - uz).color(r, g, b, a).uv(0.0F, 1.0F).uv2(lightmap).endVertex();
        buffer.vertex(px + rx - ux, py + ry - uy, pz + rz - uz).color(r, g, b, a).uv(1.0F, 1.0F).uv2(lightmap).endVertex();
        buffer.vertex(px + rx + ux, py + ry + uy, pz + rz + uz).color(r, g, b, a).uv(1.0F, 0.0F).uv2(lightmap).endVertex();
        buffer.vertex(px - rx + ux, py - ry + uy, pz - rz + uz).color(r, g, b, a).uv(0.0F, 0.0F).uv2(lightmap).endVertex();
    }

    private static class ClientParticle {
        Vec3 worldPos;
        Vec3 velocity;
        int age = 0;
        final int maxAge;
        final ResourceLocation texture;
        final float rotation;
        final float baseSize;
        final double drag;

        ClientParticle(Vec3 worldPos, Vec3 velocity, int maxAge, ResourceLocation texture, float rotation, float baseSize, double drag) {
            this.worldPos = worldPos;
            this.velocity = velocity;
            this.maxAge = maxAge;
            this.texture = texture;
            this.rotation = rotation;
            this.baseSize = baseSize;
            this.drag = drag;
        }
    }
}