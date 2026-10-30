package com.neonclash.gametest;

import com.neonclash.NeonClash;
import com.neonclash.compat.TaczBridge;
import com.neonclash.config.NCConfig;
import com.neonclash.core.round.RoundEngine;
import com.neonclash.core.round.Team;
import com.neonclash.core.upgrade.UpgradeDef;
import com.neonclash.game.BlinkService;
import com.neonclash.game.Combatant;
import com.neonclash.game.Combatants;
import com.neonclash.game.SlowService;
import com.neonclash.game.SwordService;
import com.neonclash.match.MatchController;
import com.tacz.guns.api.entity.IGunOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * runGameTestServer で実行。テンプレートは data/neonclash/structures/arena.nbt（9x6x9、床だけ）。
 * スローを使うテストはレベル全体に影響しうるので batch を分け、最後に SlowService をクリアする。
 * 戦闘者は AI を止めた Husk（日光で燃えない）で代用する。
 */
@GameTestHolder(NeonClash.MODID)
@PrefixGameTestTemplate(false)
public final class NCGameTests {
    private static final String ARENA = "arena";

    private NCGameTests() {
    }

    /** yaw: -90 = +X 向き、90 = -X 向き */
    private static Husk dummy(GameTestHelper h, double x, double y, double z, float yaw) {
        Husk e = h.spawn(EntityType.HUSK, new Vec3(x, y, z));
        e.setNoAi(true);
        e.setSilent(true);
        e.setPersistenceRequired();
        e.setYRot(yaw);
        e.setYHeadRot(yaw);
        e.setYBodyRot(yaw);
        e.setXRot(0f);
        return e;
    }

    // ------------------------------------------------------------------ 剣

    @GameTest(template = ARENA, batch = "neonclash_sword")
    public static void swordKillsInOneHit(GameTestHelper h) {
        Husk attacker = dummy(h, 2.5, 1, 4.5, -90f);
        IronGolem target = h.spawn(EntityType.IRON_GOLEM, new Vec3(4.5, 1, 4.5)); // 体力 100・防具値あり
        target.setNoAi(true);
        Combatant c = Combatants.getOrCreate(attacker);
        h.assertTrue(SwordService.tryStartWindup(c), "溜めを開始できる");
        h.assertFalse(SwordService.tryStartWindup(c), "溜め中に二重に溜められない");
        int windup = NCConfig.SERVER.swordWindupTicks.get();
        if (windup > 2) {
            h.runAfterDelay(windup - 2, () -> h.assertTrue(target.isAlive(), "溜めが終わるまでは当たらない"));
        }
        h.runAfterDelay(windup + 2, () -> {
            h.assertTrue(target.isDeadOrDying(), "剣が当たると一撃でキル（体力 " + target.getHealth() + "）");
            h.succeed();
        });
    }

