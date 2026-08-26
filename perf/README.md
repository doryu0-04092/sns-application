# パフォーマンステストの実行手順

計画と合格条件は [docs/perf-test-plan.md](../docs/perf-test-plan.md)、結果は [docs/perf-test-report.md](../docs/perf-test-report.md) を参照。

## 前提

| 必要なもの | 確認方法 |
|---|---|
| k6 v2 以上 | `k6 version`。無ければ `winget install GrafanaLabs.k6` |
| Docker Desktop | `docker version` |
| Node 22.6 以上 | `node --version`。`summarize.ts` を直接実行するため |

## 使用言語と型検査

スクリプトは TypeScript で書いている（フロントエンドと言語を揃えるため）。

| 対象 | 実行するもの | 備考 |
|---|---|---|
| `k6/**/*.ts` | k6 | k6 が `.ts` を直接実行できる |
| `report/summarize.ts` | Node 22.6+ | Node が `.ts` を直接実行できる |
| `monitor/collect.ps1` | PowerShell | **UTF-8 BOM 付きで保存すること**（下記の罠を参照） |
| `seed/*.sql` | psql | — |

**k6 も Node も型を「剥がす」だけで検査はしない。**
明らかな型エラー（`const x: number = "文字列"`）を含むスクリプトが、
警告も無く最後まで走ることを確認している。
そのため型検査は別に回す必要がある。

```powershell
cd frontend
npm run typecheck:perf     # tsc -p ../perf/tsconfig.json --noEmit
```

CIの frontend ジョブでも実行している。型定義（`@types/k6`）は
`frontend/devDependencies` にあり、`perf/tsconfig.json` の `typeRoots` から参照している。

import 先を `./config.ts` のように `.ts` 付きで書いているのは、
k6 と Node が実行時にそのパスを解決するため。
`tsconfig.json` で `allowImportingTsExtensions` を有効にしてこれを許可している。

計測用スタックは開発用と**ポート・DB名・ボリュームがすべて別**なので、開発スタックと同時に起動できる。

## 1. 計測用スタックを起動する

```powershell
docker compose -p snsapp-perf -f docker-compose.perf.yml up -d --build
docker compose -p snsapp-perf -f docker-compose.perf.yml ps
curl http://localhost:18080/api/health
```

ポートは backend 18080 / postgres 55432 / localstack 54566。

`pg_stat_statements`(どのSQLが総実行時間を食っているかの集計)を有効にする。
拡張本体は起動後に一度だけ作る必要がある。

```powershell
docker compose -p snsapp-perf -f docker-compose.perf.yml exec -T postgres `
  psql -U perf_user -d sns_application_perf -c "CREATE EXTENSION IF NOT EXISTS pg_stat_statements;"
```

## 2. シードデータを投入する

`password_hash` は BCrypt なので SQL では作れない。まず signup API で1件だけ作り、
そのハッシュを全ユーザーで使い回す。

```powershell
curl -X POST http://localhost:18080/api/auth/signup `
  -H "Content-Type: application/json" `
  -d '{"email":"perf_seed_source@example.test","password":"PerfTest1234!","displayName":"Perf Seed Source"}'

$hash = docker compose -p snsapp-perf -f docker-compose.perf.yml exec -T postgres `
  psql -U perf_user -d sns_application_perf -tAc `
  "SELECT password_hash FROM users WHERE email='perf_seed_source@example.test';"
$hash = $hash.Trim()
```

中データ(本番計測用)を投入する。

```powershell
Get-Content perf/seed/seed.sql | docker compose -p snsapp-perf -f docker-compose.perf.yml exec -T postgres `
  psql -U perf_user -d sns_application_perf `
  -v ON_ERROR_STOP=1 -v bcrypt_hash="$hash" `
  -v users_n=500 -v posts_n=20000 -v comments_n=60000 `
  -v likes_n=100000 -v follows_n=10000 -v comment_likes_n=30000
```

小データ(手順確認用)なら `users_n=50 posts_n=1000 comments_n=3000 likes_n=5000 follows_n=500 comment_likes_n=1500`。

## 3. 投入結果を検証する

```powershell
Get-Content perf/seed/verify.sql | docker compose -p snsapp-perf -f docker-compose.perf.yml exec -T postgres `
  psql -U perf_user -d sns_application_perf
