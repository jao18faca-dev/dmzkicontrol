package com.dmzkicontrol.client.frostgod;

import com.dmzkicontrol.KiControlMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

/**
 * Faiscas em volta do corpo: po dourado brilhante no Freeza Dourado; no Black, fagulhas roxas e rosa com um
 * estalo de energia de vez em quando. Saem de pontos do corpo inteiro (frente, costas, bracos), em qualquer pose.
 */
@Mod.EventBusSubscriber(modid = KiControlMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class FrostGodParticles {
    private static final DustParticleOptions GOLD = new DustParticleOptions(new Vector3f(1.0f, 0.83f, 0.28f), 0.7f);
    private static final DustParticleOptions GOLD_WHITE = new DustParticleOptions(new Vector3f(1.0f, 0.97f, 0.8f), 0.45f);
    private static final DustParticleOptions PURPLE = new DustParticleOptions(new Vector3f(0.62f, 0.18f, 0.95f), 0.7f);
    private static final DustParticleOptions PINK = new DustParticleOptions(new Vector3f(1.0f, 0.35f, 0.82f), 0.5f);

    private FrostGodParticles() {}

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || mc.isPaused()) return;
        RandomSource rand = level.random;
        for (Player p : level.players()) {
            if (p.distanceToSqr(mc.player) > 64 * 64) continue;
            FrostGodLook look = FrostGodLook.of(p);
            if (look == null) continue;
            // a propria camera em primeira pessoa: menos particulas para nao cobrir a tela
            boolean self = p == mc.player && mc.options.getCameraType().isFirstPerson();
            if (self && rand.nextInt(3) != 0) continue;
            AABB box = p.getBoundingBox();
            int count = look == FrostGodLook.GOLDEN ? 2 : 2;
            for (int i = 0; i < count; i++) {
                double x = box.minX + rand.nextDouble() * box.getXsize();
                double y = box.minY + rand.nextDouble() * box.getYsize();
                double z = box.minZ + rand.nextDouble() * box.getZsize();
                double vy = 0.01 + rand.nextDouble() * 0.03;
                if (look == FrostGodLook.GOLDEN) {
                    level.addParticle(rand.nextInt(3) == 0 ? GOLD_WHITE : GOLD, x, y, z, 0, vy, 0);
                } else {
                    level.addParticle(rand.nextInt(3) == 0 ? PINK : PURPLE, x, y, z, 0, vy, 0);
                }
            }
            if (look == FrostGodLook.BLACK && rand.nextInt(14) == 0) {
                double x = box.minX + rand.nextDouble() * box.getXsize();
                double y = box.minY + rand.nextDouble() * box.getYsize();
                double z = box.minZ + rand.nextDouble() * box.getZsize();
                level.addParticle(ParticleTypes.ELECTRIC_SPARK, x, y, z, (rand.nextDouble() - 0.5) * 0.2, 0.05, (rand.nextDouble() - 0.5) * 0.2);
            }
            if (look == FrostGodLook.GOLDEN && rand.nextInt(10) == 0) {
                double x = box.minX + rand.nextDouble() * box.getXsize();
                double y = box.minY + rand.nextDouble() * box.getYsize();
                double z = box.minZ + rand.nextDouble() * box.getZsize();
                level.addParticle(ParticleTypes.END_ROD, x, y, z, 0, 0.02, 0);
            }
        }
    }
}
