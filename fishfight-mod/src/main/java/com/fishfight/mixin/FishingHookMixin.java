package com.fishfight.mixin;

import com.fishfight.FishFightConfig;
import com.fishfight.fish.FishGenerator;
import com.fishfight.fish.FishStats;
import com.fishfight.fishing.FishingMinigameManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 魚が食いついている時に竿を引くと、バニラの釣果の代わりにミニゲームを始める。
 */
@Mixin(FishingHook.class)
public abstract class FishingHookMixin {
	@Shadow
	private int nibble;

	@Shadow
	@Nullable
	private Entity hookedIn;

	@Shadow
	@Final
	private int luck;

	@Inject(method = "retrieve", at = @At("HEAD"), cancellable = true)
	private void fishfight$startMinigame(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
		FishingHook self = (FishingHook) (Object) this;
		if (self.level().isClientSide || this.nibble <= 0 || this.hookedIn != null) {
			return;
		}
		if (!(self.getPlayerOwner() instanceof ServerPlayer player)) {
			return;
		}
		if (player.getRandom().nextFloat() * 100f >= FishFightConfig.get().minigameChance) {
			return;
		}
		FishStats stats = FishGenerator.roll(player.getRandom(), self.level().getBiome(self.blockPosition()),
				this.luck, player.getGameProfile().getName());
		FishingMinigameManager.start(player, stats);
		self.discard();
		cir.setReturnValue(1);
	}
}
