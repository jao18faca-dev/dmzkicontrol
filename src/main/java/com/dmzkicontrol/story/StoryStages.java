package com.dmzkicontrol.story;

import com.dmzkicontrol.KiControlMod;
import com.dmzkicontrol.config.KiControlConfig;
import com.dragonminez.common.quest.QuestService;
import com.mojang.datafixers.util.Pair;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * As lutas da historia no lugar onde aconteceram: Cell na Arena do Cell, Freeza e as Forcas Especiais Ginyu perto
 * da nave do Freeza em Namekusei, os capangas do Babidi na nave dele, Super Boo no Palacio de Kami, Kid Boo no
 * Planeta Sagrado dos Kaioshins, os Saiyajins na cratera do pouso. Quando o chefe da missao aparece longe do
 * lugar, o grupo e levado ate la (com uma passagem curta de tela) e o chefe e chamado de novo no lugar certo.
 */
@Mod.EventBusSubscriber(modid = KiControlMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class StoryStages {
    /** Lugar da luta: dimensao e estrutura (null = qualquer ponto da dimensao, perto de onde o jogador esta). */
    public record Stage(String key, ResourceKey<Level> dim, String structure) {}

    private static final ResourceKey<Level> OVER = Level.OVERWORLD;
    private static final ResourceKey<Level> NAMEK = ResourceKey.create(Registries.DIMENSION, new ResourceLocation("dragonminez", "namek"));
    private static final ResourceKey<Level> KAIOSHIN = ResourceKey.create(Registries.DIMENSION, new ResourceLocation("dragonminez", "sacredkaiplanet"));

    private static final Stage CELL_ARENA = new Stage("cell_arena", OVER, "dragonminez:cell_arena");
    private static final Stage FRIEZA_SHIP = new Stage("frieza_ship", NAMEK, "dragonminez:frieza_ship");
    private static final Stage BABIDI = new Stage("babidi", OVER, "dragonminez:babidi");
    private static final Stage LOOKOUT = new Stage("kamilookout", OVER, "dragonminez:kamilookout");
    private static final Stage SACRED = new Stage("sacredkaiplanet", KAIOSHIN, null);
    private static final Stage CRATER = new Stage("saiyan_crater", OVER, "dragonminez:saiyan_crater");
    private static final Stage GERO = new Stage("gero_lab", OVER, "dragonminez:gero_lab");
    /** Planeta do Beerus (dimensao do DMZ Sparking). */
    private static final Stage BEERUS = new Stage("beerus_planet",
            ResourceKey.create(Registries.DIMENSION, new ResourceLocation("dmzsparking", "beerus_planet")), null);

    private static final Map<String, Stage> STAGES = new HashMap<>();

    static {
        for (String s : new String[]{"saga_cell_perfect", "saga_cell_jr", "saga_cell_superperfect"}) STAGES.put("dragonminez:" + s, CELL_ARENA);
        for (String s : new String[]{"saga_frieza_base", "saga_frieza_first", "saga_frieza_second", "saga_frieza_third", "saga_frieza_fp",
                "saga_ginyu", "saga_ginyu_goku", "saga_recoome", "saga_burter", "saga_jeice", "saga_guldo"}) STAGES.put("dragonminez:" + s, FRIEZA_SHIP);
        for (String s : new String[]{"saga_puipui", "saga_yakon", "saga_dabura", "saga_vegeta_majin", "saga_buufat"}) STAGES.put("dragonminez:" + s, BABIDI);
        for (String s : new String[]{"saga_superbuu", "saga_superbuugohan"}) STAGES.put("dragonminez:" + s, LOOKOUT);
        STAGES.put("dragonminez:saga_kidbuu", SACRED);
        for (String s : new String[]{"saga_nappa", "saga_saibaman"}) STAGES.put("dragonminez:" + s, CRATER);
        STAGES.put("dragonminez:saga_drgero", GERO);
        STAGES.put("dmzsparking:saga_beerus", BEERUS);
    }

    /** Trocas de lugar em andamento: dono -> {missao, tick em que o chefe e chamado de novo}. */
    private static final Map<UUID, Object[]> MOVES = new HashMap<>();
    /** Ultima troca por missao (evita ficar levando o grupo de um lado para o outro). */
    private static final Map<String, Long> LAST = new HashMap<>();
    /** Lugar encontrado por estrutura (a busca e cara). */
    private static final Map<String, BlockPos> FOUND = new HashMap<>();

    private StoryStages() {}

    private static boolean on() {
        return KiControlConfig.get().story.battleStages;
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.isCanceled() || event.getLevel().isClientSide() || event.loadedFromDisk() || !on()) return;
        Entity e = event.getEntity();
        CompoundTag pd = e.getPersistentData();
        if (!pd.contains(QuestService.QUEST_OWNER_TAG)) return;
        ResourceLocation type = ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
        Stage st = type == null ? null : STAGES.get(type.toString());
        if (st == null || !(event.getLevel() instanceof ServerLevel level)) return;
        MinecraftServer server = level.getServer();
        UUID owner;
        try {
            owner = UUID.fromString(pd.getString(QuestService.QUEST_OWNER_TAG));
        } catch (IllegalArgumentException ex) {
            return;
        }
        String quest = pd.getString(QuestService.QUEST_KEY_TAG);
        if (quest.isEmpty()) return;
        if (MOVES.containsKey(owner)) {
            // ja estamos levando o grupo: este chefe nao entra (o certo vem depois)
            event.setCanceled(true);
            return;
        }
        Long last = LAST.get(quest);
        if (last != null && server.getTickCount() - last < 20 * 60) return;
        ServerLevel target = server.getLevel(st.dim);
        if (target == null) return;
        if (atStage(level, e.blockPosition(), st)) return;
        BlockPos spot = find(target, st, e.blockPosition());
        if (spot == null) return;
        ServerPlayer sp = server.getPlayerList().getPlayer(owner);
        if (sp == null) return;
        event.setCanceled(true);
        LAST.put(quest, (long) server.getTickCount());
        MOVES.put(owner, new Object[]{quest, (long) server.getTickCount() + 50L});
        travel(sp, target, spot, st);
    }

    /** O chefe ja esta no lugar certo (mesma dimensao e perto da estrutura). */
    private static boolean atStage(ServerLevel level, BlockPos pos, Stage st) {
        if (!level.dimension().equals(st.dim)) return false;
        if (st.structure == null) return true;
        BlockPos at = FOUND.get(st.dim.location() + "|" + st.structure);
        if (at != null && at.distSqr(pos) < 96 * 96) return true;
        Holder<Structure> h = holder(level, st.structure);
        if (h == null) return true;
        StructureStart start = level.structureManager().getStructureWithPieceAt(pos, h.value());
        if (start != null && start.isValid()) return true;
        BlockPos near = find(level, st, pos);
        return near != null && near.distSqr(pos) < 96 * 96;
    }

    private static Holder<Structure> holder(ServerLevel level, String id) {
        try {
            var reg = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
            return reg.getHolder(ResourceKey.create(Registries.STRUCTURE, new ResourceLocation(id))).orElse(null);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Centro da estrutura mais perto (ou um ponto seguro na dimensao, quando o lugar e a dimensao inteira). */
    private static BlockPos find(ServerLevel level, Stage st, BlockPos from) {
        String cacheKey = st.dim.location() + "|" + st.structure;
        if (st.structure == null) {
            BlockPos base = level.dimension().equals(st.dim) ? from : level.getSharedSpawnPos();
            return surface(level, base);
        }
        BlockPos cached = FOUND.get(cacheKey);
        if (cached != null) return cached;
        Holder<Structure> h = holder(level, st.structure);
        if (h == null) return null;
        BlockPos origin = level.dimension().equals(st.dim) ? from : level.getSharedSpawnPos();
        try {
            Pair<BlockPos, Holder<Structure>> r = level.getChunkSource().getGenerator()
                    .findNearestMapStructure(level, HolderSet.direct(h), origin, 100, false);
            if (r == null) return null;
            BlockPos p = r.getFirst();
            level.getChunk(p.getX() >> 4, p.getZ() >> 4);
            StructureStart start = level.structureManager().getStructureWithPieceAt(p, h.value());
            if (start == null || !start.isValid()) {
                start = level.structureManager().getStructureAt(p, h.value());
            }
            BlockPos center = start != null && start.isValid() ? start.getBoundingBox().getCenter() : p;
            BlockPos spot = surface(level, center);
            FOUND.put(cacheKey, spot);
            return spot;
        } catch (Throwable t) {
            KiControlMod.LOGGER.warn("[Palco] nao achei {} ({}).", st.structure, t.toString());
            return null;
        }
    }

    private static BlockPos surface(ServerLevel level, BlockPos p) {
        level.getChunk(p.getX() >> 4, p.getZ() >> 4);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.getX(), p.getZ());
        if (y <= level.getMinBuildHeight() + 1) y = Math.max(p.getY(), 80);
        return new BlockPos(p.getX(), y + 1, p.getZ());
    }

    /** Leva o dono e quem esta perto dele ate o lugar da luta. */
    private static void travel(ServerPlayer owner, ServerLevel target, BlockPos spot, Stage st) {
        List<ServerPlayer> group = new ArrayList<>();
        group.add(owner);
        for (ServerPlayer p : owner.serverLevel().players()) {
            if (p != owner && p.distanceTo(owner) < 48 && !p.isSpectator()) group.add(p);
        }
        int i = 0;
        for (ServerPlayer p : group) {
            double a = i * Math.PI * 2 / Math.max(1, group.size());
            double r = group.size() > 1 ? 2.5 : 0;
            p.teleportTo(target, spot.getX() + 0.5 + Math.cos(a) * r, spot.getY(), spot.getZ() + 0.5 + Math.sin(a) * r, p.getYRot(), 0f);
            p.fallDistance = 0;
            p.sendSystemMessage(Component.translatable("story.dmzkicontrol.stage." + st.key).withStyle(ChatFormatting.GOLD));
            target.playSound(null, spot, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, 0.6f);
            i++;
        }
    }

    /** Depois da viagem, chama o chefe de novo (agora no lugar certo). */
    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || MOVES.isEmpty()) return;
        MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        long now = server.getTickCount();
        List<Map.Entry<UUID, Object[]>> due = new ArrayList<>();
        for (Map.Entry<UUID, Object[]> en : MOVES.entrySet()) if (now >= (long) en.getValue()[1]) due.add(en);
        for (Map.Entry<UUID, Object[]> en : due) {
            // tira da lista antes: o chefe chamado agora tem que poder entrar
            MOVES.remove(en.getKey());
            ServerPlayer sp = server.getPlayerList().getPlayer(en.getKey());
            if (sp == null) continue;
            try {
                QuestService.resummonQuest(sp, (String) en.getValue()[0]);
            } catch (Throwable t) {
                KiControlMod.LOGGER.warn("[Palco] nao deu para chamar o chefe de novo ({}).", t.toString());
            }
        }
    }
}
