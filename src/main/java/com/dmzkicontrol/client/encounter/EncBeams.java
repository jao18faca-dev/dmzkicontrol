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
    }

    /** A cada tick: o tempo das ondas anda (o shader do DMZ anima com ele). */
    public static void tick() {
        for (KiWaveEntity w : PUPPETS.values()) w.tickCount++;
        for (KiBlastEntity b : BALL_PUPPETS.values()) b.tickCount++;
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        if (!broken) EncFx.beamPass(event.getPartialTick());
        if ((FRAME.isEmpty() && BALLS.isEmpty()) || broken) {
            FRAME.clear();
            BALLS.clear();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            FRAME.clear();
            BALLS.clear();
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
            buffers.endBatch();
        } catch (Throwable t) {
            broken = true;
            KiControlMod.LOGGER.warn("[Lutas] nao deu para usar o Kamehameha do DMZ ({}); fica o feixe do add-on.", t.toString());
        }
        FRAME.clear();
        BALLS.clear();
    }
}
