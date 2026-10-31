package com.neonclash.config;

import com.neonclash.core.beat.BeatClock;
import com.neonclash.core.combat.KillStreak;
import com.neonclash.core.movement.BlinkResolver;
import com.neonclash.core.round.RoundEngine;
import com.neonclash.core.skill.ParrySkill;
import com.neonclash.core.style.StyleEvent;
import com.neonclash.core.style.StyleMeter;
import com.neonclash.core.style.StyleRank;
import com.neonclash.core.upgrade.RarityWeights;
import com.neonclash.core.upgrade.UpgradeDef;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.ForgeConfigSpec.BooleanValue;
import net.minecraftforge.common.ForgeConfigSpec.ConfigValue;
import net.minecraftforge.common.ForgeConfigSpec.DoubleValue;
import net.minecraftforge.common.ForgeConfigSpec.IntValue;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * すべての調整値。ゲームプレイは SERVER（ワールドごと・クライアントへ自動同期）、見た目は CLIENT。
 * ファイル: world/serverconfig/neonclash-server.toml, config/neonclash-client.toml
 */
public final class NCConfig {
    public static final Server SERVER;
    public static final ForgeConfigSpec SERVER_SPEC;
    public static final Client CLIENT;
    public static final ForgeConfigSpec CLIENT_SPEC;

    static {
        Pair<Server, ForgeConfigSpec> s = new ForgeConfigSpec.Builder().configure(Server::new);
        SERVER = s.getLeft();
        SERVER_SPEC = s.getRight();
        Pair<Client, ForgeConfigSpec> c = new ForgeConfigSpec.Builder().configure(Client::new);
        CLIENT = c.getLeft();
        CLIENT_SPEC = c.getRight();
    }

    private NCConfig() {
    }

    public static final class Server {
        // movement
        public final BooleanValue movementOnlyInMatch;
        public final DoubleValue speedMultiplier;
        public final DoubleValue jumpMultiplier;
        // blink
        public final DoubleValue blinkDistance;
        public final IntValue blinkCharges;
        public final IntValue blinkCooldownTicks;
        public final DoubleValue blinkStep;
        public final DoubleValue blinkMaxStepUp;
        public final DoubleValue blinkMaxCeilingDrop;
        public final DoubleValue blinkMinDistance;
        public final DoubleValue blinkExitVelocity;
        public final IntValue blinkKillWindowTicks;
        // sword
        public final IntValue swordWindupTicks;
        public final IntValue swordRecoveryTicks;
        public final IntValue swordInterruptLockoutTicks;
        public final DoubleValue swordRange;
        public final DoubleValue swordConeDegrees;
        public final DoubleValue swordLungeVelocity;
        // feel
        public final IntValue hitStopTicks;
        public final DoubleValue hitShakeDegrees;
        public final DoubleValue hurtShakeDegrees;
        public final DoubleValue killShakeDegrees;
        public final IntValue shakeTicks;
        public final DoubleValue hitSoundVolume;
        public final DoubleValue hitSoundPitch;
        public final DoubleValue hitComboPitchStep;
        public final DoubleValue hitComboMaxPitch;
        public final IntValue hitComboWindowTicks;
        public final DoubleValue hurtSoundVolume;
        public final DoubleValue shotSoundVolume;
        public final DoubleValue shotPitchVariance;
        public final DoubleValue killSoundVolume;
        public final DoubleValue killBasePitch;
        public final DoubleValue killPitchStep;
        public final DoubleValue killMaxPitch;
        public final IntValue killStreakWindowTicks;
        public final IntValue hitParticleCount;
        public final IntValue killParticleCount;
        public final DoubleValue particleScale;
        public final DoubleValue particleSpread;
        public final DoubleValue fxViewDistance;
        // knockback
        public final DoubleValue gunKnockback;
        public final DoubleValue gunKnockbackLift;
        public final DoubleValue knockbackBeatMultiplier;
        public final DoubleValue knockbackHeadMultiplier;
        public final DoubleValue swordKnockback;
        public final DoubleValue swordKnockbackLift;
        public final DoubleValue parryKnockback;
        public final DoubleValue parryKnockbackLift;
        public final DoubleValue killBlastRadius;
        public final DoubleValue killBlastKnockback;
        public final DoubleValue killBlastLift;
        public final DoubleValue knockbackMaxSpeed;
        // kill reward
        public final DoubleValue killHeal;
        public final IntValue killSpeedTicks;
        public final IntValue killSpeedAmplifier;
        // slow
        public final DoubleValue globalSlowFactor;
        public final IntValue globalSlowTicks;
        public final DoubleValue parrySlowFactor;
        public final IntValue parrySlowTicks;
        public final DoubleValue parryZoneRadius;
        public final DoubleValue parryZoneFactor;
        public final IntValue parryZoneTicks;
        public final DoubleValue lastStandSlowFactor;
        public final IntValue lastStandSlowTicks;
        public final DoubleValue lastStandHealth;
        public final DoubleValue slowMoClientStrength;
        // beam
        public final BooleanValue beamEnabled;
        public final DoubleValue beamMaxLength;
        public final DoubleValue beamViewDistance;
        // parry
        public final IntValue parryWindowTicks;
        public final IntValue parryCooldownTicks;
        public final DoubleValue parrySuccessCooldownMultiplier;
        // beat
        public final BooleanValue beatEnabled;
        public final DoubleValue beatBpm;
        public final DoubleValue beatToleranceMs;
        public final DoubleValue beatDamageBonus;
        public final DoubleValue beatCooldownMultiplier;
        public final IntValue beatMaxLatencyCompensationMs;
        public final IntValue beatResyncSeconds;
        // style
        public final ConfigValue<List<? extends Integer>> styleThresholds;
        public final IntValue styleMaxPoints;
        public final Map<StyleEvent, IntValue> styleDeltas = new EnumMap<>(StyleEvent.class);
        // round
        public final IntValue roundSeconds;
        public final IntValue shrinkStartSeconds;
        public final IntValue intermissionSeconds;
        public final IntValue winsToWin;
        public final IntValue maxRounds;
        public final IntValue minPlayersPerTeam;
        public final IntValue maxPlayersPerTeam;
        public final DoubleValue arenaRadius;
        public final DoubleValue arenaEndRadius;
        public final DoubleValue spawnOffset;
        public final DoubleValue outsideDamagePerSecond;
        public final ConfigValue<String> handgunId;
        public final IntValue handgunAmmo;
        public final IntValue reserveAmmo;
        public final IntValue spareAmmo;
        public final ConfigValue<String> spareAmmoItem;
        public final BooleanValue friendlyFire;
        public final IntValue hudSyncInterval;
        // upgrades
        public final IntValue upgradeChoices;
        public final BooleanValue upgradeDistinctCategories;
        public final ConfigValue<List<? extends String>> upgradePool;
        public final Map<StyleRank, ConfigValue<List<? extends Number>>> rarityWeights = new EnumMap<>(StyleRank.class);
        public final DoubleValue upgradeReductionFloor;
        // team colors
        public final ConfigValue<String> redColor;
        public final ConfigValue<String> blueColor;
        public final ConfigValue<String> neutralColor;

