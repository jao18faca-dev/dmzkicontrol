package com.dmzkicontrol.cerealian;

import com.dmzkicontrol.KiControlMod;
import com.dmzkicontrol.config.KiControlConfig;
import com.dmzkicontrol.control.AuraSealManager;
import com.dmzkicontrol.race.RaceInstaller;
import com.dragonminez.common.events.DMZEvent;
import com.dragonminez.common.init.entities.ki.AbstractKiProjectile;
import com.dragonminez.common.init.entities.ki.KiBlastEntity;
import com.dragonminez.common.stats.techniques.KiAttackData;
import com.dragonminez.common.network.StatsDeltaSync;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.techniques.PredefinedTechniques;
import com.dragonminez.common.stats.techniques.TechniqueData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Eventos do modulo Cerealiano (lado servidor). */
@Mod.EventBusSubscriber(modid = KiControlMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CerealianHooks {
    private static final Map<UUID, Long> LAST_DODGE = new ConcurrentHashMap<>();

    private CerealianHooks() {}

    /**
     * Instala o que faltar (raca + skill da Mira no skills.json) e, se algo mudou depois que o DMZ ja
     * leu as configs, pede um reload ao DMZ. Roda depois do reload do proprio DMZ (prioridade baixa).
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        boolean late = RaceInstaller.installLate();
        boolean needsReload = late || (RaceInstaller.installedSomething() && event.getServer().isDedicatedServer());
        if (!needsReload) return;
        try {
            com.dragonminez.common.config.ConfigManager.reload();
            KiControlMod.LOGGER.info("Conteudo do add-on instalado; configuracao do DragonMineZ recarregada.");
        } catch (Throwable t) {
            KiControlMod.LOGGER.warn("Instalei conteudo novo, mas nao consegui recarregar o DMZ ({}). Rode /dmzreload config.", t.toString());
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) return;
        if (!(event.player instanceof ServerPlayer sp)) return;
        CerealianEye.tick(sp);
        if (sp.tickCount % 40 == 0) grantRaceKit(sp);
        if (sp.tickCount % 2 == 0 && CerealianEye.isCerealian(sp)) {
            for (AbstractKiProjectile p : sp.level().getEntitiesOfClass(AbstractKiProjectile.class, sp.getBoundingBox().inflate(6),
                    e -> e.getOwner() == sp && KiArrowTechnique.ID.equals(e.getTechniqueId()))) {
                hideCharge(p);
            }
        }
    }

    /** Todo Cerealiano tem a Mira nivel 1; no nivel configurado ganha a Flecha de Ki. */
    private static void grantRaceKit(ServerPlayer sp) {
        if (!CerealianEye.isCerealian(sp)) return;
        StatsData data = AuraSealManager.stats(sp);
        if (data == null) return;
        boolean changed = false;
        // tira as orelhas de Glind que ficaram salvas em personagens criados na versao anterior
        String bone = data.getCharacter().getActiveHeadBone();
        if (bone != null && bone.contains("ears") && !headBoneAllowed(data.getCharacter().getRaceName(), bone)) {
            data.getCharacter().setActiveHeadBone("hair");
            StatsDeltaSync.markDirty(sp);
        }
        if (data.getSkills().getSkillLevel(CerealianEye.SKILL) <= 0) {
            data.getSkills().setSkillLevel(CerealianEye.SKILL, 1);
            changed = true;
            sp.displayClientMessage(Component.translatable("message.dmzkicontrol.eye_learned").withStyle(ChatFormatting.AQUA), false);
        }
        int lvl = data.getSkills().getSkillLevel(CerealianEye.SKILL);
        var techniques = data.getTechniques();
        TechniqueData owned = techniques.getUnlockedTechniques().get(KiArrowTechnique.ID);
        if (owned instanceof KiAttackData k && k.getKiType() != KiAttackData.KiType.WAVE) {
            TechniqueData fresh = PredefinedTechniques.copyOf(KiArrowTechnique.ID);
            if (fresh != null) { techniques.unlockTechnique(fresh); changed = true; }
        }
        if (lvl >= KiControlConfig.get().cerealian.kiArrowUnlockLevel
                && PredefinedTechniques.isPredefinedTechniqueId(KiArrowTechnique.ID)
                && !techniques.getUnlockedTechniques().containsKey(KiArrowTechnique.ID)) {
            TechniqueData copy = PredefinedTechniques.copyOf(KiArrowTechnique.ID);
            if (copy != null) {
                techniques.unlockTechnique(copy);
                changed = true;
                sp.displayClientMessage(Component.translatable("message.dmzkicontrol.ki_arrow_learned").withStyle(ChatFormatting.GOLD), false);
                sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7f, 1.4f);
            }
        }
        if (changed) StatsDeltaSync.markDirty(sp, StatsDeltaSync.TECHNIQUES);
    }

    /**
     * Cerealianos nao atiram bolas de ki: o disparo basico vira um tiro fino e preciso.
     * A "onda" que o DMZ cria para carregar o Arco de Ki fica invisivel (o add-on desenha o arco).
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof AbstractKiProjectile proj)) return;
        if (!(proj.getOwner() instanceof ServerPlayer sp) || !CerealianEye.isCerealian(sp)) return;
        String tech = proj.getTechniqueId();
        if (KiArrowTechnique.ID.equals(tech)) {
            hideCharge(proj);
            return;
        }
        boolean basic = proj instanceof KiBlastEntity && (tech == null || tech.isEmpty()) && proj.getSize() <= 0.6f;
        if (!basic || !KiControlConfig.get().cerealian.preciseKiShot) return;

        event.setCanceled(true);
        // sempre da mao direita, na ponta do dedo (a animacao do Cerealiano aponta com o braco direito)
        Vec3 right = Vec3.directionFromRotation(0.0F, sp.getYRot() + 90.0F);
        Vec3 hand = new Vec3(sp.getX(), sp.getEyeY() - 0.42, sp.getZ()).add(right.scale(0.36)).add(sp.getViewVector(1f).scale(0.75));
        CerealBeamEntity beam = CerealBeamEntity.fire(sp, hand, proj.getKiDamage(), kiColor(sp, proj.getColor()));
        event.getLevel().addFreshEntity(beam);
        com.dmzkicontrol.network.KiControlNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> sp),
                new com.dmzkicontrol.network.SealAnimS2C(sp.getId(), com.dmzkicontrol.network.SealAnimS2C.LASER));
        try {
            event.getLevel().playSound(null, sp.getX(), sp.getY(), sp.getZ(), com.dragonminez.common.init.MainSounds.KIBLAST_ATTACK.get(), SoundSource.PLAYERS, 0.9f, 1.8f);
        } catch (Throwable t) {
            event.getLevel().playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 0.6f, 1.9f);
        }
    }

    /** A config da raca ainda permite essa combinacao de partes da cabeca? (o dono pode ter readicionado orelhas) */
    private static boolean headBoneAllowed(String race, String bone) {
        try {
            var cfg = com.dragonminez.common.config.ConfigManager.getRaceCharacter(race.toLowerCase(java.util.Locale.ROOT));
            if (cfg == null || cfg.getHeadBones() == null) return false;
            java.util.List<String> allowed = java.util.Arrays.asList(cfg.getHeadBones());
            for (String token : bone.split("\\+")) if (!token.isEmpty() && !allowed.contains(token)) return false;
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Esconde a bola de carga do DMZ: o Arco de Ki desenha o proprio arco no lugar. */
    private static void hideCharge(AbstractKiProjectile p) {
        if (p.getSize() > 0.01f) p.setSize(0.001f);
        if (p instanceof com.dragonminez.common.init.entities.ki.KiWaveEntity w && w.getCastSize() > 0.01f) w.setCastSize(0.0001f);
    }

    /** Cor do ki do usuario (a cor da aura atual, que muda com a forma). */
    public static int kiColor(ServerPlayer sp, int fallback) {
        StatsData data = AuraSealManager.stats(sp);
        if (data == null) return fallback;
        String hex = data.getCharacter().getActiveAuraColor();
        if (hex == null || hex.isEmpty()) return fallback;
        try {
            return Integer.parseInt(hex.startsWith("#") ? hex.substring(1) : hex, 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** Soltou a corda do Arco de Ki: troca a onda carregada do DMZ pela flecha de luz. */
    @SubscribeEvent
    public static void onKiFire(DMZEvent.KiAttackFireEvent event) {
        if (event.getKiAttack() == null || !KiArrowTechnique.ID.equals(event.getKiAttack().getId())) return;
        if (!(event.getPlayer() instanceof ServerPlayer sp)) return;
        float damage = 0f;
        for (AbstractKiProjectile p : sp.level().getEntitiesOfClass(AbstractKiProjectile.class, sp.getBoundingBox().inflate(12),
                e -> e.getOwner() == sp && KiArrowTechnique.ID.equals(e.getTechniqueId()))) {
            damage = Math.max(damage, p.getKiDamage());
            p.discard();
        }
        float charge = event.getChargeMultiplier();
        if (damage <= 0f) {
            damage = (float) (event.getStatsData().getKiDamage() * event.getKiAttack().getDamageMultiplier() * Math.max(0.1f, charge));
        }
        int pierce = 1 + Math.min(3, (int) (charge * 2.0f));
        Vec3 from = sp.getEyePosition().add(sp.getViewVector(1f).scale(0.6)).add(0, -0.15, 0);
        CerealShotEntity arrow = CerealShotEntity.create(sp, from, damage, kiColor(sp, event.getKiAttack().getColorExterior()), true, pierce, charge);
        sp.level().addFreshEntity(arrow);
        sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 1.0f, 0.6f);
        sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.5f, 2.0f);
        // devolve a pose ao DMZ depois da animacao de soltar a corda
        com.dmzkicontrol.network.KiControlNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> sp),
                new com.dmzkicontrol.network.SealAnimS2C(sp.getId(), com.dmzkicontrol.network.SealAnimS2C.KIBOW_RELEASE));
    }

    /** Ponto fraco: golpe corpo a corpo/strike no alvo marcado. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onDamageModify(DMZEvent.DamageModifyEvent event) {
        if (!(event.getAttacker() instanceof ServerPlayer sp) || event.getVictim() == null) return;
        double[] bonus = CerealianEye.consumeWeakPoint(sp, event.getVictim());
        if (bonus == null) return;
        event.setAmount(event.getAmount() * bonus[0]);
        event.setDefensePenetration(Math.min(1.0, event.getDefensePenetration() + bonus[1]));
    }

    /** Passiva "Olhar Preciso": critico extra por nivel da Mira. */
    @SubscribeEvent
    public static void onCrit(DMZEvent.CritChanceEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer sp) || !CerealianEye.isCerealian(sp)) return;
        double extra = KiControlConfig.get().cerealian.critChancePerLevel * CerealianEye.eyeLevel(sp);
        event.setChance(Math.min(1.0, event.getChance() + extra));
    }

    /** Atirador: projeteis de longe causam mais dano; tiros de outros mods/DMZ tambem podem acertar ponto fraco. */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        Entity direct = event.getSource().getDirectEntity();
        if (!(event.getSource().getEntity() instanceof ServerPlayer shooter) || direct == null || direct == shooter) return;
        double mul = CerealianEye.sniperMultiplier(shooter, event.getEntity());
        if (!(direct instanceof CerealShotEntity) && !(direct instanceof CerealBeamEntity)) {
            mul *= CerealianEye.preciseHitMultiplier(shooter, event.getEntity(), direct.position());
        }
        if (mul > 1.0) event.setAmount((float) (event.getAmount() * mul));
    }

    /** Passiva "Leitura de Trajetoria": o olho le o ki do projetil e o Cerealiano desvia. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttacked(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        Entity direct = event.getSource().getDirectEntity();
        if (direct == null || direct == event.getSource().getEntity()) return; // so projeteis
        if (!CerealianEye.isCerealian(sp)) return;
        KiControlConfig.Cerealian c = KiControlConfig.get().cerealian;
        long now = sp.level().getGameTime();
        Long last = LAST_DODGE.get(sp.getUUID());
        if (last != null && now - last < c.dodgeCooldownSeconds * 20) return;
        double chance = c.dodgeChanceBase + c.dodgeChancePerLevel * CerealianEye.eyeLevel(sp);
        if (sp.getRandom().nextDouble() >= chance) return;
        LAST_DODGE.put(sp.getUUID(), now);
        event.setCanceled(true);
        if (sp.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.CLOUD, sp.getX(), sp.getY() + 1.0, sp.getZ(), 10, 0.3, 0.5, 0.3, 0.02);
            sl.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8f, 1.8f);
        }
        sp.displayClientMessage(Component.translatable("message.dmzkicontrol.eye_dodge").withStyle(ChatFormatting.AQUA), true);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        CerealianEye.forget(event.getEntity().getUUID());
        LAST_DODGE.remove(event.getEntity().getUUID());
    }
}
