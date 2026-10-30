package com.dmzkicontrol.client;

import com.dmzkicontrol.KiControlMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

import java.util.HashMap;
import java.util.Map;

/**
 * Controlador de animacao proprio do add-on, colocado POR ULTIMO no modelo do jogador do DMZ.
 *
 * <p>O DMZ tem varios controladores (movimento, voo, pose, ki...) e o ultimo a mexer num osso vence.
 * As poses do Arco de Ki e do laser cerealiano rodam aqui, entao sempre aparecem, inclusive voando.
 * As animacoes so mexem nos bracos, entao a pose de voo/corpo do DMZ continua valendo no resto.
 */
public final class ArmAnimDriver {
    private static final String CONTROLLER = "dmzkicontrol_arms";
    public static final String BOW_CAST = "dmzkicontrol.kibow_cast";
    public static final String BOW_FIRE = "dmzkicontrol.kibow_fire";
    public static final String LASER = "dmzkicontrol.laser_fire";
    public static final String LASER_AIM = "dmzkicontrol.laser_aim";

    /** entityId -> {animacao, tick final}. */
    private static final Map<Integer, Object[]> REQUESTS = new HashMap<>();
    private static boolean warned;

    private ArmAnimDriver() {}

    /** Toca uma animacao "de uma vez" (disparo) por {@code ticks}. */
    public static void play(AbstractClientPlayer p, String anim, int ticks) {
        if (p.level() == null) return;
        REQUESTS.put(p.getId(), new Object[]{anim, p.level().getGameTime() + ticks});
        attach(p);
    }

    public static void clear() {
        REQUESTS.clear();
    }

    /** O que este jogador deve mostrar agora (null = nada, o DMZ cuida dos bracos). */
    private static String current(AbstractClientPlayer p) {
        Object[] req = REQUESTS.get(p.getId());
        if (req != null) {
            long now = p.level().getGameTime();
            if (now <= (long) req[1]) return (String) req[0];
            REQUESTS.remove(p.getId());
        }
        if (KiBowRenderer.isCharging(p)) return BOW_CAST;
        return null;
    }

    /** Garante o nosso controlador no gerenciador de animacoes do jogador (criado quando ele e renderizado). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void attach(Entity e) {
        if (!(e instanceof AbstractClientPlayer p) || !(((Object) p) instanceof GeoAnimatable geo)) return;
        try {
            AnimatableManager manager = geo.getAnimatableInstanceCache().getManagerForId(p.getId());
            if (manager == null) return;
            Map controllers = manager.getAnimationControllers();
            if (controllers.containsKey(CONTROLLER)) return;
            String[] last = {null};
            AnimationController controller = new AnimationController(geo, CONTROLLER, 3, state -> {
                String want = current(p);
                if (want == null) {
                    last[0] = null;
                    return PlayState.STOP;
                }
                if (!want.equals(last[0])) {
                    RawAnimation raw = want.endsWith("_fire") ? RawAnimation.begin().thenPlay(want) : RawAnimation.begin().thenPlayAndHold(want);
                    state.getController().setAnimation(raw);
                    state.getController().forceAnimationReset();
                    last[0] = want;
                }
                return PlayState.CONTINUE;
            });
            controllers.put(CONTROLLER, controller);
        } catch (Throwable t) {
            if (!warned) {
                warned = true;
                KiControlMod.LOGGER.warn("Nao consegui adicionar o controlador de animacao dos bracos ({}).", t.toString());
            }
        }
    }

    /** A cada tick: garante o controlador em quem esta perto e limpa pedidos vencidos. */
    public static void clientTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { REQUESTS.clear(); return; }
        SealAnimations.ensureInjected();
        for (AbstractClientPlayer p : mc.level.players()) attach(p);
        long now = mc.level.getGameTime();
        REQUESTS.entrySet().removeIf(en -> now > (long) en.getValue()[1] + 1);
    }
}