```

**「2. 不変条件の検証」がすべて 0 であることを確認してから計測に入る。**
SQL直接投入はアプリの不変条件を迂回するため、壊れたデータのまま計測すると
遅さの原因がアプリなのかデータなのか分からなくなる。

「4. コメント数が多い投稿 上位5件」に出た投稿IDを控える。次の手順で `PERF_HOT_POST_IDS` に渡す。

## 4. 環境変数

| 変数 | 既定 | 意味 |
|---|---|---|
| `BASE_URL` | `http://localhost:18080/api` | 計測対象。**AWS構成が固まったらここを差し替えるだけで同じシナリオが動く** |
| `PERF_PROFILE` | `load` | `smoke` / `smoke-preauth` / `load` / `stress` / `soak` / `spike` / `saturate` |
| `PERF_SLEEP` | `1` | 1イテレーションあたりの待ち時間(秒)。`0` で待たずに投げ続ける。**飽和点の探索に必須**(既定の1秒だと 1VU=最大1req/s となり、VU数が上限を決めてしまう) |
| `PERF_USER_COUNT` | `50` | シードしたユーザー数。VUをこの数に写像して割り当てる |
| `PERF_HOT_POST_IDS` | `11,21,31` | コメントが極端に多い投稿のID(手順3で確認した値) |
| `PERF_PASSWORD` | `PerfTest1234!` | 全 perf ユーザー共通のパスワード |
| `PERF_VERBOSE` | 未設定 | `1` にすると想定外ステータスを全件ログに出す(原因調査用) |

## 5. スクリプトの動作確認(必ず先に行う)

本番計測の前に必ずスモークで全シナリオを通す。データ量を上げてから失敗すると、
「スクリプトのバグか、アプリの限界か」が切り分けられなくなる。

```powershell
$env:PERF_PROFILE = "smoke"
foreach ($s in "timeline","following-feed","post-detail","search","write","login","mixed") {
  k6 run "perf/k6/scenarios/$s.ts"
}
```

## 6. 計測を実行する

### 負荷テスト(6シナリオ)

```powershell
$env:PERF_PROFILE = "load"
foreach ($s in "timeline","following-feed","post-detail","search","write","login") {
  k6 run --summary-export "perf/results/load-$s.json" "perf/k6/scenarios/$s.ts"
}
```

### ストレス / 耐久 / スパイク

これらは応答時間だけでは判定できない。**別ウィンドウで観測スクリプトを回しながら実行する。**

```powershell
# 別ウィンドウ
pwsh -File perf/monitor/collect.ps1 -OutFile perf/results/monitor-stress.csv

# 計測ウィンドウ
$env:PERF_PROFILE = "stress"
k6 run --summary-export perf/results/stress-mixed.json perf/k6/scenarios/mixed.ts
```

`soak`(30分)と `spike` も同様。soak は `-DurationSeconds 1900` を付けて自動終了させるとよい。

## 7. クリーンアップ(必須)

```powershell
docker compose -p snsapp-perf -f docker-compose.perf.yml down -v
```

**計測が失敗・中断した場合も必ず実行する。** ストレステストは意図的に壊すため、
途中で止まる前提で手順を組んでいる。

確認:

```powershell
docker volume ls | Select-String "snsapp-perf"    # 0件であること
docker volume ls | Select-String "sns-application_pgdata"  # 開発用は残っていること
```

## 設計上の要点(スクリプトを読む前に)

| 要点 | 理由 |
|---|---|
| `noCookiesReset: true` を設定している | k6 は**既定でイテレーションごとに cookie jar をリセットする**。このアプリの認証は httpOnly クッキーだけに依存するため、既定のままだと各VUの2回目以降が全て401になる。401は認証フィルタがDBに触れず即返すので応答が異常に速く、ステータス検証が無ければ「非常に高速」という誤った結果になる |
| 全レスポンスでステータスを検証する | 上と同じ理由。負荷テストで最も踏みやすい罠なので機械的に検出する |
| ログインはVUごとに1回だけ | BCrypt(cost 10)は1回あたり数十〜数百msのCPUを消費する。毎回呼ぶと測定値がBCryptに支配される |
| VUごとに別ユーザーを使う | 全VUが同一ユーザーだと `isMine`/`isFollowing`/`isLiked` の結果がキャッシュに乗り、実際より速い数字が出る |
| stress / spike は事前認証を使う | VUが300まで増えるため、各VUのログインでBCryptが300回走ると「何が飽和したか」の答えがBCryptになってしまう |
| soak は事前認証を使わない | 30分でアクセストークン(15分)が失効する。refresh_token はローテーション方式で、共有すると盗用と判定され全トークンが失効する |
| いいね対象から `deleted` と `isMine` を除外している | それぞれ 404 / 400 を返すアプリの正しい挙動。除外しないとシナリオ側の不備をアプリのエラー率として数えてしまう |

## 検証手順の注意(実際に踏んだ失敗)

**スクリプトの動作確認では `level=error` を除外してはいけない。**

飽和テストの準備中、検証コマンドを
`k6 run ... | grep -E "✓|✗|checks_succeeded"` と書いたため、
`level=error` の行が自分のフィルタで落ち、
`ReferenceError: SLEEP_SECONDS is not defined` が出ているのに
「checks 100%」だけを見て OK と判定してしまった。

例外はHTTPリクエストの**後**で投げられるため、リクエスト自体は成功し
チェックも通る。そのため「チェックが通っている＝正常」は成立しない。
その状態で本番計測に入り、8分間の測定結果を1本無駄にした。

検証は必ず**エラーが無いことを先に見る**こと:

```powershell
$out = k6 run --summary-mode compact "perf/k6/scenarios/mixed.ts" 2>&1
if ($out -match "level=error|ReferenceError") { "FAILED" } else { "OK" }
```
