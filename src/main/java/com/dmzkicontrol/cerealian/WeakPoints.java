package com.dmzkicontrol.cerealian;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Pontos fracos que o Olho Cereal enxerga no corpo do oponente. Mesma conta no servidor e no
 * cliente: posicoes relativas ao corpo (largura/altura da caixa e direcao do tronco).
 * A cada nivel da Mira aparece mais um ponto.
 */
public final class WeakPoints {
    /** {lado (+ = esquerda do alvo), altura (fracao), frente (+) / costas (-)} em fracoes do tamanho. */
    private static final double[][] SLOTS = {
            {0.12, 0.70, 0.52},   // 1: coracao
            {0.00, 0.90, 0.50},   // 2: cabeca
            {-0.58, 0.56, 0.05},  // 3: costelas (lado direito)
            {0.00, 0.60, -0.52},  // 4: coluna
            {0.55, 0.28, 0.20},   // 5: joelho (so com a Mira Cereal)
    };
    public static final int MAX = SLOTS.length;

    private WeakPoints() {}

    public static Vec3 position(LivingEntity e, int index, float partial) {
        double[] s = SLOTS[Math.max(0, Math.min(MAX - 1, index))];
        double w = e.getBbWidth(), h = e.getBbHeight();
        float bodyYaw = Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot);
        double rad = Math.toRadians(bodyYaw);
        // frente do corpo no Minecraft: (-sin, cos); lado esquerdo do alvo: (cos, sin)
        double fx = -Math.sin(rad), fz = Math.cos(rad);
        double lx = Math.cos(rad), lz = Math.sin(rad);
        double side = s[0] * w, fwd = s[2] * w;
        Vec3 base = e.getPosition(partial);
        return new Vec3(base.x + lx * side + fx * fwd, base.y + s[1] * h, base.z + lz * side + fz * fwd);
    }

    /** Indice do ponto ainda ativo (bit no mask) mais perto de {@code pos}, ou -1 se nenhum dentro do raio. */
    public static int nearest(LivingEntity e, int mask, Vec3 pos, double radius) {
        int best = -1;
        double bestD = radius * radius;
        for (int i = 0; i < MAX; i++) {
            if ((mask & (1 << i)) == 0) continue;
            double d = position(e, i, 1.0f).distanceToSqr(pos);
            if (d <= bestD) { bestD = d; best = i; }
        }
        return best;
    }
}
