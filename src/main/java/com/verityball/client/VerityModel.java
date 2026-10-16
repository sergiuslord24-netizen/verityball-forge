package com.verityball.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.verityball.VerityVariant;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Modelo de Verity cargado desde assets/verityball/models/verity_model.json
 * (convertido desde verity_ball_model.bbmodel) + reproductor de animaciones propio.
 */
public class VerityModel {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<VerityVariant.Type, VerityModel> CACHE = new HashMap<>();
    private static final java.util.Set<VerityVariant.Type> FAILED = new java.util.HashSet<>();

    public static class Quad {
        public String tex;
        /** 4 vértices * (x, y, z, u, v), posiciones en píxeles de Blockbench (1/16 de bloque). */
        public float[] v = new float[20];
        /** 4 vértices * (nx, ny, nz) */
        public float[] n = new float[12];
    }

    public static class Bone {
        public String name;
        public float px, py, pz;
        /** Rotación de reposo del hueso en grados (Blockbench). La animación se suma a esta. */
        public float rx, ry, rz;
        public final List<Quad> quads = new ArrayList<>();
        public final List<Bone> children = new ArrayList<>();
    }

    public static class Key {
        public float time;
        public float[] val = new float[3];
        public int interp; // 0 linear, 1 step, 2 catmullrom
    }

    public static class Anim {
        public String name;
        public float length;
        public boolean loop;
        public int priority;
        /** hueso -> [canal][keys]; canal 0 = rotación, 1 = posición, 2 = escala */
        public final Map<String, Key[][]> tracks = new HashMap<>();
    }

    public final List<Bone> roots = new ArrayList<>();
    public final Map<String, Anim> anims = new HashMap<>();

    /** Una animación en marcha: nombre y segundos desde su inicio. */
    public static class Play {
        public final String name;
        public final float seconds;

        public Play(String name, float seconds) {
            this.name = name;
            this.seconds = seconds;
        }
    }

    /** Modelo del tipo dado (se carga una sola vez). Devuelve null si no se pudo cargar. */
    public static VerityModel get(VerityVariant.Type type) {
        VerityModel m = CACHE.get(type);
        if (m == null && !FAILED.contains(type)) {
            try {
                m = load(new ResourceLocation("verityball", "models/" + type.modelFile + ".json"));
                CACHE.put(type, m);
            } catch (Exception e) {
                FAILED.add(type);
                LOGGER.error("No se pudo cargar el modelo " + type.modelFile, e);
            }
        }
        return m;
    }