    @GameTest(template = ARENA, batch = "neonclash_sword")
    public static void swordOutOfRangeMisses(GameTestHelper h) {
        Husk attacker = dummy(h, 1.5, 1, 4.5, -90f);
        Husk far = dummy(h, 7.5, 1, 4.5, 90f);
        Combatant c = Combatants.getOrCreate(attacker);
        SwordService.tryStartWindup(c);
        h.runAfterDelay(NCConfig.SERVER.swordWindupTicks.get() + 2, () -> {
            h.assertTrue(far.isAlive() && far.getHealth() == far.getMaxHealth(), "射程外には当たらない");
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ ブリンク

    @GameTest(template = ARENA, batch = "neonclash_blink")
    public static void blinkDoesNotPassThroughWall(GameTestHelper h) {
        // x=5 に厚さ 1 の壁（向こう側 x>=6 は空いている）
        for (int y = 1; y <= 4; y++) {
            for (int z = 0; z <= 8; z++) {
                h.setBlock(new BlockPos(5, y, z), Blocks.STONE);
            }
        }
        Husk e = dummy(h, 2.5, 1, 4.5, -90f);
        Combatant c = Combatants.getOrCreate(e);
        int before = c.blink.charges();
        double startX = e.getX();
        double wallX = h.absolutePos(new BlockPos(5, 1, 4)).getX();

        h.assertTrue(BlinkService.tryBlink(c), "壁の手前まではブリンクできる");
        h.assertTrue(e.getX() > startX + 0.5, "前に進んでいる");
        h.assertTrue(e.getX() + e.getBbWidth() / 2 <= wallX + 1.0e-6, "壁を貫通していない x=" + e.getX() + " wall=" + wallX);
        h.assertTrue(h.getLevel().noCollision(e, e.getBoundingBox()), "ブロックにめり込んでいない");
        h.assertTrue(c.blink.charges() == before - 1, "チャージを 1 消費");

        int left = c.blink.charges();
        h.assertFalse(BlinkService.tryBlink(c), "壁に密着していると失敗");
        h.assertTrue(c.blink.charges() == left, "失敗したブリンクはチャージを消費しない");
        h.succeed();
    }

    // ------------------------------------------------------------------ 範囲スロー

    @GameTest(template = ARENA, batch = "neonclash_slowzone")
    public static void slowZoneAffectsOnlyInside(GameTestHelper h) {
        SlowService.clearAll();
        Husk inside = dummy(h, 2.5, 1, 2.5, 0f);
        Husk outside = dummy(h, 7.5, 1, 7.5, 0f);
        Vec3 center = h.absoluteVec(new Vec3(2.5, 2, 2.5));
        SlowService.addZone(h.getLevel(), center, 2.0, 0.3, 100);

        Arrow arrowIn = arrow(h, new Vec3(2.0, 3.4, 2.5));
        Arrow arrowOut = arrow(h, new Vec3(6.0, 3.4, 7.5));

        h.runAfterDelay(3, () -> {
            h.assertTrue(SlowService.isSlowed(inside), "範囲内のエンティティは減速");
            h.assertFalse(SlowService.isSlowed(outside), "範囲外のエンティティは影響なし");
            double in = arrowIn.getDeltaMovement().length(), out = arrowOut.getDeltaMovement().length();
            h.assertTrue(in < out * 0.5, "範囲内の弾は減速 in=" + in + " out=" + out);
            h.assertTrue(out > 0.09, "範囲外の弾はほぼそのまま out=" + out);
            SlowService.clearAll();
            h.succeed();
        });
    }

    private static Arrow arrow(GameTestHelper h, Vec3 rel) {
        Vec3 p = h.absoluteVec(rel);
        Arrow a = new Arrow(h.getLevel(), p.x, p.y, p.z);
        a.setNoGravity(true);
        a.setDeltaMovement(0.1, 0, 0);
        h.getLevel().addFreshEntity(a);
        return a;
    }

    // ------------------------------------------------------------------ ラストスタンド

    @GameTest(template = ARENA, batch = "neonclash_laststand")
    public static void lastStandTriggersOncePerLife(GameTestHelper h) {
        SlowService.clearAll();
        Husk killer = dummy(h, 2.5, 1, 4.5, -90f);
        Husk victim = dummy(h, 4.5, 1, 4.5, 90f);
        Combatants.getOrCreate(killer);
        Combatant vc = Combatants.getOrCreate(victim);

        SwordService.bladeKill(killer, victim);
        h.assertTrue(victim.isAlive() && !victim.isDeadOrDying(), "1 回目の致命傷はラストスタンドで耐える");
        h.assertTrue(vc.lastStand.triggerCount() == 1, "発動回数 1");
        h.assertFalse(vc.lastStand.isAvailable(), "使用済み");

        h.runAfterDelay(2, () -> {
            h.assertTrue(SlowService.isSlowed(killer), "自分以外はスロー");
            h.assertFalse(SlowService.isSlowed(victim), "自分はスローしない");
            SwordService.bladeKill(killer, victim);
            h.assertTrue(victim.isDeadOrDying(), "同じライフの 2 回目は発動せず死ぬ");
            h.assertTrue(vc.lastStand.triggerCount() == 1, "発動は 1 ライフ 1 回だけ");
            vc.newLife();
            h.assertTrue(vc.lastStand.isAvailable(), "次のライフでは再び使える");
            SlowService.clearAll();
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ ラウンド進行

    @GameTest(template = ARENA, batch = "neonclash_match", timeoutTicks = 600)
    public static void roundFlowKillEndUpgradeNext(GameTestHelper h) {
        SlowService.clearAll();
        Husk red = dummy(h, 1.5, 1, 4.5, -90f);
        Husk blue = dummy(h, 7.5, 1, 4.5, 90f);
        Map<Husk, Team> members = new LinkedHashMap<>();
        members.put(red, Team.RED);
        members.put(blue, Team.BLUE);
        MatchController m = MatchController.start(h.getLevel(), h.absoluteVec(new Vec3(4.5, 1, 4.5)), members,
                new MatchController.Options(false, 2.0));

        // ラウンド開始
        h.assertTrue(m.engine().phase() == RoundEngine.Phase.ROUND && m.engine().round() == 1, "ラウンド 1 開始");
        Combatant bc = Combatants.get(blue);
        Combatant rc = Combatants.get(red);
        h.assertTrue(bc != null && rc != null && bc.inMatch && rc.inMatch, "参加者が登録されている");
        bc.lastStand.tryTrigger(); // 切り札は使用済みにしておき、キルを通す

        // キル → ラウンド終了
        h.assertTrue(SwordService.bladeKill(red, blue), "キルが入る");
        h.assertTrue(blue.isAlive(), "試合中の死亡はキャンセルされて観戦扱い");
        h.assertTrue(bc.eliminated, "青は脱落");
        h.assertTrue(m.engine().phase() == RoundEngine.Phase.INTERMISSION, "ラウンド終了 → ラウンド間");
        h.assertTrue(m.engine().score(Team.RED) == 1, "赤に 1 点");

        // 強化選択
        List<UpgradeDef> offers = m.offersFor(red.getUUID());
        h.assertTrue(!offers.isEmpty() && offers.size() <= NCConfig.SERVER.upgradeChoices.get(), "強化の選択肢が出る: " + offers);
        h.assertTrue(offers.stream().map(UpgradeDef::id).distinct().count() == offers.size(), "選択肢に重複が無い");
        h.assertTrue(m.choose(red.getUUID(), 0), "選べる");
        h.assertFalse(m.choose(red.getUUID(), 1), "2 回は選べない");
        h.assertTrue(rc.upgrades.owns(offers.get(0).id()), "選んだ強化が付く");

        // 次ラウンド
        h.succeedWhen(() -> {
            h.assertTrue(m.engine().phase() == RoundEngine.Phase.ROUND && m.engine().round() == 2, "ラウンド 2 を待っている");
            h.assertFalse(bc.eliminated, "次ラウンドで復帰");
            h.assertTrue(bc.upgrades.owned().size() == 1, "選ばなかった人にはランダムで 1 つ付く");
            h.assertTrue(bc.lastStand.isAvailable(), "新しいライフで切り札が再装填");
            m.stop();
            SlowService.clearAll();
        });
    }

    // ------------------------------------------------------------------ TaCZ 連携

    @GameTest(template = ARENA, batch = "neonclash_tacz", timeoutTicks = 300)
    public static void taczGunHitGoesThroughModHandlers(GameTestHelper h) {
        Husk shooter = dummy(h, 1.5, 1, 4.5, -90f);
        Husk target = dummy(h, 6.5, 1, 4.5, 90f);
        ItemStack gun = TaczBridge.makeGun(NCConfig.SERVER.handgunId.get(), 17);
        h.assertFalse(gun.isEmpty(), "TaCZ のハンドガンが作れる（既定の銃パックが読み込まれている）: " + NCConfig.SERVER.handgunId.get());
        shooter.setItemInHand(InteractionHand.MAIN_HAND, gun);
        Combatants.getOrCreate(shooter);
        Combatant tc = Combatants.getOrCreate(target);

        IGunOperator op = IGunOperator.fromLivingEntity(shooter);
        op.initialData();
        op.draw(shooter::getMainHandItem);
        double dx = target.getX() - shooter.getX();
        double dy = shooter.getEyeY() - (target.getY() + target.getBbHeight() * 0.5);
        float pitch = (float) Math.toDegrees(Math.atan2(dy, dx));

        h.succeedWhen(() -> {
            if (tc.gunHitsTaken == 0) {
                op.shoot(() -> pitch, () -> -90f);
            }
            h.assertTrue(tc.gunHitsTaken > 0, "TaCZ の命中イベントがこの mod の処理を通るのを待っている");
            h.assertTrue(target.getHealth() < target.getMaxHealth(), "ダメージが入っている");
        });
    }
}
