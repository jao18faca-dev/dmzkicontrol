package com.dmzkicontrol.client.encounter;

import com.dmzkicontrol.KiControlMod;
import com.dragonminez.common.init.MainEntities;
import com.dragonminez.common.init.entities.ki.KiBlastEntity;
import com.dragonminez.common.init.entities.ki.KiWaveEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Kamehameha com o modelo do proprio DragonMineZ: um KiWaveEntity "de mentira" (so no cliente, fora do mundo)
 * configurado como o Kamehameha do DMZ e desenhado pelo renderizador dele. Nao causa dano, nao quebra blocos,
 * nao existe no servidor - e so a imagem.
 *
 * <p>O renderizador do DMZ guarda o desenho numa fila que e esvaziada depois das particulas, entao
 * desenhamos no estagio AFTER_ENTITIES (antes disso, no mesmo quadro).
 */
public final class EncBeams {
    /** Pedido de um feixe neste quadro. */
    public record Req(String key, Vec3 from, Vec3 to, float size, int core, int border, int outline) {}

    /** Pedido de uma esfera gigante (Genki Dama, Esfera da Destruicao) neste quadro. */
    public record Ball(String key, Vec3 center, float size, int type, int core, int border, int outline) {}

    /**
     * Outro ataque de ki do DMZ neste quadro: 0 = rajada (bola de ki), 1 = disco (Kienzan), 2 = laser (Raio da Morte),
     * 3 = explosao. {@code dir} e a direcao (disco e laser), {@code age} o tempo de vida (explosao cresce com ele).
     */
    public record Proj(String key, int kind, Vec3 pos, Vec3 dir, float size, int[] col, float length, int age) {}

    public static final int BLAST = 0, DISK = 1, LASER = 2, EXPLOSION = 3;
    /** Rajada comum (amarela), Kienzan (amarelo claro), Raio da Morte (rosa). */
    public static final int[] YELLOW = {0xFFFFE6, 0xFFD54A, 0xFF9A00};
    public static final int[] DISK_COL = {0xFFFFF2, 0xFFE47A, 0xFFB000};
    public static final int[] DEATH = {0xFFE8FF, 0xFF66E0, 0xC01890};

    private static final List<Proj> PROJS = new ArrayList<>();
    private static final Map<String, net.minecraft.world.entity.Entity> PROJ_PUPPETS = new HashMap<>();

    private static boolean projBroken;

    /** Os ataques de ki do DMZ (rajada, disco, laser, explosao) estao funcionando. */
    public static boolean projOk() {
        return !broken && !projBroken;
    }

    public static void proj(String key, int kind, Vec3 pos, Vec3 dir, float size, int[] col, float length, int age) {
        if (!projOk()) return;
        PROJS.add(new Proj(key, kind, pos, dir, size, col, length, age));
    }

    private static java.lang.reflect.Field LASER_YAW, LASER_PITCH;

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void laserAngles(com.dragonminez.common.init.entities.ki.KiLaserEntity e, float yaw, float pitch) {
        try {
            if (LASER_YAW == null) {
                LASER_YAW = com.dragonminez.common.init.entities.ki.KiLaserEntity.class.getDeclaredField("FIXED_YAW");
                LASER_PITCH = com.dragonminez.common.init.entities.ki.KiLaserEntity.class.getDeclaredField("FIXED_PITCH");
                LASER_YAW.setAccessible(true);
                LASER_PITCH.setAccessible(true);
            }
            e.getEntityData().set((net.minecraft.network.syncher.EntityDataAccessor) LASER_YAW.get(null), yaw);
            e.getEntityData().set((net.minecraft.network.syncher.EntityDataAccessor) LASER_PITCH.get(null), pitch);
        } catch (Throwable ignored) {
        }
    }

    private static final List<Req> FRAME = new ArrayList<>();
    private static final List<Ball> BALLS = new ArrayList<>();
    private static final Map<String, KiBlastEntity> BALL_PUPPETS = new HashMap<>();
    private static final Map<String, KiWaveEntity> PUPPETS = new HashMap<>();
    private static boolean broken;

