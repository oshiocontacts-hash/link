# Twitch Genre Pulse
Twitchカテゴリの時間別トレンドを可視化するNext.jsダッシュボードです。

## セットアップ
```bash
pnpm install
pnpm prisma:migrate
pnpm seed
pnpm dev
```

## 実データ収集
`.env` を作成:
```env
TWITCH_CLIENT_ID=""
TWITCH_CLIENT_SECRET=""
DATABASE_URL="file:./dev.db"
TWITCH_COLLECT_MAX_PAGES="10"
TWITCH_DEFAULT_LANGUAGE="all"
```

```bash
pnpm collect -- --language all --pages 10
pnpm collect -- --language ja --pages 10
```

## cron
```cron
0 * * * * cd /path/to/twitch-genre-pulse && pnpm collect -- --language all --pages 10
5 * * * * cd /path/to/twitch-genre-pulse && pnpm collect -- --language ja --pages 10
```

## 注意
Get Streamsのページング中にライブ状況が変わるため、値は近似です。