        Server(ForgeConfigSpec.Builder b) {
            b.comment("移動").push("movement");
            movementOnlyInMatch = b.comment("true なら移動強化は試合中のプレイヤーだけに掛かる").define("onlyInMatch", true);
            speedMultiplier = b.comment("移動速度の倍率（バニラ=1.0）").defineInRange("speedMultiplier", 1.3, 0.1, 5.0);
            jumpMultiplier = b.comment("ジャンプの初速の倍率（バニラ=1.0）").defineInRange("jumpMultiplier", 1.3, 0.1, 5.0);
            b.pop();

            b.comment("ブリンク（視線方向への短距離ワープ）").push("blink");
            blinkDistance = b.comment("最大距離（ブロック）").defineInRange("distance", 6.0, 0.5, 32.0);
            blinkCharges = b.comment("最大チャージ数").defineInRange("charges", 2, 1, 10);
            blinkCooldownTicks = b.comment("1チャージの回復時間（tick, 20=1秒）。チャージは1つずつ順番に回復").defineInRange("cooldownTicks", 50, 0, 1200);
            blinkStep = b.comment("経路の当たり判定の刻み（小さいほど正確）").defineInRange("step", 0.25, 0.05, 1.0);
            blinkMaxStepUp = b.comment("床に当たったとき持ち上げてよい高さ").defineInRange("maxStepUp", 0.6, 0.0, 2.0);
            blinkMaxCeilingDrop = b.comment("天井に当たったとき下げてよい高さ").defineInRange("maxCeilingDrop", 0.6, 0.0, 2.0);
            blinkMinDistance = b.comment("これ未満しか進めないなら失敗（チャージ消費なし）").defineInRange("minDistance", 0.75, 0.0, 8.0);
            blinkExitVelocity = b.comment("ワープ後に視線方向へ残す速度（勢い）").defineInRange("exitVelocity", 0.35, 0.0, 3.0);
            blinkKillWindowTicks = b.comment("ブリンク後この tick 以内のキルを「ブリンク直後のキル」とする").defineInRange("killWindowTicks", 30, 0, 200);
            b.pop();

            b.comment("剣（当たれば一撃キル）").push("sword");
            swordWindupTicks = b.comment("振る前の溜め").defineInRange("windupTicks", 6, 1, 100);
            swordRecoveryTicks = b.comment("斬撃後の硬直").defineInRange("recoveryTicks", 12, 0, 200);
            swordInterruptLockoutTicks = b.comment("溜めを撃たれて潰された後、溜め直せるまで").defineInRange("interruptLockoutTicks", 4, 0, 100);
            swordRange = b.comment("射程（目の位置からの距離, ブロック）").defineInRange("range", 2.8, 0.5, 10.0);
            swordConeDegrees = b.comment("当たり判定の円錐の半角（度）").defineInRange("coneDegrees", 45.0, 1.0, 180.0);
            swordLungeVelocity = b.comment("斬撃時の前方への踏み込み速度").defineInRange("lungeVelocity", 0.45, 0.0, 3.0);
            b.pop();

            b.comment("手応え").push("feel");
            hitStopTicks = b.comment("ヒットストップ：被弾側の入力を止める tick（押された勢いは止めない）").defineInRange("hitStopTicks", 3, 0, 40);
            hitShakeDegrees = b.comment("命中時に攻撃側のカメラを揺らす強さ（度）").defineInRange("hitShakeDegrees", 0.8, 0.0, 20.0);
            hurtShakeDegrees = b.comment("被弾時に自分のカメラを揺らす強さ（度）").defineInRange("hurtShakeDegrees", 1.6, 0.0, 20.0);
            killShakeDegrees = b.comment("キル時のカメラの揺れ（度）").defineInRange("killShakeDegrees", 3.0, 0.0, 20.0);
            shakeTicks = b.comment("カメラの揺れの長さ").defineInRange("shakeTicks", 5, 0, 40);
            hitSoundVolume = b.defineInRange("hitSoundVolume", 0.9, 0.0, 4.0);
            hitSoundPitch = b.comment("命中音の基本ピッチ").defineInRange("hitSoundPitch", 1.0, 0.5, 2.0);
            hitComboPitchStep = b.comment("連続命中1回ごとのピッチ上昇").defineInRange("hitComboPitchStep", 0.12, 0.0, 1.0);
            hitComboMaxPitch = b.defineInRange("hitComboMaxPitch", 1.5, 0.5, 2.0);
            hitComboWindowTicks = b.comment("この tick 以内の次の命中を連続命中とする").defineInRange("hitComboWindowTicks", 30, 1, 400);
            hurtSoundVolume = b.comment("撃たれた側に鳴る音").defineInRange("hurtSoundVolume", 0.8, 0.0, 4.0);
            shotSoundVolume = b.comment("TaCZ の銃声に重ねるネオンの発射音（1 を超えると遠くまで届く）").defineInRange("shotSoundVolume", 1.6, 0.0, 8.0);
            shotPitchVariance = b.comment("発射音のピッチのばらつき").defineInRange("shotPitchVariance", 0.08, 0.0, 1.0);
            killSoundVolume = b.defineInRange("killSoundVolume", 1.0, 0.0, 4.0);
            killBasePitch = b.comment("キル音の基本ピッチ").defineInRange("killBasePitch", 1.0, 0.5, 2.0);
            killPitchStep = b.comment("連続キル1回ごとのピッチ上昇").defineInRange("killPitchStep", 0.19, 0.0, 1.0);
            killMaxPitch = b.defineInRange("killMaxPitch", 2.0, 0.5, 2.0);
            killStreakWindowTicks = b.comment("この tick 以内の次のキルを連続キルとする").defineInRange("killStreakWindowTicks", 160, 1, 2400);
            hitParticleCount = b.comment("バニラのダストの数。近くで大きな四角になるので既定は 0（演出は CLIENT の fx*）").defineInRange("hitParticleCount", 0, 0, 200);
            killParticleCount = b.defineInRange("killParticleCount", 0, 0, 500);
            particleScale = b.defineInRange("particleScale", 1.2, 0.1, 4.0);
            particleSpread = b.defineInRange("particleSpread", 0.25, 0.0, 3.0);
            fxViewDistance = b.comment("演出を送るプレイヤーまでの距離").defineInRange("fxViewDistance", 96.0, 8.0, 512.0);
            b.pop();

            b.comment("ノックバック（速さの単位はブロック/tick。地上で止まるまでに速さの約 2 倍進む）").push("knockback");
            gunKnockback = b.comment("弾の命中で押す強さ").defineInRange("gun", 0.42, 0.0, 5.0);
            gunKnockbackLift = b.comment("弾の命中で浮かせる強さ").defineInRange("gunLift", 0.12, 0.0, 3.0);
            knockbackBeatMultiplier = b.comment("ビートに乗った弾の倍率").defineInRange("beatMultiplier", 1.6, 0.0, 10.0);
            knockbackHeadMultiplier = b.comment("ヘッドショットの倍率").defineInRange("headMultiplier", 1.3, 0.0, 10.0);
            swordKnockback = b.comment("剣を受けてラストスタンドで耐えた相手を押す強さ").defineInRange("sword", 1.3, 0.0, 5.0);
            swordKnockbackLift = b.defineInRange("swordLift", 0.35, 0.0, 3.0);
            parryKnockback = b.comment("パリィ成功で周りの敵を押す強さ（範囲は slow.parryZoneRadius）").defineInRange("parry", 0.9, 0.0, 5.0);
            parryKnockbackLift = b.defineInRange("parryLift", 0.25, 0.0, 3.0);
            killBlastRadius = b.comment("キルの爆風の半径（0 で無効）。敵味方を問わず押す").defineInRange("killBlastRadius", 4.0, 0.0, 32.0);
            killBlastKnockback = b.defineInRange("killBlast", 0.5, 0.0, 5.0);
            killBlastLift = b.defineInRange("killBlastLift", 0.2, 0.0, 3.0);
            knockbackMaxSpeed = b.comment("押された速さの上限").defineInRange("maxSpeed", 1.4, 0.1, 10.0);
            b.pop();

            b.comment("キル報酬").push("killReward");
            killHeal = b.comment("回復量（ハート1個=2）").defineInRange("heal", 6.0, 0.0, 40.0);
            killSpeedTicks = b.comment("スピードアップの長さ").defineInRange("speedTicks", 40, 0, 600);
            killSpeedAmplifier = b.comment("スピードアップのレベル（0=I）").defineInRange("speedAmplifier", 1, 0, 9);
            b.pop();

            b.comment("スロー演出").push("slow");
            globalSlowFactor = b.comment("ラウンド決着キル時の全員スローの速度倍率").defineInRange("globalFactor", 0.3, 0.01, 1.0);
            globalSlowTicks = b.comment("全員スローの長さ（10=0.5秒）").defineInRange("globalTicks", 10, 0, 200);
            parrySlowFactor = b.comment("パリィ成功時、攻撃してきた相手に掛けるスロー倍率").defineInRange("parryTargetFactor", 0.4, 0.01, 1.0);
            parrySlowTicks = b.defineInRange("parryTargetTicks", 30, 0, 400);
            parryZoneRadius = b.comment("パリィ成功時に自分の周りに張る範囲スローの半径（0 で無効）").defineInRange("parryZoneRadius", 3.5, 0.0, 32.0);
            parryZoneFactor = b.defineInRange("parryZoneFactor", 0.35, 0.01, 1.0);
            parryZoneTicks = b.defineInRange("parryZoneTicks", 30, 0, 400);
            lastStandSlowFactor = b.comment("ラストスタンド発動時、自分以外に掛けるスロー倍率").defineInRange("lastStandFactor", 0.5, 0.01, 1.0);
            lastStandSlowTicks = b.comment("ラストスタンドのスロー時間（30=1.5秒）").defineInRange("lastStandTicks", 30, 0, 600);
            lastStandHealth = b.comment("ラストスタンドで踏みとどまった後の体力").defineInRange("lastStandHealth", 2.0, 0.5, 40.0);
            slowMoClientStrength = b.comment("クライアント演出（彩度・音程・FOV）の強さ 0..1").defineInRange("clientStrength", 1.0, 0.0, 1.0);
            b.pop();

            b.comment("弾の軌道に残るビーム（色はチームカラー）。太さや濃さは CLIENT の visual").push("beam");
            beamEnabled = b.define("enabled", true);
            beamMaxLength = b.comment("1 発のビームを追う最大距離（ブロック）").defineInRange("maxLength", 96.0, 1.0, 512.0);
            beamViewDistance = b.comment("ビームを送るプレイヤーまでの距離").defineInRange("viewDistance", 96.0, 8.0, 512.0);
            b.pop();

            b.comment("パリィ").push("parry");
            parryWindowTicks = b.comment("受付時間").defineInRange("windowTicks", 6, 1, 60);
            parryCooldownTicks = b.defineInRange("cooldownTicks", 100, 0, 2400);
            parrySuccessCooldownMultiplier = b.comment("成功時、残りクールダウンに掛ける倍率").defineInRange("successCooldownMultiplier", 0.5, 0.0, 1.0);
            b.pop();

            b.comment("ビート（オプション）。外しても罰は無い").push("beat");
            beatEnabled = b.comment("false でビート機能を丸ごとオフ（HUD 表示・ボーナスとも）").define("enabled", true);
            beatBpm = b.defineInRange("bpm", 128.0, 30.0, 300.0);
            beatToleranceMs = b.comment("判定の猶予（前後, ms）").defineInRange("toleranceMs", 60.0, 0.0, 500.0);
            beatDamageBonus = b.comment("ビートに乗った射撃のダメージ増加（0.2=+20%）").defineInRange("damageBonus", 0.2, 0.0, 5.0);
            beatCooldownMultiplier = b.comment("ビートに乗ったブリンクのクールダウン倍率").defineInRange("cooldownMultiplier", 0.6, 0.0, 1.0);
            beatMaxLatencyCompensationMs = b.comment("判定時に差し引く通信遅延（ping/2）の上限").defineInRange("maxLatencyCompensationMs", 150, 0, 1000);
            beatResyncSeconds = b.comment("クライアントへビート位相を再送する間隔").defineInRange("resyncSeconds", 10, 1, 600);
            b.pop();

            b.comment("スタイルランク D→C→B→A→S").push("style");
            styleThresholds = b.comment("C, B, A, S に上がるのに必要なポイント（昇順4つ）")
                    .defineList("thresholds", List.of(60, 160, 300, 480), o -> o instanceof Integer i && i >= 0);
            styleMaxPoints = b.defineInRange("maxPoints", 1000, 1, 100000);
            b.comment("イベントごとのポイント増減").push("points");
            int[] defaults = {60, 60, 70, 90, 10, 40, -15, -60};
            StyleEvent[] events = StyleEvent.values();
            for (int i = 0; i < events.length; i++) {
                styleDeltas.put(events[i], b.defineInRange(events[i].name().toLowerCase(java.util.Locale.ROOT), defaults[i], -100000, 100000));
            }
            b.pop();
            b.pop();

            b.comment("ラウンド").push("round");
            roundSeconds = b.defineInRange("roundSeconds", 90, 5, 3600);
            shrinkStartSeconds = b.comment("ラウンド開始からエリア縮小が始まるまで").defineInRange("shrinkStartSeconds", 60, 0, 3600);
            intermissionSeconds = b.comment("ラウンド間（強化選択）").defineInRange("intermissionSeconds", 5, 1, 120);
            winsToWin = b.comment("何本先取か").defineInRange("winsToWin", 3, 1, 50);
            maxRounds = b.comment("引き分けが続いた場合の最大ラウンド数（0=無制限）").defineInRange("maxRounds", 9, 0, 200);
            minPlayersPerTeam = b.defineInRange("minPlayersPerTeam", 1, 1, 16);
            maxPlayersPerTeam = b.defineInRange("maxPlayersPerTeam", 4, 1, 16);
            arenaRadius = b.comment("アリーナ半径（/pvp arena set の位置が中心）").defineInRange("arenaRadius", 28.0, 4.0, 512.0);
            arenaEndRadius = b.comment("縮小後の半径").defineInRange("arenaEndRadius", 8.0, 1.0, 512.0);
            spawnOffset = b.comment("中心から各チームのスポーンまでの距離（赤は -X、青は +X）").defineInRange("spawnOffset", 12.0, 0.0, 512.0);
            outsideDamagePerSecond = b.comment("エリア外にいるときのダメージ/秒").defineInRange("outsideDamagePerSecond", 3.0, 0.0, 100.0);
            handgunId = b.comment("配布する TaCZ のハンドガン ID").define("handgunId", "tacz:glock_17");
            handgunAmmo = b.comment("装填数").defineInRange("handgunAmmo", 17, 0, 1000);
            reserveAmmo = b.comment("銃の中の予備弾薬（手持ちの弾なしでリロードできる。ラウンドごとに補充）。0 なら下の spareAmmo を配る")
                    .defineInRange("reserveAmmo", 9999, 0, 9999);
            spareAmmo = b.comment("予備弾薬の個数（reserveAmmo が 0 のときだけ配る）").defineInRange("spareAmmo", 68, 0, 6400);
            spareAmmoItem = b.comment("予備弾薬の TaCZ 弾薬 ID（空なら配らない）").define("spareAmmoId", "tacz:9mm");
            friendlyFire = b.define("friendlyFire", false);
            hudSyncInterval = b.comment("HUD 同期間隔（tick）").defineInRange("hudSyncInterval", 2, 1, 40);
            b.pop();

            b.comment("ラウンド間の強化").push("upgrades");
            upgradeChoices = b.defineInRange("choices", 3, 1, 6);
            upgradeDistinctCategories = b.comment("3択を別々の枠から出す").define("distinctCategories", true);
            upgradeReductionFloor = b.comment("「-x%」系強化を重ねたときの倍率の下限").defineInRange("reductionFloor", 0.25, 0.0, 1.0);
            upgradePool = b.comment("id|CATEGORY(WEAPON,SWORD,MOVEMENT,ULTIMATE)|RARITY(COMMON,RARE,EPIC,LEGENDARY)|STAT|value|stackable",
                            "STAT: " + java.util.Arrays.toString(com.neonclash.core.upgrade.UpgradeStat.values()))
                    .defineList("pool", defaultPool(), o -> o instanceof String s && isValidUpgrade(s));
            b.comment("スタイルランクごとのレア度の重み [COMMON, RARE, EPIC, LEGENDARY]").push("rarityWeights");
            double[][] w = {{70, 25, 5, 0}, {55, 30, 12, 3}, {40, 35, 20, 5}, {25, 35, 28, 12}, {10, 30, 35, 25}};
            for (StyleRank r : StyleRank.values()) {
                List<Number> def = new ArrayList<>();
                for (double d : w[r.ordinal()]) def.add(d);
                rarityWeights.put(r, b.defineList(r.name(), def, o -> o instanceof Number n && n.doubleValue() >= 0));
            }
            b.pop();
            b.pop();

            b.comment("チームカラー（#RRGGBB）").push("colors");
            redColor = b.define("red", "#FF2E63");
            blueColor = b.define("blue", "#00E5FF");
            neutralColor = b.comment("チーム外（試合外の練習など）").define("neutral", "#F8F32B");
            b.pop();
        }

