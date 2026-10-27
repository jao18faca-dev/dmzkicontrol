package com.dmzkicontrol.client;

import com.dmzkicontrol.KiControlMod;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ViewportEvent;

import java.lang.reflect.Method;

/**
 * Cutscene do selo (so no seu cliente).
 *
 * <p>A camera fica em terceira pessoa e, no {@link ViewportEvent.ComputeCameraAngles} (depois do
 * {@code Camera.setup} e antes da renderizacao), movemos a posicao e o angulo dela por um caminho
 * suave em volta do personagem: de tras, contornando ate um close no peito no momento do selo, e
 * abrindo de novo. O mod do DMZ continua controlando o resto da camera normalmente.
 *
 * <p>Linha do tempo (ticks): 0-40 a aura e puxada para dentro, 44 pulso (o selo vale no servidor),
 * 46-70 titulo. Apertar a tecla do selo pula a cena.
 */
public final class CutsceneController {
    public static final int DURATION = 70;
    public static final int PULSE_TICK = 44;

    /** {tick, angulo em volta (0 = de frente), distancia, altura}. */
    private static final double[][] KEYS = {
            {0, 200, 4.6, 1.30},
            {14, 125, 3.7, 0.95},
            {28, 38, 2.5, 0.45},
            {40, 12, 1.6, 0.22},
            {46, 4, 1.35, 0.18},
            {56, -12, 2.3, 0.35},
            {70, -30, 3.9, 0.70},
    };

    private static boolean active;
    private static int ticks;
    private static CameraType previousCamera;
    private static float baseYaw;
    private static float lockedYaw, lockedPitch;
    private static boolean playedThisSession;
    private static Method setPosition;
    private static boolean reflectionFailed;

    private CutsceneController() {}

    public static boolean isActive() {
        return active;
    }

    public static int ticks() {
        return ticks;
    }

    public static boolean playedThisSession() {
        return playedThisSession;
    }

    public static void resetSession() {
        stop();
        playedThisSession = false;
    }

    public static void start() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || active) return;
        active = true;
        ticks = 0;
        playedThisSession = true;
        previousCamera = mc.options.getCameraType();
        mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        baseYaw = p.yBodyRot;
        lockedYaw = p.getYRot();
        lockedPitch = p.getXRot();
        p.playSound(SoundEvents.BEACON_POWER_SELECT, 0.6f, 0.6f);
    }

    public static void stop() {
        if (!active) return;
        active = false;
        Minecraft mc = Minecraft.getInstance();
        if (previousCamera != null) mc.options.setCameraType(previousCamera);
        previousCamera = null;
    }

    public static void clientTick() {
        if (!active) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || !p.isAlive() || mc.level == null) { stop(); return; }
        if (mc.isPaused()) return;
        ticks++;
        if (ticks == PULSE_TICK) p.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 0.5f);
        if (ticks >= DURATION) stop();
    }

    /** Segura a direcao do personagem durante a cena (o mouse nao gira o boneco). */
    public static void lockRotation() {
        if (!active) return;
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return;
        p.setYRot(lockedYaw);
        p.yRotO = lockedYaw;
        p.setXRot(lockedPitch);
        p.xRotO = lockedPitch;
    }

    // ------------------------------------------------------------------ camera

    private static double time(float partial) {
        return Math.min(DURATION, ticks + partial);
    }

    /** Catmull-Rom entre as chaves para a coluna {@code col}. */
    private static double sample(double t, int col) {
        int i = 0;
        while (i < KEYS.length - 2 && t > KEYS[i + 1][0]) i++;
        double[] p0 = KEYS[Math.max(0, i - 1)], p1 = KEYS[i], p2 = KEYS[i + 1], p3 = KEYS[Math.min(KEYS.length - 1, i + 2)];
        double u = Mth.clamp((t - p1[0]) / Math.max(1e-6, p2[0] - p1[0]), 0, 1);
        double u2 = u * u, u3 = u2 * u;
        return 0.5 * ((2 * p1[col]) + (-p0[col] + p2[col]) * u
                + (2 * p0[col] - 5 * p1[col] + 4 * p2[col] - p3[col]) * u2
                + (-p0[col] + 3 * p1[col] - 3 * p2[col] + p3[col]) * u3);
    }

    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (!active) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return;
        float partial = (float) event.getPartialTick();
        double t = time(partial);

        Vec3 feet = p.getPosition(partial);
        double eye = p.getEyeHeight();
        double targetH = eye * (t < 46 ? 0.74 : Mth.lerp(Mth.clamp((t - 46) / 12.0, 0, 1), 0.74, 0.86));
        Vec3 target = feet.add(0, targetH, 0);

        double theta = Math.toRadians(baseYaw + sample(t, 1));
        double dist = sample(t, 2);
        double height = sample(t, 3);
        Vec3 desired = target.add(-Math.sin(theta) * dist, height, Math.cos(theta) * dist);

        // nao atravessar paredes
        BlockHitResult hit = mc.level.clip(new ClipContext(target, desired, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, p));
        Vec3 cam = desired;
        if (hit.getType() != HitResult.Type.MISS) {
            Vec3 dir = desired.subtract(target);
            double len = Math.max(0.3, hit.getLocation().distanceTo(target) - 0.2);
            cam = target.add(dir.normalize().scale(len));
        }

        if (!movePosition(event.getCamera(), cam)) return; // sem posicao livre, mantem a camera padrao

        Vec3 d = target.subtract(cam);
        double horiz = Math.sqrt(d.x * d.x + d.z * d.z);
        float yaw = (float) (Math.toDegrees(Mth.atan2(d.z, d.x))) - 90f;
        float pitch = (float) (-Math.toDegrees(Mth.atan2(d.y, horiz)));

        // tremor curto no pulso
        if (t >= PULSE_TICK && t < PULSE_TICK + 8) {
            float k = (float) (1.0 - (t - PULSE_TICK) / 8.0) * 1.6f;
            yaw += (float) Math.sin(t * 9.1) * k;
            pitch += (float) Math.cos(t * 7.3) * k;
        }
        float roll = (float) (Math.sin(Mth.clamp((t - 30) / 26.0, 0, 1) * Math.PI) * 3.0);

        event.setYaw(yaw);
        event.setPitch(pitch);
        event.setRoll(roll);
    }

    public static void onFov(ViewportEvent.ComputeFov event) {
        if (!active) return;
        double t = time((float) event.getPartialTick());
        double f;
        if (t < 40) f = Mth.lerp(t / 40.0, 1.0, 0.82);
        else if (t < 46) f = Mth.lerp((t - 40) / 6.0, 0.82, 1.06);
        else f = Mth.lerp(Mth.clamp((t - 46) / 24.0, 0, 1), 1.06, 1.0);
        event.setFOV(event.getFOV() * f);
    }

    /** Camera.setPosition(Vec3) e protegido; achamos pela assinatura (funciona com qualquer mapeamento). */
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
            KiControlMod.LOGGER.warn("Cutscene: nao foi possivel mover a camera ({}); a cena roda com a camera normal.", t.toString());
            return false;
        }
    }
}