    /** Cores do Kamehameha (azul do anime). */
    public static final int[] KAME = {0xF2FDFF, 0x7FE6FF, 0x1C7BFF};
    /** Raio/onda do Freeza e Galick Ho (roxo). */
    public static final int[] PURPLE = {0xFFF0FF, 0xE07BFF, 0x7A1CFF};
    /** Ki do Majin Boo (rosa). */
    public static final int[] PINK = {0xFFF2FA, 0xFF8AD0, 0xD81B78};
    /** Genki Dama (as cores que o proprio DMZ usa). */
    public static final int[] GENKI = {0xFFFFFF, 0xC4FFFD, 0x00F8FF};
    /** Esfera da Destruicao do Beerus (laranja quente com borda vermelha). */
    public static final int[] HAKAI = {0xFFF6D8, 0xFFB43C, 0xE0301C};
    /** Render do DMZ: 5 = Genki Dama, 2 = esfera grande. */
    public static final int GENKI_TYPE = 5, LARGE_TYPE = 2;

    private EncBeams() {}

    /** Pede um feixe para este quadro (chamado pelos efeitos antes do desenho). */
    public static void beam(String key, Vec3 from, Vec3 to, float size, int[] col) {
        if (broken) return;
        FRAME.add(new Req(key, from, to, size, col[0], col[1], col[2]));
    }

    /** Pede uma esfera gigante do DMZ neste quadro (centro no mundo). */
    public static void ball(String key, Vec3 center, float size, int type, int[] col) {
        if (broken) return;
        BALLS.add(new Ball(key, center, size, type, col[0], col[1], col[2]));
    }

    public static boolean available() {
        return !broken;
    }

    public static void clear() {
        FRAME.clear();
        PUPPETS.clear();
        BALLS.clear();
        BALL_PUPPETS.clear();
        PROJS.clear();
        PROJ_PUPPETS.clear();
    }

