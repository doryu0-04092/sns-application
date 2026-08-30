#!/usr/bin/env bash
# PRのCIが終わるまで待ち、各チェックが確定した時点で1行ずつ出力する。
#
# **`gh pr checks --watch` を直接使わない理由。**
# push の直後はチェックがまだGitHubに登録されておらず、--watch は監視対象が無いまま
# 即座に終了コード8(Checks pending)で抜ける。その戻り値を「完了」と受け取ると、
# **一度も走っていないCIを緑だと誤認する**。実際にそれで取り違えた(2026-08-30)。
#
# そのため
#   1. チェックが1件以上登録されるまで待ってから
#   2. bucket が pending でなくなるまで待つ
#   3. かならず sleep を挟み、上限時間で必ず抜ける
# という形にしている。**終了コードではなく bucket で判定する。**
#
# 使い方: wait-for-checks.sh <PR番号> [上限秒(既定 2400)] [間隔秒(既定 30)]
set -uo pipefail

PR="${1:?PR番号を渡すこと}"
DEADLINE_SECONDS="${2:-2400}"
INTERVAL="${3:-30}"

started=$(date +%s)
seen=""

elapsed() { echo $(( $(date +%s) - started )); }

while :; do
    # 一時的なAPIエラーで監視ごと落とさない。次の周回で取り直す。
    snapshot=$(gh pr checks "$PR" --json name,bucket,link 2>/dev/null) || snapshot=""

    if [ -n "$snapshot" ] && [ "$(jq -r 'length' <<<"$snapshot")" -gt 0 ]; then
        # 確定したものだけを、初めて見た時に1行出す。
        current=$(jq -r '.[] | select(.bucket != "pending") | "\(.name): \(.bucket)"' <<<"$snapshot" | sort)
        comm -13 <(printf '%s\n' "$seen") <(printf '%s\n' "$current")
        seen="$current"

        if jq -e 'all(.bucket != "pending")' <<<"$snapshot" >/dev/null; then
            if jq -e 'any(.bucket == "fail")' <<<"$snapshot" >/dev/null; then
                echo "RESULT: FAIL ($(elapsed)秒)"
                exit 1
            fi
            echo "RESULT: PASS ($(elapsed)秒)"
            exit 0
        fi
    fi

    if [ "$(elapsed)" -ge "$DEADLINE_SECONDS" ]; then
        # **黙って抜けない。** 抜けた理由が分からないと「まだ動いている」と見分けが付かない。
        echo "RESULT: TIMEOUT ($(elapsed)秒。チェックが確定しなかった)"
        exit 2
    fi

    sleep "$INTERVAL"
done
