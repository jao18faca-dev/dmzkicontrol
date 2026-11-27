package com.dmzkicontrol.story;

import com.dmzkicontrol.KiControlMod;
import com.dmzkicontrol.config.KiControlConfig;
import com.dmzkicontrol.network.KiControlNetwork;
import com.dmzkicontrol.network.PrepHudS2C;
import com.dragonminez.common.events.DMZEvent;
import com.dragonminez.common.quest.QuestObjective;
import com.dragonminez.common.quest.QuestService;
import com.dragonminez.common.quest.objectives.KillObjective;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * O prazo para treinar: depois de certos chefes, o proximo inimigo so chega quando o tempo acaba - como o
 * "1 ano ate os Saiyajins chegarem" ou os "10 dias ate os Jogos do Cell". Enquanto o prazo corre, o inimigo da
 * historia nao aparece (fica esperando); quando o tempo acaba ele chega, pronto ou nao. Quem quiser ir antes usa
 * /dmzpronto. Dentro da Sala do Tempo o relogio de fora quase para.
 */
@Mod.EventBusSubscriber(modid = KiControlMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class StoryDeadlines {
    /**
     * Um prazo: comeca quando termina uma missao contra {@code trigger}; segura os chefes {@code targets}.
     *
     * @param days      dias do Minecraft de prazo (20 min cada)
     * @param storyDays o mesmo prazo em dias da historia (o HUD mostra "faltam 7 meses")
     */
    public record Window(String id, String trigger, Set<String> targets, double days, int storyDays) {}

    private static String d(String s) {
        return "dragonminez:" + s;
    }

    public static final List<Window> WINDOWS = List.of(
            new Window("saiyans", d("saga_raditz"), Set.of(d("saga_saibaman"), d("saga_nappa"), d("saga_vegeta")), 5, 365),
            new Window("namek", d("saga_vegeta"), Set.of(d("saga_cui"), d("saga_dodoria"), d("saga_zarbon"), d("saga_zarbont1"),
                    d("saga_friezasoldier01"), d("saga_friezasoldier02"), d("saga_friezasoldier03")), 2.5, 30),
            new Window("androids", d("saga_king_cold"), Set.of(d("saga_a19"), d("saga_drgero")), 6, 1095),
            new Window("cell_games", d("saga_cell_semiperfect"), Set.of(d("saga_cell_perfect")), 3, 10),
            new Window("buu", d("saga_cell_superperfect"), Set.of(d("saga_spopovitch"), d("saga_puipui"), d("saga_yakon"),
                    d("saga_dabura"), d("saga_babidi")), 6, 2555),
            new Window("beerus", d("saga_kidbuu"), Set.of("dmzsparking:saga_beerus"), 5, 1460));

    private static final String TAG = "dmzkicontrol_prep";
    private static final long DAY = 24000L;
    private static final Map<UUID, Long> NAGGED = new HashMap<>();

    private StoryDeadlines() {}

    private static KiControlConfig.Story cfg() {
        return KiControlConfig.get().story;
    }

    private static CompoundTag data(Player p) {
        return p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getCompound(TAG);
    }

    private static void save(Player p, CompoundTag tag) {
        CompoundTag keep = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        keep.put(TAG, tag);
        p.getPersistentData().put(Player.PERSISTED_NBT_TAG, keep);
    }

    private static Window byId(String id) {
        for (Window w : WINDOWS) if (w.id.equals(id)) return w;
        return null;
    }

    /** Prazo correndo agora para este jogador (ou null). */
    public static Window active(Player p) {
        CompoundTag t = data(p);
        if (!t.contains("active") || t.getLong("left") <= 0) return null;
        return byId(t.getString("active"));
    }

    // ------------------------------------------------------------ inicio do prazo

    @SubscribeEvent
    public static void onQuestDone(DMZEvent.QuestCompletedEvent event) {
        if (!cfg().trainingDeadlines || event.getQuest() == null) return;
        for (QuestObjective o : event.getQuest().getObjectives()) {
            if (!(o instanceof KillObjective k)) continue;
            String id = k.getEntityId() == null ? "" : k.getEntityId();
            if (!id.contains(":")) id = "dragonminez:" + id;
            for (Window w : WINDOWS) {
                if (!w.trigger.equals(id)) continue;
                start(event.getPlayer(), w);
                if (event.getPartyMembers() != null) for (ServerPlayer m : event.getPartyMembers()) if (m != event.getPlayer()) start(m, w);
                return;
            }
        }
    }

    private static void start(ServerPlayer sp, Window w) {
        if (sp == null) return;
        CompoundTag t = data(sp);
        CompoundTag done = t.getCompound("done");
        if (done.getBoolean(w.id) || w.id.equals(t.getString("active"))) return;
        long total = Math.max(DAY / 4, Math.round(w.days * DAY * Math.max(0.05, cfg().deadlineScale)));
        t.putString("active", w.id);
        t.putLong("left", total);
        t.putLong("total", total);
        t.putString("held", "");
        save(sp, t);
        title(sp, Component.translatable("story.dmzkicontrol.deadline.start." + w.id).withStyle(ChatFormatting.GOLD),
                Component.translatable("story.dmzkicontrol.deadline.start_sub"));
        sp.sendSystemMessage(Component.translatable("story.dmzkicontrol.deadline.start_chat." + w.id).withStyle(ChatFormatting.YELLOW));
        sp.sendSystemMessage(Component.translatable("story.dmzkicontrol.deadline.ready_hint").withStyle(ChatFormatting.GRAY));
        sync(sp);
    }

    // ------------------------------------------------------------ o inimigo espera o prazo

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || event.loadedFromDisk() || !cfg().trainingDeadlines) return;
        Entity e = event.getEntity();
        CompoundTag pd = e.getPersistentData();
        if (!pd.contains(QuestService.QUEST_OWNER_TAG)) return;
        ResourceLocation type = ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
        if (type == null) return;
        MinecraftServer server = event.getLevel().getServer();
        if (server == null) return;
        UUID owner;
        try {
            owner = UUID.fromString(pd.getString(QuestService.QUEST_OWNER_TAG));
        } catch (IllegalArgumentException ex) {
            return;
        }
        ServerPlayer sp = server.getPlayerList().getPlayer(owner);
        if (sp == null) return;
        Window w = active(sp);
        if (w == null || !w.targets.contains(type.toString())) return;
        // ainda nao e hora: o inimigo nao chega (a missao fica guardada para quando o prazo acabar)
        event.setCanceled(true);
        CompoundTag t = data(sp);
        String key = pd.getString(QuestService.QUEST_KEY_TAG);
        if (!key.isEmpty()) {
            t.putString("held", key);
            save(sp, t);
        }
        long now = server.getTickCount();
        Long last = NAGGED.get(owner);
        if (last == null || now - last > 400) {
            NAGGED.put(owner, now);
            sp.displayClientMessage(Component.translatable("story.dmzkicontrol.deadline.not_yet." + w.id).withStyle(ChatFormatting.GOLD), true);
        }
    }

    // ------------------------------------------------------------ relogio

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null || server.getTickCount() % 20 != 0) return;
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            Window w = active(sp);
            if (w == null) continue;
            CompoundTag t = data(sp);
            long left = t.getLong("left");
            boolean chamber = cfg().timeChamberSlowsClock && sp.level().dimension().location().toString().equals("dragonminez:time_chamber");
            long step = chamber ? 1 : 20;
            long before = left;
            left -= step;
            t.putLong("left", left);
            save(sp, t);
            long total = Math.max(1, t.getLong("total"));
            // avisos quando faltar 1/4 e o ultimo dia
            if (before > total / 4 && left <= total / 4) sp.sendSystemMessage(Component.translatable("story.dmzkicontrol.deadline.quarter." + w.id).withStyle(ChatFormatting.GOLD));
            if (left <= 0) arrive(sp, w, false);
            else sync(sp);
        }
    }

    /** O prazo acabou (ou o jogador disse que esta pronto): o inimigo chega. */
    private static void arrive(ServerPlayer sp, Window w, boolean ready) {
        CompoundTag t = data(sp);
        String held = t.getString("held");
        CompoundTag done = t.getCompound("done");
        done.putBoolean(w.id, true);
        t.put("done", done);
        t.remove("active");
        t.putLong("left", 0);
        t.putString("held", "");
        save(sp, t);
        sync(sp);
        title(sp, Component.translatable(ready ? "story.dmzkicontrol.deadline.ready" : "story.dmzkicontrol.deadline.arrived." + w.id).withStyle(ChatFormatting.RED),
                Component.translatable("story.dmzkicontrol.deadline.arrived_sub"));
        sp.level().playSound(null, sp.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.6f, 1.2f);
        if (!held.isEmpty()) {
            try {
                Component r = QuestService.resummonQuest(sp, held);
                if (r != null) KiControlMod.LOGGER.debug("[Prazo] {}: {}", held, r.getString());
            } catch (Throwable ex) {
                KiControlMod.LOGGER.warn("[Prazo] nao deu para chamar o inimigo de {} ({}).", held, ex.toString());
            }
        }
    }

    // ------------------------------------------------------------ HUD

    private static void sync(ServerPlayer sp) {
        CompoundTag t = data(sp);
        Window w = active(sp);
        PrepHudS2C msg = w == null ? new PrepHudS2C("", 0, 1, 0)
                : new PrepHudS2C(w.id, (int) Math.min(Integer.MAX_VALUE, t.getLong("left")), (int) Math.min(Integer.MAX_VALUE, Math.max(1, t.getLong("total"))), w.storyDays);
        KiControlNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), msg);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) sync(sp);
    }

    private static void title(ServerPlayer sp, Component main, Component sub) {
        sp.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
        sp.connection.send(new ClientboundSetSubtitleTextPacket(sub));
        sp.connection.send(new ClientboundSetTitleTextPacket(main));
    }

    // ------------------------------------------------------------ comandos

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("dmzpronto").executes(c -> {
            if (!(c.getSource().getEntity() instanceof ServerPlayer sp)) return 0;
            Window w = active(sp);
            if (w == null) {
                sp.sendSystemMessage(Component.translatable("story.dmzkicontrol.deadline.none"));
                return 0;
            }
            arrive(sp, w, true);
            return 1;
        }));
        // teste: /dmzprazo <id> comeca um prazo (precisa de permissao)
        event.getDispatcher().register(Commands.literal("dmzprazo").requires(s -> s.hasPermission(2))
                .then(Commands.argument("id", com.mojang.brigadier.arguments.StringArgumentType.word())
                        .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(WINDOWS.stream().map(Window::id), b))
                        .executes(c -> {
                            if (!(c.getSource().getEntity() instanceof ServerPlayer sp)) return 0;
                            Window w = byId(com.mojang.brigadier.arguments.StringArgumentType.getString(c, "id"));
                            if (w == null) return 0;
                            CompoundTag t = data(sp);
                            CompoundTag done = t.getCompound("done");
                            done.remove(w.id);
                            t.put("done", done);
                            t.remove("active");
                            save(sp, t);
                            start(sp, w);
                            return 1;
                        })));
    }
}
