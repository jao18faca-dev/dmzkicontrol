package com.dmzkicontrol.client;

import com.dmzkicontrol.KiControlMod;
import com.dmzkicontrol.network.SealAnimS2C;
import com.dragonminez.client.animation.IPlayerAnimatable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.core.animation.Animation;
import software.bernie.geckolib.loading.object.BakedAnimations;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Animacoes do selo no modelo GeckoLib do DragonMineZ.
 *
 * <p>O DMZ toca animacoes de ki pelo nome ({@link IPlayerAnimatable#dragonminez$playKiAnimation}),
 * procurando nos arquivos de animacao dele. Nossas animacoes ficam em
 * {@code assets/dmzkicontrol/animations/seal.animation.json} (o GeckoLib carrega todas) e, antes de
 * tocar, copiamos as entradas para o conjunto de animacoes do jogador do DMZ no cache do GeckoLib.
 * Nada do DMZ e sobrescrito; se o DMZ mudar a estrutura, as animacoes so deixam de tocar.
 */
public final class SealAnimations {
    public static final String QUICK = "dmzkicontrol.seal_quick";
    public static final String CUTSCENE = "dmzkicontrol.seal_cutscene";
    public static final String RELEASE = "dmzkicontrol.seal_release";
    public static final String BREAK = "dmzkicontrol.seal_break";

    private static final ResourceLocation OURS = new ResourceLocation(KiControlMod.MODID, "animations/seal.animation.json");
    private static final ResourceLocation DMZ_PRIMARY = new ResourceLocation("dragonminez", "animations/entity/races/movement.animation.json");

    /** entityId -> [tick de parada, nome]. */
    private static final Map<Integer, Object[]> STOPS = new HashMap<>();
    /** Jogadores em que a pose de tiro do DMZ fica desligada por alguns ticks (laser cerealiano). */
    private static final Map<Integer, Long> NO_SHOOT_POSE = new HashMap<>();
    private static final String LASER_ANIM = "dmzkicontrol.laser_fire";
    /** entityId -> [tipo, tick de inicio] das particulas em andamento. */
    private static final Map<Integer, long[]> FX = new HashMap<>();
    /** Jogadores com a aura selada (todas as auras do DMZ deles ficam escondidas). */
    private static final java.util.Set<Integer> SEALED = new java.util.HashSet<>();
    /** entityId -> {auraActive, permanentAura, forcedAura} originais durante o frame. */
    private static final Map<Integer, boolean[]> SAVED = new HashMap<>();
    private static boolean warned;

    private SealAnimations() {}

    /** Garante que nossas animacoes estao no conjunto do DMZ (o cache e recriado a cada F3+T). */
    public static boolean ensureInjected() {
        try {
            Map<ResourceLocation, BakedAnimations> cache = GeckoLibCache.getBakedAnimations();
            BakedAnimations dmz = cache.get(DMZ_PRIMARY);
            BakedAnimations ours = cache.get(OURS);
            if (dmz == null || ours == null) return warnOnce("arquivo de animacao nao encontrado");
            boolean missing = false;
            for (String key : ours.animations().keySet()) {
                if (dmz.getAnimation(key) != ours.getAnimation(key)) { missing = true; break; }
            }
            if (!missing) return true;
            Map<String, Animation> merged = new HashMap<>(dmz.animations());
            merged.putAll(ours.animations());
            cache.put(DMZ_PRIMARY, new BakedAnimations(merged));
            return true;
        } catch (Throwable t) {
            return warnOnce(t.toString());
        }
    }

    private static boolean warnOnce(String why) {
        if (!warned) {
            warned = true;
            KiControlMod.LOGGER.warn("Animacoes do selo indisponiveis ({}); o selo funciona sem a pose.", why);
        }
        return false;
    }

    public static void onPacket(int entityId, byte type) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (type == SealAnimS2C.SEALED_ON) { SEALED.add(entityId); return; }
        if (type == SealAnimS2C.SEALED_OFF) { SEALED.remove(entityId); return; }
        Entity e = mc.level.getEntity(entityId);
        if (!(e instanceof AbstractClientPlayer player)) return;
        boolean self = mc.player != null && player.getId() == mc.player.getId();

        switch (type) {
            case SealAnimS2C.QUICK -> {
                play(player, QUICK, 27);
                SealFxRenderer.start(player, type);
                FX.put(player.getId(), new long[]{SealAnimS2C.QUICK, mc.level.getGameTime()});
            }
            case SealAnimS2C.CUTSCENE -> {
                play(player, CUTSCENE, CutsceneController.DURATION);
                SealFxRenderer.start(player, type);
                FX.put(player.getId(), new long[]{SealAnimS2C.CUTSCENE, mc.level.getGameTime()});
                if (self) CutsceneController.start();
            }
            case SealAnimS2C.RELEASE -> {
                play(player, RELEASE, 14);
                SealFxRenderer.start(player, type);
                SealEffects.burst(player, false);
            }
            case SealAnimS2C.BREAK -> {
                play(player, BREAK, 18);
                SealFxRenderer.start(player, type);
                if (self) CutsceneController.stop();
                SealEffects.burst(player, true);
            }
            case SealAnimS2C.LASER -> {
                // troca a pose instantanea do ki blast do DMZ por um "dedo apontado" suave
                NO_SHOOT_POSE.put(player.getId(), mc.level.getGameTime() + 16);
                if (((Object) player) instanceof IPlayerAnimatable anim) {
                    try { anim.dragonminez$setShootingKi(false); } catch (Throwable ignored) { }
                }
                ArmAnimDriver.play(player, ArmAnimDriver.LASER, 14);
            }
            case SealAnimS2C.KIBOW_RELEASE -> {
                // solta a corda: animacao de disparo (o controlador de ki do DMZ encerra sozinho os "_fire")
                ArmAnimDriver.play(player, ArmAnimDriver.BOW_FIRE, 14);
                STOPS.put(player.getId(), new Object[]{mc.level.getGameTime() + 16, "ki.kibow"});
                KiBowRenderer.onRelease(player);
            }
            case SealAnimS2C.CANCEL -> {
                stop(player);
                FX.remove(player.getId());
                SealFxRenderer.cancel(player);
                if (self) CutsceneController.stop();
            }
            default -> { }
        }
    }

    public static void play(AbstractClientPlayer player, String name, int ticks) {
        if (!(((Object) player) instanceof IPlayerAnimatable anim) || !ensureInjected()) return;
        try {
            anim.dragonminez$playKiAnimation(name, false);
            STOPS.put(player.getId(), new Object[]{player.level().getGameTime() + ticks, name});
        } catch (Throwable t) {
            warnOnce(t.toString());
        }
    }

    public static void stop(AbstractClientPlayer player) {
        if (!(((Object) player) instanceof IPlayerAnimatable anim)) return;
        try {
            if (anim.dragonminez$getCurrentKiAnimation().startsWith("dmzkicontrol.")) anim.dragonminez$stopKiAnimation();
        } catch (Throwable ignored) { }
        STOPS.remove(player.getId());
    }

    /** Fim de cada animacao: devolve o controle das poses ao DMZ (se ninguem trocou a animacao antes). */
    public static void clientTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { STOPS.clear(); FX.clear(); SEALED.clear(); return; }
        long now = mc.level.getGameTime();
        tickEffects(mc, now);
        suppressShootPose();
        if (now % 7 == 0) {
            for (int id : SEALED) {
                if (mc.level.getEntity(id) instanceof AbstractClientPlayer p
                        && !(p == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson())) SealEffects.ambient(p);
            }
        }
        Iterator<Map.Entry<Integer, Object[]>> it = STOPS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Object[]> en = it.next();
            if (now < (long) en.getValue()[0]) continue;
            it.remove();
            Entity e = mc.level.getEntity(en.getKey());
            if (((Object) e) instanceof IPlayerAnimatable anim) {
                try {
                    String current = anim.dragonminez$getCurrentKiAnimation();
                    String name = (String) en.getValue()[1];
                    boolean match = name.equals("ki.kibow") ? (current.startsWith("ki.kibow") || current.startsWith("dmzkicontrol.kibow")) : name.equals(current);
                    if (match) anim.dragonminez$stopKiAnimation();
                } catch (Throwable ignored) { }
            }
        }
    }

    /** Aura sendo puxada para o peito e o pulso final, para quem estiver vendo. */
    private static void tickEffects(Minecraft mc, long now) {
        Iterator<Map.Entry<Integer, long[]>> it = FX.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, long[]> en = it.next();
            Entity e = mc.level.getEntity(en.getKey());
            if (!(e instanceof AbstractClientPlayer p)) { it.remove(); continue; }
            long t = now - en.getValue()[1];
            boolean cutscene = en.getValue()[0] == SealAnimS2C.CUTSCENE;
            int pulseAt = cutscene ? CutsceneController.PULSE_TICK : 8;
            if (t < pulseAt) {
                double k = (double) t / pulseAt;
                SealEffects.converge(p, cutscene ? 2 + (int) (k * 4) : 4, cutscene ? 2.6 - k * 1.4 : 1.4);
            } else {
                if (t <= pulseAt + 2) SealEffects.pulse(p);
                it.remove();
            }
        }
    }

    public static void clearSealed() {
        SEALED.clear();
        SealFxRenderer.clear();
        NO_SHOOT_POSE.clear();
        ArmAnimDriver.clear();
        SAVED.clear();
    }

    /**
     * O ki fica dentro do corpo: so durante o desenho do frame, desligamos na copia LOCAL dos dados
     * do DMZ as flags que fazem a aura aparecer, e devolvemos os valores no fim do frame. O servidor
     * e a logica do DMZ nunca veem essa mudanca.
     */
    /** O pacote de pose de tiro do DMZ chega depois do nosso: desliga de novo a cada tick/frame. */
    public static void suppressShootPose() {
        if (NO_SHOOT_POSE.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { NO_SHOOT_POSE.clear(); return; }
        long now = mc.level.getGameTime();
        NO_SHOOT_POSE.entrySet().removeIf(en -> {
            if (now > en.getValue()) return true;
            if (((Object) mc.level.getEntity(en.getKey())) instanceof IPlayerAnimatable anim) {
                try { anim.dragonminez$setShootingKi(false); } catch (Throwable ignored) { }
            }
            return false;
        });
    }

    public static void hideAurasForFrame() {
        if (SEALED.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        for (int id : SEALED) {
            Entity e = mc.level.getEntity(id);
            if (e == null) continue;
            com.dragonminez.common.stats.StatsProvider.get(com.dragonminez.common.stats.StatsCapability.INSTANCE, e).ifPresent(data -> {
                var st = data.getStatus();
                SAVED.put(id, new boolean[]{st.isAuraActive(), st.isPermanentAura()});
                st.setAuraActive(false);
                st.setPermanentAura(false);
            });
        }
    }

    public static void restoreAurasAfterFrame() {
        if (SAVED.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            for (Map.Entry<Integer, boolean[]> en : SAVED.entrySet()) {
                Entity e = mc.level.getEntity(en.getKey());
                if (e == null) continue;
                boolean[] v = en.getValue();
                com.dragonminez.common.stats.StatsProvider.get(com.dragonminez.common.stats.StatsCapability.INSTANCE, e).ifPresent(data -> {
                    data.getStatus().setAuraActive(v[0]);
                    data.getStatus().setPermanentAura(v[1]);
                });
            }
        }
        SAVED.clear();
    }
}
