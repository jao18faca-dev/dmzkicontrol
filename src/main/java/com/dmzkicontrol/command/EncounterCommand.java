package com.dmzkicontrol.command;

import com.dmzkicontrol.KiControlMod;
import com.dmzkicontrol.encounter.EncScript;
import com.dmzkicontrol.encounter.EncScripts;
import com.dmzkicontrol.encounter.EncounterDirector;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.Map;

/**
 * Teste das lutas cinematograficas sem precisar fazer a missao.
 * /dmzluta <chefe> [fase]   -> invoca o chefe na sua frente (ou usa um que ja esteja perto) e comeca a fase
 * /dmzluta parar            -> encerra a cena em andamento
 * Chefes: freeza (forma final), freeza100, cell. Precisa de permissao de comandos (cheats ligados no LAN).
 */
@Mod.EventBusSubscriber(modid = KiControlMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class EncounterCommand {
    private static final Map<String, String> ALIASES = Map.of(
            "freeza", "frieza_base", "frieza", "frieza_base", "frieza_base", "frieza_base",
            "freeza100", "frieza_fp", "frieza_fp", "frieza_fp",
            "cell", "cell_perfect", "cell_perfect", "cell_perfect");

    private EncounterCommand() {}

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("dmzluta")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("parar").executes(EncounterCommand::stop))
                .then(Commands.argument("chefe", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("freeza", "freeza100", "cell"), b))
                        .executes(c -> start(c, 1))
                        .then(Commands.argument("fase", IntegerArgumentType.integer(1, 9))
                                .executes(c -> start(c, IntegerArgumentType.getInteger(c, "fase"))))));
    }

    private static int start(CommandContext<CommandSourceStack> c, int phase) {
        CommandSourceStack src = c.getSource();
        if (!(src.getEntity() instanceof ServerPlayer sp)) {
            src.sendFailure(Component.literal("Use dentro do jogo."));
            return 0;
        }
        String id = ALIASES.get(StringArgumentType.getString(c, "chefe").toLowerCase());
        EncScript script = id == null ? null : EncScripts.byId(id);
        if (script == null) {
            src.sendFailure(Component.literal("Chefe desconhecido. Use: freeza, freeza100 ou cell."));
            return 0;
        }
        if (phase > script.phases.size()) {
            src.sendFailure(Component.literal("Esse chefe tem " + script.phases.size() + " fase(s)."));
            return 0;
        }
        String err = EncounterDirector.debugStart(sp, script, phase - 1);
        if (err != null) {
            src.sendFailure(Component.literal(err));
            return 0;
        }
        src.sendSuccess(() -> Component.literal("Cena " + script.id + ", fase " + phase + " de " + script.phases.size() + "."), false);
        return 1;
    }

    private static int stop(CommandContext<CommandSourceStack> c) {
        int n = EncounterDirector.stopAll();
        c.getSource().sendSuccess(() -> Component.literal(n == 0 ? "Nenhuma cena em andamento." : "Cena encerrada."), false);
        return n;
    }
}
