package com.neonclash.game;

import com.neonclash.config.NCConfig;
import com.neonclash.core.combat.KillStreak;
import com.neonclash.core.combat.SwordCharge;
import com.neonclash.core.movement.BlinkCharges;
import com.neonclash.core.round.Team;
import com.neonclash.core.skill.LastStand;
import com.neonclash.core.skill.ParrySkill;
import com.neonclash.core.style.StyleMeter;
import com.neonclash.core.upgrade.UpgradeStat;
import com.neonclash.core.upgrade.UpgradeStats;
import net.minecraft.world.entity.LivingEntity;

import javax.annotation.Nullable;
import java.util.UUID;

/** サーバー側で持つ、戦闘者1人分の状態。プレイヤー以外（GameTest のモブ等）も扱える。 */
public final class Combatant {
    public final UUID id;
    LivingEntity entity;

    public final BlinkCharges blink;
    public final SwordCharge sword = new SwordCharge();
    public final ParrySkill parry = new ParrySkill();
    public final LastStand lastStand = new LastStand();
    public final KillStreak killStreak = new KillStreak();
    public final StyleMeter style;
    public final UpgradeStats upgrades = new UpgradeStats();

    @Nullable
    public Team team;
    /** 試合に参加中か（移動強化・HUD 同期などの条件） */
    public boolean inMatch;
    /** このラウンドで脱落済み */
    public boolean eliminated;

    public int hitStopTicks;
    public long lastBlinkTick = Long.MIN_VALUE;
    /** 直近のビートに乗った射撃の tick（弾にボーナスを付けるため） */
    public long lastOnBeatFireTick = Long.MIN_VALUE;
    /** TaCZ の命中を処理した回数（GameTest・デバッグ用） */
    public int gunHitsTaken;
    public int gunHitsDealt;

    Combatant(LivingEntity entity) {
        this.id = entity.getUUID();
        this.entity = entity;
        this.blink = new BlinkCharges(NCConfig.SERVER.blinkCharges.get());
        this.style = new StyleMeter(NCConfig.SERVER.styleSettings());
    }

    public LivingEntity entity() {
        return entity;
    }

    /** 強化を反映した最大ブリンク数に合わせる。 */
    public void refreshBlinkMax() {
        int max = NCConfig.SERVER.blinkCharges.get() + (int) Math.round(upgrades.get(UpgradeStat.BLINK_CHARGES));
        if (max != blink.maxCharges()) {
            blink.setMaxCharges(Math.max(1, max));
        }
    }

    /** 新しいライフ（ラウンド開始・リスポーン）。 */
    public void newLife() {
        refreshBlinkMax();
        blink.refill();
        sword.reset();
        parry.reset();
        lastStand.rearm();
        hitStopTicks = 0;
        eliminated = false;
        lastBlinkTick = Long.MIN_VALUE;
    }

    public void tick() {
        blink.tick();
        parry.tick();
        if (hitStopTicks > 0) {
            hitStopTicks--;
        }
    }

    public boolean recentlyBlinked(long now) {
        return lastBlinkTick != Long.MIN_VALUE && now - lastBlinkTick <= NCConfig.SERVER.blinkKillWindowTicks.get();
    }

    public int teamColor() {
        NCConfig.Server c = NCConfig.SERVER;
        if (team == Team.RED) return NCConfig.parseColor(c.redColor.get(), 0xFF2E63);
        if (team == Team.BLUE) return NCConfig.parseColor(c.blueColor.get(), 0x00E5FF);
        return NCConfig.parseColor(c.neutralColor.get(), 0xF8F32B);
    }
}
