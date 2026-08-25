# 計測中のリソース使用状況を一定間隔でCSVに記録する。
#
# なぜ必要か:
#   応答時間だけでは「何が枯渇したのか」が分からない。CPUが足りないのか、
#   メモリが足りないのか、DBコネクションが足りないのかで打つ手がまったく違う。
#   特にストレステストと耐久テストは、この観測が無いと結果を解釈できない。
#
# 見どころ:
#   - db_active が 10 に張り付く      -> HikariCP のプール(既定10本)を全て使い切っている。
#     このとき応答時間が connection-timeout の既定30秒に張り付いていれば、
#     原因はプールサイズだと特定できる。
#     ※ db_connections はアイドル時から 10 前後ある。HikariCP は
#       minimumIdle も既定10なので、待機中でも接続を保持し続けるため。
#       枯渇の兆候は「接続数の増加」ではなく「active の増加」に現れる。
#   - backend_mem_mb が単調増加       -> リークの疑い(耐久テストの主眼)。
#   - backend_cpu_pct が上限に張り付く -> CPU律速。相関サブクエリか BCrypt か署名生成。
#   - wait_events が Lock 主体         -> ロック競合。Client/IO ならディスク待ち。
#
# 使い方:
#   .\perf\monitor\collect.ps1 -OutFile perf\results\monitor-stress.csv
#   停止は Ctrl+C、または -DurationSeconds で自動終了させる。
#
# 注意: このファイルは UTF-8 BOM 付きで保存すること。
#   Windows PowerShell 5.1 は BOM の無い UTF-8 をシステムANSIコードページとして読むため、
#   日本語コメントが化け、化けた結果に引用符が現れると構文エラーになる。

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$OutFile,

    [int]$IntervalSeconds = 5,

    # 0 なら手動で止めるまで回り続ける。
    [int]$DurationSeconds = 0,

    [string]$Project = 'snsapp-perf',
    [string]$ComposeFile = 'docker-compose.perf.yml',
    [string]$DbUser = 'perf_user',
    [string]$DbName = 'sns_application_perf'
)

$ErrorActionPreference = 'Stop'

$outDir = Split-Path -Parent $OutFile
if ($outDir -and -not (Test-Path $outDir)) {
    New-Item -ItemType Directory -Force -Path $outDir | Out-Null
}

'timestamp,elapsed_sec,backend_cpu_pct,backend_mem_mb,postgres_cpu_pct,postgres_mem_mb,db_connections,db_active,db_idle_in_txn,max_query_sec,wait_events' |
    Out-File -FilePath $OutFile -Encoding utf8

Write-Host "collecting to $OutFile (interval ${IntervalSeconds}s). Ctrl+C to stop."

$start = Get-Date

# メモリ表記 "123.4MiB" を MB の数値に直す。
function ConvertTo-Megabytes {
    param([string]$Raw)
    if ($Raw -match '^([\d.]+)\s*([KMG]i?B)$') {
        $value = [double]$matches[1]
        switch ($matches[2]) {
            'KiB' { return [math]::Round($value / 1024, 1) }
            'MiB' { return [math]::Round($value, 1) }
            'GiB' { return [math]::Round($value * 1024, 1) }
        }
        return [math]::Round($value, 1)
    }
    return ''
}

# DBの状態は1本のクエリでまとめて取る。
# 分けて発行すると docker exec の往復だけで1サンプル数秒かかり、
# 指定した間隔どおりにサンプリングできなくなる(実測で3秒指定が9秒になった)。
$dbSql = @"
SELECT
  count(*) FILTER (WHERE backend_type = 'client backend')          AS conns,
  count(*) FILTER (WHERE state = 'active')                         AS active,
  count(*) FILTER (WHERE state = 'idle in transaction')            AS idle_in_tx,
  coalesce(round(max(extract(epoch FROM now() - query_start)) FILTER (WHERE state = 'active')::numeric, 1), 0) AS max_query_sec,
  coalesce(
    (SELECT string_agg(t || ':' || c, ' ')
     FROM (SELECT coalesce(wait_event_type, 'running') AS t, count(*) AS c
           FROM pg_stat_activity
           WHERE datname = '$DbName' AND state = 'active'
           GROUP BY 1) w),
    'none')                                                        AS waits
FROM pg_stat_activity
WHERE datname = '$DbName';
"@

while ($true) {
    $now = Get-Date
    $elapsed = [int]($now - $start).TotalSeconds

    if ($DurationSeconds -gt 0 -and $elapsed -ge $DurationSeconds) { break }

    $backendCpu = ''; $backendMem = ''; $pgCpu = ''; $pgMem = ''
    $conns = ''; $active = ''; $idleInTx = ''; $maxQuery = ''; $waits = ''

    try {
        # docker stats は全コンテナ分を1回で取る(コンテナごとに呼ぶと往復が倍になる)。
        $stats = docker stats --no-stream --format '{{.Name}}|{{.CPUPerc}}|{{.MemUsage}}'
        foreach ($line in $stats) {
            $parts = $line -split '\|'
            if ($parts.Count -lt 3) { continue }
            $cpu = ($parts[1] -replace '%', '').Trim()
            $mem = ConvertTo-Megabytes -Raw (($parts[2] -split '/')[0].Trim())
            if ($parts[0] -like '*backend*')  { $backendCpu = $cpu; $backendMem = $mem }
            if ($parts[0] -like '*postgres*') { $pgCpu = $cpu; $pgMem = $mem }
        }

        $row = docker compose -p $Project -f $ComposeFile exec -T postgres `
            psql -U $DbUser -d $DbName -tAF '|' -c $dbSql
        if ($LASTEXITCODE -eq 0 -and $row) {
            $fields = (($row | Out-String).Trim()) -split '\|'
            if ($fields.Count -ge 5) {
                $conns = $fields[0]; $active = $fields[1]; $idleInTx = $fields[2]
                $maxQuery = $fields[3]; $waits = $fields[4] -replace ',', ';'
            }
        }
    }
    catch {
        # 観測が落ちても計測本体は止めない。欠測として記録して次へ進む。
        $waits = 'COLLECT_ERROR'
    }

    $line = '{0},{1},{2},{3},{4},{5},{6},{7},{8},{9},{10}' -f `
        $now.ToString('yyyy-MM-ddTHH:mm:ss'), $elapsed,
        $backendCpu, $backendMem, $pgCpu, $pgMem,
        $conns, $active, $idleInTx, $maxQuery, $waits

    Add-Content -Path $OutFile -Value $line -Encoding utf8

    Start-Sleep -Seconds $IntervalSeconds
}

Write-Host "done. rows written to $OutFile"
