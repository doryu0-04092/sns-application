import { check } from 'k6';
import { Rate } from 'k6/metrics';
import type { RefinedResponse, ResponseType } from 'k6/http';
import type { ApiEnvelope } from './config.ts';

/** k6 の http ヘルパが返すレスポンス。ボディの型は用途によって変わるため総称で受ける。 */
export type K6Response = RefinedResponse<ResponseType | undefined>;

// 想定外のステータスが返った割合。thresholds でこれを 0 に縛る。
export const unexpectedStatus = new Rate('unexpected_status');

/**
 * レスポンスのステータスコードを検証する。
 *
 * 全レスポンスで必ず呼ぶこと。認証に失敗した場合、JwtAuthFilter は
 * DBに一切触れずに即座に 401 を返すため「異常に速い数字」が出る。
 * 検証が無いと、その誤りに気づかないまま「性能が良い」と誤って結論づけてしまう。
 * これは負荷テストで最も踏みやすい罠なので、機械的に検出できるようにしておく。
 */
export function expectStatus(res: K6Response, expected: number, label: string): boolean {
  const ok = res.status === expected;
  unexpectedStatus.add(!ok);
  check(res, { [`${label} == ${expected}`]: () => ok });

  // 全件出すとログが溢れるので、通常は各VUの最初の数イテレーションだけ詳細を出す。
  // 原因調査時は PERF_VERBOSE=1 で全件出す(抑制されたせいで原因が掴めない場面があった)。
  if (!ok && (__ENV.PERF_VERBOSE === '1' || __ITER < 2)) {
    const body = res.body ? String(res.body).slice(0, 300) : '(empty)';
    console.error(`[${label}] expected ${expected} but got ${res.status}: ${body}`);
  }
  return ok;
}

/**
 * レスポンスボディの data を取り出す。
 * このAPIは成功時 {"data": ...} / 失敗時 {"error": {...}} のエンベロープで返す
 * (frontend/src/api/client.ts と同じ扱い)。
 */
export function dataOf<T>(res: K6Response): T | null {
  try {
    const parsed = res.json() as unknown as ApiEnvelope<T>;
    return parsed && parsed.data !== undefined ? (parsed.data as T) : null;
  } catch {
    return null;
  }
}
