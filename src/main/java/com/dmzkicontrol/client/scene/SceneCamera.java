package com.dmzkicontrol.client.scene;

import com.dmzkicontrol.KiControlMod;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ViewportEvent;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Camera da cutscene de transformacao (so de quem esta se transformando).
 * Chaves: angulo em volta do corpo (0 = de frente para o rosto, 180 = de costas), distancia e altura em
 * "alturas de jogador" (cresce junto com formas gigantes), altura do alvo (fracao da altura do corpo),
 * zoom e inclinacao. Duas chaves no mesmo tick = corte seco. Funciona no chao e voando (ancora no corpo).
 */
public final class SceneCamera {
    private static float lockedYaw, lockedPitch;
    private static Method setPosition;
    private static boolean reflectionFailed;

    private SceneCamera() {}

    static void begin(LocalPlayer p) {
        lockedYaw = p.getYRot();
        lockedPitch = p.getXRot();
    }

    static void lockRotation(LocalPlayer p) {
        p.setYRot(lockedYaw);
        p.yRotO = lockedYaw;
        p.setXRot(lockedPitch);
        p.xRotO = lockedPitch;
    }

    public static void renderTickLock() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && ScenePlayback.isLocalActive()) lockRotation(mc.player);
    }

    /** Valor interpolado da coluna col no tempo t, respeitando cortes (chaves repetidas). */
    static double sample(List<Scene.Cam> keys, double t, int col) {
        int n = keys.size();
        if (n == 1) return get(keys.get(0), col);
        // segmento: [a, b] sem cortes no meio
        int i = 0;
        while (i < n - 1 && t >= keys.get(i + 1).t()) i++;
        if (i >= n - 1) return get(keys.get(n - 1), col);
        Scene.Cam p1 = keys.get(i), p2 = keys.get(i + 1);
        if (p2.t() <= p1.t()) return get(p2, col);
        Scene.Cam p0 = i > 0 && keys.get(i - 1).t() < p1.t() ? keys.get(i - 1) : p1;
        Scene.Cam p3 = i + 2 < n && keys.get(i + 2).t() > p2.t() ? keys.get(i + 2) : p2;
        double u = Mth.clamp((t - p1.t()) / (p2.t() - p1.t()), 0, 1);
        double a = get(p0, col), b = get(p1, col), c = get(p2, col), d = get(p3, col);
        double u2 = u * u, u3 = u2 * u;
        return 0.5 * ((2 * b) + (-a + c) * u + (2 * a - 5 * b + 4 * c - d) * u2 + (-a + 3 * b - 3 * c + d) * u3);
    }

    private static double get(Scene.Cam k, int col) {
        return switch (col) {
            case 1 -> k.ang();
            case 2 -> k.dist();
            case 3 -> k.h();
            case 4 -> k.look();
            case 5 -> k.fov();
            default -> k.roll();
        };
    }

    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        ScenePlayback.Play play = ScenePlayback.local();
        if (play == null) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return;
        float partial = (float) event.getPartialTick();
        double t = Math.min(play.scene.len, play.time(partial));
        List<Scene.Cam> keys = play.scene.cam;
        double unit = Math.max(1.0, p.getBbHeight() / 1.8);
        Vec3 feet = p.getPosition(partial);
        Vec3 target = feet.add(0, p.getBbHeight() * sample(keys, t, 4), 0);
        double theta = Math.toRadians(play.bodyYaw + sample(keys, t, 1));
        double dist = sample(keys, t, 2) * unit;
        double height = sample(keys, t, 3) * unit;
        Vec3 desired = target.add(-Math.sin(theta) * dist, height, Math.cos(theta) * dist);

        BlockHitResult hit = mc.level.clip(new ClipContext(target, desired, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, p));
        Vec3 cam = desired;
        if (hit.getType() != HitResult.Type.MISS) {
            Vec3 dir = desired.subtract(target);
            double len = Math.max(0.3, hit.getLocation().distanceTo(target) - 0.2);
            cam = target.add(dir.normalize().scale(len));
        }
        if (!movePosition(event.getCamera(), cam)) return;

        Vec3 d = target.subtract(cam);
        double horiz = Math.sqrt(d.x * d.x + d.z * d.z);
        float yaw = (float) Math.toDegrees(Mth.atan2(d.z, d.x)) - 90f;
        float pitch = (float) -Math.toDegrees(Mth.atan2(d.y, horiz));
        float roll = (float) sample(keys, t, 6);

        // tremores
        for (Scene.Shake s : play.scene.shake) {
            if (t < s.t() || t > s.t() + s.d()) continue;
            double k = (1.0 - (t - s.t()) / s.d()) * s.amp();
            yaw += (float) (Math.sin(t * 9.1 + s.t()) * k);
            pitch += (float) (Math.cos(t * 7.3 + s.t()) * k);
            roll += (float) (Math.sin(t * 5.7) * k * 0.5);
        }
        event.setYaw(yaw);
        event.setPitch(pitch);
        event.setRoll(roll);
    }

    public static void onFov(ViewportEvent.ComputeFov event) {
        ScenePlayback.Play play = ScenePlayback.local();
        if (play == null) return;
        double t = Math.min(play.scene.len, play.time((float) event.getPartialTick()));
        event.setFOV(event.getFOV() * Mth.clamp(sample(play.scene.cam, t, 5), 0.3, 1.8));
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
            KiControlMod.LOGGER.warn("Cutscene de transformacao: camera nao pode ser movida ({}).", t.toString());
            return false;
        }
    }
}
