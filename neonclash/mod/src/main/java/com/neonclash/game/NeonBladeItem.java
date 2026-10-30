package com.neonclash.game;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * ネオンブレード。左クリックでバニラの攻撃の代わりに「溜め → 斬撃」を行う（当たれば一撃キル）。
 * 実際の判定は {@link SwordService}。
 */
public class NeonBladeItem extends Item {
    public NeonBladeItem(Properties props) {
        super(props.rarity(Rarity.EPIC));
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return false;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
