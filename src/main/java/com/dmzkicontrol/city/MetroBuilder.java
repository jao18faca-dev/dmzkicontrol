package com.dmzkicontrol.city;

import com.dmzkicontrol.KiControlMod;
import com.dmzkicontrol.config.KiControlConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cidades ENORMES: em volta de cada cidade do DMZ Sparking (West City, Central City, East City, Satan City) nascem
 * bairros inteiros - cada bairro e uma West City completa (os pedacos de 16x16 do proprio Sparking, lidos do jar
 * dele, girados para nao ficarem iguais). Com 2 aneis a cidade vira uma metropole de 5x5 bairros (~2800 blocos).
 * Os bairros sao construidos aos poucos, perto dos jogadores, como o Sparking faz com a cidade dele: o terreno e
 * aplainado no nivel da cidade (aterro embaixo, morro cortado em cima) e os pedacos sao colocados.
 * Nada do Sparking e copiado para este mod: sem o Sparking instalado, nada acontece.
 */
@Mod.EventBusSubscriber(modid = KiControlMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MetroBuilder {
    private static final String SPK = "dmzsparking";
    private static final String TILES = "data/dmzsparking/structures/west_city_tiles/";
    private static final String[] CITIES = {"west_city", "central_city", "east_city", "satan_city"};
    private static final int TILE = 16;

    /** Mapa dos pedacos da West City (grade N x N de nomes). */
    private static String[][] grid;
    private static int gridN, ground = 7, clearAbove = 96;
    private static boolean loadTried;
    private static final Map<String, Integer> DOOR_Y = new HashMap<>();
    private static final Map<String, StructureTemplate> TEMPLATES = new HashMap<>();
    private static long nextScan;

    private MetroBuilder() {}

    private static KiControlConfig.Cities cfg() {
        return KiControlConfig.get().cities;
    }

    // ------------------------------------------------------------ dados salvos

    /** Uma metropole: a cidade original e os bairros em volta. */
    static final class Metro {
        String city;
        int minX, minZ, maxX, maxZ, originY;
        final List<District> districts = new ArrayList<>();
    }

    static final class District {
        int x0, z0, rot;
        BitSet placed = new BitSet();
    }

    public static final class Data extends SavedData {
        final Map<String, Metro> metros = new HashMap<>();

        static Data load(CompoundTag tag) {
            Data d = new Data();
            ListTag list = tag.getList("metros", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag m = list.getCompound(i);
                Metro mt = new Metro();
                mt.city = m.getString("city");
                mt.minX = m.getInt("minX");
                mt.minZ = m.getInt("minZ");
                mt.maxX = m.getInt("maxX");
                mt.maxZ = m.getInt("maxZ");
                mt.originY = m.getInt("y");
                ListTag ds = m.getList("d", Tag.TAG_COMPOUND);
                for (int j = 0; j < ds.size(); j++) {
                    CompoundTag dt = ds.getCompound(j);
                    District di = new District();
                    di.x0 = dt.getInt("x");
                    di.z0 = dt.getInt("z");
                    di.rot = dt.getInt("r");
                    di.placed = BitSet.valueOf(dt.getLongArray("p"));
                    mt.districts.add(di);
                }
                d.metros.put(m.getString("key"), mt);
            }
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            ListTag list = new ListTag();
            for (Map.Entry<String, Metro> e : metros.entrySet()) {
                Metro mt = e.getValue();
                CompoundTag m = new CompoundTag();
                m.putString("key", e.getKey());
                m.putString("city", mt.city);
                m.putInt("minX", mt.minX);
                m.putInt("minZ", mt.minZ);
                m.putInt("maxX", mt.maxX);
                m.putInt("maxZ", mt.maxZ);
                m.putInt("y", mt.originY);
                ListTag ds = new ListTag();
                for (District di : mt.districts) {
                    CompoundTag dt = new CompoundTag();
                    dt.putInt("x", di.x0);
                    dt.putInt("z", di.z0);
                    dt.putInt("r", di.rot);
                    dt.putLongArray("p", di.placed.toLongArray());
                    ds.add(dt);
                }
                m.put("d", ds);
                list.add(m);
            }
            tag.put("metros", list);
            return tag;
        }
    }

    private static Data data(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(Data::load, Data::new, "dmzkicontrol_metros");
    }

    // ------------------------------------------------------------ leitura do Sparking

    private static Path spk(String rel) {
        try {
            var file = ModList.get().getModFileById(SPK);
            return file == null ? null : file.getFile().findResource(rel);
        } catch (Throwable t) {
            return null;
        }
    }

    private static JsonObject json(String rel) {
        Path p = spk(rel);
        if (p == null || !Files.exists(p)) return null;
        try (Reader r = Files.newBufferedReader(p)) {
            return JsonParser.parseReader(r).getAsJsonObject();
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean ready() {
        if (grid != null) return true;
        if (loadTried) return false;
        loadTried = true;
        if (!ModList.get().isLoaded(SPK)) return false;
        JsonObject idx = json(TILES + "index.json");
        if (idx == null) {
            KiControlMod.LOGGER.warn("[Metropole] mapa da West City do Sparking nao encontrado; cidades ficam do tamanho normal.");
            return false;
        }
        gridN = idx.get("grid_x").getAsInt();
        if (idx.has("ground")) ground = idx.get("ground").getAsInt();
        if (idx.has("clear_above")) clearAbove = idx.get("clear_above").getAsInt();
        grid = new String[gridN][gridN];
        for (JsonElement e : idx.getAsJsonArray("tiles")) {
            JsonArray a = e.getAsJsonArray();
            int x = a.get(0).getAsInt(), z = a.get(1).getAsInt();
            if (x >= 0 && z >= 0 && x < gridN && z < gridN) grid[x][z] = a.get(2).getAsString();
        }
        for (String c : CITIES) {
            String dir = c.equals("west_city") ? "west_city_tiles" : c;
            JsonObject plan = json("data/dmzsparking/structures/" + dir + "/plan.json");
            int y = c.equals("west_city") ? ground : 5;
            try {
                if (plan != null) y = plan.getAsJsonArray("buildings").get(0).getAsJsonObject().getAsJsonArray("door").get(1).getAsInt();
            } catch (Throwable ignored) {
            }
            DOOR_Y.put(c, y);
        }
        KiControlMod.LOGGER.info("[Metropole] West City do Sparking: {}x{} pedacos; bairros prontos para crescer.", gridN, gridN);
        return true;
    }

    // ------------------------------------------------------------ achar as cidades

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        KiControlConfig.Cities c = cfg();
        if (!c.megaCities || c.rings <= 0) return;
        MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null || !ready()) return;
        ServerLevel level = server.getLevel(Level.OVERWORLD);
        if (level == null || level.players().isEmpty()) return;
        long now = server.getTickCount();
        if (now >= nextScan) {
            nextScan = now + 600;
            for (ServerPlayer sp : level.players()) scan(level, sp);
        }
        build(level, System.nanoTime() + (long) (Math.max(1, c.buildMillisPerTick) * 1_000_000L));
    }

    private static void scan(ServerLevel level, ServerPlayer sp) {
        try {
            var reg = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
            List<Holder<Structure>> hs = new ArrayList<>();
            for (String c : CITIES) reg.getHolder(ResourceKey.create(Registries.STRUCTURE, new ResourceLocation(SPK, c))).ifPresent(hs::add);
            if (hs.isEmpty()) return;
            Pair<BlockPos, Holder<Structure>> r = level.getChunkSource().getGenerator()
                    .findNearestMapStructure(level, HolderSet.direct(hs), sp.blockPosition(), 48, false);
            if (r == null) return;
            BlockPos p = r.getFirst();
            String key = (p.getX() >> 4) + "," + (p.getZ() >> 4);
            Data d = data(level);
            if (d.metros.containsKey(key)) return;
            ChunkAccess chunk = level.getChunk(p.getX() >> 4, p.getZ() >> 4, ChunkStatus.STRUCTURE_STARTS);
            StructureStart start = chunk.getStartForStructure(r.getSecond().value());
            if (start == null || !start.isValid()) return;
            String city = r.getSecond().unwrapKey().map(k -> k.location().getPath()).orElse("west_city");
            BoundingBox bb = start.getBoundingBox();
            Metro mt = new Metro();
            mt.city = city;
            mt.minX = bb.minX();
            mt.minZ = bb.minZ();
            mt.maxX = bb.maxX();
            mt.maxZ = bb.maxZ();
            // se a caixa nao tem o tamanho da cidade (so o "plano" dela), usa o tamanho conhecido em volta do centro
            int want = switch (city) {
                case "west_city" -> gridN * TILE;
                case "east_city" -> 240;
                default -> 352;
            };
            if (Math.abs(bb.getXSpan() - want) > 64 || Math.abs(bb.getZSpan() - want) > 64) {
                BlockPos c = bb.getCenter();
                mt.minX = c.getX() - want / 2;
                mt.minZ = c.getZ() - want / 2;
                mt.maxX = mt.minX + want - 1;
                mt.maxZ = mt.minZ + want - 1;
                KiControlMod.LOGGER.info("[Metropole] caixa de {} com {}x{}; usando {} em volta do centro.", city, bb.getXSpan(), bb.getZSpan(), want);
            }
            int doorY = DOOR_Y.getOrDefault(city, 5);
            mt.originY = bb.minY() + doorY - ground + cfg().groundOffset;
            plan(mt, key.hashCode());
            d.metros.put(key, mt);
            d.setDirty();
            KiControlMod.LOGGER.info("[Metropole] {} em {} vira metropole: {} bairros em volta.", city, bb.getCenter(), mt.districts.size());
        } catch (Throwable t) {
            KiControlMod.LOGGER.debug("[Metropole] busca: {}", t.toString());
        }
    }

    /** Distribui os bairros em aneis em volta da cidade original. */
    private static void plan(Metro mt, int seed) {
        int size = gridN * TILE;
        int rings = Math.min(3, cfg().rings);
        int cx = (mt.minX + mt.maxX) / 2, cz = (mt.minZ + mt.maxZ) / 2;
        int halfX = (mt.maxX - mt.minX + 1) / 2, halfZ = (mt.maxZ - mt.minZ + 1) / 2;
        RandomSource r = RandomSource.create(seed);
        for (int dx = -rings; dx <= rings; dx++) {
            for (int dz = -rings; dz <= rings; dz++) {
                if (dx == 0 && dz == 0) continue;
                District di = new District();
                di.x0 = axis(dx, cx, halfX, size);
                di.z0 = axis(dz, cz, halfZ, size);
                di.rot = r.nextInt(4);
                mt.districts.add(di);
            }
        }
    }

    /** Canto do bairro num eixo: colado na cidade original (que pode ter outro tamanho) e depois de bairro em bairro. */
    private static int axis(int d, int center, int half, int size) {
        if (d == 0) return center - size / 2;
        if (d > 0) return center + half + (d - 1) * size;
        return center - half - (-d) * size;
    }

    // ------------------------------------------------------------ construir perto dos jogadores

    private static void build(ServerLevel level, long deadline) {
        Data d = data(level);
        if (d.metros.isEmpty()) return;
        int radius = Math.max(48, cfg().buildRadius);
        for (ServerPlayer sp : level.players()) {
            int px = sp.getBlockX(), pz = sp.getBlockZ();
            for (Metro mt : d.metros.values()) {
                for (District di : mt.districts) {
                    int size = gridN * TILE;
                    if (px < di.x0 - radius || px > di.x0 + size + radius || pz < di.z0 - radius || pz > di.z0 + size + radius) continue;
                    // pedacos mais perto primeiro
                    int gx0 = Math.max(0, (px - radius - di.x0) / TILE), gx1 = Math.min(gridN - 1, (px + radius - di.x0) / TILE);
                    int gz0 = Math.max(0, (pz - radius - di.z0) / TILE), gz1 = Math.min(gridN - 1, (pz + radius - di.z0) / TILE);
                    int bestX = -1, bestZ = -1;
                    long bestD = Long.MAX_VALUE;
                    for (int gx = gx0; gx <= gx1; gx++) {
                        for (int gz = gz0; gz <= gz1; gz++) {
                            if (di.placed.get(gx * gridN + gz)) continue;
                            int bx = di.x0 + gx * TILE + 8, bz = di.z0 + gz * TILE + 8;
                            if (!level.hasChunk(bx >> 4, bz >> 4)) continue;
                            long dd = (long) (bx - px) * (bx - px) + (long) (bz - pz) * (bz - pz);
                            if (dd < bestD) {
                                bestD = dd;
                                bestX = gx;
                                bestZ = gz;
                            }
                        }
                    }
                    while (bestX >= 0) {
                        place(level, mt, di, bestX, bestZ);
                        di.placed.set(bestX * gridN + bestZ);
                        d.setDirty();
                        if (System.nanoTime() > deadline) return;
                        bestX = -1;
                        // proximo mais perto no mesmo bairro
                        bestD = Long.MAX_VALUE;
                        for (int gx = gx0; gx <= gx1; gx++) {
                            for (int gz = gz0; gz <= gz1; gz++) {
                                if (di.placed.get(gx * gridN + gz)) continue;
                                int bx = di.x0 + gx * TILE + 8, bz = di.z0 + gz * TILE + 8;
                                if (!level.hasChunk(bx >> 4, bz >> 4)) continue;
                                long dd = (long) (bx - px) * (bx - px) + (long) (bz - pz) * (bz - pz);
                                if (dd < bestD) {
                                    bestD = dd;
                                    bestX = gx;
                                    bestZ = gz;
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /** Pedaco da grade original que cai nesta celula, com o giro do bairro. */
    private static String tileFor(int rot, int gx, int gz) {
        int n = gridN - 1;
        return switch (rot) {
            case 1 -> grid[gz][n - gx];      // giro de 90 (horario): (x,z) -> (n-z, x)
            case 2 -> grid[n - gx][n - gz];
            case 3 -> grid[n - gz][gx];
            default -> grid[gx][gz];
        };
    }

    private static void place(ServerLevel level, Metro mt, District di, int gx, int gz) {
        String name = tileFor(di.rot, gx, gz);
        if (name == null) return;
        int x0 = di.x0 + gx * TILE, z0 = di.z0 + gz * TILE, y0 = mt.originY;
        StructureTemplate t = TEMPLATES.computeIfAbsent(name, n -> level.getStructureManager().getOrCreate(new ResourceLocation(SPK, "west_city_tiles/" + n)));
        if (t == null || t.getSize().getX() == 0) return;
        prepare(level, x0, y0, z0, t.getSize().getY());
        Rotation rot = switch (di.rot) {
            case 1 -> Rotation.CLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
        int s = TILE - 1;
        BlockPos at = switch (di.rot) {
            case 1 -> new BlockPos(x0 + s, y0, z0);
            case 2 -> new BlockPos(x0 + s, y0, z0 + s);
            case 3 -> new BlockPos(x0, y0, z0 + s);
            default -> new BlockPos(x0, y0, z0);
        };
        StructurePlaceSettings set = new StructurePlaceSettings().setRotation(rot).setIgnoreEntities(true).setKnownShape(true);
        try {
            level.registryAccess().registryOrThrow(Registries.PROCESSOR_LIST)
                    .getOptional(new ResourceLocation(SPK, "arena_foundation"))
                    .ifPresent((StructureProcessorList pl) -> {
                        for (StructureProcessor pr : pl.list()) set.addProcessor(pr);
                    });
        } catch (Throwable ignored) {
        }
        t.placeInWorld(level, at, at, set, level.getRandom(), 2);
    }

    /** Aplaina a coluna do pedaco: aterro de pedra/terra ate o chao e morro cortado acima. */
    private static void prepare(ServerLevel level, int x0, int y0, int z0, int height) {
        BlockState air = Blocks.AIR.defaultBlockState(), stone = Blocks.STONE.defaultBlockState(), dirt = Blocks.DIRT.defaultBlockState();
        int top = Math.min(level.getMaxBuildHeight() - 1, y0 + ground + clearAbove);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = x0; x < x0 + TILE; x++) {
            for (int z = z0; z < z0 + TILE; z++) {
                int surf = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z);
                for (int y = Math.min(top, surf); y >= y0 + height; y--) {
                    m.set(x, y, z);
                    if (!level.getBlockState(m).isAir()) level.setBlock(m, air, 2 | 16);
                }
                for (int y = y0 - 1; y > y0 - 48 && y > level.getMinBuildHeight(); y--) {
                    m.set(x, y, z);
                    BlockState b = level.getBlockState(m);
                    if (b.isSolid()) break;
                    level.setBlock(m, y > y0 - 4 ? dirt : stone, 2 | 16);
                }
            }
        }
    }

    // ------------------------------------------------------------ sem monstros nas ruas

    @SubscribeEvent
    public static void onSpawn(MobSpawnEvent.FinalizeSpawn event) {
        if (event.getSpawnType() != net.minecraft.world.entity.MobSpawnType.NATURAL) return;
        if (event.getEntity().getType().getCategory() != MobCategory.MONSTER) return;
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        if (grid == null) return;
        int x = (int) event.getX(), z = (int) event.getZ();
        for (Metro mt : data(level).metros.values()) {
            for (District di : mt.districts) {
                int size = gridN * TILE;
                if (x >= di.x0 && x < di.x0 + size && z >= di.z0 && z < di.z0 + size) {
                    event.setSpawnCancelled(true);
                    return;
                }
            }
        }
    }
}
