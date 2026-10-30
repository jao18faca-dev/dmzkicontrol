package com.dmzkicontrol.client.scene;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/** Roteiro de uma cutscene de transformacao ja lido do JSON. Tempos em ticks (20 = 1 s). */
public final class Scene {
    public record Cam(double t, double ang, double dist, double h, double look, double fov, double roll) {}
    public record Shake(double t, double d, double amp) {}
    public record Pose(int t, String name) {}
    public record Fx(int t, int d, String kind, JsonObject p) {}
    public record Snd(int t, String id, float vol, float pitch) {}

    public final String id;
    public final int len;
    public final List<Cam> cam = new ArrayList<>();
    public final List<Shake> shake = new ArrayList<>();
    public final List<Pose> pose = new ArrayList<>();
    public final List<Fx> fx = new ArrayList<>();
    public final List<Fx> scr = new ArrayList<>();
    public final List<Snd> snd = new ArrayList<>();
    public final int titleT, titleD, subT, subD;
    public final String titleKey, subKey;

    public Scene(String id, JsonObject o) {
        this.id = id;
        this.len = o.has("len") ? o.get("len").getAsInt() : 120;
        for (JsonElement e : arr(o, "cam")) {
            JsonArray a = e.getAsJsonArray();
            cam.add(new Cam(d(a, 0, 0), d(a, 1, 180), d(a, 2, 4), d(a, 3, 0.6), d(a, 4, 0.6), d(a, 5, 1), d(a, 6, 0)));
        }
        if (cam.isEmpty()) cam.add(new Cam(0, 180, 4, 0.6, 0.6, 1, 0));
        for (JsonElement e : arr(o, "shake")) {
            JsonArray a = e.getAsJsonArray();
            shake.add(new Shake(d(a, 0, 0), d(a, 1, 10), d(a, 2, 1)));
        }
        for (JsonElement e : arr(o, "pose")) {
            JsonArray a = e.getAsJsonArray();
            pose.add(new Pose(a.get(0).getAsInt(), a.get(1).getAsString()));
        }
        for (JsonElement e : arr(o, "fx")) fx.add(fx(e.getAsJsonArray()));
        for (JsonElement e : arr(o, "scr")) scr.add(fx(e.getAsJsonArray()));
        for (JsonElement e : arr(o, "snd")) {
            JsonArray a = e.getAsJsonArray();
            snd.add(new Snd(a.get(0).getAsInt(), a.get(1).getAsString(), (float) d(a, 2, 1), (float) d(a, 3, 1)));
        }
        JsonArray ti = o.has("title") ? o.getAsJsonArray("title") : null;
        titleT = ti != null ? ti.get(0).getAsInt() : Math.max(0, len - 50);
        titleD = ti != null ? ti.get(1).getAsInt() : 45;
        JsonArray su = o.has("sub") ? o.getAsJsonArray("sub") : null;
        subT = su != null ? su.get(0).getAsInt() : titleT + 6;
        subD = su != null ? su.get(1).getAsInt() : titleD - 6;
        String k = id.replace(":", ".").replace("*", "any");
        titleKey = "scene.dmzkicontrol." + k + ".title";
        subKey = "scene.dmzkicontrol." + k + ".sub";
    }

    private static Fx fx(JsonArray a) {
        JsonObject p = a.size() > 3 && a.get(3).isJsonObject() ? a.get(3).getAsJsonObject() : new JsonObject();
        return new Fx(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsString(), p);
    }

    private static JsonArray arr(JsonObject o, String k) {
        return o.has(k) && o.get(k).isJsonArray() ? o.getAsJsonArray(k) : new JsonArray();
    }

    private static double d(JsonArray a, int i, double def) {
        return a.size() > i && !a.get(i).isJsonNull() ? a.get(i).getAsDouble() : def;
    }

    // ---- leitura de parametros dos efeitos
    public static double num(JsonObject p, String k, double def) {
        return p.has(k) ? p.get(k).getAsDouble() : def;
    }

    public static String str(JsonObject p, String k, String def) {
        return p.has(k) ? p.get(k).getAsString() : def;
    }

    /** Cor "#RRGGBB" em {r,g,b} (0-1). */
    public static float[] color(JsonObject p, String k, int def) {
        int c = def;
        if (p.has(k)) {
            String s = p.get(k).getAsString().trim();
            if (s.startsWith("#")) s = s.substring(1);
            try { c = Integer.parseInt(s, 16); } catch (NumberFormatException ignored) {}
        }
        return new float[]{((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f};
    }

    public static int rgb(JsonObject p, String k, int def) {
        float[] f = color(p, k, def);
        return ((int) (f[0] * 255) << 16) | ((int) (f[1] * 255) << 8) | (int) (f[2] * 255);
    }
}
