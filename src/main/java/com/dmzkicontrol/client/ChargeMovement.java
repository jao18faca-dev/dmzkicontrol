package com.dmzkicontrol.client;

import com.dmzkicontrol.config.KiControlConfig;
import com.dragonminez.client.events.FlySkillEvent;
import com.dragonminez.client.systems.worldboss.ClientWorldBossState;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.character.Status;
import com.dragonminez.common.stats.techniques.TechniqueDispatcher;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.event.MovementInputUpdateEvent;

/**
 * O DMZ zera os comandos de movimento enquanto voce carrega o ki. Aqui, rodando depois dele, os comandos
 * voltam quando o UNICO motivo do bloqueio e carregar o ki: atordoado, golpe de ki que prende, choque de
 * golpes, chefe conjurando, pose do Potara e trava da esquiva continuam travando como no DMZ.
 */
public final class ChargeMovement {
    private ChargeMovement() {}

    public static void onMovement(MovementInputUpdateEvent event) {
        KiControlConfig.Movement cfg = KiControlConfig.get().movement;
        if (!cfg.moveWhileChargingKi || CutsceneController.isActive()) return;
        Player p = event.getEntity();
        Minecraft mc = Minecraft.getInstance();
        if (p != mc.player || mc.screen != null) return;
        StatsData data = StatsProvider.get(StatsCapability.INSTANCE, p).resolve().orElse(null);
        if (data == null) return;
        try {
            Status st = data.getStatus();
            if (!st.isChargingKi()) return;
            if (st.isStunned() || st.isActionCharging() || st.getPotaraPoseTimer() > 0 || st.getEvasionLockTicks() > 0) return;
            if (TechniqueDispatcher.isMovementRestrictedKiAttack(p, data)) return;
            if (FlySkillEvent.isStrikeClashHeld() || ClientWorldBossState.Player.isCasting()) return;
        } catch (Throwable t) {
            return; // DMZ mudou: deixa como ele quer
        }

        Options o = mc.options;
        Input in = event.getInput();
        in.up = o.keyUp.isDown();
        in.down = o.keyDown.isDown();
        in.left = o.keyLeft.isDown();
        in.right = o.keyRight.isDown();
        in.jumping = o.keyJump.isDown();
        in.shiftKeyDown = o.keyShift.isDown();
        float speed = (float) Math.max(0.0, cfg.chargingMoveSpeed);
        float forward = (in.up == in.down ? 0f : (in.up ? 1f : -1f)) * speed;
        float strafe = (in.left == in.right ? 0f : (in.left ? 1f : -1f)) * speed;
        if (p.isMovingSlowly()) {
            forward *= 0.3f;
            strafe *= 0.3f;
        }
        in.forwardImpulse = forward;
        in.leftImpulse = strafe;
    }
}
