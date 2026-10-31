# Neon Clash — テンポ重視のネオン PvP ミニゲーム（Minecraft 1.20.1 / Forge 47 / TaCZ）

暗いインダストリアルなアリーナで、ネオンのチームカラーがぶつかる 2〜4 人 × 2 チームの対戦 mod です。
1 ラウンド 90 秒、3 本先取。ハンドガン（TaCZ）と一撃必殺の剣、ブリンク、パリィ、切り札で戦います。

> **現状**: 開発環境からは Forge Maven・Mojang・CurseForge に届かないため、実機のビルド・GameTest・起動と録画は GitHub Actions で行っています。
> ビルド成功、ユニットテスト 121 件・GameTest 8 件すべて成功、Xvfb 上の `runClient` で 3 ラウンドの試合を自動プレイして録画済み
> （`neonclash-demo-media` ブランチ）。詳細は [テストレポート](docs/TEST_REPORT.md) と下の「判断ログ」を参照してください。

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
| 切り札 | 自動 | ラストスタンド：致命傷を 1 ライフに 1 回だけ耐え、自分以外を 1.5 秒スロー |
| 強化選択 | 1 / 2 / 3 またはクリック | ラウンド間 5 秒。選ばなければランダム |

- 脱落するとそのラウンドは観戦（スペクテイター）。次のラウンドで全員復帰します。
- 60 秒経過でエリアが縮み始め（90 秒で最小）、エリア外では毎秒ダメージ。
- ラウンドを決めたキルの瞬間だけ全員が 0.5 秒スローになります（彩度低下・効果音のピッチ低下・FOV 変化・レターボックス）。
- スタイルランク D→C→B→A→S：空中キル・ブリンク直後のキル・剣キル・ビート・パリィで上昇、被弾・死亡で下降。
  ラウンド終了時のランクが高いほど強化の選択肢のレア度が上がります。