    private static VerityModel load(ResourceLocation file) throws Exception {
        VerityModel m = new VerityModel();
        try (BufferedReader reader = Minecraft.getInstance().getResourceManager().getResourceOrThrow(file).openAsReader()) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();

            Map<String, Bone> byName = new HashMap<>();
            for (JsonElement be : root.getAsJsonArray("bones")) {
                JsonObject bo = be.getAsJsonObject();
                Bone bone = new Bone();
                bone.name = bo.get("name").getAsString();
                JsonArray piv = bo.getAsJsonArray("pivot");
                bone.px = piv.get(0).getAsFloat();
                bone.py = piv.get(1).getAsFloat();
                bone.pz = piv.get(2).getAsFloat();
                if (bo.has("rot")) {
                    JsonArray rot = bo.getAsJsonArray("rot");
                    bone.rx = rot.get(0).getAsFloat();
                    bone.ry = rot.get(1).getAsFloat();
                    bone.rz = rot.get(2).getAsFloat();
                }
                for (JsonElement qe : bo.getAsJsonArray("quads")) bone.quads.add(parseQuad(qe.getAsJsonObject()));
                byName.put(bone.name, bone);
                if (bo.get("parent").isJsonNull()) m.roots.add(bone);
                else byName.get(bo.get("parent").getAsString()).children.add(bone);
            }

            for (JsonElement ae : root.getAsJsonArray("animations")) {
                JsonObject ao = ae.getAsJsonObject();
                Anim anim = new Anim();
                anim.name = ao.get("name").getAsString();
                anim.length = ao.get("length").getAsFloat();
                anim.loop = ao.get("loop").getAsBoolean();
                anim.priority = priorityOf(anim.name);
                JsonObject tracks = ao.getAsJsonObject("tracks");
                for (Map.Entry<String, JsonElement> te : tracks.entrySet()) {
                    Key[][] channels = new Key[3][];
                    JsonObject co = te.getValue().getAsJsonObject();
                    parseChannel(co, "rotation", 0, channels);
                    parseChannel(co, "position", 1, channels);
                    parseChannel(co, "scale", 2, channels);
                    anim.tracks.put(te.getKey(), channels);
                }
                m.anims.put(anim.name, anim);
            }
        }
        return m;
    }

    /** Mismas prioridades que tu script.lua de Figura. */
    private static int priorityOf(String name) {
        switch (name) {
            case "talk": case "emote3": return 1;
            case "its_coming": return 2;
            default: return 0;
        }
    }

    private static void parseChannel(JsonObject co, String name, int idx, Key[][] out) {
        if (!co.has(name)) return;
        JsonArray arr = co.getAsJsonArray(name);
        Key[] keys = new Key[arr.size()];
        for (int i = 0; i < keys.length; i++) {
            JsonArray k = arr.get(i).getAsJsonArray();
            Key key = new Key();
            key.time = k.get(0).getAsFloat();
            JsonArray v = k.get(1).getAsJsonArray();
            key.val[0] = v.get(0).getAsFloat();
            key.val[1] = v.get(1).getAsFloat();
            key.val[2] = v.get(2).getAsFloat();
            key.interp = k.get(2).getAsInt();
            keys[i] = key;
        }
        out[idx] = keys;
    }

    private static Quad parseQuad(JsonObject qo) {
        Quad q = new Quad();
        q.tex = qo.get("tex").getAsString();
        JsonArray v = qo.getAsJsonArray("v");
        for (int i = 0; i < 20; i++) q.v[i] = v.get(i).getAsFloat();
        if (qo.has("n")) {
            JsonArray n = qo.getAsJsonArray("n");
            for (int i = 0; i < 4; i++) {
                q.n[i * 3] = n.get(0).getAsFloat();
                q.n[i * 3 + 1] = n.get(1).getAsFloat();
                q.n[i * 3 + 2] = n.get(2).getAsFloat();
            }
        } else {
            // esfera: normal suave = dirección desde el centro
            JsonArray c = qo.getAsJsonArray("center");
            float cx = c.get(0).getAsFloat(), cy = c.get(1).getAsFloat(), cz = c.get(2).getAsFloat();
            for (int i = 0; i < 4; i++) {
                float dx = q.v[i * 5] - cx, dy = q.v[i * 5 + 1] - cy, dz = q.v[i * 5 + 2] - cz;
                float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (len < 1e-6f) { dx = 0; dy = 1; dz = 0; len = 1; }
                q.n[i * 3] = dx / len;
                q.n[i * 3 + 1] = dy / len;
                q.n[i * 3 + 2] = dz / len;
            }
        }
        return q;
    }

    // ------------------------------------------------------------------
    // Animación
    // ------------------------------------------------------------------

    /** Pose de un hueso: [rx, ry, rz, px, py, pz, sx, sy, sz]. */
    public static float[] restPose() {
        return new float[]{0, 0, 0, 0, 0, 0, 1, 1, 1};
    }

    /**
     * Calcula la pose de todos los huesos. Por cada hueso y canal gana la animación
     * con mayor prioridad; con la misma prioridad gana la que va después en la lista
     * (así se puede poner primero la base y encima el movimiento o el estado).
     */
    public Map<String, float[]> computePose(List<Play> plays) {
        Map<String, float[]> pose = new HashMap<>();
        Map<String, int[]> best = new HashMap<>();
        for (Play play : plays) {
            Anim a = anims.get(play.name);
            if (a == null) continue;
            float t;
            if (a.loop && a.length > 0) t = play.seconds % a.length;
            else t = Math.min(play.seconds, a.length);
            for (Map.Entry<String, Key[][]> e : a.tracks.entrySet()) {
                float[] p = pose.computeIfAbsent(e.getKey(), k -> restPose());
                int[] b = best.computeIfAbsent(e.getKey(), k -> new int[]{-1, -1, -1});
                for (int c = 0; c < 3; c++) {
                    Key[] keys = e.getValue()[c];
                    if (keys == null || keys.length == 0) continue;
                    if (a.priority >= b[c]) {
                        float[] v = sample(keys, t, a.loop, a.length);
                        p[c * 3] = v[0];
                        p[c * 3 + 1] = v[1];
                        p[c * 3 + 2] = v[2];
                        b[c] = a.priority;
                    }
                }
            }
        }
        return pose;
    }

    private static float[] sample(Key[] ks, float t, boolean loop, float length) {
        int n = ks.length;
        if (n == 1 || t <= ks[0].time) return ks[0].val;
        if (t >= ks[n - 1].time) return ks[n - 1].val;
        int i = 0;
        while (i + 1 < n && ks[i + 1].time <= t) i++;
        Key a = ks[i], b = ks[i + 1];
        if (a.interp == 1) return a.val; // step
        float f = (t - a.time) / (b.time - a.time);
        float[] out = new float[3];
        if (a.interp == 0) {
            for (int c = 0; c < 3; c++) out[c] = a.val[c] + (b.val[c] - a.val[c]) * f;
            return out;
        }
        // Catmull-Rom
        boolean closed = loop && ks[n - 1].time >= length - 1e-3f;
        Key p0 = i > 0 ? ks[i - 1] : (loop ? ks[closed ? n - 2 : n - 1] : a);
        Key p3 = i + 2 < n ? ks[i + 2] : (loop ? ks[closed ? 1 : 0] : b);
        float f2 = f * f, f3 = f2 * f;
        for (int c = 0; c < 3; c++) {
            float v0 = p0.val[c], v1 = a.val[c], v2 = b.val[c], v3 = p3.val[c];
            out[c] = 0.5f * ((2 * v1) + (-v0 + v2) * f
                    + (2 * v0 - 5 * v1 + 4 * v2 - v3) * f2
                    + (-v0 + 3 * v1 - 3 * v2 + v3) * f3);
        }
        return out;
    }
}