        private static boolean isValidUpgrade(String s) {
            try {
                UpgradeDef.parse(s);
                return true;
            } catch (RuntimeException e) {
                return false;
            }
        }

        private static List<String> defaultPool() {
            return List.of(
                    "gun_dmg_s|WEAPON|COMMON|GUN_DAMAGE|0.10|true",
                    "gun_dmg_m|WEAPON|RARE|GUN_DAMAGE|0.20|true",
                    "beat_dmg|WEAPON|EPIC|BEAT_DAMAGE|0.30|false",
                    "gun_dmg_l|WEAPON|LEGENDARY|GUN_DAMAGE|0.45|false",
                    "blade_quick_s|SWORD|COMMON|SWORD_WINDUP|0.15|true",
                    "blade_reach|SWORD|RARE|SWORD_RANGE|0.6|true",
                    "blade_quick_l|SWORD|EPIC|SWORD_WINDUP|0.35|false",
                    "blade_long|SWORD|LEGENDARY|SWORD_RANGE|1.4|false",
                    "stride|MOVEMENT|COMMON|MOVE_SPEED|0.06|true",
                    "spring|MOVEMENT|COMMON|JUMP|0.10|true",
                    "blink_far|MOVEMENT|RARE|BLINK_DISTANCE|1.5|true",
                    "blink_cd|MOVEMENT|EPIC|BLINK_COOLDOWN|0.25|false",
                    "blink_extra|MOVEMENT|LEGENDARY|BLINK_CHARGES|1|false",
                    "parry_wide|ULTIMATE|COMMON|PARRY_WINDOW|2|true",
                    "vamp|ULTIMATE|RARE|KILL_HEAL|4|true",
                    "stand_long|ULTIMATE|EPIC|LAST_STAND_DURATION|30|false",
                    "stand_tough|ULTIMATE|LEGENDARY|LAST_STAND_HEALTH|8|false");
        }

