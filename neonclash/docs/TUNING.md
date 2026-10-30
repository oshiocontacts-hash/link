# 次に手触りを調整すべき数値

実際に遊べていない（Forge 層を起動できていない）ため、すべて「最初に触るべき候補」です。上から優先度順。
キーは SERVER config（`neonclash-server.toml`）、`CLIENT:` は `neonclash-client.toml`。

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
| 9 | `slow.lastStandTicks` / `lastStandFactor` / `lastStandHealth` | 60 / 0.35 / 4.0 | 3 秒で逆転できるが、確定ではないバランスか | 体力 2〜6、倍率 0.3〜0.5 |
| 10 | `parry.windowTicks` / `cooldownTicks` | 6 / 100 | 狙って取れるか、取れたときに報われるか | 窓 4〜8、成功で CD 半分 |
| 11 | `beat.bpm` / `beat.toleranceMs` | 128 / 100 | 撃ち合いのリズムに乗れるか。外しても罰なし | 110〜140 BPM、猶予 80〜120ms |
| 12 | `beat.damageBonus` | 0.2 | ビートに乗るかどうかで TTK（倒すまでの発数）が変わるか | 発数が 1 発減る程度が目安 |
| 13 | `feel.killPitchStep` / `killStreakWindowTicks` | 0.15 / 160 | 連続キルの音程上昇が分かるか（2.0 で頭打ち） | 0.1〜0.2 |
| 14 | `killReward.heal` / `speedTicks` | 6 / 40 | キル後に次の戦闘へ繋げられるか | 回復 4〜8 |
| 15 | `round.arenaRadius` / `arenaEndRadius` / `outsideDamagePerSecond` | 28 / 8 / 3 | 終盤 30 秒で必ず接敵するか | 人数に合わせて 20〜35 |
| 16 | `style.thresholds` / `style.points.*` | 100/250/450/700 | 1 ラウンドで B〜A、上手い人が S に届くか | 実測のキル数で調整 |
| 17 | `upgrades.rarityWeights.*` | D:[70,25,5,0] … S:[10,30,35,25] | ランクを上げる動機になる差があるか | |
| 18 | `blink.exitVelocity` | 0.35 | ワープ後に滑る感覚が気持ちいいか、制御を失うか | 0.2〜0.5 |
| 19 | CLIENT: `crosshairPulseScale` / `crosshairPulseTicks` | 0.6 / 4 | 拍が見えるが邪魔ではないか | |
| 20 | CLIENT: `slowMoFovMultiplier` / `slowMoSoundPitch` | 0.85 / 0.6 | スロー演出が「効いてる」感じがするか、酔わないか | |
