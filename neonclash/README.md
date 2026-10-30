# Neon Clash — テンポ重視のネオン PvP ミニゲーム（Minecraft 1.20.1 / Forge 47 / TaCZ）

暗いインダストリアルなアリーナで、ネオンのチームカラーがぶつかる 2〜4 人 × 2 チームの対戦 mod です。
1 ラウンド 90 秒、3 本先取。ハンドガン（TaCZ）と一撃必殺の剣、ブリンク、パリィ、切り札で戦います。

> **現状（重要）**: この開発環境では `maven.minecraftforge.net`・Mojang（`piston-meta` / `libraries.minecraft.net` など）・
> CurseMaven / CurseForge / Modrinth がネットワークポリシーで拒否されたため、**Forge mod 部分はビルドも起動もできていません**。
> Minecraft に依存しないゲームロジック（`core/`）はビルドとユニットテスト（113 件）が全部通っています。
> 詳細は [テストレポート](docs/TEST_REPORT.md) と下の「判断ログ」を参照してください。

## 遊び方

1. サーバーに Forge 47.x・TaCZ・Neon Clash を入れる（クライアントにも同じものが必要）。
2. アリーナの中心に立って `/pvp arena set`（半径は config の `round.arenaRadius`）。
3. `/pvp team <player> red|blue` で 1 チーム 1〜4 人（config で変更可）を割り当てる。
4. `/pvp start`。各ラウンド開始時に、赤は中心の -X 側、青は +X 側にスポーンし、ハンドガンと剣が配られます（持っていなければ）。

| 操作 | 既定キー | 内容 |
|---|---|---|
| 射撃 | 左クリック（銃を持って） | TaCZ のハンドガン。ビートに合わせて撃つとダメージ +20% |
| 剣 | 左クリック（ネオンブレードを持って） | 短い溜め → 斬撃。当たれば一撃キル。溜め中にハンドガンで撃たれると溜めがリセット |
| ブリンク | 左 Alt | 視線方向に短距離ワープ（2 チャージ）。壁は抜けない。ビートに合わせるとクールダウン短縮 |
| パリィ | X | 一瞬だけ攻撃を無効化。成功すると相手を短時間スロー＋自分の周りに範囲スロー（弾も遅くなる） |
| 切り札 | 自動 | ラストスタンド：致命傷を 1 ライフに 1 回だけ耐え、自分以外を 3 秒スロー |
| 強化選択 | 1 / 2 / 3 またはクリック | ラウンド間 5 秒。選ばなければランダム |

- 脱落するとそのラウンドは観戦（スペクテイター）。次のラウンドで全員復帰します。
- 60 秒経過でエリアが縮み始め（90 秒で最小）、エリア外では毎秒ダメージ。
- ラウンドを決めたキルの瞬間だけ全員が 0.5 秒スローになります（彩度低下・効果音のピッチ低下・FOV 変化・レターボックス）。
- スタイルランク D→C→B→A→S：空中キル・ブリンク直後のキル・剣キル・ビート・パリィで上昇、被弾・死亡で下降。
  ラウンド終了時のランクが高いほど強化の選択肢のレア度が上がります。
- TaCZ の銃はハンドガン以外も持ち込めます（溜め潰しは TaCZ の銃全般の命中で発生）。

## コマンド

| コマンド | 権限 | 説明 |
|---|---|---|
| `/pvp start` | OP(2) | 割り当て済みのプレイヤーで試合開始 |
| `/pvp stop` | OP(2) | 試合を止め、ゲームモードを元に戻す |
| `/pvp team <player> <red\|blue>` | OP(2) | チーム割り当て |
| `/pvp arena set` | OP(2) | 実行位置をアリーナ中心として保存（次元ごと・ワールドに保存） |
| `/pvp choose <n>` | 誰でも | 強化を番号で選ぶ（UI の代わり） |
| `/pvp status` | 誰でも | 進行状況 |
| `/pvp slowfield <radius> <seconds>` | OP(2) | 範囲スローを置いて手触りを確認する（デバッグ用） |

## config

数値はすべて config です。ゲームプレイは **SERVER**（`<world>/serverconfig/neonclash-server.toml`、クライアントへ自動同期）、
見た目は **CLIENT**（`config/neonclash-client.toml`、各自で調整）。主なセクション：

