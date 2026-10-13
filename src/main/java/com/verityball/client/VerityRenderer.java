package com.verityball.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.verityball.VerityBallMod;
import com.verityball.entity.VerityEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

public class VerityRenderer extends EntityRenderer<VerityEntity> {
    /** Cambia este número para hacer a Verity más grande o más pequeña. */
    public static final float SCALE = 1.0F;

    private static final Map<String, ResourceLocation> TEXTURES = new HashMap<>();

    private static ResourceLocation tex(String name) {
        return TEXTURES.computeIfAbsent(name,
                n -> new ResourceLocation(VerityBallMod.MODID, "textures/entity/" + n + ".png"));
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
        return tex("base");
    }

    @Override
    public void render(VerityEntity entity, float entityYaw, float partialTick, PoseStack ps,
                       MultiBufferSource buffers, int light) {
        VerityModel model = VerityModel.get();
        if (model != null) {
            ps.pushPose();
            ps.mulPose(Axis.YP.rotationDegrees(180.0F - Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot())));
            ps.scale(SCALE, SCALE, SCALE);

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

            for (VerityModel.Bone bone : model.roots) {
                renderBone(bone, pose, ps, buffers, light, sphereColor);
            }
            ps.popPose();
        }
        super.render(entity, entityYaw, partialTick, ps, buffers, light); // nombre (name tag)
    }

    private void renderBone(VerityModel.Bone bone, Map<String, float[]> pose, PoseStack ps,
                            MultiBufferSource buffers, int light, float[] sphereColor) {
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
                    vc = buffers.getBuffer(RenderType.entityCutoutNoCull(tex(q.tex)));
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
            renderBone(child, pose, ps, buffers, light, sphereColor);
        }
        ps.popPose();
    }
}
