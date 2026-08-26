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
 * refetchOnMount: false でマウント時の再取得を止める。
 * これで初回の1本が消え、以降は refetchInterval(3分)だけが動く。
 *
 * staleTime を 0 のままにしているのは、間隔が来たとき・
 * ウィンドウにフォーカスが戻ったときには必ず取りに行かせたいためである
 * (新着確認が目的なので、キャッシュを返されては意味が無い)。
 * これは QueryClient の既定 30 秒を、この用途に限って打ち消している。
 *
 * queryKey に newestLoadedId が入っているため、投稿やバナー押下で
 * 基準が変わると別のクエリになり、そこでも同じ理由で初回取得は起きない。
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
    refetchOnMount: false,
    staleTime: 0,
  });
}
