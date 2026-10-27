package com.dmzkicontrol.command;

import com.dmzkicontrol.KiControlMod;
import com.dmzkicontrol.config.KiControlConfig;
import com.dmzkicontrol.control.AuraSealManager;
import com.dmzkicontrol.control.MasteryStore;
import com.dragonminez.common.stats.StatsData;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collection;
import java.util.Map;

/**
 * /auracontrol                                  -> estado: forma, dominio, ki poupado, bonus, tensao
 * /auracontrol toggle                           -> sela/libera a aura (igual a tecla G)
 * /auracontrol list                             -> dominio em todas as formas que voce ja treinou
 * /auracontrol reload                           -> recarrega config/dmzkicontrol.json (op)
 * /auracontrol mastery set <alvos> <0-100>      -> define o dominio na forma ATUAL dos alvos (op)
 * /auracontrol mastery setform <alvos> <grupo:forma> <0-100> (op)
 * /auracontrol mastery reset <alvos>            -> zera todo o dominio (op)
 */
@Mod.EventBusSubscriber(modid = KiControlMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AuraControlCommand {

    private AuraControlCommand() {}

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("auracontrol")
                .executes(ctx -> status(ctx.getSource()))
                .then(Commands.literal("toggle").executes(ctx -> {
                    AuraSealManager.requestToggle(ctx.getSource().getPlayerOrException(), false);
                    return 1;
                }))
                .then(Commands.literal("list").executes(ctx -> list(ctx.getSource())))
                .then(Commands.literal("reload")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> {
                            KiControlConfig.load();
                            ctx.getSource().sendSuccess(() -> Component.translatable("message.dmzkicontrol.reloaded"), true);
                            return 1;
                        }))
                .then(Commands.literal("mastery")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("set")
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .then(Commands.argument("percent", DoubleArgumentType.doubleArg(0, 100))
                                                .executes(ctx -> setCurrent(ctx.getSource(),
                                                        EntityArgument.getPlayers(ctx, "targets"),
                                                        DoubleArgumentType.getDouble(ctx, "percent"))))))
                        .then(Commands.literal("setform")
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .then(Commands.argument("form", StringArgumentType.string())
                                                .then(Commands.argument("percent", DoubleArgumentType.doubleArg(0, 100))
                                                        .executes(ctx -> setForm(ctx.getSource(),
                                                                EntityArgument.getPlayers(ctx, "targets"),
                                                                StringArgumentType.getString(ctx, "form"),
                                                                DoubleArgumentType.getDouble(ctx, "percent")))))))
                        .then(Commands.literal("reset")
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(ctx -> reset(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets")))))));
    }

    private static int status(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer sp = src.getPlayerOrException();
        StatsData data = AuraSealManager.stats(sp);
        String key = data == null ? null : AuraSealManager.formKey(data);
        if (key == null) {
            src.sendSuccess(() -> Component.translatable("message.dmzkicontrol.status_base"), false);
            return 0;
        }
        boolean stack = AuraSealManager.hasStackOnTop(data);
        double perSec = AuraSealManager.strainPerSecond(sp, key);
        Component strain = perSec <= 0
                ? Component.translatable("message.dmzkicontrol.strain_none").withStyle(ChatFormatting.LIGHT_PURPLE)
                : Component.literal(String.format(java.util.Locale.ROOT, "%.1f%%/s", perSec * 100.0));
        Component msg = Component.translatable("message.dmzkicontrol.status",
                key,
                Component.translatable(AuraSealManager.isSealed(sp) ? "menu.dmzkicontrol.on" : "menu.dmzkicontrol.off"),
                AuraSealManager.pct(MasteryStore.mastery(sp, key)),
                AuraSealManager.pct(AuraSealManager.kiReduction(sp, key, stack)),
                AuraSealManager.pct(AuraSealManager.powerBonus(sp, key)),
                AuraSealManager.pct(AuraSealManager.critBonus(sp, key)),
                strain);
        src.sendSuccess(() -> msg, false);
        if (!AuraSealManager.hasRequiredSkill(data)) {
            src.sendSuccess(() -> Component.translatable("message.dmzkicontrol.need_skill",
                    Component.translatable("skill.dragonminez." + KiControlConfig.get().requiredSkill))
                    .withStyle(ChatFormatting.YELLOW), false);
        }
        return 1;
    }

    private static int list(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer sp = src.getPlayerOrException();
        Map<String, Double> all = MasteryStore.all(sp);
        if (all.isEmpty()) {
            src.sendSuccess(() -> Component.translatable("message.dmzkicontrol.list_empty"), false);
            return 0;
        }
        src.sendSuccess(() -> Component.translatable("message.dmzkicontrol.list_header").withStyle(ChatFormatting.AQUA), false);
        for (String key : all.keySet()) {
            String line = " - " + key + ": " + AuraSealManager.pct(MasteryStore.mastery(sp, key)) + "%";
            src.sendSuccess(() -> Component.literal(line), false);
        }
        return all.size();
    }

    private static int setCurrent(CommandSourceStack src, Collection<ServerPlayer> targets, double percent) {
        int n = 0;
        for (ServerPlayer sp : targets) {
            StatsData data = AuraSealManager.stats(sp);
            String key = data == null ? null : AuraSealManager.formKey(data);
            if (key == null) continue;
            MasteryStore.setMastery(sp, key, percent / 100.0);
            AuraSealManager.sendHud(sp, data);
            n++;
        }
        int count = n;
        src.sendSuccess(() -> Component.translatable("message.dmzkicontrol.mastery_set", count), true);
        return n;
    }

    private static int setForm(CommandSourceStack src, Collection<ServerPlayer> targets, String form, double percent) {
        String key = form.toLowerCase(java.util.Locale.ROOT);
        for (ServerPlayer sp : targets) {
            MasteryStore.setMastery(sp, key, percent / 100.0);
            StatsData data = AuraSealManager.stats(sp);
            if (data != null) AuraSealManager.sendHud(sp, data);
        }
        src.sendSuccess(() -> Component.translatable("message.dmzkicontrol.mastery_set", targets.size()), true);
        return targets.size();
    }

    private static int reset(CommandSourceStack src, Collection<ServerPlayer> targets) {
        for (ServerPlayer sp : targets) {
            MasteryStore.resetAll(sp);
            StatsData data = AuraSealManager.stats(sp);
            if (data != null) AuraSealManager.sendHud(sp, data);
        }
        src.sendSuccess(() -> Component.translatable("message.dmzkicontrol.mastery_reset", targets.size()), true);
        return targets.size();
    }
}