        // ---- core 用の設定オブジェクトへの変換 ----

        public BlinkResolver.Settings blinkSettings(double extraDistance) {
            return new BlinkResolver.Settings(blinkDistance.get() + extraDistance, blinkStep.get(), blinkMaxStepUp.get(),
                    blinkMaxCeilingDrop.get(), blinkMinDistance.get());
        }

        public KillStreak.Settings killStreakSettings() {
            return new KillStreak.Settings(killStreakWindowTicks.get(), killBasePitch.get().floatValue(),
                    killPitchStep.get().floatValue(), killMaxPitch.get().floatValue());
        }

        public KillStreak.Settings hitComboSettings() {
            return new KillStreak.Settings(hitComboWindowTicks.get(), hitSoundPitch.get().floatValue(),
                    hitComboPitchStep.get().floatValue(), hitComboMaxPitch.get().floatValue());
        }

        public ParrySkill.Settings parrySettings(int extraWindow) {
            return new ParrySkill.Settings(parryWindowTicks.get() + extraWindow, parryCooldownTicks.get(), parrySuccessCooldownMultiplier.get());
        }

        public StyleMeter.Settings styleSettings() {
            List<? extends Integer> t = styleThresholds.get();
            int[] th = new int[StyleRank.values().length - 1];
            for (int i = 0; i < th.length; i++) {
                th[i] = i < t.size() ? t.get(i) : (i == 0 ? 0 : th[i - 1]);
            }
            for (int i = 1; i < th.length; i++) th[i] = Math.max(th[i], th[i - 1]);
            Map<StyleEvent, Integer> d = new EnumMap<>(StyleEvent.class);
            styleDeltas.forEach((k, v) -> d.put(k, v.get()));
            return new StyleMeter.Settings(th, styleMaxPoints.get(), d);
        }

