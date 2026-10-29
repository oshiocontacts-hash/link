package com.fishfight.item;

import com.fishfight.FishFight;
import com.fishfight.fish.FishGenerator;
import com.fishfight.fish.FishRarity;
import com.fishfight.fish.FishSpecies;
import com.fishfight.fish.FishStats;
import com.fishfight.fish.Mutation;
import com.fishfight.registry.ModComponents;
import com.fishfight.registry.ModItems;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * 釣った魚。個体ステータスは {@link ModComponents#FISH_STATS} に入っている。
 */
public class FishItem extends Item {
	private final FishSpecies species;

	public FishItem(FishSpecies species, Properties properties) {
		super(properties);
		this.species = species;
	}

	public FishSpecies species() {
		return species;
	}

	public static ItemStack create(FishStats stats) {
		ItemStack stack = new ItemStack(ModItems.fish(stats.species()));
		stack.set(ModComponents.FISH_STATS, stats);
		return stack;
	}

	@Override
	public Component getName(ItemStack stack) {
		FishStats stats = stack.get(ModComponents.FISH_STATS);
		if (stats == null) {
			return super.getName(stack);
		}
		// 外側を空にしておくと、バニラのレア度色で上書きされない
		return Component.empty().append(stats.displayName());
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
		FishStats stats = stack.get(ModComponents.FISH_STATS);
		if (stats == null) {
			lines.add(FishFight.tr("tooltip.unidentified").withStyle(ChatFormatting.DARK_GRAY));
			return;
		}
		stats.appendTooltip(lines);
		lines.add(FishFight.tr("tooltip.hint").withStyle(ChatFormatting.DARK_AQUA));
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		FishStats stats = stack.get(ModComponents.FISH_STATS);
		return stats != null && (stats.rarity().isAtLeast(FishRarity.LEGENDARY) || stats.has(Mutation.GOLDEN));
	}

	@Override
	public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
		// クリエイティブ等で入手した「まだ個体が決まっていない魚」はここで個体を決める
		if (!level.isClientSide && !stack.has(ModComponents.FISH_STATS)) {
			stack.set(ModComponents.FISH_STATS, FishGenerator.generate(level.random, species, null, entity.getName().getString()));
		}
	}
}
