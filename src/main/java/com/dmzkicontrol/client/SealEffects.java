package com.dmzkicontrol.client;

import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Particulas do selo (so visual, lado do cliente). Usa a cor da aura da forma atual do DMZ. */
public final class SealEffects {

    private SealEffects() {}

    /** Cor da aura do jogador (0xRRGGBB), lida dos dados do DMZ. */
    public static int auraColor(Player p) {
        try {
            StatsData data = StatsProvider.get(StatsCapability.INSTANCE, p).resolve().orElse(null);
            if (data != null) {
                String hex = data.getCharacter().getActiveAuraColor();
                if (hex != null && !hex.isEmpty()) return Integer.parseInt(hex.startsWith("#") ? hex.substring(1) : hex, 16) & 0xFFFFFF;
            }
        } catch (RuntimeException ignored) { }
        return 0x7FFFFF;
    }

    private static Vector3f rgb(int c) {
        return new Vector3f(((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f);
    }

    public static Vec3 chest(Player p) {
        return p.position().add(0, p.getBbHeight() * 0.68, 0);
    }

    /** Aura sendo puxada para dentro do peito. */
    public static void converge(Player p, int count, double radius) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        RandomSource r = p.getRandom();
        Vec3 c = chest(p);
        DustParticleOptions dust = new DustParticleOptions(rgb(auraColor(p)), 1.1f);
        for (int i = 0; i < count; i++) {
            double th = r.nextDouble() * Math.PI * 2, ph = (r.nextDouble() - 0.5) * Math.PI;
            double rr = radius * (0.7 + r.nextDouble() * 0.5);
            Vec3 from = c.add(Math.cos(th) * Math.cos(ph) * rr, Math.sin(ph) * rr * 0.9, Math.sin(th) * Math.cos(ph) * rr);
            Vec3 v = c.subtract(from).scale(0.09);
            level.addParticle(ParticleTypes.END_ROD, from.x, from.y, from.z, v.x, v.y, v.z);
            level.addParticle(dust, from.x, from.y, from.z, 0, 0, 0);
        }
    }

    /** O momento em que o ki se fecha dentro do corpo. */
    public static void pulse(Player p) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        Vec3 c = chest(p);
        level.addParticle(ParticleTypes.FLASH, c.x, c.y, c.z, 0, 0, 0);
        DustParticleOptions dust = new DustParticleOptions(rgb(auraColor(p)), 1.4f);
        for (int i = 0; i < 36; i++) {
            double a = i / 36.0 * Math.PI * 2;
            double dx = Math.cos(a), dz = Math.sin(a);
            level.addParticle(ParticleTypes.END_ROD, c.x, p.getY() + 0.1, c.z, dx * 0.35, 0.01, dz * 0.35);
            level.addParticle(dust, c.x + dx * 0.4, c.y, c.z + dz * 0.4, 0, 0, 0);
        }
    }

    /** Aura voltando para fora (liberar) ou explodindo (perdeu o controle). */
    public static void burst(Player p, boolean violent) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        RandomSource r = p.getRandom();
        Vec3 c = chest(p);
        DustParticleOptions dust = new DustParticleOptions(rgb(violent ? 0xFF5040 : auraColor(p)), 1.6f);
        int n = violent ? 70 : 35;
        double speed = violent ? 0.6 : 0.3;
        for (int i = 0; i < n; i++) {
            Vec3 dir = new Vec3(r.nextGaussian(), r.nextGaussian() * 0.6, r.nextGaussian()).normalize();
            level.addParticle(violent ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.END_ROD,
                    c.x, c.y, c.z, dir.x * speed, dir.y * speed, dir.z * speed);
            level.addParticle(dust, c.x + dir.x * 0.5, c.y + dir.y * 0.5, c.z + dir.z * 0.5, 0, 0, 0);
        }
        if (violent) level.addParticle(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 0, 0, 0);
    }
}
