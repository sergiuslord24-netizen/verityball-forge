package com.verityball.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.verityball.VerityBallMod;
import com.verityball.VerityState;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VerityRenderer extends EntityRenderer<VerityEntity> {
    /** A qué distancia (bloques) empieza a seguirte con la mirada. */
    private static final double LOOK_RANGE = 32.0D;
    /** Por encima de esta velocidad (bloques/tick) se orienta según hacia dónde camina. */
    private static final float MOVING_SPEED = 0.04F;

    private static final Map<String, ResourceLocation> TEXTURES = new HashMap<>();

    /** Textura de una parte (base, face_idle, fem...) para la variante dada: textures/entity/&lt;variante&gt;/&lt;parte&gt;.png */
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
        VerityVariant v = entity.getVariant();
        return tex(v, v.isFem() ? "fem" : "base");
    }

    /** Escala total con la que se dibuja (modelo, y más pequeña si va en la mano). */
    private static float totalScale(VerityEntity entity) {
        VerityVariant.Type t = entity.getVariant().type;
        return t.scale * (entity.isHeld() ? t.heldScale : 1.0F);
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
        return new Vec3(hand.x - ex, hand.y - VerityEntity.heldAnchorDrop(entity.getVariant()) - ey, hand.z - ez);
    }

    /**
     * Una vez por tick: gira suavemente hacia donde toca. Si se está moviendo, mira hacia donde camina;
     * si está quieta, mira al jugador local (yaw) y, la esfera, inclina la cara hacia sus ojos (pitch).
     */
    private static void updateLook(VerityEntity entity) {
        if (entity.lookTick == entity.tickCount) return;

        boolean fem = entity.getVariant().isFem();
        float targetYaw = entity.getYRot();
        float targetPitch = 0.0F;

        double mx = entity.getX() - entity.xo, mz = entity.getZ() - entity.zo;
        boolean moving = !entity.isHeld() && entity.moveSpeed > MOVING_SPEED && (mx * mx + mz * mz) > 1.0E-6D;

        LocalPlayer me = Minecraft.getInstance().player;
        if (moving) {
            targetYaw = (float) (Mth.atan2(mz, mx) * (180.0D / Math.PI)) - 90.0F;
        } else if (me != null && entity.isLookingAtPlayers()) {
            double dx = me.getX() - entity.getX();
            double dz = me.getZ() - entity.getZ();
            float scale = totalScale(entity);
            double dy = me.getEyeY() - (entity.getY() + (entity.getVariant().type.anchorY * scale));
            double horizSq = dx * dx + dz * dz;
            if (horizSq + dy * dy < LOOK_RANGE * LOOK_RANGE && horizSq > 1.0E-4D) {
                targetYaw = (float) (Mth.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
                if (!fem) { // la humanoide solo gira el cuerpo; la esfera además inclina la cara
                    targetPitch = Mth.clamp((float) Math.toDegrees(Math.atan2(dy, Math.sqrt(horizSq))), -30.0F, 40.0F);
                }
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

    /** Animaciones que hay que combinar ahora mismo, de la base a la más prioritaria. */
    private static List<VerityModel.Play> buildPlays(VerityEntity entity, float partialTick) {
        List<VerityModel.Play> plays = new ArrayList<>();
        VerityVariant.Type type = entity.getVariant().type;
        float sinceState = Math.max(0f, entity.tickCount - entity.stateStartTick + partialTick) / 20.0F;

        if (type != VerityVariant.Type.FEM) {
            for (String name : entity.getState().animationsFor(type)) plays.add(new VerityModel.Play(name, sinceState));
            return plays;
        }

        // Humanoide: "idle" siempre de base; si camina, caminar/correr; si está quieta, la animación del estado
        float clock = (entity.tickCount + partialTick) / 20.0F;
        plays.add(new VerityModel.Play("idle", clock));
        if (!entity.isHeld() && entity.moveSpeed > 0.02F) {
            float step = entity.walkClock + partialTick * (entity.moveSpeed / (entity.moveSpeed > 0.25F ? 0.30F : 0.20F)) / 20.0F;
            plays.add(new VerityModel.Play(entity.moveSpeed > 0.25F ? "run" : "walk", step));
        } else {
            for (String name : entity.getState().animationsFor(type)) plays.add(new VerityModel.Play(name, sinceState));
        }
        return plays;
    }

    @Override
    public void render(VerityEntity entity, float entityYaw, float partialTick, PoseStack ps,
                       MultiBufferSource buffers, int light) {
        VerityVariant variant = entity.getVariant();
        VerityModel model = VerityModel.get(variant.type);
        if (model != null) {
            boolean held = entity.isHeld();
            float scale = totalScale(entity);
            this.shadowRadius = held ? 0.0F : (variant.isFem() ? 0.4F : 0.3F);

            updateLook(entity);
            float yaw = Mth.rotLerp(partialTick, entity.lookYawO, entity.lookYaw);
            float pitch = Mth.lerp(partialTick, entity.lookPitchO, entity.lookPitch);

            ps.pushPose();
            ps.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));
            ps.scale(scale, scale, scale);
            if (!variant.isFem() && pitch != 0.0F) {
                // inclinar la cara hacia arriba/abajo girando alrededor del centro de la esfera
                float cy = variant.type.anchorY;
                ps.translate(0.0F, cy, 0.0F);
                ps.mulPose(Axis.XP.rotationDegrees(pitch));
                ps.translate(0.0F, -cy, 0.0F);
            }

            Map<String, float[]> pose = model.computePose(buildPlays(entity, partialTick));

            boolean rainbow = entity.isRainbow() && !variant.isFem();
            boolean dead = entity.getState() == VerityState.DEAD;
            boolean clown = entity.getState() == VerityState.CLOWN;
            float[] sphereColor = {1f, 1f, 1f};
            if (rainbow) {
                float t = entity.tickCount + partialTick;
                float blend = 0.5F * (1.0F + (float) Math.sin(2.0 * Math.PI * t / 120.0));
                int idx = ((int) Math.floor(t / 60.0F)) % RAINBOW.length;
                float[] c1 = RAINBOW[idx], c2 = RAINBOW[(idx + 1) % RAINBOW.length];
                for (int i = 0; i < 3; i++) sphereColor[i] = c1[i] + (c2[i] - c1[i]) * blend;
            }

            for (VerityModel.Bone bone : model.roots) {
                renderBone(bone, pose, ps, buffers, light, sphereColor, variant, rainbow, dead, clown);
            }
            ps.popPose();
        }
        super.render(entity, entityYaw, partialTick, ps, buffers, light); // nombre (name tag)
    }

    private void renderBone(VerityModel.Bone bone, Map<String, float[]> pose, PoseStack ps,
                            MultiBufferSource buffers, int light, float[] sphereColor,
                            VerityVariant variant, boolean rainbow, boolean dead, boolean clown) {
        // La cara de muerto (XX) solo se dibuja en el estado DEAD
        if (bone.name.equals("mouth_dead") && !dead) return;
        if (bone.name.equals("mouth_clown") && !(clown && variant == VerityVariant.MORBOSITY)) return;
        // Accesorios de una sola variante: el hueso "hat_<variante>" solo se dibuja en esa variante
        if (bone.name.startsWith("hat_") && !bone.name.equals("hat_" + variant.key)) return;

        float[] p = pose.get(bone.name);
        if (p == null) p = VerityModel.restPose();

        // escala 0 = hueso oculto (así se cambian las caras)
        if (Math.abs(p[6]) < 1.0E-4F || Math.abs(p[7]) < 1.0E-4F || Math.abs(p[8]) < 1.0E-4F) return;

        ps.pushPose();
        ps.translate(-p[3] / 16.0F, p[4] / 16.0F, p[5] / 16.0F);
        ps.translate(bone.px / 16.0F, bone.py / 16.0F, bone.pz / 16.0F);
        // Rotación = reposo del hueso + animación. Convención de Blockbench: en la animación X e Y van invertidas.
        ps.mulPose(Axis.ZP.rotationDegrees(bone.rz + p[2]));
        ps.mulPose(Axis.YP.rotationDegrees(bone.ry - p[1]));
        ps.mulPose(Axis.XP.rotationDegrees(bone.rx - p[0]));
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
            renderBone(child, pose, ps, buffers, light, sphereColor, variant, rainbow, dead, clown);
        }
        ps.popPose();
    }
}
