# 次に手触りを調整すべき数値

実際に遊べていない（Forge 層を起動できていない）ため、すべて「最初に触るべき候補」です。上から優先度順。
キーは SERVER config（`neonclash-server.toml`）、`CLIENT:` は `neonclash-client.toml`。

## 簡易プレイテスト（シミュレーション）で見えたこと

上から 3 点は既定値を変更済み（理由と測定値は README の判断ログ 27〜29）。下は変更前の記録。

ボットの簡易モデルなので傾向として読むこと（`docs/TEST_REPORT.md` 参照）。

- **ビートの猶予が広すぎる。** 128 BPM（拍間隔 469ms）で ±100ms だと猶予が拍の 43% を占め、適当に撃っても 43% がビート判定になった（実測 58/136 発）。
  「狙って合わせたご褒美」にするなら `beat.toleranceMs` を 50〜70ms（猶予 21〜30%）に下げるか、`damageBonus` を下げる。
- **ラストスタンドが実質 2 つ目のライフになっている。** キル 9 回に対して発動 10 回。毎ライフ自動で発動するので、1 ラウンドで倒すのに必要な致命打がほぼ倍になる。
  強化（切り札枠）で取得する、発動にエネルギーが要る、`lastStandHealth` を 2 程度に下げる、などを検討。
- **スタイルランクが上がらない。** 被弾 -50 に対してキル +60 で、4 発で倒される撃ち合いだと被弾の減点がキルの加点を上回り、4 人中 3 人が D のまま。
  ランクで強化のレア度を上げる仕組みが働かないので、`style.points.damage_taken` を -15〜-20 程度にするか、被弾の減点に上限（1 ラウンドあたり）を付ける。
- **ラウンドが短い（6〜17 秒）。** 2v2・アリーナ半径 19（動画用）では縮小前に決着した。実際のアリーナ（半径 28・90 秒）でも、エリア縮小が出番を持つかは要確認。

## 一覧

| # | 数値 | 既定 | 見るポイント | 動かす方向の目安 |
|---|---|---|---|---|
| 1 | `feel.hitStopTicks` | 3 tick (150ms) | 当てた瞬間の「止まり」が気持ちいいか、撃たれた側がストレスか | 2〜4。ハンドガンの連射速度より長いとハメになる |
| 2 | `blink.distance` | 6.0 | アリーナ半径 28 に対して逃げ・詰めのどちらにも使えるか | 5〜8。剣の射程＋踏み込み（約 3.3）より十分長いこと |
| 3 | `blink.cooldownTicks` | 50 (2.5s) | 2 チャージを連続で吐いた後の「待ち」が長すぎないか | 40〜70。ビート時 ×0.6 なので実質 30 |
| 4 | `sword.windupTicks` | 6 (300ms) | 反応して撃てば潰せる長さか（溜め潰しが成立するか） | 5〜9。短いと剣が強すぎ、長いと当たらない |
| 5 | `sword.range` / `sword.coneDegrees` | 2.8 / 45° | ブリンク→斬りのコンボが「狙って」決まるか | 射程 2.5〜3.5。円錐は狭いほど上手さが出る |
| 6 | `feel.hitShakeDegrees` / `killShakeDegrees` / `shakeTicks` | 0.8 / 2.0 / 5 | 揺れが気持ちいいか、照準が狂うほどか | 命中 0.5〜1.2、キル 1.5〜3 |
| 7 | `movement.speedMultiplier` / `jumpMultiplier` | 1.3 / 1.3 | TaCZ の弾速・反動と組み合わせて当てられるか | 1.2〜1.5。ジャンプ 1.3 で約 2 ブロック届く |
| 8 | `slow.globalTicks` / `globalFactor` | 10 (0.5s) / 0.3 | 決着キルの「間」が余韻になっているか、だれないか | 8〜14 tick |
| 9 | `slow.lastStandTicks` / `lastStandFactor` / `lastStandHealth` | 30 / 0.5 / 2.0 | 1.5 秒で逆転できるが、確定ではないバランスか | 体力 2〜6、倍率 0.3〜0.5 |
| 10 | `parry.windowTicks` / `cooldownTicks` | 6 / 100 | 狙って取れるか、取れたときに報われるか | 窓 4〜8、成功で CD 半分 |
| 11 | `beat.bpm` / `beat.toleranceMs` | 128 / 60 | 撃ち合いのリズムに乗れるか。外しても罰なし | 110〜140 BPM、猶予 55〜80ms（50ms 以下は tick の遅れで拍ちょうどが外れる） |
| 12 | `beat.damageBonus` | 0.2 | ビートに乗るかどうかで TTK（倒すまでの発数）が変わるか | 発数が 1 発減る程度が目安 |
| 13 | `feel.killPitchStep` / `killStreakWindowTicks` | 0.15 / 160 | 連続キルの音程上昇が分かるか（2.0 で頭打ち） | 0.1〜0.2 |
| 14 | `killReward.heal` / `speedTicks` | 6 / 40 | キル後に次の戦闘へ繋げられるか | 回復 4〜8 |
| 15 | `round.arenaRadius` / `arenaEndRadius` / `outsideDamagePerSecond` | 28 / 8 / 3 | 終盤 30 秒で必ず接敵するか | 人数に合わせて 20〜35 |
| 16 | `style.thresholds` / `style.points.*` | 60/160/300/480 | 1 ラウンドで B〜A、上手い人が S に届くか | 実測のキル数で調整 |
| 17 | `upgrades.rarityWeights.*` | D:[70,25,5,0] … S:[10,30,35,25] | ランクを上げる動機になる差があるか | |
| 18 | `blink.exitVelocity` | 0.35 | ワープ後に滑る感覚が気持ちいいか、制御を失うか | 0.2〜0.5 |
| 19 | CLIENT: `crosshairPulseScale` / `crosshairPulseTicks` | 0.6 / 4 | 拍が見えるが邪魔ではないか | |
| 20 | CLIENT: `slowMoFovMultiplier` / `slowMoSoundPitch` | 0.85 / 0.6 | スロー演出が「効いてる」感じがするか、酔わないか | |
| 21 | CLIENT: `beamFadeTicks` / `beamGlowWidth` / `beamGlowAlpha` | 16 / 0.32 / 0.45 | ビームが残る長さと濃さ。撃ち合いで相手が見えなくならないか | 10〜30。2v2 以上で画面がうるさければ短く |
| 23 | `knockback.gun` / `gunLift` | 0.42 / 0.12 | 押された側が操作を奪われすぎないか、剣の間合いに入れなくならないか | 0.2〜0.6。1 発 1〜2 ブロックが既定の目安 |
| 24 | `knockback.killBlast` / `killBlastRadius` | 0.5 / 4 | 味方も押されるので、邪魔か盛り上がるか | 味方を押したくなければ半径 0 |
| 25 | CLIENT: `fxKillSparks` / `fxKillShards` / `killFlashAlpha` | 80 / 40 / 0.16 | キルが派手すぎて次の相手が見えなくならないか | 2v2 以上で試す |
| 26 | CLIENT: `musicVolume` / `musicLatencyMs` | 0.8 / 40 | 効果音と BGM の音量差。BGM のキックが拍の表示より遅れて聞こえないか | 遅れて聞こえるなら latency を増やす |
| 27 | `feel.shotSoundVolume` | 1.6 | TaCZ の銃声と重ねて、うるさくないか | 下げるなら 0.8 |
| 22 | `style.points.on_beat` | 10 | 空撃ちでもビートに合わせれば加点される。狙って合わせるとランクが上がりすぎないか | 下げるなら 3〜5。命中時だけ加点にするのは別の修正 |
