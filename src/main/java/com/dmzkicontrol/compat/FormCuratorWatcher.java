package com.dmzkicontrol.compat;

import com.dmzkicontrol.KiControlMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Outros add-ons recolocam formas em momentos diferentes (o DMZ Sparking, por exemplo, reinstala as dele
 * depois que o servidor sobe e de novo quando o jogador entra no mundo), desfazendo a curadoria.
 *
 * <p>Com o servidor rodando, a cada 5 segundos olhamos se algum arquivo de forma/preco do DMZ mudou.
 * Se mudou, a curadoria roda de novo, o DMZ recarrega e os jogadores recebem a config nova.
 */
@Mod.EventBusSubscriber(modid = KiControlMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FormCuratorWatcher {
    private static final int EVERY_TICKS = 100;
    private static long lastSignature = Long.MIN_VALUE;
    private static int ticks;

    private FormCuratorWatcher() {}

    /** "Assinatura" dos arquivos que a curadoria cuida: muda quando qualquer um deles e escrito. */
    static long signature() {
        Path dmz = FMLPaths.CONFIGDIR.get().resolve("dragonminez");
        if (!Files.isDirectory(dmz)) return 0L;
        long sig = 0L;
        try (Stream<Path> walk = Files.walk(dmz, 4)) {
            for (Path p : (Iterable<Path>) walk::iterator) {
                String rel = dmz.relativize(p).toString().replace('\\', '/');
                if (!rel.endsWith(".json") || rel.startsWith("_removidos_evolution") || rel.startsWith("oldBackup")) continue;
                boolean watched = rel.startsWith("forms/") || rel.contains("/forms/") || rel.equals("skills.json") || rel.endsWith("/character.json");
                if (!watched) continue;
                sig = sig * 31 + rel.hashCode() + Files.getLastModifiedTime(p).toMillis() + Files.size(p);
            }
        } catch (IOException e) {
            return lastSignature;
        }
        return sig;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (++ticks < EVERY_TICKS) return;
        ticks = 0;
        long sig = signature();
        if (sig == lastSignature) return;
        boolean first = lastSignature == Long.MIN_VALUE;
        boolean changed = FormCurator.run(first ? "servidor" : "outro mod mexeu nas formas");
        lastSignature = signature();
        if (!changed) return;
        try {
            com.dragonminez.common.config.ConfigManager.reload();
            MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
                    try { com.dragonminez.common.config.ConfigManager.sendConfigSync(sp); } catch (Throwable ignored) { }
                }
            }
        } catch (Throwable t) {
            KiControlMod.LOGGER.warn("Curadoria refeita, mas nao consegui recarregar o DMZ ({}). Rode /dmzreload config.", t.toString());
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        lastSignature = Long.MIN_VALUE;
        ticks = 0;
    }
}