- TaCZ の銃はハンドガン以外も持ち込めます（溜め潰しは TaCZ の銃全般の命中で発生）。
- 弾の軌道にはチームカラーの半透明ビームが残り、根元から順に消えていきます。ビートに乗った弾は太くなります。
- 弾が当たると相手は押され、倒すと相手は砕けて周りを爆風で押します。パリィ成功でも周りの敵を押します。
- 試合中は拍に同期した BGM が流れ、スタイルランクが上がるほどメロディが厚くなります。

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
| `feel` | `hitStopTicks`(3) `hitShakeDegrees`(0.8) `hurtShakeDegrees`(1.6) `killShakeDegrees`(3.0) `shakeTicks`(5) 命中/キル音の音量・ピッチ、`hitComboPitchStep`(0.12) `hitComboMaxPitch`(1.5) `hitComboWindowTicks`(30) `shotSoundVolume`(1.6) `shotPitchVariance`(0.08) `hurtSoundVolume`(0.8) `killPitchStep`(0.19) `killMaxPitch`(2.0) `killStreakWindowTicks`(160) パーティクル数・大きさ `fxViewDistance`(96) |
| `knockback` | `gun`(0.42) `gunLift`(0.12) `beatMultiplier`(1.6) `headMultiplier`(1.3) `sword`(1.3) `swordLift`(0.35) `parry`(0.9) `parryLift`(0.25) `killBlastRadius`(4) `killBlast`(0.5) `killBlastLift`(0.2) `maxSpeed`(1.4) |
| `killReward` | `heal`(6.0) `speedTicks`(40) `speedAmplifier`(1) |
| `slow` | `globalFactor`(0.3) `globalTicks`(10) `parryTargetFactor`(0.4) `parryTargetTicks`(30) `parryZoneRadius`(3.5) `parryZoneFactor`(0.35) `parryZoneTicks`(30) `lastStandFactor`(0.5) `lastStandTicks`(30) `lastStandHealth`(2.0) `clientStrength`(1.0) |
| `beam` | `enabled`(true) `maxLength`(96) `viewDistance`(96) |
| `parry` | `windowTicks`(6) `cooldownTicks`(100) `successCooldownMultiplier`(0.5) |
| `beat` | `enabled`(true) `bpm`(128) `toleranceMs`(60) `damageBonus`(0.2) `cooldownMultiplier`(0.6) `maxLatencyCompensationMs`(150) `resyncSeconds`(10) |
| `style` | `thresholds`([60,160,300,480]) `maxPoints`(1000) `points.*`（kill 60 / air_kill 60 / blink_kill 70 / sword_kill 90 / on_beat 10 / parry 40 / damage_taken -15 / death -60） |
| `round` | `roundSeconds`(90) `shrinkStartSeconds`(60) `intermissionSeconds`(5) `winsToWin`(3) `maxRounds`(9) `min/maxPlayersPerTeam`(1/4) `arenaRadius`(28) `arenaEndRadius`(8) `spawnOffset`(12) `outsideDamagePerSecond`(3) `handgunId`("tacz:glock_17") `handgunAmmo`(17) `reserveAmmo`(9999) `spareAmmoId`("tacz:9mm") `spareAmmo`(68) `friendlyFire`(false) `hudSyncInterval`(2) |
| `upgrades` | `choices`(3) `distinctCategories`(true) `reductionFloor`(0.25) `pool`（`id\|CATEGORY\|RARITY\|STAT\|value\|stackable` の文字列リスト） `rarityWeights.D..S`（[COMMON,RARE,EPIC,LEGENDARY] の重み） |
| `colors` | `red`(#FF2E63) `blue`(#00E5FF) `neutral`(#F8F32B) |
| CLIENT `visual` | `cameraShakeScale` `slowMoDesaturate` `slowMoFovMultiplier`(0.85) `slowMoSoundPitch`(0.6) `slowMoFadeTicks` `crosshairPulseScale` `crosshairBaseSize` `crosshairPulseTicks` `hudScale` `rankPopTicks` `rankPopScale` `showBeat` `beatClickVolume` `beatAccentEvery` `hitMarkerTicks` `killMarkerTicks` `flashTicks` `hitStopInputFreeze`、ビーム：`beamEnabled` `beamFadeTicks`(16) `beamGlowWidth`(0.32) `beamCoreWidth`(0.07) `beamGlowAlpha`(0.45) `beamCoreAlpha`(0.85) `beamCoreWhiten`(0.55) `beamNearFade`(0.8) `beamTaperDistance`(5) `beamMuzzleRight`(0.05) `beamMuzzleDown`(0.14) `beamMuzzleDecay`(10) `beamSubdivide`(1) `beamMaxSegments`(1024) `beamBeatWidth`(1.7)、演出：`fxEnabled` `fxMaxSparks`(2048) `fxHitSparks`(16) `fxImpactSparks`(9) `fxKillSparks`(80) `fxKillShards`(40) `fxBlinkSparks`(24) `fxMuzzleSize`(0.55) `damageNumbers` `damageNumberTicks`(18) `damageNumberScale`(0.03) `blinkFovKick`(0.12) `killFovKick`(-0.06) `fovKickTicks`(8) `killFlashAlpha`(0.16) `speedLineTicks`(7) `killBannerTicks`(34) `hurtIndicatorTicks`(18) `lowHealthThreshold`(6) `beatVignetteAlpha`(0.12) `zoneWall` `zoneWallHeight`(10) `zoneWallAlpha`(0.16) `zoneWallNearAlpha`(0.6) `zoneWallNearDistance`(8)、BGM：`musicEnabled` `musicVolume`(0.8) `musicSlowMoDuck`(0.4) `musicIntermissionDuck`(0.6) `musicLatencyMs`(40) `musicDriveByRank`([0,0.25,0.55,0.8,1]) |

効果音と BGM は `tools/sfx/make_sfx.py` で合成した ogg（`assets/neonclash/sounds/`）で、リソースパックで差し替えられます。
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

### 実機デモの録画（GitHub Actions）

`.github/workflows/neonclash-demo.yml` が、`claude/neonclash-pvp-mod` への push で次を行います。

1. TaCZ の Forge 1.20.1 版を Modrinth から取得（`ci/fetch_tacz.py`）→ `./gradlew build`
2. Xvfb + Mesa（`LIBGL_ALWAYS_SOFTWARE=1`）で `./gradlew :mod:runClient -Pneonclash.demo=true` を起動。
   `-Dneonclash.demo=true` のときだけ動く `DemoDriver` が、ワールド作成 → コマンドでアリーナを作って `/pvp practice 2`（Husk 2 体と練習試合）
   → 照準・射撃・ブリンク・剣・パリィ・強化選択を自動で操作 → 終了、まで行います
3. 画面は `Screenshot.takeScreenshot(mc.getMainRenderTarget())` で `run/demo_frames/` に連番 PNG（実時間 10fps）で保存し、ffmpeg で動画に
4. BGM を `ci/groove.py` で合成し、`demo_beat.json`（録画開始時のビートの位相）でゲーム内の拍に合わせて付ける
5. `runGameTestServer` も実行
6. 動画・ログ・jar を `neonclash-demo-media` ブランチと Artifacts に置く

### 音付きのテスト動画（ローカル）

```bash
./gradlew :mod:runClient -Pneonclash.demo=true -Pneonclash.demoFps=30
python tools/sfx/mix_demo.py --fps 30 --out media/neonclash-demo-full.mp4
```

720p・30fps で録画し、ゲーム内で鳴った音の記録（`run/demo_sounds.jsonl`）から音声を組み立てます。バニラの音（足音など）は入りません。
動画の見栄えのため、録画中だけ FOV を 100 にし、Husk 1 体に銃を撃たせ、プレイヤーに耐性と再生の効果を付けます。試合のルールや既定値は変えません。

### BGM（`ci/groove.py`）

numpy + scipy だけで合成するグルーヴ系の曲です（外部音源なし）。
128 BPM（mod のビート既定値と同じ）・F マイナー、コード進行は | Fm9 | D♭maj9 | A♭maj7 | E♭6/9 | の 4 小節ループ。
四つ打ちキック・2/4 拍のクラップ・スウィングした 16 分ハット・裏拍のオープンハット・シンコペーションするベース（＋サブ）・裏拍のコードスタブ・
スーパーソウのパッド・ピンポンディレイのアルペジオ・リード・スネアロール・ライザー・インパクト、キックによるサイドチェイン。
構成は Intro → Groove → Build → Drop → Breakdown → Drop 2 → Outro で、動画の長さに合わせて伸縮します。

```bash
python3 ci/groove.py --duration 90 --out bgm.wav                       # 0 秒目が小節の頭
python3 ci/groove.py --duration 90 --beat-json run/demo_beat.json --out bgm.wav   # ゲーム内の拍に合わせる
```

### 効果音と試合の BGM（`tools/sfx/make_sfx.py`）

```bash
python tools/sfx/make_sfx.py                 # 全部作り直す
python tools/sfx/make_sfx.py --only hit,kill # 一部だけ（music で BGM）
```

ffmpeg（libvorbis）が必要です。楽器は `ci/groove.py` のものを使います。

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
6. **TaCZ の CurseForge File ID は未確定。** GitHub Actions では Modrinth（`timeless-and-classics-zero`）から最新の Forge 1.20.1 リリースを取っている（1.1.8-hotfix2 で動作確認）。 CurseForge に到達できず「最新の 1.20.1 版」を特定できないため、推測で ID を書かず
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
15. ラストスタンドの「自分以外がスロー」は、アリーナ半径の 2 倍以内の自分以外の全員と、自分以外の弾。踏みとどまった後の体力は config。
16. 時間切れ：生存人数 → 同数なら残り体力の割合の合計 → それも同じなら引き分け。引き分けはどちらにも点が入らず次ラウンド。
    引き分けが続く場合に備えて `maxRounds`（9）で打ち切り、点の多い方の勝ち（同点なら試合も引き分け）。同時全滅も引き分け。
17. 強化 3 択は「枠をランダム → ランクの重みでレア度 → 枠×レア度から 1 つ」。3 つは別々の枠から出す（`distinctCategories`）。
    同じ 3 択内で重複なし、所持済みの非スタック強化は出さない。その枠に重みのあるレア度が無ければ一番低いレア度を出す。選ばなければランダムで 1 つ。
18. スタイルランクは試合を通して持ち越し（ラウンドごとにリセットしない）。ポイント制で、しきい値・増減はすべて config。
19. ビートの判定はサーバー時刻（ms）で、プレイヤーの ping/2（上限 150ms）を差し引く。HUD の脈動と拍のクリック音は、
    サーバー時刻に同期したクライアントの時計で鳴らす（サーバー tick の 50ms 刻みに縛られないため）。BPM 変更は直前の拍を基準に切り替えて拍番号を連続させる。
    猶予の境界ちょうどは猶予内。
20. 移動強化は試合中のプレイヤーだけ（`movement.onlyInMatch`）。剣・ブリンクは試合外でも練習できる。パリィと切り札はプレイヤーは試合中だけ（試合外で `/kill` を耐えるなどを防ぐ）。
21. ロードアウトはインベントリを消さず、持っていない物だけ足す（他の持ち物を壊さないため）。ゲームモードは試合中 ADVENTURE、終了時に元へ戻す。
22. 効果音はバニラの音イベントを参照（オリジナル音源を用意できないため）。命中音とキル音は別の音で、キル音は連続キルでピッチが上がる。→ 判断ログ 47 で自作の音に置き換えた。
23. スロー時の彩度は PostChain（バニラの `color_convolve`）を GUI 描画直前に掛ける。uniform を実行時に変える API が無いので、彩度の値は shader JSON 側。
24. HUD のレイアウト（px 位置）は調整値ではなく配置なのでコード内に置いた。大きさは `hudScale`、演出の長さ・強さは CLIENT config。
25. キー：TaCZ が R/G/C/Z/T/O/V/H を使うので、ブリンク=左 Alt、パリィ=X。
26. GameTest の戦闘者は AI を止めた Husk（日光で燃えない）で代用。試合コントローラはモブも参加者として扱えるようにしてある。

**調整（簡易プレイテストの 3 点）**

シムは実データ（TaCZ の glock_17 は 18 ブロック以内の胴撃ち 7 = 3 発キル）に合わせ、`--stats` で各設定 80 試合を回して比べた。ボットの簡易モデルなので値は傾向として読むこと。

27. **`beat.toleranceMs` 100 → 60。** 偶然ビートに乗る率が 42% → 26%。TaCZ の発射はサーバー tick で処理され判定が最大 50ms 遅れるので、50ms 以下にすると拍ちょうどの入力が外れ得る。60 はその手前で一番厳しい値。
28. **ラストスタンドは `lastStandTicks` 60 → 30、`lastStandFactor` 0.35 → 0.5、`lastStandHealth` 4 → 2。** 体力だけ下げても変化なし（4 も 2 も次の 1 発で倒れる）。効くのはスローで、発動後にキルを取る率が 39% → 32%、ラウンドを生き残る率が 19% → 15%。
    **ただし数値だけでは根本解決ではない。** 1 ライフ 1 回の自動発動は変わらない（キルより発動が多いのも同じ）し、剣の一撃も 1 回は耐える。スローを完全に消しても 21% が下限。満足できなければ発動条件のルール変更が必要。
29. **スタイルは `damage_taken` -50 → -15、`death` -200 → -60、`thresholds` を 60/160/300/480 に。** `damage_taken` だけでは D のままが 81% → 68%。死亡 -200 がキル 3 回分を消すのが主因だったので死亡も緩め、試合が 3〜5 ラウンドで終わることに合わせてしきい値も下げた。強化抽選時のランクは D 81% → 48%、C 27%・B 18%・A 6%。
30. 既定値の変更は既存のワールドには反映されない。`serverconfig/neonclash-server.toml` が残っているワールドは古い値のままなので、新しいワールドで試すか、その toml を消すこと。

**軌道のビーム**

31. 銃の軌道をチーム色の半透明ビームとして残す。TaCZ の曳光（`getTracerColorOverride`）は曳光弾にしか効かず、太さも足りないので使わず、自前で描く。
    弾の位置はサーバーの値を tick ごとに `S2CBeamPacket` で送る。クライアント側の弾は当たり判定を持たず 1 tick 分めり込むのと、範囲スローの速度変更が届かない（テストレポートの既知の問題 4）ので、見た目と当たりをずらさないため。
32. 弾は命中した tick の最後にもう 1 tick 分進むので、そのまま描くと壁や相手を突き抜けて見える。相手に当たった点は `EntityHurtByGunEvent.Pre` で覚え、壁はレイキャストで手前に切る。
33. 色はサーバーのチームカラー（`colors`）。試合外の弾は `neutral`。TaCZ の銃すべて（Husk など mod の参加者以外が撃った弾も含む）が対象。
34. 一人称では弾が目の位置から出るので、根元を銃口の方へずらし（`beamMuzzle*`）、カメラ近くは細く薄くする（`beamTaperDistance` / `beamNearFade`）。腰だめの位置に合わせた値なので、エイム中は根元が少し下にずれる。
35. 銃本体のモデルは TaCZ のままで、変えたのは軌道だけ。
36. 命中パーティクルの位置を弾の座標から相手の体に変更。Pre/Post の時点の弾の座標は tick 開始時のもので、近距離では撃った人の目の前になり、大きなパーティクルが画面を塞いでいた。

**ノックバックと演出**

37. ヒットストップは被弾側の速度を毎 tick 0 にしていたので、そのままではノックバックが消える。入力だけを止める形に変更した（モブは経路を止める）。止まる感触より、押される感触を優先した。
38. TaCZ の銃データの `knockback`（glock は 0）は触らず、この mod の命中処理で全銃共通に押す。銃ごとの強さはまだ無い。向きは弾の水平成分だけを使い、上下の角度で強さが変わらないようにした。連射で積み重なって飛びすぎないよう `knockback.maxSpeed` で頭打ち。
39. 銃の押しは既定で 1 発あたり 1〜2 ブロック。3 発で倒れる撃ち合いでは剣の間合いの外へ押し出せるので、銃が剣に対して有利になる方へ動く。シム（`PlaytestSim`）はノックバックを持たないので、この影響は測っていない。
40. ラストスタンドの発動では周りを押さない。判断ログ 28 のとおり既に 2 つ目のライフに近いので、距離まで取れると強くなりすぎる。剣を受けて耐えた本人だけは吹き飛ぶ（`knockback.sword`）。
41. パリィ成功は範囲スローの半径内の敵を押す。遠くの射手には届かない。キルの爆風は敵味方を問わず押す。
42. 試合中のキルは死亡をキャンセルして体を消すので、倒れる姿の代わりに、倒した弾の向きへ破片が飛ぶ演出にした。向きは `EntityHurtByGunEvent.Pre` で覚える（Post より先に死亡処理が走るため）。
43. 世界の中の演出（`S2CFxPacket` / `client/FxRenderer`）は加算合成の板だけで描く。テクスチャを足さず、色をチームカラーのまま変えられる。バニラのダストは近くで大きな四角になって画面を塞ぐので、既定で切った（`hitParticleCount` / `killParticleCount` は 0）。ブリンクの軌跡のダストは削除。
    光の板はカメラに近いほど薄くする。自分のビート射撃の輪も、銃口がカメラのすぐ前にあるので小さくする。
44. エリアの境界はダストの輪をやめ、HUD の半径からクライアントが光の壁を描く。パケットの追加が要らず、毎 tick なめらかに見える。壁の高さの基準はカメラ（HUD に中心の高さが無いため）。
45. 発射音と発射の光は、弾が実際に出た時点（`EntityJoinLevelEvent`）で出す。`GunFireEvent` は弾薬チェックの前に来るため。TaCZ の銃声は消さずに重ねる。
46. ビートの加点が空撃ちでも入る問題は今回も変えていない。ビートに乗った弾は押しが 1.6 倍、ビームが太く、音に鈴が重なる。

**音と BGM**

47. 効果音を全部自作に置き換えた（`tools/sfx/make_sfx.py`、numpy + scipy + ffmpeg）。バニラの音はゲーム内の別の場面を連想させ、ネオンの世界に合わなかった。音程のある音は F マイナーに揃え、BGM に重なっても濁らないようにした。生成済みの ogg をリポジトリに入れている（CI は再生成しない）。
48. 命中音は連続で当てると音程が上がる（`hitCombo*`）。仕組みは連続キルの `KillStreak` と同じ。合成音を基準ピッチで鳴らすので `hitSoundPitch` を 1.5→1.0、`killBasePitch` を 0.9→1.0 に変更。`killPitchStep` は短 3 度に近い 0.19。
49. 試合の BGM は 128 BPM・16 小節（ちょうど 30 秒）のループで、拍の時計の小節の頭で再生を始める。開始を判定するのは毎フレームで、tick だと最大 50ms ずれるため。音が出るまでの遅れは `musicLatencyMs` で見込む。
50. ループを重ねた時のずれは測っていない。Minecraft の ogg の読み出しがループの長さを完全に守る保証が無いので、ラウンドごとに鳴らし直して誤差を消す（次の小節の頭まで最大 1.9 秒無音になる）。サーバーの `bpm` を変えた場合はピッチで合わせる。
51. BGM はリズムとメロディの 2 層で、スタイルランクでメロディの層の音量が変わる（`musicDriveByRank`）。ランクを上げる動機を音でも見せるため。2 層の再生開始は別々に行うので、数 ms ずれることがある。
52. BGM が鳴っている間は拍のクリックを鳴らさない（キックが拍を刻む）。音量設定の「音楽」が 0 ならクリックに戻る。スロー中は音量だけ下げ、ピッチは変えない（ピッチを変えると拍からずれる）。バニラの音楽は試合中止める。

**弾薬**

53. 弾を持ち歩かなくてもリロードできるようにした。TaCZ の仮想の予備弾薬（銃の NBT `DummyAmmo`）を使い、ラウンド開始時に持っている銃すべてへ `reserveAmmo` まで補充する。弾アイテムは配らない。`reserveAmmo` を 0 にすると従来どおり弾アイテムを配る。試合が終わっても銃の予備弾薬は残る。
