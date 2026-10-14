package com.verityball.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.verityball.VerityBallMod;
import com.verityball.VerityVariant;
import com.verityball.entity.VerityEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

public class VerityRenderer extends EntityRenderer<VerityEntity> {
    /** Cambia este número para hacer a Verity más grande o más pequeña. */
    public static final float SCALE = 1.0F;
    /** Tamaño relativo cuando la llevas en la mano. */
    public static final float HELD_SCALE = 0.5F;
    /** Altura del centro de la esfera sobre los pies (4.4 px de Blockbench). */
    private static final float CENTER_Y = 4.4F / 16.0F;

    /** A qué distancia (bloques) empieza a seguirte con la mirada. */
    private static final double LOOK_RANGE = 32.0D;

    private static final Map<String, ResourceLocation> TEXTURES = new HashMap<>();

    /** Textura de una parte (base, face_idle...) para la variante dada: textures/entity/&lt;variante&gt;/&lt;parte&gt;.png */
    private static ResourceLocation tex(VerityVariant variant, String name) {
        return TEXTURES.computeIfAbsent(variant.key + "/" + name,
                n -> new ResourceLocation(VerityBallMod.MODID, "textures/entity/" + n + ".png"));
    }

    /** Esfera blanca: con el arcoíris activo el color sale puro, sin mezclarse con el color de la variante. */
    private static ResourceLocation whiteSphere() {
        return TEXTURES.computeIfAbsent("base_white",
                n -> new ResourceLocation(VerityBallMod.MODID, "textures/entity/base_white.png"));
    }

    // Colores del arcoíris (igual que tu script de Figura)
    private static final float[][] RAINBOW = {
            {1f, 0f, 0f}, {1f, 0.5f, 0f}, {1f, 1f, 0f}, {0f, 1f, 0f},
            {0f, 0f, 1f}, {0.29f, 0f, 0.51f}, {0.93f, 0.51f, 0.93f}
    };

