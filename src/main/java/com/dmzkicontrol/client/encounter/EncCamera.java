package com.dmzkicontrol.client.encounter;

import com.dmzkicontrol.KiControlMod;
import com.dmzkicontrol.encounter.EncScript.Beat;
import com.dmzkicontrol.encounter.EncScript.Shot;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ViewportEvent;

import java.lang.reflect.Method;

/**
 * Camera da luta: cada cena tem enquadramentos (de onde, para onde, zoom) presos aos personagens, com transicao
 * suave ou corte seco. Afasta sozinha quando tem mais jogadores na cena, cresce com chefes grandes e treme nos impactos.
 */
public final class EncCamera {
    private static Method setPosition;
    private static boolean reflectionFailed;
    private static float lastFov = 1f;
    private static Vec3 lastEye, lastAt;

    private EncCamera() {}

    /** Posicao de uma ancora no mundo. */
    private static Vec3 anchor(EncClient.State s, String name, float partial) {
        Minecraft mc = Minecraft.getInstance();
        switch (name) {
            case "boss" -> {
                Entity b = s.boss();
                return b != null ? b.getPosition(partial) : s.world(0, s.phase.lift, 0);
            }
            case "me" -> {
                if (mc.player != null) return mc.player.getPosition(partial);
                return group(s, partial);
            }
            case "mid" -> {
                Vec3 g = group(s, partial);
                Entity b = s.boss();
                Vec3 bp = b != null ? b.getPosition(partial) : s.world(0, s.phase.lift, 0);
                return g.add(bp).scale(0.5);
            }
            case "p0", "p1", "p2", "p3" -> {
                int r = name.charAt(1) - '0';
                Entity p = s.player(Math.min(r, s.n() - 1));
                if (p != null) return p.getPosition(partial);
                return group(s, partial);
            }
            default -> {
                return group(s, partial);
            }
        }
    }

    public static Vec3 group(EncClient.State s, float partial) {
        Vec3 c = Vec3.ZERO;
        int k = 0;
        for (int i = 0; i < s.n(); i++) {
            Entity p = s.player(i);
            if (p == null) continue;
            c = c.add(p.getPosition(partial));
            k++;
        }
        return k == 0 ? s.world(0, s.phase.lift, s.phase.distance) : c.scale(1.0 / k);
    }

    /** Escala do deslocamento: grupo maior = camera mais longe; chefe grande = enquadramento maior. */
    private static double scale(EncClient.State s, String name) {
        if (name.equals("boss")) return Math.max(1.0, EncClient.bossHeight() / 2.0);
        if (name.equals("group") || name.equals("mid")) return 1.0 + 0.16 * (s.n() - 1);
        return 1.0;
    }

    private static Vec3 eye(EncClient.State s, Shot sh, float partial) {
        double k = scale(s, sh.from());
        return anchor(s, sh.from(), partial).add(s.dir(sh.off()[0] * k, sh.off()[1] * Math.max(1, k * 0.8), sh.off()[2] * k));
    }

    private static Vec3 look(EncClient.State s, Shot sh, float partial) {
        double k = sh.at().equals("boss") ? scale(s, "boss") : 1.0;
        return anchor(s, sh.at(), partial).add(s.dir(sh.atOff()[0], sh.atOff()[1] * k, sh.atOff()[2]));
    }

    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        EncClient.State s = EncClient.state();
        if (s == null || !s.participant()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        Beat beat = s.beat();
        if (beat.shots.isEmpty()) return;
        float partial = (float) event.getPartialTick();
        double t = s.time(partial);
        int i = 0;
        while (i + 1 < beat.shots.size() && beat.shots.get(i + 1).t() <= t) i++;
        Shot cur = beat.shots.get(i);
        Vec3 eye = eye(s, cur, partial), at = look(s, cur, partial);
        float fov = cur.fov(), roll = cur.roll();
        if (i > 0 && cur.blend() > 0) {
            double u = Mth.clamp((t - cur.t()) / cur.blend(), 0, 1);
            u = u * u * (3 - 2 * u);
            Shot prev = beat.shots.get(i - 1);
            eye = eye(s, prev, partial).lerp(eye, u);
            at = look(s, prev, partial).lerp(at, u);
            fov = Mth.lerp((float) u, prev.fov(), fov);
            roll = Mth.lerp((float) u, prev.roll(), roll);
        }
        // nao atravessa paredes
        BlockHitResult hit = mc.level.clip(new ClipContext(at, eye, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc.player));
        if (hit.getType() != HitResult.Type.MISS) {
            Vec3 dir = eye.subtract(at);
            double len = Math.max(0.4, hit.getLocation().distanceTo(at) - 0.25);
            eye = at.add(dir.normalize().scale(len));
        }
        // cena de transformacao por cima da camera da luta (mistura na saida)
        Object[] cine = EncTransformCine.camera(s, partial);
        if (cine != null) {
            float mix = (float) cine[4];
            eye = eye.lerp((Vec3) cine[0], mix);
            at = at.lerp((Vec3) cine[1], mix);
            fov = Mth.lerp(mix, fov, (float) cine[2]);
            roll = Mth.lerp(mix, roll, (float) cine[3]);
            BlockHitResult h2 = mc.level.clip(new ClipContext(at, eye, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc.player));
            if (h2.getType() != HitResult.Type.MISS) {
                Vec3 dir = eye.subtract(at);
                eye = at.add(dir.normalize().scale(Math.max(0.4, h2.getLocation().distanceTo(at) - 0.25)));
            }
        }
        // hitstop: nos impactos fortes a camera congela um instante (o golpe "pesa")
        if (EncHud.hitstop(partial) && lastEye != null) {
            eye = lastEye;
            at = lastAt;
        } else {
            lastEye = eye;
            lastAt = at;
        }
        roll += EncHud.rollKick(partial);
        if (!movePosition(event.getCamera(), eye)) return;
        Vec3 d = at.subtract(eye);
        double horiz = Math.sqrt(d.x * d.x + d.z * d.z);
        float yaw = (float) Math.toDegrees(Mth.atan2(d.z, d.x)) - 90f;
        float pitch = (float) -Math.toDegrees(Mth.atan2(d.y, horiz));
        float[] sh = EncHud.shakeOffset(partial);
        event.setYaw(yaw + sh[0]);
        event.setPitch(pitch + sh[1]);
        event.setRoll(roll + sh[2]);
        lastFov = fov;
    }

    public static void onFov(ViewportEvent.ComputeFov event) {
        if (!EncClient.active()) return;
        float punch = EncHud.fovPunch((float) event.getPartialTick());
        event.setFOV(event.getFOV() * Mth.clamp(lastFov * punch, 0.3f, 1.8f));
    }

    private static boolean movePosition(Camera camera, Vec3 pos) {
        if (reflectionFailed) return false;
        try {
            if (setPosition == null) {
                for (Method m : Camera.class.getDeclaredMethods()) {
                    if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == Vec3.class && m.getReturnType() == void.class) {
                        m.setAccessible(true);
                        setPosition = m;
                        break;
                    }
                }
                if (setPosition == null) throw new NoSuchMethodException("Camera#setPosition(Vec3)");
            }
            setPosition.invoke(camera, pos);
            return true;
        } catch (Throwable t) {
            reflectionFailed = true;
            KiControlMod.LOGGER.warn("Lutas: camera nao pode ser movida ({}).", t.toString());
            return false;
        }
    }
}
