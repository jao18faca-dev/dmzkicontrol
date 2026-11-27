package com.dmzkicontrol.client.encounter;

import com.dmzkicontrol.KiControlMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * O espirito do Goku no Kamehameha pai e filho: o modelo do Goku do proprio DMZ aparece so no cliente, atras do
 * ombro de quem segura o feixe, virado para o Cell, com o brilho dourado em volta. Nao existe no servidor,
 * nao luta e some sozinho.
 */
public final class EncSpirit {
    private static final String TYPE = "dragonminez:saga_goku_mid_base";
    private static final int ID = -731_337;
    private static Entity puppet;
    private static int role = -1;
    private static long until;

    private EncSpirit() {}

    /** Faz o espirito aparecer atras do jogador do papel indicado, por {@code ticks}. */
    public static void show(EncClient.State s, int forRole, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || s == null) return;
        clear();
        try {
            EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(TYPE));
            if (type == null) return;
            Entity e = type.create(level);
            if (e == null) return;
            e.setId(ID);
            e.setNoGravity(true);
            e.setSilent(true);
            e.setCustomNameVisible(false);
            puppet = e;
            role = forRole;
            until = level.getGameTime() + ticks;
            place(s, 1f);
            level.putNonPlayerEntity(ID, e);
            if (e instanceof com.dragonminez.common.init.entities.sagas.DBSagasEntity saga) saga.setKiCharge(true);
        } catch (Throwable t) {
            KiControlMod.LOGGER.warn("[Lutas] espirito do Goku nao apareceu: {}", t.toString());
            puppet = null;
        }
    }

    public static void tick(EncClient.State s) {
        if (puppet == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || s == null || mc.level.getGameTime() > until) {
            clear();
            return;
        }
        place(s, 1f);
    }

    /** Fica um passo atras e ao lado do jogador, encarando o chefe. */
    private static void place(EncClient.State s, float partial) {
        Entity p = s.player(Math.max(0, role));
        if (p == null || puppet == null) return;
        Entity b = s.boss();
        Vec3 pp = p.position();
        Vec3 toBoss = b != null ? b.position().subtract(pp) : s.dir(0, 0, -1);
        Vec3 h = new Vec3(toBoss.x, 0, toBoss.z);
        h = h.lengthSqr() < 1e-4 ? new Vec3(0, 0, -1) : h.normalize();
        Vec3 side = new Vec3(-h.z, 0, h.x);
        Vec3 at = pp.add(h.scale(-0.75)).add(side.scale(-0.7)).add(0, 0.15, 0);
        float yaw = (float) (Mth.atan2(-h.x, h.z) * Mth.RAD_TO_DEG);
        puppet.moveTo(at.x, at.y, at.z, yaw, 0f);
        puppet.setYHeadRot(yaw);
        if (puppet instanceof net.minecraft.world.entity.LivingEntity le) {
            le.setYBodyRot(yaw);
            le.yBodyRotO = yaw;
            le.yHeadRotO = yaw;
        }
        puppet.setDeltaMovement(Vec3.ZERO);
    }

    /** Posicao do espirito (para o brilho dourado em volta dele). */
    public static Entity puppet() {
        return puppet;
    }

    public static void clear() {
        Minecraft mc = Minecraft.getInstance();
        if (puppet != null && mc.level != null) {
            ClientLevel level = mc.level;
            try {
                level.removeEntity(ID, Entity.RemovalReason.DISCARDED);
            } catch (Throwable ignored) {
            }
        }
        puppet = null;
        role = -1;
    }
}