    /** A cada tick: o tempo das ondas anda (o shader do DMZ anima com ele). */
    public static void tick() {
        for (KiWaveEntity w : PUPPETS.values()) w.tickCount++;
        for (KiBlastEntity b : BALL_PUPPETS.values()) b.tickCount++;
        for (net.minecraft.world.entity.Entity e : PROJ_PUPPETS.values()) if (!(e instanceof com.dragonminez.common.init.entities.ki.KiExplosionVisualEntity)) e.tickCount++;
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        if (!broken) EncFx.beamPass(event.getPartialTick());
        if ((FRAME.isEmpty() && BALLS.isEmpty() && PROJS.isEmpty()) || broken) {
            FRAME.clear();
            BALLS.clear();
            PROJS.clear();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            FRAME.clear();
            BALLS.clear();
            PROJS.clear();
            return;
        }
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        float partial = event.getPartialTick();
        try {
            for (Req r : FRAME) {
                KiWaveEntity w = PUPPETS.computeIfAbsent(r.key(), k -> {
                    KiWaveEntity n = new KiWaveEntity(MainEntities.KI_WAVE.get(), mc.level);
                    n.setKiRenderType(1);
                    n.setCastWave(1);
                    return n;
                });
                Vec3 d = r.to().subtract(r.from());
                double len = d.length();
                if (len < 0.05) continue;
                float yaw = (float) (Mth.atan2(-d.x, d.z) * Mth.RAD_TO_DEG);
                float pitch = (float) (-Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG);
                w.setPos(r.from());
                w.xo = r.from().x;
                w.yo = r.from().y;
                w.zo = r.from().z;
                w.setYRot(yaw);
                w.setXRot(pitch);
                w.yRotO = yaw;
                w.xRotO = pitch;
                w.setSize(r.size());
                w.setCastSize(r.size() * 2f);
                w.setColors(r.core(), r.border(), r.outline());
                w.setFiring(true);
                w.setBeamLength((float) len);
                mc.getEntityRenderDispatcher().render(w, r.from().x - cam.x, r.from().y - cam.y, r.from().z - cam.z,
                        yaw, partial, pose, buffers, LightTexture.FULL_BRIGHT);
            }
            for (Ball b : BALLS) {
                KiBlastEntity e = BALL_PUPPETS.computeIfAbsent(b.key(), k -> new KiBlastEntity(MainEntities.KI_BLAST.get(), mc.level));
                e.setKiRenderType(b.type());
                e.setSize(b.size());
                e.setColors(b.core(), b.border(), b.outline());
                e.setFiring(false);
                // o renderizador do DMZ desenha a esfera meia altura acima dos pes da entidade
                Vec3 at = b.center().subtract(0, e.getBbHeight() / 2.0, 0);
                e.setPos(at);
                e.xo = at.x;
                e.yo = at.y;
                e.zo = at.z;
                mc.getEntityRenderDispatcher().render(e, at.x - cam.x, at.y - cam.y, at.z - cam.z,
                        0f, partial, pose, buffers, LightTexture.FULL_BRIGHT);
            }
            for (Proj q : PROJS) {
                if (projBroken) break;
                try {
                net.minecraft.world.entity.Entity e = PROJ_PUPPETS.computeIfAbsent(q.key() + "#" + q.kind(), k -> switch (q.kind()) {
                    case DISK -> new com.dragonminez.common.init.entities.ki.KiDiskEntity(MainEntities.KI_DISC.get(), mc.level);
                    case LASER -> new com.dragonminez.common.init.entities.ki.KiLaserEntity(MainEntities.KI_LASER.get(), mc.level);
                    case EXPLOSION -> new com.dragonminez.common.init.entities.ki.KiExplosionVisualEntity(MainEntities.KI_EXPLOSION_VISUAL.get(), mc.level);
                    default -> new KiBlastEntity(MainEntities.KI_BLAST.get(), mc.level);
                });
                Vec3 d = q.dir() == null || q.dir().lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : q.dir().normalize();
                float yaw = (float) (Mth.atan2(-d.x, d.z) * Mth.RAD_TO_DEG);
                float pitch = (float) (-Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG);
                Vec3 at = q.pos();
                if (e instanceof com.dragonminez.common.init.entities.ki.AbstractKiProjectile kp) {
                    kp.setSize(q.size());
                    kp.setColors(q.col()[0], q.col()[1], q.col()[2]);
                }
                if (e instanceof KiBlastEntity kb) {
                    kb.setKiRenderType(0);
                    kb.setFiring(true);
                    at = at.subtract(0, kb.getBbHeight() / 2.0, 0);
                } else if (e instanceof com.dragonminez.common.init.entities.ki.KiDiskEntity kd) {
                    kd.setFiring(true);
                    kd.setCastTime(0);
                } else if (e instanceof com.dragonminez.common.init.entities.ki.KiLaserEntity kl) {
                    kl.setFiring(true);
                    kl.setBeamLength(q.length());
                    laserAngles(kl, yaw, pitch);
                } else if (e instanceof com.dragonminez.common.init.entities.ki.KiExplosionVisualEntity kx) {
                    if (kx.getMaxSize() != q.size()) kx.setupExplosion(q.col()[0], q.col()[1], q.col()[2], q.size());
                    kx.tickCount = q.age();
                }
                e.setPos(at);
                e.xo = at.x;
                e.yo = at.y;
                e.zo = at.z;
                e.setYRot(yaw);
                e.setXRot(pitch);
                e.yRotO = yaw;
                e.xRotO = pitch;
                mc.getEntityRenderDispatcher().render(e, at.x - cam.x, at.y - cam.y, at.z - cam.z, yaw, partial, pose, buffers, LightTexture.FULL_BRIGHT);
                } catch (Throwable t) {
                    projBroken = true;
                    KiControlMod.LOGGER.warn("[Lutas] ataque de ki do DMZ nao desenhou ({}); fica o efeito do add-on.", t.toString());
                }
            }
            buffers.endBatch();
        } catch (Throwable t) {
            broken = true;
            KiControlMod.LOGGER.warn("[Lutas] nao deu para usar o Kamehameha do DMZ ({}); fica o feixe do add-on.", t.toString());
        }
        FRAME.clear();
        BALLS.clear();
        PROJS.clear();
    }
}
