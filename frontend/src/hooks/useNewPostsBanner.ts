import { useQuery } from "@tanstack/react-query";
import { listPosts } from "../api/posts";
import { postsKeys } from "../api/queryKeys";
import type { CursorPage, Feed, Post } from "../types/post";

// サーバー負荷を抑えるため3分間隔でのみ新着投稿を確認する。
const POLL_INTERVAL_MS = 180000;

/**
 * マウント直後の1回分を省くための初期値。
 *
 * このクエリが聞いているのは「いま読み込んだ最新の投稿(newestLoadedId)より
 * 新しい投稿はあるか」である。読み込んだ直後に同じことを聞けば、
 * 答えは定義上「無い」になる。つまりマウント時の取得は確実に空を返す。
 */
const EMPTY_PAGE: CursorPage<Post> = { items: [], nextCursor: null };

/**
 * 新着投稿バナー用の定期確認。
 *
 * 実測(docs/perf-test-report.md 7-3):
 *   /home の表示1回で sinceId 付きのリクエストが2本(タブ2つ分)発火しており、
 *   いずれも画面表示直後だった。上記の通り確実に空が返るため、これは純粋な無駄だった。
 *
 * initialData でクエリを「データを持っている」状態から始め、
 * staleTime を巡回間隔と同じにすることで、その初期値を「たった今取得したもの」
 * として扱わせる。これで最初の1本が消え、以降は refetchInterval(3分)だけが動く。
 * refetchInterval はタイマーで発火するため staleTime の影響を受けない。
 *
 * staleTime を 0 にしてはいけない。
 *   queryKey に newestLoadedId が入っているため、フィードを読み込んで
 *   基準IDが null から実際の値に変わった瞬間に「別のクエリ」になる。
 *   これはマウントではないので refetchOnMount: false では止まらず、
 *   staleTime が 0 だと initialData が即座に stale と判定されて取得が走る。
 *   実際、staleTime: 0 + refetchOnMount: false の組み合わせでは
 *   sinceId 付きのリクエストが消えないことを計測で確認している。
 */
export function useNewPostsBanner(feed: Feed, newestLoadedId: number | null) {
  return useQuery({
    queryKey: postsKeys.newCheck(feed, newestLoadedId),
    queryFn: () => listPosts({ feed, sinceId: newestLoadedId, limit: 20 }),
    enabled: newestLoadedId != null,
    refetchInterval: POLL_INTERVAL_MS,
    refetchIntervalInBackground: false,
    initialData: EMPTY_PAGE,
    initialDataUpdatedAt: () => Date.now(),
    staleTime: POLL_INTERVAL_MS,
  });
}
