package com.dmzkicontrol.compat;

import com.dmzkicontrol.KiControlMod;
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
 * <p>Com o servidor rodando, a cada minuto olhamos se algum arquivo de forma/preco do DMZ mudou e, se
 * mudou, arrumamos os arquivos (sem recarregar o DMZ). Se o mesmo mod desfaz tudo de novo seguidamente,
 * a vigia para ate o proximo inicio, para nunca brigar com ele.
 */
@Mod.EventBusSubscriber(modid = KiControlMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FormCuratorWatcher {
    private static final int EVERY_TICKS = 1200; // 1 minuto
    private static long lastSignature = Long.MIN_VALUE;
    private static int ticks;
    private static int streak;
    private static boolean stopped;

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
        if (event.phase != TickEvent.Phase.END || stopped) return;
        if (++ticks < EVERY_TICKS) return;
        ticks = 0;
        long sig = signature();
        if (sig == lastSignature) return;
        // So arruma os ARQUIVOS (valem no proximo inicio). Nunca recarrega o DMZ daqui: o DMZ Sparking
        // recoloca algumas formas a cada recarga, e recarregar de novo virava um ciclo sem fim que
        // travava o servidor (3.8.4).
        boolean changed = FormCurator.run("arquivos mexidos por outro mod");
        lastSignature = signature();
        streak = changed ? streak + 1 : 0;
        if (streak >= 3) {
            stopped = true;
            KiControlMod.LOGGER.warn("Curadoria: outro mod recoloca as mesmas formas toda hora; a vigia parou ate o proximo inicio "
                    + "(no DMZ Sparking, installForms = false em config/dmzsparking/common.toml evita isso).");
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        lastSignature = Long.MIN_VALUE;
        ticks = 0;
        streak = 0;
        stopped = false;
    }
}