    public VerityRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.3F;
    }

    @Override
    public ResourceLocation getTextureLocation(VerityEntity entity) {
        return tex(entity.getVariant(), "base");
    }

    /**
     * Si la lleva un jugador, la dibujamos pegada a su mano usando la posición suavizada del jugador
     * (así no va a tirones aunque la posición del servidor llegue cada pocos ticks).
     */
    @Override
    public Vec3 getRenderOffset(VerityEntity entity, float partialTick) {
        Player holder = entity.getHolderPlayer();
        if (holder == null) return Vec3.ZERO;
        Vec3 hand = VerityEntity.handCenter(holder, partialTick);
        double ex = Mth.lerp((double) partialTick, entity.xOld, entity.getX());
        double ey = Mth.lerp((double) partialTick, entity.yOld, entity.getY());
        double ez = Mth.lerp((double) partialTick, entity.zOld, entity.getZ());
        return new Vec3(hand.x - ex, hand.y - CENTER_Y * SCALE * HELD_SCALE - ey, hand.z - ez);
    }

    /** Una vez por tick: gira suavemente la cara hacia el jugador local (yaw) y la inclina hacia sus ojos (pitch). */
    private static void updateLook(VerityEntity entity) {
        if (entity.lookTick == entity.tickCount) return;

        float targetYaw = entity.getYRot();
        float targetPitch = 0.0F;
        LocalPlayer me = Minecraft.getInstance().player;
        if (me != null) {
            double dx = me.getX() - entity.getX();
            double dz = me.getZ() - entity.getZ();
            double dy = me.getEyeY() - (entity.getY() + CENTER_Y * SCALE * (entity.isHeld() ? HELD_SCALE : 1.0F));
            double horizSq = dx * dx + dz * dz;
            if (horizSq + dy * dy < LOOK_RANGE * LOOK_RANGE && horizSq > 1.0E-4D) {
                targetYaw = (float) (Mth.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
                targetPitch = Mth.clamp((float) Math.toDegrees(Math.atan2(dy, Math.sqrt(horizSq))), -30.0F, 40.0F);
            }
        }

        if (entity.lookTick < 0) { // primera vez: mirar directo, sin dar vueltas
            entity.lookYaw = entity.lookYawO = targetYaw;
            entity.lookPitch = entity.lookPitchO = targetPitch;
        } else {
            entity.lookYawO = entity.lookYaw;
            entity.lookPitchO = entity.lookPitch;
            float dYaw = Mth.wrapDegrees(targetYaw - entity.lookYaw);
            entity.lookYaw += Mth.clamp(dYaw * 0.35F, -25.0F, 25.0F);
            entity.lookPitch += Mth.clamp((targetPitch - entity.lookPitch) * 0.35F, -10.0F, 10.0F);
        }
        entity.lookTick = entity.tickCount;
    }

    @Override
    public void render(VerityEntity entity, float entityYaw, float partialTick, PoseStack ps,
                       MultiBufferSource buffers, int light) {
        VerityModel model = VerityModel.get();
        if (model != null) {
            boolean held = entity.isHeld();
            float scale = SCALE * (held ? HELD_SCALE : 1.0F);
            this.shadowRadius = held ? 0.0F : 0.3F;

            updateLook(entity);
            float yaw = Mth.rotLerp(partialTick, entity.lookYawO, entity.lookYaw);
            float pitch = Mth.lerp(partialTick, entity.lookPitchO, entity.lookPitch);

            ps.pushPose();
            ps.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));
            ps.scale(scale, scale, scale);
            // inclinar la cara hacia arriba/abajo girando alrededor del centro de la esfera
            ps.translate(0.0F, CENTER_Y, 0.0F);
            ps.mulPose(Axis.XP.rotationDegrees(pitch));
            ps.translate(0.0F, -CENTER_Y, 0.0F);

            float seconds = Math.max(0f, entity.tickCount - entity.stateStartTick + partialTick) / 20.0F;
            Map<String, float[]> pose = model.computePose(entity.getState(), seconds);

            float[] sphereColor = {1f, 1f, 1f};
            if (entity.isRainbow()) {
                float t = entity.tickCount + partialTick;
                float blend = 0.5F * (1.0F + (float) Math.sin(2.0 * Math.PI * t / 120.0));
                int idx = ((int) Math.floor(t / 60.0F)) % RAINBOW.length;
                float[] c1 = RAINBOW[idx], c2 = RAINBOW[(idx + 1) % RAINBOW.length];
                for (int i = 0; i < 3; i++) sphereColor[i] = c1[i] + (c2[i] - c1[i]) * blend;
            }

            VerityVariant variant = entity.getVariant();
            boolean rainbow = entity.isRainbow();
            for (VerityModel.Bone bone : model.roots) {
                renderBone(bone, pose, ps, buffers, light, sphereColor, variant, rainbow);
            }
            ps.popPose();
        }
        super.render(entity, entityYaw, partialTick, ps, buffers, light); // nombre (name tag)
    }

    private void renderBone(VerityModel.Bone bone, Map<String, float[]> pose, PoseStack ps,
                            MultiBufferSource buffers, int light, float[] sphereColor,
                            VerityVariant variant, boolean rainbow) {
        // Accesorios de una sola variante: el hueso "hat_<variante>" solo se dibuja en esa variante
        if (bone.name.startsWith("hat_") && !bone.name.equals("hat_" + variant.key)) return;

        float[] p = pose.get(bone.name);
        if (p == null) p = VerityModel.restPose();

        // escala 0 = hueso oculto (así se cambian las caras)
        if (Math.abs(p[6]) < 1.0E-4F || Math.abs(p[7]) < 1.0E-4F || Math.abs(p[8]) < 1.0E-4F) return;

        ps.pushPose();
        ps.translate(-p[3] / 16.0F, p[4] / 16.0F, p[5] / 16.0F);
        ps.translate(bone.px / 16.0F, bone.py / 16.0F, bone.pz / 16.0F);
        // Convención de rotación de Blockbench: X e Y invertidas
        ps.mulPose(Axis.ZP.rotationDegrees(p[2]));
        ps.mulPose(Axis.YP.rotationDegrees(-p[1]));
        ps.mulPose(Axis.XP.rotationDegrees(-p[0]));
        ps.scale(p[6], p[7], p[8]);
        ps.translate(-bone.px / 16.0F, -bone.py / 16.0F, -bone.pz / 16.0F);

        if (!bone.quads.isEmpty()) {
            float[] color = bone.name.equals("sphere") ? sphereColor : new float[]{1f, 1f, 1f};
            PoseStack.Pose last = ps.last();
            Matrix4f mat = last.pose();
            Matrix3f nor = last.normal();
            String lastTex = null;
            VertexConsumer vc = null;
            for (VerityModel.Quad q : bone.quads) {
                if (vc == null || !q.tex.equals(lastTex)) {
                    ResourceLocation texture = (rainbow && q.tex.equals("base")) ? whiteSphere() : tex(variant, q.tex);
                    vc = buffers.getBuffer(RenderType.entityCutoutNoCull(texture));
                    lastTex = q.tex;
                }
                for (int i = 0; i < 4; i++) {
                    vc.vertex(mat, q.v[i * 5] / 16.0F, q.v[i * 5 + 1] / 16.0F, q.v[i * 5 + 2] / 16.0F)
                            .color(color[0], color[1], color[2], 1.0F)
                            .uv(q.v[i * 5 + 3], q.v[i * 5 + 4])
                            .overlayCoords(OverlayTexture.NO_OVERLAY)
                            .uv2(light)
                            .normal(nor, q.n[i * 3], q.n[i * 3 + 1], q.n[i * 3 + 2])
                            .endVertex();
                }
            }
        }

        for (VerityModel.Bone child : bone.children) {
            renderBone(child, pose, ps, buffers, light, sphereColor, variant, rainbow);
        }
        ps.popPose();
    }
}