        public RoundEngine.Settings roundSettings() {
            int round = roundSeconds.get() * 20;
            return new RoundEngine.Settings(round, Math.min(round, shrinkStartSeconds.get() * 20),
                    intermissionSeconds.get() * 20, winsToWin.get(), maxRounds.get());
        }

        public RarityWeights rarityWeights() {
            RarityWeights rw = new RarityWeights();
            rarityWeights.forEach((rank, v) -> {
                List<? extends Number> l = v.get();
                double[] arr = new double[com.neonclash.core.upgrade.Rarity.values().length];
                for (int i = 0; i < arr.length; i++) arr[i] = i < l.size() ? l.get(i).doubleValue() : 0;
                rw.set(rank, arr);
            });
            return rw;
        }

        public List<UpgradeDef> upgradeDefs() {
            List<UpgradeDef> out = new ArrayList<>();
            for (String s : upgradePool.get()) {
                out.add(UpgradeDef.parse(s));
            }
            return out;
        }

        public BeatClock newBeatClock(double nowMs) {
            return new BeatClock(beatBpm.get(), nowMs, beatToleranceMs.get(), beatEnabled.get());
        }
    }

    public static final class Client {
        public final DoubleValue cameraShakeScale;
        public final BooleanValue slowMoDesaturate;
        public final DoubleValue slowMoFovMultiplier;
        public final DoubleValue slowMoSoundPitch;
        public final IntValue slowMoFadeTicks;
        public final DoubleValue crosshairPulseScale;
        public final DoubleValue crosshairBaseSize;
        public final IntValue crosshairPulseTicks;
        public final DoubleValue hudScale;
        public final IntValue rankPopTicks;
        public final DoubleValue rankPopScale;
        public final BooleanValue showBeat;
        public final DoubleValue hitStopInputFreeze;
        public final IntValue hitMarkerTicks;
        public final IntValue killMarkerTicks;
        public final IntValue flashTicks;
        public final DoubleValue beatClickVolume;
        public final IntValue beatAccentEvery;
        public final BooleanValue beamEnabled;
        public final IntValue beamFadeTicks;
        public final IntValue beamMaxSegments;
        public final DoubleValue beamGlowWidth;
        public final DoubleValue beamCoreWidth;
        public final DoubleValue beamGlowAlpha;
        public final DoubleValue beamCoreAlpha;
        public final DoubleValue beamCoreWhiten;
        public final DoubleValue beamNearFade;
        public final DoubleValue beamTaperDistance;
        public final DoubleValue beamSubdivide;
        public final DoubleValue beamMuzzleRight;
        public final DoubleValue beamMuzzleDown;
        public final DoubleValue beamMuzzleDecay;
        public final DoubleValue beamBeatWidth;
        public final BooleanValue fxEnabled;
        public final IntValue fxMaxSparks;
        public final IntValue fxHitSparks;
        public final IntValue fxImpactSparks;
        public final IntValue fxKillSparks;
        public final IntValue fxKillShards;
        public final IntValue fxBlinkSparks;
        public final DoubleValue fxMuzzleSize;
        public final BooleanValue damageNumbers;
        public final IntValue damageNumberTicks;
        public final DoubleValue damageNumberScale;
        public final DoubleValue blinkFovKick;
        public final DoubleValue killFovKick;
        public final IntValue fovKickTicks;
        public final DoubleValue killFlashAlpha;
        public final IntValue speedLineTicks;
        public final IntValue killBannerTicks;
        public final IntValue hurtIndicatorTicks;
        public final DoubleValue lowHealthThreshold;
        public final DoubleValue beatVignetteAlpha;
        public final BooleanValue zoneWall;
        public final DoubleValue zoneWallHeight;
        public final DoubleValue zoneWallAlpha;
        public final DoubleValue zoneWallNearAlpha;
        public final DoubleValue zoneWallNearDistance;
        public final BooleanValue musicEnabled;
        public final DoubleValue musicVolume;
        public final DoubleValue musicSlowMoDuck;
        public final DoubleValue musicIntermissionDuck;
        public final IntValue musicLatencyMs;
        public final ConfigValue<List<? extends Number>> musicDriveByRank;

