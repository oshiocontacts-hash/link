package com.fishfight.command;

import com.fishfight.FishFight;
import com.fishfight.battle.FishBattleManager;
import com.fishfight.fish.FishGenerator;
import com.fishfight.fish.FishRarity;
import com.fishfight.fish.FishSpecies;
import com.fishfight.fish.FishStats;
import com.fishfight.fish.Mutation;
import com.fishfight.fish.Skill;
import com.fishfight.fishing.FishingMinigameManager;
import com.fishfight.item.FishItem;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
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
	/** 体質とスキルを空白区切りでいくつでも指定できる（例: arms leg_sprout charge） */
	private static final SuggestionProvider<CommandSourceStack> EXTRAS = (ctx, builder) -> {
		String typed = builder.getRemaining();
		int lastSpace = typed.lastIndexOf(' ');
		String prefix = typed.substring(0, lastSpace + 1);
		SuggestionsBuilder last = builder.createOffset(builder.getStart() + lastSpace + 1);
		Stream<String> ids = Stream.concat(Arrays.stream(Mutation.values()).map(m -> m.id), Arrays.stream(Skill.values()).map(k -> k.id));
		SharedSuggestionProvider.suggest(ids.filter(id -> !prefix.contains(id)), last);
		return last.buildFuture();
	};

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
				.then(Commands.literal("practice")
						.executes(ctx -> {
							FishBattleManager.practice(ctx.getSource().getPlayerOrException(), null);
							return 1;
						})
						.then(Commands.argument("species", StringArgumentType.word()).suggests(SPECIES)
								.executes(ctx -> practice(ctx, StringArgumentType.getString(ctx, "species"), null, null))
								.then(Commands.argument("rarity", StringArgumentType.word()).suggests(RARITIES)
										.executes(ctx -> practice(ctx, StringArgumentType.getString(ctx, "species"),
												StringArgumentType.getString(ctx, "rarity"), null))
										.then(Commands.argument("extras", StringArgumentType.greedyString()).suggests(EXTRAS)
												.executes(ctx -> practice(ctx, StringArgumentType.getString(ctx, "species"),
														StringArgumentType.getString(ctx, "rarity"),
														StringArgumentType.getString(ctx, "extras")))))))
				.then(Commands.literal("info").executes(ctx -> info(ctx.getSource())))
				.then(Commands.literal("give")
						.requires(source -> source.hasPermission(2))
						.executes(ctx -> give(ctx, null, null, null))
						.then(Commands.argument("species", StringArgumentType.word()).suggests(SPECIES)
								.executes(ctx -> give(ctx, StringArgumentType.getString(ctx, "species"), null, null))
								.then(Commands.argument("rarity", StringArgumentType.word()).suggests(RARITIES)
										.executes(ctx -> give(ctx, StringArgumentType.getString(ctx, "species"),
												StringArgumentType.getString(ctx, "rarity"), null))
										.then(Commands.argument("extras", StringArgumentType.greedyString()).suggests(EXTRAS)
												.executes(ctx -> give(ctx, StringArgumentType.getString(ctx, "species"),
														StringArgumentType.getString(ctx, "rarity"),
														StringArgumentType.getString(ctx, "extras"))))))));

		// テスト用：魚がかかった状態からミニゲームを始める
		dispatcher.register(Commands.literal("fishfight")
				.then(Commands.literal("testcatch")
						.requires(source -> source.hasPermission(2))
						.then(Commands.argument("species", StringArgumentType.word()).suggests(SPECIES)
								.then(Commands.argument("rarity", StringArgumentType.word()).suggests(RARITIES)
										.executes(ctx -> testCatch(ctx, StringArgumentType.getString(ctx, "species"),
												StringArgumentType.getString(ctx, "rarity"), null))
										.then(Commands.argument("extras", StringArgumentType.greedyString()).suggests(EXTRAS)
												.executes(ctx -> testCatch(ctx, StringArgumentType.getString(ctx, "species"),
														StringArgumentType.getString(ctx, "rarity"),
														StringArgumentType.getString(ctx, "extras"))))))));

		dispatcher.register(Commands.literal("ff").executes(ctx -> help(ctx.getSource())).redirect(root));
	}

	private static int help(CommandSourceStack source) {
		for (String line : List.of("title", "fish", "skills", "challenge", "accept", "practice", "info", "give")) {
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
			@Nullable String extras) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		FishStats stats = rollFish(player, speciesId, rarityId, extras);
		player.getInventory().placeItemBackInInventory(FishItem.create(stats));
		ctx.getSource().sendSuccess(() -> FishFight.tr("command.given", stats.displayName()), false);
		return 1;
	}

	private static int practice(CommandContext<CommandSourceStack> ctx, String speciesId, @Nullable String rarityId,
			@Nullable String extras) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		FishStats wild = rollFish(player, speciesId, rarityId, extras);
		FishBattleManager.practice(player, new FishStats(wild.species(), wild.rarity(), wild.size(), wild.weight(), wild.hp(),
				wild.atk(), wild.def(), wild.spd(), wild.crit(), wild.personality(), wild.mutations(), wild.skills(), "", 0));
		return 1;
	}

	private static int testCatch(CommandContext<CommandSourceStack> ctx, String speciesId, String rarityId,
			@Nullable String extras) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		if (player.fishing != null) {
			player.fishing.discard();
		}
		FishingMinigameManager.start(player, rollFish(player, speciesId, rarityId, extras));
		return 1;
	}

	private static FishStats rollFish(ServerPlayer player, @Nullable String speciesId, @Nullable String rarityId,
			@Nullable String extras) {
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
		List<Mutation> mutations = new ArrayList<>();
		List<Skill> skills = new ArrayList<>();
		if (extras != null) {
			for (String token : extras.trim().split("\\s+")) {
				for (Mutation m : Mutation.values()) {
					if (m.id.equals(token)) {
						mutations.add(m);
					}
				}
				Skill skill = Skill.byId(token);
				if (skill != null) {
					skills.add(skill);
				}
			}
		}
		return FishGenerator.generate(player.getRandom(), species, rarity, player.getGameProfile().getName(), mutations, skills);
	}
}
