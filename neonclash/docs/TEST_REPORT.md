# テストレポート

実行日: 2026-10-30 / 実行環境: Linux（クラウド VM）、OpenJDK 17.0.20、Gradle 8.8（wrapper）

## まとめ

| 種類 | 結果 |
|---|---|
| ユニットテスト（JUnit 5、`core`） | **113 件すべて成功**（失敗 0・スキップ 0） |
| GameTest（Forge、`runGameTestServer`） | **未実行**：7 件を実装済みだが、Forge / Minecraft を取得できずビルド不可 |
| スモークテスト（`runServer`） | **未実行**：同上 |
| スモークテスト（Xvfb + `runClient` で HUD スクショ） | **スキップ**：同上（Xvfb 自体は VM に入っている） |
| ビルド済み jar | `core/build/libs/neonclash-core-0.1.0.jar` のみ。mod jar（`neonclash-0.1.0.jar`）は作れていない |

実行コマンド: `./gradlew -Pneonclash.coreOnly=true clean :core:build`（JUnit の XML/HTML は `core/build/test-results/test/`・`core/build/reports/tests/test/`）

## 取得できなかった依存（ネットワークポリシーで CONNECT 403）

| ホスト | 必要だったもの |
|---|---|
| `maven.minecraftforge.net` | ForgeGradle プラグイン、Forge 1.20.1-47.x、MCP/公式マッピング関連 |
| `piston-meta.mojang.com` / `piston-data.mojang.com` / `launchermeta.mojang.com` / `launcher.mojang.com` / `libraries.minecraft.net` / `resources.download.minecraft.net` | Minecraft 本体・ライブラリ・アセット |
| `cursemaven.com` / `www.cursemaven.com` / `www.curseforge.com` / `api.curseforge.com` / `edge.forgecdn.net` / `mediafilez.forgecdn.net` | TaCZ の配布 jar（と最新 File ID の確認） |
| `api.modrinth.com` | TaCZ の代替取得元（確認のみ） |

ビルド失敗ログ: [`docs/logs/forge-build-attempt.log`](logs/forge-build-attempt.log)（`Plugin [id: 'net.minecraftforge.gradle'] was not found`）。
指示どおり、ミラーなどでの迂回はしていません。環境のネットワーク設定で上記ホストを許可すれば、そのまま `./gradlew build` 以降を実行できる構成です。

取得できたもの: Gradle 配布（services.gradle.org）、JUnit（Maven Central、`repo.maven.apache.org` が断続的に 429 を返したため `repo1.maven.org` を併用）、
OpenJDK 17（apt）、TaCZ のソース（GitHub、読み取りのみ。API 確認に使用）。

## ユニットテスト（全件成功）

| クラス | 件数 | 主な内容 |
|---|---|---|
| `BeatClockTest` | 24 | 拍ちょうど／猶予内（±100ms ちょうどを含む）／猶予外（±100.1ms 以上）／拍の中間／早押し遅押しの符号／基準より前／**BPM 変更（直前の拍で再基準化・拍番号が連続）**／割り切れない BPM（128）で誤差が溜まらない／拍ちょうどで BPM 変更／猶予 0／無効化／位相／不正 BPM／クライアント同期 |
| `BlinkChargesTest` | 11 | 2 チャージ使い切り／順番に回復／チャージごとのクールダウン（ビート短縮）／回復中の消費／上限／一括 tick／CD 0／最大数の増加／全回復／進捗／不正値 |
| `BlinkResolverTest` | 9 | 全距離／**壁の手前で止まる**／**厚さ 1 の壁を抜けない**（刻みを荒く設定しても）／床沿いに滑る／段差の持ち上げと上限／**天井にめり込まない**／薄い天井を持ち上げ補正で抜けない／密着時は失敗／ゼロベクトル |
| `SwordChargeTest` | 9 | 溜め完了 tick にだけ斬撃／二重溜め不可／**溜め中の被弾でリセット**／リセット後は最初から／ロックアウト／溜め中以外の被弾は無視／硬直中は次の溜め不可／進捗／硬直 0 |
| `KillStreakTest` | 2 | 連続キルでピッチ上昇・上限／間隔が空くとリセット |
| `StyleMeterTest` | 9 | 初期 D／空中・ブリンク・剣キルの上昇量／D→S／**被弾で下降**（境界ちょうど含む）／下限・上限／しきい値の境界／進捗／不正設定／ランクの上下 |
| `RoundEngineTest` | 14 | 60 秒で縮小開始／全滅で勝利と決着キルの記録／**時間切れ**（人数→体力→引き分け）／同時全滅は引き分け／**3 本先取**／2-2 からの決着／maxRounds 打ち切り（点差あり・同点）／次ラウンドで復活・時間リセット／ラウンド外の脱落無視／ラウンド間のチーム消滅／チーム不足で開始不可／エリア縮小の補間 |
| `UpgradeDraftTest` | 30 | 3 択の重複なし・枠も別（×20 回）／**ランクが高いほど平均レア度が上がる**（4000 回×5 ランク、期待値 ±0.05）／重み 0 のレア度は出ない／所持済み非スタックは除外・スタック可は出る／候補不足／レア度のフォールバック／プール内の ID 重複／枠が少ない場合／シード固定で再現／config 文字列のパース／強化値の合算 |
| `SkillsTest` | 5 | パリィの受付・クールダウン・成功時短縮／**ラストスタンドは 1 ライフ 1 回**／範囲スロー（内外・重なり・期限） |

