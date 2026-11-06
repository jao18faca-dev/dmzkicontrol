package com.dmzkicontrol.client;

import com.dmzkicontrol.KiControlMod;
import com.dmzkicontrol.compat.UltraCompat;
import com.dmzkicontrol.config.KiControlClientConfig;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.config.ModConfig;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Troca entre a HUD do DMZ Ultra e a original do DragonMineZ. O Ultra ja tem uma chave para cada peca que ele
 * redesenha (a moldura do jogador, os menus, o anel da trava...), mas liga todas sem perguntar. Aqui elas sao
 * ligadas ou desligadas juntas, na config do proprio Ultra (dmzultra-client.toml), e valem na hora.
 */
public final class UltraHud {
    private UltraHud() {}

    /** Chaves do Ultra que substituem a HUD/menus do DMZ, com o valor de cada modo: {ultra, dmz}. */
    private static Map<String, Object[]> switches() {
        Map<String, Object[]> m = new LinkedHashMap<>();
        m.put("frame.playerFrame", new Object[]{true, false});        // moldura com retrato no lugar das barras do DMZ
        m.put("frame.targetFrame", new Object[]{"world", "off"});     // painel do alvo do Ultra
        m.put("menus.restyleDmzMenus", new Object[]{true, false});    // menus do DMZ no vidro do Ultra
        m.put("menus.restyleDmz22Panels", new Object[]{true, false});
        m.put("menus.restyleDmzTutorial", new Object[]{true, false});
        m.put("menus.raceChooserV2", new Object[]{true, false});      // escolha de raca redesenhada
        m.put("hud.restyleActionBar", new Object[]{true, false});     // texto acima da hotbar numa placa
        m.put("hud.chatAsNotices", new Object[]{true, false});        // mensagens do chat viram cartoes
        m.put("conquest.arrangeRightHud", new Object[]{true, false}); // reorganiza o canto superior direito
        m.put("lockon.hudRing", new Object[]{true, false});           // anel da trava na tela (o do DMZ fica no mundo)
        return m;
    }

    public static boolean available() {
        return UltraCompat.present() && spec() != null;
    }

    public static KiControlClientConfig.UltraHud mode() {
        try {
            return KiControlClientConfig.INSTANCE.ultraHud.get();
        } catch (Throwable t) {
            return KiControlClientConfig.UltraHud.ASK;
        }
    }

    /** Guarda a escolha e aplica na config do Ultra. */
    public static void choose(KiControlClientConfig.UltraHud mode) {
        try {
            KiControlClientConfig.INSTANCE.ultraHud.set(mode);
            KiControlClientConfig.SPEC.save();
        } catch (Throwable t) {
            KiControlMod.LOGGER.warn("[HUD] escolha da HUD nao salva: {}", t.toString());
        }
        apply(mode);
    }

    /** Aplica o modo salvo (ao entrar no mundo, caso o Ultra tenha voltado alguma chave). */
    public static void apply(KiControlClientConfig.UltraHud mode) {
        if (mode == KiControlClientConfig.UltraHud.ASK) return;
        ForgeConfigSpec spec = spec();
        if (spec == null) return;
        int col = mode == KiControlClientConfig.UltraHud.DMZ ? 1 : 0;
        int changed = 0;
        for (Map.Entry<String, Object[]> e : switches().entrySet()) {
            try {
                Object value = spec.getValues().get(e.getKey());
                if (!(value instanceof ForgeConfigSpec.ConfigValue<?> cv)) continue;
                Object want = e.getValue()[col];
                if (want.equals(cv.get())) continue;
                @SuppressWarnings("unchecked") ForgeConfigSpec.ConfigValue<Object> any = (ForgeConfigSpec.ConfigValue<Object>) cv;
                any.set(want);
                changed++;
            } catch (Throwable t) {
                KiControlMod.LOGGER.debug("[HUD] {} nao trocado: {}", e.getKey(), t.toString());
            }
        }
        if (changed > 0) {
            try {
                spec.save();
            } catch (Throwable ignored) {}
            KiControlMod.LOGGER.info("[HUD] HUD {}: {} chave(s) do DMZ Ultra trocada(s).", mode == KiControlClientConfig.UltraHud.DMZ ? "original do DragonMineZ" : "do DMZ Ultra", changed);
        }
    }

    private static ForgeConfigSpec spec() {
        try {
            Set<ModConfig> client = ConfigTracker.INSTANCE.configSets().get(ModConfig.Type.CLIENT);
            if (client == null) return null;
            for (ModConfig mc : client) {
                if (UltraCompat.MODID.equals(mc.getModId()) && mc.getFileName().endsWith("dmzultra-client.toml")
                        && mc.getSpec() instanceof ForgeConfigSpec s && s.isLoaded()) return s;
            }
        } catch (Throwable t) {
            KiControlMod.LOGGER.debug("[HUD] config do DMZ Ultra nao achada: {}", t.toString());
        }
        return null;
    }
}
