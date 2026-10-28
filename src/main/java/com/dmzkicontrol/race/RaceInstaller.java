package com.dmzkicontrol.race;

import com.dmzkicontrol.KiControlMod;
import com.dmzkicontrol.config.KiControlConfig;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Instala as racas do add-on no sistema de racas por JSON do DragonMineZ
 * ({@code config/dragonminez/races/<raca>/}). So escreve arquivos que ainda nao existem,
 * entao edicoes do dono do servidor nunca sao sobrescritas. Apague a pasta para reinstalar os padroes.
 */
public final class RaceInstaller {
    private static final String[] CEREALIAN_FILES = {"character.json", "stats.json", "forms/cerealforms.json"};
    private static boolean installedSomething;

    private RaceInstaller() {}

    public static boolean installedSomething() {
        return installedSomething;
    }

    public static void installAll() {
        KiControlConfig.Cerealian c = KiControlConfig.get().cerealian;
        if (c.installRace) install("cerealian", c.raceKey, CEREALIAN_FILES);
    }

    private static void install(String bundled, String raceKey, String[] files) {
        Path dir = FMLPaths.CONFIGDIR.get().resolve("dragonminez").resolve("races").resolve(raceKey);
        String version = dmzConfigVersion();
        for (String file : files) {
            Path target = dir.resolve(file);
            if (Files.exists(target)) continue;
            String res = "/dmzkicontrol_defaults/races/" + bundled + "/" + file;
            try (InputStream in = RaceInstaller.class.getResourceAsStream(res)) {
                if (in == null) {
                    KiControlMod.LOGGER.error("Arquivo padrao da raca nao encontrado no jar: {}", res);
                    continue;
                }
                String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                if (version != null) json = json.replace("\"configVersion\": \"2.2.0\"", "\"configVersion\": \"" + version + "\"");
                Files.createDirectories(target.getParent());
                Files.writeString(target, json, StandardCharsets.UTF_8);
                installedSomething = true;
                KiControlMod.LOGGER.info("Raca '{}': instalado {}", raceKey, target);
            } catch (Exception e) {
                KiControlMod.LOGGER.error("Falha ao instalar {} da raca '{}'", file, raceKey, e);
            }
        }
    }

    /** Usa a versao de config do DMZ instalado, para ele nao mandar nossos arquivos para o oldBackup. */
    private static String dmzConfigVersion() {
        try {
            // lido em tempo de execucao (a constante seria embutida na compilacao)
            Object v = Class.forName("com.dragonminez.common.config.ConfigManager").getField("CONFIG_VERSION").get(null);
            return v instanceof String str ? str : null;
        } catch (Throwable t) {
            return null;
        }
    }
}