## GameTest（実装済み・未実行）

`mod/src/main/java/com/neonclash/gametest/NCGameTests.java`、テンプレート `data/neonclash/structures/arena.nbt`（9×6×9、床のみ。生成スクリプトで書き出し、読み戻して構造を確認済み）。

| テスト | 内容 | 状態 |
|---|---|---|
| `swordKillsInOneHit` | 体力 100 のアイアンゴーレムが剣 1 回で倒れる。溜め完了前は当たらない | 未実行 |
| `swordOutOfRangeMisses` | 射程外の相手には当たらない | 未実行 |
| `blinkDoesNotPassThroughWall` | 厚さ 1 の壁の手前で止まり、めり込まない。壁に密着した 2 回目は失敗しチャージを消費しない | 未実行 |
| `slowZoneAffectsOnlyInside` | 範囲内のモブにだけスロー、範囲内の矢だけ減速 | 未実行 |
| `lastStandTriggersOncePerLife` | 1 回目の致命傷は耐える・他者がスロー・2 回目は死ぬ・次のライフで再装填 | 未実行 |
| `roundFlowKillEndUpgradeNext` | 開始 → キル（死亡キャンセル・脱落）→ ラウンド終了 → 強化選択（重複なし・1 回だけ）→ 未選択者にランダム付与 → ラウンド 2 開始 | 未実行 |
| `taczGunHitGoesThroughModHandlers` | AI 停止モブに TaCZ のハンドガン（`IGunOperator`）で撃たせ、命中が `EntityHurtByGunEvent` 経由でこの mod の処理を通る | 未実行 |

## 既知の問題・リスク

1. **Forge 層は一度もコンパイルされていない。** API の使い方は Forge 1.20.1 / 公式マッピングの知識と TaCZ のソースに基づくもので、
   最初のビルドでいくつかコンパイルエラーが出る可能性がある（別の目でのコードレビューで見つかった点は修正済み。下記）。
2. TaCZ の CurseForge File ID が未設定（`gradle.properties`）。設定しないと mod のビルドは明示的に止まる。
3. GameTest `taczGunHitGoesThroughModHandlers` は、TaCZ の既定の銃パックが GameTest サーバーで読み込まれること、モブの射撃に必要な初期化（`initialData` → `draw`）が
   ソースから読み取った通りに動くことが前提。ブレがあると数発外れるので `succeedWhen` で命中するまで撃ち続ける作りにしている。
4. TaCZ の弾は `setShouldReceiveVelocityUpdates(false)` なので、範囲スローで遅くした弾の見た目が遅れる可能性がある（`hurtMarked` で速度パケットを送っているが未確認）。当たり判定はサーバーなので影響はない。
5. スロー中の彩度はシェーダー JSON の固定値（実行時に強さを変える API が無い）。フェードは FOV・音程・レターボックスで表現。
6. プレイヤーのヒットストップはクライアント側で止めている（サーバー権威ではない）。改造クライアントは無視できる。