        Client(ForgeConfigSpec.Builder b) {
            b.comment("見た目・演出（各プレイヤーが調整）").push("visual");
            cameraShakeScale = b.comment("カメラの揺れの倍率（0 で無効）").defineInRange("cameraShakeScale", 1.0, 0.0, 3.0);
            slowMoDesaturate = b.comment("スロー中に彩度を落とす").define("slowMoDesaturate", true);
            slowMoFovMultiplier = b.comment("スロー中の FOV 倍率").defineInRange("slowMoFovMultiplier", 0.85, 0.3, 1.5);
            slowMoSoundPitch = b.comment("スロー中の効果音のピッチ倍率").defineInRange("slowMoSoundPitch", 0.6, 0.1, 1.0);
            slowMoFadeTicks = b.comment("スロー演出の出入りのフェード").defineInRange("slowMoFadeTicks", 3, 0, 40);
            crosshairPulseScale = b.comment("拍でクロスヘアが脈打つ大きさ").defineInRange("crosshairPulseScale", 0.6, 0.0, 3.0);
            crosshairBaseSize = b.comment("クロスヘアの大きさ（px）").defineInRange("crosshairBaseSize", 5.0, 1.0, 40.0);
            crosshairPulseTicks = b.comment("脈動が減衰するまで（tick）").defineInRange("crosshairPulseTicks", 4, 1, 20);
            hudScale = b.defineInRange("hudScale", 1.0, 0.25, 4.0);
            rankPopTicks = b.comment("ランク上昇の弾けるアニメーションの長さ").defineInRange("rankPopTicks", 12, 1, 100);
            rankPopScale = b.comment("ランク上昇時の拡大率").defineInRange("rankPopScale", 1.9, 1.0, 5.0);
            showBeat = b.define("showBeat", true);
            hitStopInputFreeze = b.comment("被弾ヒットストップ中の入力の残し具合（0=完全停止）").defineInRange("hitStopInputFreeze", 0.0, 0.0, 1.0);
            hitMarkerTicks = b.comment("命中マーカーの表示時間").defineInRange("hitMarkerTicks", 6, 0, 40);
            killMarkerTicks = b.comment("キルマーカーの表示時間").defineInRange("killMarkerTicks", 12, 0, 60);
            flashTicks = b.comment("ビート・パリィ成功などのフラッシュの長さ").defineInRange("flashTicks", 6, 0, 40);
            beatClickVolume = b.comment("試合中に拍で鳴るクリック音の音量（0 で無音）").defineInRange("beatClickVolume", 0.25, 0.0, 1.0);
            beatAccentEvery = b.comment("何拍ごとにアクセントを付けるか").defineInRange("beatAccentEvery", 4, 1, 16);
            beamEnabled = b.comment("弾の軌道にビームを残す").define("beamEnabled", true);
            beamFadeTicks = b.comment("ビームが消えるまで（16=0.8秒）").defineInRange("beamFadeTicks", 16, 1, 200);
            beamMaxSegments = b.comment("同時に描く区間の上限").defineInRange("beamMaxSegments", 1024, 16, 16384);
            beamGlowWidth = b.comment("にじみの太さ（ブロック）").defineInRange("beamGlowWidth", 0.32, 0.0, 4.0);
            beamCoreWidth = b.comment("芯の太さ（ブロック）").defineInRange("beamCoreWidth", 0.07, 0.0, 4.0);
            beamGlowAlpha = b.comment("にじみの濃さ").defineInRange("beamGlowAlpha", 0.45, 0.0, 1.0);
            beamCoreAlpha = b.comment("芯の濃さ").defineInRange("beamCoreAlpha", 0.85, 0.0, 1.0);
            beamCoreWhiten = b.comment("芯をどれだけ白に寄せるか（0=チーム色のまま）").defineInRange("beamCoreWhiten", 0.55, 0.0, 1.0);
            beamNearFade = b.comment("カメラからこの距離より近い所を薄くする").defineInRange("beamNearFade", 0.8, 0.0, 16.0);
            beamTaperDistance = b.comment("カメラからこの距離より近い所を細くする").defineInRange("beamTaperDistance", 5.0, 0.0, 64.0);
            beamSubdivide = b.comment("描画の分割の長さ（ブロック）").defineInRange("beamSubdivide", 1.0, 0.1, 16.0);
            beamMuzzleRight = b.comment("一人称で自分のビームの根元を右へずらす量").defineInRange("beamMuzzleRight", 0.05, -2.0, 2.0);
            beamMuzzleDown = b.comment("一人称で自分のビームの根元を下へずらす量").defineInRange("beamMuzzleDown", 0.14, -2.0, 2.0);
            beamMuzzleDecay = b.comment("根元のずらしが 0 になる距離").defineInRange("beamMuzzleDecay", 10.0, 0.0, 64.0);
            beamBeatWidth = b.comment("ビートに乗った射撃のビームの太さの倍率").defineInRange("beamBeatWidth", 1.7, 1.0, 8.0);
            fxEnabled = b.comment("光の粒・輪・斬撃の弧など世界の中の演出").define("fxEnabled", true);
            fxMaxSparks = b.comment("同時に描く粒の上限").defineInRange("fxMaxSparks", 2048, 16, 16384);
            fxHitSparks = b.comment("命中の粒の数").defineInRange("fxHitSparks", 16, 0, 256);
            fxImpactSparks = b.comment("壁に着弾した粒の数").defineInRange("fxImpactSparks", 9, 0, 256);
            fxKillSparks = b.comment("キルの粒の数").defineInRange("fxKillSparks", 80, 0, 1024);
            fxKillShards = b.comment("キルで砕ける破片の数").defineInRange("fxKillShards", 40, 0, 1024);
            fxBlinkSparks = b.comment("ブリンクの粒の数").defineInRange("fxBlinkSparks", 24, 0, 256);
            fxMuzzleSize = b.comment("発射の光の大きさ（ブロック）").defineInRange("fxMuzzleSize", 0.55, 0.0, 4.0);
            damageNumbers = b.comment("当てたダメージの数字を出す").define("damageNumbers", true);
            damageNumberTicks = b.defineInRange("damageNumberTicks", 18, 1, 100);
            damageNumberScale = b.defineInRange("damageNumberScale", 0.03, 0.005, 0.2);
            blinkFovKick = b.comment("ブリンクで FOV を広げる量（0.12=+12%）").defineInRange("blinkFovKick", 0.12, -0.5, 0.5);
            killFovKick = b.comment("キルで FOV を変える量（負で寄る）").defineInRange("killFovKick", -0.06, -0.5, 0.5);
            fovKickTicks = b.defineInRange("fovKickTicks", 8, 1, 40);
            killFlashAlpha = b.comment("キルの画面フラッシュの濃さ").defineInRange("killFlashAlpha", 0.16, 0.0, 1.0);
            speedLineTicks = b.comment("キル・ブリンクの集中線の長さ").defineInRange("speedLineTicks", 7, 0, 40);
            killBannerTicks = b.comment("KILL 表示の長さ").defineInRange("killBannerTicks", 34, 0, 200);
            hurtIndicatorTicks = b.comment("撃たれた向きの表示の長さ").defineInRange("hurtIndicatorTicks", 18, 0, 100);
            lowHealthThreshold = b.comment("この体力以下で画面の縁が脈打つ").defineInRange("lowHealthThreshold", 6.0, 0.0, 40.0);
            beatVignetteAlpha = b.comment("ランク A 以上で拍に合わせて画面の縁を光らせる濃さ").defineInRange("beatVignetteAlpha", 0.12, 0.0, 1.0);
            zoneWall = b.comment("エリアの境界を光の壁で描く").define("zoneWall", true);
            zoneWallHeight = b.comment("壁の高さ（カメラの上下それぞれ）").defineInRange("zoneWallHeight", 10.0, 1.0, 64.0);
            zoneWallAlpha = b.comment("壁の濃さ").defineInRange("zoneWallAlpha", 0.16, 0.0, 1.0);
            zoneWallNearAlpha = b.comment("壁に近づいたときの濃さ").defineInRange("zoneWallNearAlpha", 0.6, 0.0, 1.0);
            zoneWallNearDistance = b.defineInRange("zoneWallNearDistance", 8.0, 0.1, 64.0);
            musicEnabled = b.comment("試合の BGM（拍に同期。音量はゲームの「音楽」でも変わる）").define("musicEnabled", true);
            musicVolume = b.defineInRange("musicVolume", 0.8, 0.0, 1.0);
            musicSlowMoDuck = b.comment("スロー中の BGM の音量倍率").defineInRange("musicSlowMoDuck", 0.4, 0.0, 1.0);
            musicIntermissionDuck = b.comment("ラウンド間の BGM の音量倍率").defineInRange("musicIntermissionDuck", 0.6, 0.0, 1.0);
            musicLatencyMs = b.comment("音が出るまでの遅れの見込み。BGM が拍より遅れて聞こえるなら増やす").defineInRange("musicLatencyMs", 40, 0, 500);
            musicDriveByRank = b.comment("スタイルランク D, C, B, A, S ごとのメロディ層の音量")
                    .defineList("musicDriveByRank", List.of(0.0, 0.25, 0.55, 0.8, 1.0), o -> o instanceof Number n && n.doubleValue() >= 0);
            b.pop();
        }
    }

    /** "#RRGGBB" → 0xRRGGBB。壊れていたら fallback。 */
    public static int parseColor(String s, int fallback) {
        try {
            String h = s.trim();
            if (h.startsWith("#")) h = h.substring(1);
            return Integer.parseInt(h, 16) & 0xFFFFFF;
        } catch (RuntimeException e) {
            return fallback;
        }
    }
}
