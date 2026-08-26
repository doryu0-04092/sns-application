import { useInfiniteQuery } from "@tanstack/react-query";
import { listPosts } from "../api/posts";
import { postsKeys } from "../api/queryKeys";
import type { Feed } from "../types/post";

/**
 * タイムラインの投稿一覧。
 *
 * enabled を受け取るのは、TimelinePage が「全体」「フォロー中」の2つのパネルを
 * 同時にマウントし、非表示側をCSSで隠す作りになっているためである。
 * タブを切り替えてもスクロール位置と取得済みページが保たれる利点があるが、
 * そのままだと表示していない側のフィードまで毎回取得してしまう。
 *
 * 実測(docs/perf-test-report.md 7-3):
 *   /home の表示1回で feed=all と feed=following の両方が呼ばれていた。
 *   feed=following は follows との JOIN が増える分だけDB側のコストも高い。
 *
 * enabled=false の間はリクエストを出さず、タブが選択された時点で初めて取得する。
 * 一度取得した内容はキャッシュに残るため、タブを往復しても再取得は発生しない。
 */
export function usePostsFeed(feed: Feed, enabled = true) {
  return useInfiniteQuery({
    queryKey: postsKeys.list(feed),
    queryFn: ({ pageParam }) => listPosts({ feed, cursor: pageParam, limit: 20 }),
    initialPageParam: null as string | null,
    getNextPageParam: (lastPage) => lastPage.nextCursor,
    enabled,
  });
}
