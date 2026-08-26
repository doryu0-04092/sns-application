import { ApiError } from "./client";

/** 再試行する最大回数。TanStack Query の既定値と同じ。 */
const MAX_RETRIES = 3;

/**
 * クエリの失敗を再試行してよいかを判定する。
 *
 * <b>なぜ必要か</b>: 既定では 4xx でも3回再試行される。
 * 存在しない投稿を開いたとき `GET /api/posts/{id}` が404を4回(初回+3回)発行し、
 * その間ずっと「読み込み中...」が出続けて、
 * 「見つかりませんでした」の表示が約2.5秒遅れていた(E2Eテストで実測)。
 *
 * 4xx は「リクエストが誤っている」「対象が存在しない」というサーバーの確定した応答であり、
 * 同じリクエストを送り直しても結果は変わらない。再試行に意味があるのは
 * 通信の瞬断や 5xx のような一時的な失敗だけである。
 *
 * 401 も再試行しない。アクセストークン失効の復帰は
 * `apiFetch` 側がリフレッシュ+1回だけの再実行で吸収しており(client.ts)、
 * そこを通ってなお401なら再試行しても通らない。
 */
export function shouldRetryQuery(failureCount: number, error: unknown): boolean {
  if (error instanceof ApiError && error.status >= 400 && error.status < 500) {
    return false;
  }
  return failureCount < MAX_RETRIES;
}
