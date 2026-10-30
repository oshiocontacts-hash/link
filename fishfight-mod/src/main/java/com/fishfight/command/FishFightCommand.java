package com.fishfight.command;

import com.fishfight.FishFight;
import com.fishfight.battle.FishBattleManager;
import com.fishfight.fish.FishGenerator;
import com.fishfight.fish.FishRarity;
import com.fishfight.fish.FishSpecies;
import com.fishfight.fish.FishStats;
import com.fishfight.fish.Mutation;
import com.fishfight.fishing.FishingMinigameManager;
import com.fishfight.item.FishItem;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

public final class FishFightCommand {
	private static final SuggestionProvider<CommandSourceStack> SPECIES = (ctx, builder) ->
			SharedSuggestionProvider.suggest(Arrays.stream(FishSpecies.values()).map(s -> s.id), builder);
	private static final SuggestionProvider<CommandSourceStack> RARITIES = (ctx, builder) ->
			SharedSuggestionProvider.suggest(Arrays.stream(FishRarity.values()).map(r -> r.id), builder);
	private static final SuggestionProvider<CommandSourceStack> MUTATIONS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(Arrays.stream(Mutation.values()).map(m -> m.id), builder);

	private FishFightCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		LiteralCommandNode<CommandSourceStack> root = dispatcher.register(Commands.literal("fishfight")
				.executes(ctx -> help(ctx.getSource()))
				.then(Commands.literal("help").executes(ctx -> help(ctx.getSource())))
				.then(Commands.literal("challenge")
						.then(Commands.argument("player", EntityArgument.player())
								.executes(ctx -> {
									FishBattleManager.challenge(ctx.getSource().getPlayerOrException(), EntityArgument.getPlayer(ctx, "player"));
									return 1;
								})))
				.then(Commands.literal("accept").executes(ctx -> {
					FishBattleManager.accept(ctx.getSource().getPlayerOrException());
					return 1;
				}))
				.then(Commands.literal("deny").executes(ctx -> {
					FishBattleManager.deny(ctx.getSource().getPlayerOrException());
					return 1;
				}))
				.then(Commands.literal("practice").executes(ctx -> {
					FishBattleManager.practice(ctx.getSource().getPlayerOrException());
					return 1;
				}))
				.then(Commands.literal("info").executes(ctx -> info(ctx.getSource())))
				.then(Commands.literal("give")
						.requires(source -> source.hasPermission(2))
						.executes(ctx -> give(ctx, null, null, null))
						.then(Commands.argument("species", StringArgumentType.word()).suggests(SPECIES)
								.executes(ctx -> give(ctx, StringArgumentType.getString(ctx, "species"), null, null))
								.then(Commands.argument("rarity", StringArgumentType.word()).suggests(RARITIES)
										.executes(ctx -> give(ctx, StringArgumentType.getString(ctx, "species"),
												StringArgumentType.getString(ctx, "rarity"), null))
										.then(Commands.argument("mutation", StringArgumentType.word()).suggests(MUTATIONS)
												.executes(ctx -> give(ctx, StringArgumentType.getString(ctx, "species"),
														StringArgumentType.getString(ctx, "rarity"),
														StringArgumentType.getString(ctx, "mutation"))))))));

		// テスト用：魚がかかった状態からミニゲームを始める
		dispatcher.register(Commands.literal("fishfight")
				.then(Commands.literal("testcatch")
						.requires(source -> source.hasPermission(2))
						.then(Commands.argument("species", StringArgumentType.word()).suggests(SPECIES)
								.then(Commands.argument("rarity", StringArgumentType.word()).suggests(RARITIES)
										.executes(ctx -> testCatch(ctx, StringArgumentType.getString(ctx, "species"),
												StringArgumentType.getString(ctx, "rarity"), null))
										.then(Commands.argument("mutation", StringArgumentType.word()).suggests(MUTATIONS)
												.executes(ctx -> testCatch(ctx, StringArgumentType.getString(ctx, "species"),
														StringArgumentType.getString(ctx, "rarity"),
														StringArgumentType.getString(ctx, "mutation"))))))));

		dispatcher.register(Commands.literal("ff").executes(ctx -> help(ctx.getSource())).redirect(root));
	}

	private static int help(CommandSourceStack source) {
		for (String line : List.of("title", "fish", "challenge", "accept", "practice", "info", "give")) {
			ChatFormatting color = line.equals("title") ? ChatFormatting.GOLD : ChatFormatting.GRAY;
			source.sendSuccess(() -> FishFight.tr("help." + line).withStyle(color), false);
		}
		return 1;
	}

	private static int info(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		FishStats stats = FishBattleManager.heldFish(player);
		if (stats == null) {
			source.sendFailure(FishFight.tr("battle.err.no_fish"));
			return 0;
		}
		List<Component> lines = new ArrayList<>();
		lines.add(stats.displayName());
		stats.appendTooltip(lines);
		for (Component line : lines) {
			source.sendSuccess(() -> line, false);
		}
		return 1;
	}

	private static int give(CommandContext<CommandSourceStack> ctx, @Nullable String speciesId, @Nullable String rarityId,
			@Nullable String mutationId) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		FishStats stats = rollFish(player, speciesId, rarityId, mutationId);
		player.getInventory().placeItemBackInInventory(FishItem.create(stats));
		ctx.getSource().sendSuccess(() -> FishFight.tr("command.given", stats.displayName()), false);
		return 1;
	}

	private static int testCatch(CommandContext<CommandSourceStack> ctx, String speciesId, String rarityId,
			@Nullable String mutationId) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		if (player.fishing != null) {
			player.fishing.discard();
		}
		FishingMinigameManager.start(player, rollFish(player, speciesId, rarityId, mutationId));
		return 1;
	}

	private static FishStats rollFish(ServerPlayer player, @Nullable String speciesId, @Nullable String rarityId,
			@Nullable String mutationId) {
		FishSpecies species = speciesId == null
				? FishSpecies.values()[player.getRandom().nextInt(FishSpecies.values().length)]
				: FishSpecies.byId(speciesId);
		FishRarity rarity = null;
		if (rarityId != null) {
			for (FishRarity r : FishRarity.values()) {
				if (r.id.equals(rarityId)) {
					rarity = r;
				}
			}
		}
		Mutation mutation = null;
		if (mutationId != null) {
			for (Mutation m : Mutation.values()) {
				if (m.id.equals(mutationId)) {
					mutation = m;
				}
			}
		}
		return FishGenerator.generate(player.getRandom(), species, rarity, player.getGameProfile().getName(), mutation);
	}
}
