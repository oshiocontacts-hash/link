# 引き継ぎメモ（2026-11-01 時点）

別の PC で開発を続けるためのメモ。新しい PC の Claude は、これと README の判断ログを先に読むこと。

## セットアップ

必要なもの：Git、JDK 17（Temurin）、Python 3 + numpy・scipy、ffmpeg（libvorbis 入り）、GitHub CLI（`gh`。CI の確認用）

```bash
git clone https://github.com/oshiocontacts-hash/link
cd link
git checkout claude/neonclash-pvp-mod
cd neonclash
pip install numpy scipy
python ci/fetch_tacz.py
./gradlew build
./gradlew :mod:runGameTestServer
```

- プロジェクトは `neonclash/` の下。ブランチは `claude/neonclash-pvp-mod`。
- TaCZ の jar（`mod/libs/`、1.1.8-hotfix2）は git に入っていないので `ci/fetch_tacz.py` で取る。`gradle.properties` の設定は済んでいる。
- `mod/run/`（ワールド・設定）と `media/`（動画）も git に入っていない。

新しい PC で必要な手作業：

- `mod/run/eula.txt` は持ち越されない。`:mod:runServer` を使う前に、EULA の確認を本人に取ること。
- マスター音量は 100% から始まる。本人は 19% で耳が痛く、5% では小さすぎ、**10%** に落ち着いた。初回の起動前に `mod/run/options.txt` の `soundCategory_master:0.1` を設定する（ファイルが無ければ一度起動してから、ゲームを閉じて編集）。

## 作業のルール（本人の指示）

- 返事は全部日本語。
- 数値はすべて `NCConfig` 経由。Minecraft に依存しないロジックは `core/` に置いて JUnit を書く。
- `client` パッケージは `DistExecutor` 経由でのみ呼ぶ。
- TaCZ の API はソースで確認する（github.com/MCModderAnchor/tacz の 1.20.1 ブランチ）。HEAD は配布版より新しいので、`javap` で jar でも確かめる。
- 区切りごとにコミットして同じブランチへ push。ドキュメントだけの変更は `[skip ci]`。
- push で GitHub Actions が動き、結果は `neonclash-demo-media` ブランチに入る。
- 判断は README の判断ログに理由と一緒に追記する。
- サブエージェントを使うときは Sonnet を指定する（使用量の節約）。

## 落とし穴

- PowerShell は `-Pneonclash.demo=true` を分割するので、`"-Pneonclash.demo=true"` と引用符で囲む。
- Windows の clone は CRLF。改行をまたぐ置換は改行をそろえてから行う。
- 既存ワールドには既定値の変更が効かない（`serverconfig/neonclash-server.toml` が残る）。新しいワールドで試す。
- 音は Claude には聞こえない。長さ・音量・スペクトログラムで確かめ、聞いていないことを本人に伝える。
- デモのウィンドウは本人のデスクトップに開き、スピーカーから音が出る。
- 一人称のカメラのすぐ近くに描いた演出は巨大に写り、画面を白くする。カメラとの距離で薄くし、剣の間合いの連続フレームで確かめる（まばらなサンプルだと見逃す）。
- 録画の自動操作は間合いを取るように調整してある。突っ込ませると Husk の体で画面が埋まる。
- 録画中の `Cannot contact authentication server` は開発用アカウントの通信エラーで、mod とは無関係。

## 確認の手順

```bash
./gradlew build
./gradlew :mod:runGameTestServer
```

音付きのテスト動画：

```bash
./gradlew :mod:runClient -Pneonclash.demo=true -Pneonclash.demoFps=30
python tools/sfx/mix_demo.py --fps 30 --out media/neonclash-demo-full.mp4
```

手で遊ぶとき：`./gradlew :mod:runClient`、スーパーフラット・チート ON の新規ワールドで `/pvp arena set` → `/pvp practice 3`。

## 今の状態

できているもの：銃の軌道のビーム、3 点の調整（ビート猶予・ラストスタンド・スタイル）、ノックバック、ネオン演出一式、自作の効果音と拍同期の BGM、手持ちの弾なしのリロード、自動操作の動画。

最後の確認結果：ユニットテスト 143 件、GameTest 12/12、GitHub Actions 成功。

本人の確認待ち・未解決：

- 音が気持ちいいか。本人から「耳が痛い」と言われたのは音量についてで、音そのものの評価はまだ。
- BGM のキックが拍の表示より遅れて聞こえないか（遅れるなら `musicLatencyMs`）。
- ノックバックの強さ。銃が剣より有利になる方へ動いている。シミュレーションには入っていない。
- 剣の斬撃の弧は爆発の光に紛れがち。
- プレイヤー自身が押される動きは未確認。
- ラストスタンドは数値だけでは「2 つ目のライフ」を解消できていない。まだ強ければ発動条件の変更が要る（README 判断ログ 28）。
- ビートの加点は空撃ちでも入る。
- 銃本体のモデルは TaCZ のまま。
- 練習の Husk は銃を撃たない（録画の間だけ撃たせている）。緑の渦は Husk の攻撃の空腹効果で、この mod の演出ではない。
- BGM はラウンドごとに鳴らし直すので、開始直後に最大 1.9 秒無音になる。
