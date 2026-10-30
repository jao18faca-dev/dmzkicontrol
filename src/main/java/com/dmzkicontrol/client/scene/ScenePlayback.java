package com.dmzkicontrol.client.scene;

import com.dmzkicontrol.KiControlMod;
import com.dmzkicontrol.client.ArmAnimDriver;
import com.dmzkicontrol.formscene.SceneLibrary;
import com.dmzkicontrol.network.FormSceneSkipC2S;
import com.dmzkicontrol.network.KiControlNetwork;
import com.google.gson.JsonObject;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Cutscenes de transformacao em andamento no cliente (de qualquer jogador por perto).
 * Para o proprio jogador: camera, tela e controles travados. Para os outros: efeitos no mundo, sons e poses.
 */
public final class ScenePlayback {
    public static final class Play {
        public final int entityId;
        public final Scene scene;
        public final long start;
        public final float bodyYaw;
        int lastTick = -1;
        public final long seed;

        Play(int entityId, Scene scene, long start, float bodyYaw) {
            this.entityId = entityId;
            this.scene = scene;
            this.start = start;
            this.bodyYaw = bodyYaw;
            this.seed = entityId * 31L + start;
        }

        /** Tempo da cena (ticks, com fracao). */
        public double time(float partial) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return 0;
            return Math.max(0, mc.level.getGameTime() - start + partial);
        }
    }

    private static final Map<Integer, Play> PLAYS = new HashMap<>();
    private static final Map<String, Scene> PARSED = new HashMap<>();
    private static CameraType previousCamera;
    private static Integer localId;

    private ScenePlayback() {}

    public static Map<Integer, Play> plays() {
        return PLAYS;
    }

    public static Play local() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;
        return PLAYS.get(mc.player.getId());
    }

    public static boolean isLocalActive() {
        return local() != null;
    }

    private static Scene scene(String id) {
        return PARSED.computeIfAbsent(id, k -> {
            JsonObject o = SceneLibrary.get(k);
            return new Scene(k, o == null ? new JsonObject() : o);
        });
    }

    public static void onPacket(int entityId, String sceneId, long start, boolean stop) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (stop) {
            end(entityId);
            return;
        }
        Entity e = mc.level.getEntity(entityId);
        float yaw = e instanceof net.minecraft.world.entity.LivingEntity le ? le.yBodyRot : (e != null ? e.getYRot() : 0f);
        Play play = new Play(entityId, scene(sceneId), start, yaw);
        PLAYS.put(entityId, play);
        SceneFx.onStart(play);
        if (mc.player != null && entityId == mc.player.getId()) {
            if (previousCamera == null) previousCamera = mc.options.getCameraType();
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            localId = entityId;
            SceneCamera.begin(mc.player);
        }
    }

    private static void end(int entityId) {
        Play p = PLAYS.remove(entityId);
        if (p != null) SceneFx.onEnd(p);
        Minecraft mc = Minecraft.getInstance();
        if (localId != null && localId == entityId) {
            localId = null;
            if (previousCamera != null) mc.options.setCameraType(previousCamera);
            previousCamera = null;
        }
    }

    public static void clear() {
        for (Integer id : PLAYS.keySet().toArray(new Integer[0])) end(id);
        PLAYS.clear();
        SceneFx.clear();
    }

    /** Pula a propria cena (tecla). */
    public static void skipLocal() {
        Play p = local();
        if (p == null) return;
        KiControlNetwork.CHANNEL.sendToServer(new FormSceneSkipC2S());
        end(p.entityId);
    }

    public static void clientTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            if (!PLAYS.isEmpty()) clear();
            return;
        }
        if (mc.isPaused()) return;
        Iterator<Map.Entry<Integer, Play>> it = PLAYS.entrySet().iterator();
        java.util.List<Integer> finished = new java.util.ArrayList<>();
        while (it.hasNext()) {
            Play p = it.next().getValue();
            Entity e = mc.level.getEntity(p.entityId);
            int now = (int) Math.floor(p.time(0));
            if (e == null || !e.isAlive() || now >= p.scene.len) {
                finished.add(p.entityId);
                continue;
            }
            for (int t = p.lastTick + 1; t <= now; t++) fire(p, e, t);
            p.lastTick = now;
            SceneFx.tick(p, e, now);
        }
        for (Integer id : finished) end(id);
        if (localId != null && mc.player != null) lockLocal(mc.player);
    }

    /** Eventos que comecam no tick t: poses e sons. */
    private static void fire(Play p, Entity e, int t) {
        for (int i = 0; i < p.scene.pose.size(); i++) {
            Scene.Pose pose = p.scene.pose.get(i);
            if (pose.t() != t) continue;
            int until = i + 1 < p.scene.pose.size() ? p.scene.pose.get(i + 1).t() : p.scene.len;
            if (e instanceof AbstractClientPlayer acp) ArmAnimDriver.play(acp, "dmzkicontrol.cs." + pose.name(), Math.max(2, until - t + 2));
        }
        for (Scene.Snd s : p.scene.snd) {
            if (s.t() != t) continue;
            try {
                SoundEvent ev = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation(s.id()));
                if (ev != null) e.level().playLocalSound(e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), ev, SoundSource.PLAYERS, s.vol(), s.pitch(), false);
            } catch (Exception ex) {
                KiControlMod.LOGGER.debug("[Cutscenes] som {} invalido", s.id());
            }
        }
        for (Scene.Fx f : p.scene.fx) if (f.t() == t) SceneFx.onFxStart(p, e, f);
    }

    /** O proprio jogador nao gira nem anda durante a cena. */
    private static void lockLocal(LocalPlayer lp) {
        SceneCamera.lockRotation(lp);
        lp.setDeltaMovement(lp.getDeltaMovement().multiply(0, 1, 0));
        net.minecraft.client.KeyMapping.releaseAll();
    }
}
