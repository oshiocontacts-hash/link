package com.neonclash.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.neonclash.NeonClash;
import com.neonclash.config.NCConfig;
import com.neonclash.core.round.Team;
import com.neonclash.game.SlowService;
import com.neonclash.match.ArenaData;
import com.neonclash.match.MatchController;
import com.neonclash.match.MatchRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * /pvp start | stop | team &lt;player&gt; &lt;red|blue&gt; | arena set
 * （おまけ: /pvp choose &lt;1..n&gt; 強化をコマンドで選ぶ、/pvp slowfield &lt;radius&gt; &lt;seconds&gt; 範囲スローの確認用）
 */
@Mod.EventBusSubscriber(modid = NeonClash.MODID)
public final class PvpCommand {
    private PvpCommand() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("pvp")
                .then(Commands.literal("start").requires(s -> s.hasPermission(2)).executes(PvpCommand::start))
                .then(Commands.literal("stop").requires(s -> s.hasPermission(2)).executes(PvpCommand::stop))
                .then(Commands.literal("team").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("team", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(new String[]{"red", "blue"}, b))
                                        .executes(PvpCommand::team))))
                .then(Commands.literal("arena").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("set").executes(PvpCommand::arenaSet)))
                .then(Commands.literal("choose")
                        .then(Commands.argument("index", IntegerArgumentType.integer(1, 9)).executes(PvpCommand::choose)))
                .then(Commands.literal("status").executes(PvpCommand::status))
                .then(Commands.literal("slowfield").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("radius", DoubleArgumentType.doubleArg(0.5, 64))
                                .then(Commands.argument("seconds", DoubleArgumentType.doubleArg(0.05, 60))
                                        .executes(PvpCommand::slowField)))));
    }

    private static int start(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        if (!MatchRegistry.active().isEmpty()) {
            src.sendFailure(Component.translatable("neonclash.cmd.already_running"));
            return 0;
        }
        ServerLevel level = src.getLevel();
        Vec3 center = ArenaData.get(level).center();
        if (center == null) {
            src.sendFailure(Component.translatable("neonclash.cmd.no_arena"));
            return 0;
        }
        Map<ServerPlayer, Team> members = new LinkedHashMap<>();
        Map<Team, Integer> counts = new EnumMap<>(Team.class);
        for (Map.Entry<UUID, Team> e : MatchRegistry.assignments().entrySet()) {
            ServerPlayer p = src.getServer().getPlayerList().getPlayer(e.getKey());
            if (p != null && p.level() == level) {
                members.put(p, e.getValue());
                counts.merge(e.getValue(), 1, Integer::sum);
            }
        }
        int min = NCConfig.SERVER.minPlayersPerTeam.get(), max = NCConfig.SERVER.maxPlayersPerTeam.get();
        for (Team t : Team.values()) {
            int n = counts.getOrDefault(t, 0);
            if (n < min || n > max) {
                src.sendFailure(Component.translatable("neonclash.cmd.team_size", t.name(), n, min, max));
                return 0;
            }
        }
        MatchController.start(level, center, members, MatchController.Options.fromConfig());
        src.sendSuccess(() -> Component.translatable("neonclash.cmd.started"), true);
        return 1;
    }

    private static int stop(CommandContext<CommandSourceStack> ctx) {
        int n = 0;
        for (MatchController m : MatchRegistry.active()) {
            m.stop();
            n++;
        }
        SlowService.clearAll();
        int stopped = n;
        ctx.getSource().sendSuccess(() -> Component.translatable("neonclash.cmd.stopped", stopped), true);
        return n;
    }

    private static int team(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
        Team t;
        try {
            t = Team.parse(StringArgumentType.getString(ctx, "team"));
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendFailure(Component.translatable("neonclash.cmd.bad_team"));
            return 0;
        }
        MatchRegistry.assign(p.getUUID(), t);
        ctx.getSource().sendSuccess(() -> Component.translatable("neonclash.cmd.team", p.getDisplayName(), t.name()), true);
        return 1;
    }

    private static int arenaSet(CommandContext<CommandSourceStack> ctx) {
        Vec3 pos = ctx.getSource().getPosition();
        ArenaData.get(ctx.getSource().getLevel()).setCenter(pos);
        ctx.getSource().sendSuccess(() -> Component.translatable("neonclash.cmd.arena",
                String.format(java.util.Locale.ROOT, "%.1f %.1f %.1f", pos.x, pos.y, pos.z),
                NCConfig.SERVER.arenaRadius.get()), true);
        return 1;
    }

    private static int choose(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        int idx = IntegerArgumentType.getInteger(ctx, "index") - 1;
        boolean ok = MatchRegistry.findFor(p.getUUID()).map(m -> m.choose(p.getUUID(), idx)).orElse(false);
        if (!ok) {
            ctx.getSource().sendFailure(Component.translatable("neonclash.cmd.cannot_choose"));
            return 0;
        }
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        if (MatchRegistry.active().isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.translatable("neonclash.cmd.no_match"), false);
            return 0;
        }
        for (MatchController m : MatchRegistry.active()) {
            ctx.getSource().sendSuccess(() -> Component.literal(m.describe()), false);
        }
        return 1;
    }

    private static int slowField(CommandContext<CommandSourceStack> ctx) {
        double r = DoubleArgumentType.getDouble(ctx, "radius");
        int ticks = (int) Math.round(DoubleArgumentType.getDouble(ctx, "seconds") * 20);
        SlowService.addZone(ctx.getSource().getLevel(), ctx.getSource().getPosition(), r, NCConfig.SERVER.parryZoneFactor.get(), ticks);
        return 1;
    }
}