| セクション | 主なキー（既定値） |
|---|---|
| `movement` | `onlyInMatch`(true) `speedMultiplier`(1.3) `jumpMultiplier`(1.3) |
| `blink` | `distance`(6.0) `charges`(2) `cooldownTicks`(50) `step`(0.25) `maxStepUp`(0.6) `maxCeilingDrop`(0.6) `minDistance`(0.75) `exitVelocity`(0.35) `killWindowTicks`(30) |
| `sword` | `windupTicks`(6) `recoveryTicks`(12) `interruptLockoutTicks`(4) `range`(2.8) `coneDegrees`(45) `lungeVelocity`(0.45) |
| `feel` | `hitStopTicks`(3) `hitShakeDegrees`(0.8) `killShakeDegrees`(2.0) `shakeTicks`(5) 命中/キル音の音量・ピッチ、`killPitchStep`(0.15) `killMaxPitch`(2.0) `killStreakWindowTicks`(160) パーティクル数・大きさ |
| `killReward` | `heal`(6.0) `speedTicks`(40) `speedAmplifier`(1) |
| `slow` | `globalFactor`(0.3) `globalTicks`(10) `parryTargetFactor`(0.4) `parryTargetTicks`(30) `parryZoneRadius`(3.5) `parryZoneFactor`(0.35) `parryZoneTicks`(30) `lastStandFactor`(0.35) `lastStandTicks`(60) `lastStandHealth`(4.0) `clientStrength`(1.0) |
| `parry` | `windowTicks`(6) `cooldownTicks`(100) `successCooldownMultiplier`(0.5) |
| `beat` | `enabled`(true) `bpm`(128) `toleranceMs`(100) `damageBonus`(0.2) `cooldownMultiplier`(0.6) `maxLatencyCompensationMs`(150) `resyncSeconds`(10) |
| `style` | `thresholds`([100,250,450,700]) `maxPoints`(1000) `points.*`（kill 60 / air_kill 60 / blink_kill 70 / sword_kill 90 / on_beat 10 / parry 40 / damage_taken -50 / death -200） |
| `round` | `roundSeconds`(90) `shrinkStartSeconds`(60) `intermissionSeconds`(5) `winsToWin`(3) `maxRounds`(9) `min/maxPlayersPerTeam`(1/4) `arenaRadius`(28) `arenaEndRadius`(8) `spawnOffset`(12) `outsideDamagePerSecond`(3) `handgunId`("tacz:glock_17") `handgunAmmo`(17) `spareAmmoId`("tacz:9mm") `spareAmmo`(68) `friendlyFire`(false) `hudSyncInterval`(2) |
| `upgrades` | `choices`(3) `distinctCategories`(true) `reductionFloor`(0.25) `pool`（`id\|CATEGORY\|RARITY\|STAT\|value\|stackable` の文字列リスト） `rarityWeights.D..S`（[COMMON,RARE,EPIC,LEGENDARY] の重み） |
| `colors` | `red`(#FF2E63) `blue`(#00E5FF) `neutral`(#F8F32B) |
| CLIENT `visual` | `cameraShakeScale` `slowMoDesaturate` `slowMoFovMultiplier`(0.85) `slowMoSoundPitch`(0.6) `slowMoFadeTicks` `crosshairPulseScale` `crosshairBaseSize` `crosshairPulseTicks` `hudScale` `rankPopTicks` `rankPopScale` `showBeat` `beatClickVolume` `beatAccentEvery` `hitMarkerTicks` `killMarkerTicks` `flashTicks` `hitStopInputFreeze` |

効果音はバニラの音を `assets/neonclash/sounds.json` で参照しているだけなので、リソースパックで差し替えられます。
スロー時の彩度は `assets/neonclash/shaders/post/slowmo.json` の `Saturation`（0.25）です。

## 構成

```
neonclash/
  core/   Minecraft 非依存のロジック（ビート判定、ブリンクのチャージと安全位置、剣の溜め、スタイル、ラウンド進行、強化抽選、スロー範囲…）＋ JUnit 5
  mod/    Forge 層（config、ネットワーク、TaCZ 連携、試合進行、HUD、GameTest）。core のクラスは mod jar に同梱
  docs/   テストレポート、手触り調整リスト、ビルド失敗ログ
  tools/sim/  簡易プレイテスト：core のロジックで 2v2 のボット戦を動かして動画にする（Minecraft は不要）
```

### 簡易プレイテスト（Minecraft なしで動く）

```bash
./gradlew -Pneonclash.coreOnly=true :core:jar
javac -encoding UTF-8 -cp core/build/libs/neonclash-core-0.1.0.jar -d build/sim tools/sim/PlaytestSim.java
java -Djava.awt.headless=true -cp core/build/libs/neonclash-core-0.1.0.jar:build/sim PlaytestSim playtest.mp4   # ffmpeg が必要
```

実機映像ではなく、core の判定（ブリンク・剣の溜め・ビート・スタイル・ラウンド・強化抽選）を 2D 俯瞰で動かしたものです。終了時に集計を標準出力へ出します。

### ビルド

```bash
# ネットワーク制限下でもできること：core のビルドとテスト
./gradlew -Pneonclash.coreOnly=true :core:build

# Forge Maven / Mojang / CurseMaven に届く環境で
#   gradle.properties の tacz_curse_file_id（または mod/libs に置いた jar の tacz_local_name / tacz_local_version）を設定してから
./gradlew build                # mod/build/libs/neonclash-0.1.0.jar
./gradlew :mod:runGameTestServer
./gradlew :mod:runServer       # スモークテスト
```

## 判断ログ

仕様に書かれていない点・環境の制約で、こう判断しました。

**環境・依存**
1. **Forge mod はビルドできていない。迂回はしていない。** `maven.minecraftforge.net`（ForgeGradle・Forge）、Mojang の各ホスト、
   `cursemaven.com` / `www.curseforge.com` / `api.curseforge.com` / `edge.forgecdn.net` / `mediafilez.forgecdn.net`、`api.modrinth.com`、
   `maven.neoforged.net`、`jitpack.io` がすべて CONNECT 403。指示どおりミラー等での迂回はせず、ログを `docs/logs/forge-build-attempt.log` に残しました。
2. 依存が無くても進められる部分を最大化するため、ロジックを `core` に分離して先に完成・テストし、Forge 層はソースまで書いて止めています（コンパイル未確認）。
3. Maven Central は `repo.maven.apache.org` が断続的に 429（レート制限）を返したので、同じ Maven Central の `repo1.maven.org` を先に参照するようにしました（ポリシー拒否ではないので迂回には当たらないと判断）。
4. JDK は 21 しか無かったので apt で OpenJDK 17 を入れ、core は `release = 17` でビルド。
5. **TaCZ の API はソースで確認しました**（GitHub `MCModderAnchor/tacz` の `1.20.1` ブランチ、2026-10-30 時点の HEAD `2d304e3`）。
   使ったのは `GunFireEvent` / `EntityHurtByGunEvent.Pre` / `.Post` / `EntityKillByGunEvent` / `AmmoHitBlockEvent` / `IGunOperator` /
   `GunItemBuilder` / `AmmoItemBuilder` / `EntityKineticBullet` / `IGun.getIGunOrNull` です。
   2026-10-18 に追加された `GunFirePostEvent` は配布版に入っていない可能性があるため使わず、2024 年から存在する API だけに限定しました。
6. **TaCZ の CurseForge File ID は未確定。** CurseForge に到達できず「最新の 1.20.1 版」を特定できないため、推測で ID を書かず
   `gradle.properties` の `tacz_curse_file_id` を空にして、未設定ならビルドが分かりやすいメッセージで止まるようにしました。
   プロジェクトのスラッグ `timeless-and-classics-zero-1028108` も記憶に基づく値なので、設定時に確認してください。ローカル jar（`tacz_local_name` / `tacz_local_version`）でも可。
7. Forge は `47.3.0`（TaCZ 自身の開発版が `47.3.19` を使っているのに合わせた範囲。存在確認はできていない）。

**ゲーム仕様**
8. ブリンクのチャージは「1 つずつ順番に回復」（消費したチャージごとにクールダウンが積まれる）。ビートボーナスは消費したチャージのクールダウンに掛ける。
9. ブリンク先は視線方向に 0.25 ブロック刻み（体の幅・高さの半分以下に自動で細かくする）で進め、最初にぶつかった所で止める＝壁は抜けない。
   床にぶつかったら最大 `maxStepUp` 持ち上げ、天井なら `maxCeilingDrop` 下げる。持ち上げ・下げの経路も塞がっていないことを確認（薄い天井を抜けない）。
   `minDistance` 未満しか進めないときは失敗扱いでチャージを消費しない。ワープ後は視線方向に少し速度を残す（勢いが気持ちいいので）。
10. 剣は通常の攻撃を置き換える（ブレードを持った左クリックはサーバーへ「溜め開始」パケット）。射程内・円錐内で一番近い 1 人に当てる。
    一撃キルは「防具・エンチャント・耐性・無敵時間を無視するダメージタイプ `neonclash:blade` で 残り体力＋吸収＋最大体力 を与える」で実装。
    溜めを潰すのは TaCZ の銃の命中（ハンドガンに限定しない）。潰された後は `interruptLockoutTicks` だけ溜め直せない。
11. ヒットストップ：被弾側の移動停止はプレイヤーならクライアント（入力と速度を止める）、モブならサーバーで止める。攻撃側はカメラを揺らす。
12. キル処理は銃・剣・エリアなど全部を `LivingDeathEvent` で一本化。試合中の死亡はキャンセルして観戦へ（リスポーン画面を挟まずテンポを保つ）。
    そのため試合中は TaCZ の `EntityKillByGunEvent` ではなく `.Post` が飛ぶが、脱落済みとして演出の二重発火を防いでいる。
13. 範囲スローをどこで使うか仕様に無かったので、**パリィ成功時に自分の周りに張る**（飛んでくる弾が遅くなる）ことにした。確認用に `/pvp slowfield` も用意。
    生き物は移動速度の属性モディファイア（同期される）、弾は速度ベクトルを掛け直し（出たら戻す）。矢など TaCZ 以外の `Projectile` も対象。
14. 全員スローはラウンド決着キルのみ。1.20.1 にはサーバーの tick 速度変更が無いので「全員・全弾を倍率で遅くする＋全員に演出パケット」で表現。
15. ラストスタンドの「自分以外が 3 秒スロー」は、アリーナ半径の 2 倍以内の自分以外の全員と、自分以外の弾。踏みとどまった後の体力は config。
16. 時間切れ：生存人数 → 同数なら残り体力の割合の合計 → それも同じなら引き分け。引き分けはどちらにも点が入らず次ラウンド。
    引き分けが続く場合に備えて `maxRounds`（9）で打ち切り、点の多い方の勝ち（同点なら試合も引き分け）。同時全滅も引き分け。
17. 強化 3 択は「枠をランダム → ランクの重みでレア度 → 枠×レア度から 1 つ」。3 つは別々の枠から出す（`distinctCategories`）。
    同じ 3 択内で重複なし、所持済みの非スタック強化は出さない。その枠に重みのあるレア度が無ければ一番低いレア度を出す。選ばなければランダムで 1 つ。
18. スタイルランクは試合を通して持ち越し（ラウンドごとにリセットしない）。ポイント制で、しきい値・増減はすべて config。
19. ビートの判定はサーバー時刻（ms）で、プレイヤーの ping/2（上限 150ms）を差し引く。HUD の脈動と拍のクリック音は、
    サーバー時刻に同期したクライアントの時計で鳴らす（サーバー tick の 50ms 刻みに縛られないため）。BPM 変更は直前の拍を基準に切り替えて拍番号を連続させる。
    猶予の境界ちょうど（±100ms）は猶予内。
20. 移動強化は試合中のプレイヤーだけ（`movement.onlyInMatch`）。剣・ブリンクは試合外でも練習できる。パリィと切り札はプレイヤーは試合中だけ（試合外で `/kill` を耐えるなどを防ぐ）。
21. ロードアウトはインベントリを消さず、持っていない物だけ足す（他の持ち物を壊さないため）。ゲームモードは試合中 ADVENTURE、終了時に元へ戻す。
22. 効果音はバニラの音イベントを参照（オリジナル音源を用意できないため）。命中音とキル音は別の音で、キル音は連続キルでピッチが上がる。
23. スロー時の彩度は PostChain（バニラの `color_convolve`）を GUI 描画直前に掛ける。uniform を実行時に変える API が無いので、彩度の値は shader JSON 側。
24. HUD のレイアウト（px 位置）は調整値ではなく配置なのでコード内に置いた。大きさは `hudScale`、演出の長さ・強さは CLIENT config。
25. キー：TaCZ が R/G/C/Z/T/O/V/H を使うので、ブリンク=左 Alt、パリィ=X。
26. GameTest の戦闘者は AI を止めた Husk（日光で燃えない）で代用。試合コントローラはモブも参加者として扱えるようにしてある。
