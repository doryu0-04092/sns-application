// 計測対象と規模の設定。
//
// 接続先をハードコードしないのは、AWS構成が固まった時点で BASE_URL を差し替えるだけで
// 同じシナリオをそのまま再実行できるようにするため。
// 例: BASE_URL=https://api.example.com/api k6 run scenarios/timeline.ts

export const BASE_URL: string = __ENV.BASE_URL || 'http://localhost:18080/api';

// 全 perf ユーザーが同じパスワードを共有する(seed.sql が同一の BCrypt ハッシュを配る)。
export const PASSWORD: string = __ENV.PERF_PASSWORD || 'PerfTest1234!';

// perf_1..perf_N が存在する前提。VU数がこれを超えた場合は先頭から再利用する。
export const USER_COUNT: number = Number(__ENV.PERF_USER_COUNT || 50);

// コメントが極端に多い投稿のID。seed.sql が3件作り、verify.sql の「上位5件」で確認できる。
// LIMITなし全件取得(CommentMapper.findByPostId)の影響を測るために使う。
export const HOT_POST_IDS: number[] = (__ENV.PERF_HOT_POST_IDS || '11,21,31')
  .split(',')
  .map((s: string) => Number(s.trim()))
  .filter((n: number) => Number.isFinite(n));

// 1ページあたりの取得件数。フロントエンドの実装(usePostsFeed.ts など)が 20 固定なので合わせる。
export const PAGE_LIMIT: number = Number(__ENV.PERF_PAGE_LIMIT || 20);

export const JSON_HEADERS: Record<string, string> = { 'Content-Type': 'application/json' };

// 検索シナリオが使う語。seed.sql が display_name に 'PerfUser N' と一部に ' tester' を入れている。
// 両端ワイルドカードの ILIKE になるため、前方一致では無いことに意味がある。
export const SEARCH_TERMS: string[] = ['tester', 'PerfUser', 'user', 'perf', 'er 1'];

export function pick<T>(arr: T[]): T {
  return arr[Math.floor(Math.random() * arr.length)];
}

// 1イテレーションあたりの待ち時間(秒)。
//
// 既定の1秒は「実際の利用者は取得と同時に次の操作をしない」という想定に基づく。
// ただしこの値のままだと 1VU = 最大1req/s となり、VUを増やしても
// 「アプリの限界」ではなく「VU数」が上限を決めてしまう。
// 飽和点を探すストレステストでは 0 にして、VUあたりのリクエスト密度を上げる。
export const SLEEP_SECONDS: number = Number(__ENV.PERF_SLEEP ?? 1);

/** APIのレスポンスエンベロープ。成功時は data、失敗時は error が入る。 */
export interface ApiEnvelope<T> {
  data?: T;
  error?: { code: string; message: string };
}

/** カーソルページネーションの1ページ分。フロントの CursorPage<T> と同じ形。 */
export interface CursorPage<T> {
  items: T[];
  nextCursor: string | null;
}

/** 投稿一覧・詳細で返る投稿。計測に必要なフィールドのみ宣言する。 */
export interface PostSummary {
  id: number;
  deleted: boolean;
  isMine: boolean;
}
