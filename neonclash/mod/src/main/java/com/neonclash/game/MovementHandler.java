package com.neonclash.game;

import com.neonclash.NeonClash;
import com.neonclash.config.NCConfig;
import com.neonclash.core.upgrade.UpgradeStat;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * 移動速度・ジャンプ力の強化。
 * 移動速度はサーバーで属性モディファイアとして付ける（クライアントへ自動同期される）。
 * ジャンプはクライアント側で計算されるので、ジャンプイベントは両サイドで処理し、
 * 「速度モディファイアが付いているか」を強化対象の印として使う（モディファイアは同期されるので両サイドで一致する）。
 */
@Mod.EventBusSubscriber(modid = NeonClash.MODID)
public final class MovementHandler {
    public static final UUID SPEED_ID = UUID.fromString("6f1c1d2e-55a1-4f0e-9a51-2d1bb0c7a101");
    private static final String SPEED_NAME = "neonclash.speed";
    /** クライアント側：自分のジャンプ強化倍率（HUD パケットで同期。共通クラスに置いてクライアント専用クラスを参照しない） */
    public static volatile double clientJumpUpgrade = 1.0;

    private MovementHandler() {
    }

    /** サーバー tick ごとに呼ぶ。 */
    public static void apply(Combatant c) {
        LivingEntity e = c.entity();
        AttributeInstance attr = e.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attr == null) {
            return;
        }
        boolean active = !NCConfig.SERVER.movementOnlyInMatch.get() || c.inMatch;
        double wanted = active ? NCConfig.SERVER.speedMultiplier.get() * c.upgrades.increaseMultiplier(UpgradeStat.MOVE_SPEED) - 1.0 : Double.NaN;
        AttributeModifier current = attr.getModifier(SPEED_ID);
        if (Double.isNaN(wanted)) {
            if (current != null) attr.removeModifier(SPEED_ID);
            return;
        }
        if (current == null || Math.abs(current.getAmount() - wanted) > 1.0e-6) {
            if (current != null) attr.removeModifier(SPEED_ID);
            attr.addTransientModifier(new AttributeModifier(SPEED_ID, SPEED_NAME, wanted, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    @SubscribeEvent
    public static void onJump(LivingEvent.LivingJumpEvent event) {
        LivingEntity e = event.getEntity();
        AttributeInstance attr = e.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attr == null || attr.getModifier(SPEED_ID) == null) {
            return;
        }
        double mult = NCConfig.SERVER.jumpMultiplier.get();
        if (!e.level().isClientSide()) {
            Combatant c = Combatants.get(e);
            if (c != null) mult *= c.upgrades.increaseMultiplier(UpgradeStat.JUMP);
        } else {
            mult *= clientJumpUpgrade;
        }
        Vec3 v = e.getDeltaMovement();
        e.setDeltaMovement(v.x, v.y * mult, v.z);
    }
}
