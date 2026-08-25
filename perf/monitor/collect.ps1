# 計測中のリソース使用状況を一定間隔でCSVに記録する。
#
# なぜ必要か:
#   応答時間だけでは「何が枯渇したのか」が分からない。CPUが足りないのか、
#   メモリが足りないのか、DBコネクションが足りないのかで打つ手がまったく違う。
#   特にストレステストと耐久テストは、この観測が無いと結果を解釈できない。
#
# 見どころ:
#   - db_connections が 10 で張り付く  -> HikariCP のプールサイズ(既定10)が上限。
#     このとき応答時間は connection-timeout の既定30秒に張り付くはずで、
#     その2つが揃えば原因はプールサイズだと特定できる。
#   - backend_mem_mb が単調増加      -> リークの疑い(耐久テストの主眼)。
#   - backend_cpu_pct が上限に張り付く -> CPU律速。相関サブクエリか BCrypt か署名生成。
#
# 使い方:
#   pwsh -File perf/monitor/collect.ps1 -OutFile perf/results/monitor-stress.csv
#   停止は Ctrl+C、または -DurationSeconds で自動終了させる。

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

# ヘッダーを書き出す。-Encoding utf8 を明示しないと Set-Content が
# システムANSIコードページで書き、他ツールで読めなくなることがある。
'timestamp,elapsed_sec,backend_cpu_pct,backend_mem_mb,postgres_cpu_pct,postgres_mem_mb,db_connections,db_active,db_idle_in_txn,wait_events' |
    Out-File -FilePath $OutFile -Encoding utf8

Write-Host "collecting to $OutFile (interval ${IntervalSeconds}s). Ctrl+C to stop."

$start = Get-Date

# docker stats を1回だけ実行して指定コンテナの CPU% と メモリMB を返す。
function Get-ContainerStats {
    param([string]$NamePattern)

    $line = docker stats --no-stream --format '{{.Name}}|{{.CPUPerc}}|{{.MemUsage}}' |
            Where-Object { $_ -like "*$NamePattern*" } |
            Select-Object -First 1

    if (-not $line) { return @{ Cpu = ''; Mem = '' } }

    $parts = $line -split '\|'
    $cpu = ($parts[1] -replace '%', '').Trim()
    # MemUsage は "123.4MiB / 1GiB" の形。左側だけを MB に直す。
    $memRaw = ($parts[2] -split '/')[0].Trim()
    $mem = ''
    if ($memRaw -match '^([\d.]+)\s*([KMG]i?B)$') {
        $value = [double]$matches[1]
        switch ($matches[2]) {
            'KiB' { $mem = [math]::Round($value / 1024, 1) }
            'MiB' { $mem = [math]::Round($value, 1) }
            'GiB' { $mem = [math]::Round($value * 1024, 1) }
            default { $mem = [math]::Round($value, 1) }
        }
    }
    return @{ Cpu = $cpu; Mem = $mem }
}

function Invoke-PerfDbQuery {
    param([string]$Sql)
    $result = docker compose -p $Project -f $ComposeFile exec -T postgres `
        psql -U $DbUser -d $DbName -tAc $Sql
    if ($LASTEXITCODE -ne 0) { return '' }
    return ($result | Out-String).Trim()
}

while ($true) {
    $now = Get-Date
    $elapsed = [int]($now - $start).TotalSeconds

    if ($DurationSeconds -gt 0 -and $elapsed -ge $DurationSeconds) { break }

    try {
        $backend = Get-ContainerStats -NamePattern 'backend'
        $postgres = Get-ContainerStats -NamePattern 'postgres'

        # アプリが握っている接続数。HikariCP のプールサイズがそのまま現れる。
        $connSql = "SELECT count(*) FROM pg_stat_activity WHERE datname='$DbName' AND backend_type='client backend';"
        $connections = Invoke-PerfDbQuery -Sql $connSql

        $activeSql = "SELECT count(*) FROM pg_stat_activity WHERE datname='$DbName' AND state='active';"
        $active = Invoke-PerfDbQuery -Sql $activeSql

        # idle in transaction が積み上がるとトランザクションの閉じ忘れを疑う。
        $idleTxSql = "SELECT count(*) FROM pg_stat_activity WHERE datname='$DbName' AND state='idle in transaction';"
        $idleInTx = Invoke-PerfDbQuery -Sql $idleTxSql

        # 何を待っているか。Lock なら競合、IO ならディスク、null 主体なら CPU 律速。
        $waitSql = "SELECT coalesce(string_agg(wait_event_type || ':' || cnt, ' '), 'none') FROM (SELECT coalesce(wait_event_type,'running') AS wait_event_type, count(*) AS cnt FROM pg_stat_activity WHERE datname='$DbName' AND state='active' GROUP BY 1) x;"
        $waits = (Invoke-PerfDbQuery -Sql $waitSql) -replace ',', ';'

        $row = '{0},{1},{2},{3},{4},{5},{6},{7},{8},{9}' -f `
            $now.ToString('yyyy-MM-ddTHH:mm:ss'), $elapsed,
            $backend.Cpu, $backend.Mem, $postgres.Cpu, $postgres.Mem,
            $connections, $active, $idleInTx, $waits

        Add-Content -Path $OutFile -Value $row -Encoding utf8
    }
    catch {
        # 観測が落ちても計測本体は止めない。欠測として記録して次へ進む。
        Add-Content -Path $OutFile -Encoding utf8 -Value (
            '{0},{1},,,,,,,,COLLECT_ERROR' -f $now.ToString('yyyy-MM-ddTHH:mm:ss'), $elapsed
        )
    }

    Start-Sleep -Seconds $IntervalSeconds
}

Write-Host "done. rows written to $OutFile"
