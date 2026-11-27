package com.dmzkicontrol.client.encounter;

import com.dmzkicontrol.KiControlMod;
import com.dragonminez.common.init.MainEntities;
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

    private static final List<Req> FRAME = new ArrayList<>();
    private static final Map<String, KiWaveEntity> PUPPETS = new HashMap<>();
    private static boolean broken;

    /** Cores do Kamehameha (azul do anime). */
    public static final int[] KAME = {0xF2FDFF, 0x7FE6FF, 0x1C7BFF};

    private EncBeams() {}

    /** Pede um feixe para este quadro (chamado pelos efeitos antes do desenho). */
    public static void beam(String key, Vec3 from, Vec3 to, float size, int[] col) {
        if (broken) return;
        FRAME.add(new Req(key, from, to, size, col[0], col[1], col[2]));
    }

    public static boolean available() {
        return !broken;
    }

    public static void clear() {
        FRAME.clear();
        PUPPETS.clear();
    }

    /** A cada tick: o tempo das ondas anda (o shader do DMZ anima com ele). */
    public static void tick() {
        for (KiWaveEntity w : PUPPETS.values()) w.tickCount++;
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        if (!broken) EncFx.beamPass(event.getPartialTick());
        if (FRAME.isEmpty() || broken) {
            FRAME.clear();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            FRAME.clear();
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
            buffers.endBatch();
        } catch (Throwable t) {
            broken = true;
            KiControlMod.LOGGER.warn("[Lutas] nao deu para usar o Kamehameha do DMZ ({}); fica o feixe do add-on.", t.toString());
        }
        FRAME.clear();
    }
}
