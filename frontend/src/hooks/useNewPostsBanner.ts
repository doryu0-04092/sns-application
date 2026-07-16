import { useQuery } from "@tanstack/react-query";
import { listPosts } from "../api/posts";
import { postsKeys } from "../api/queryKeys";
import type { Feed } from "../types/post";

// サーバー負荷を抑えるため3分間隔でのみ新着投稿を確認する。
const POLL_INTERVAL_MS = 180000;

export function useNewPostsBanner(feed: Feed, newestLoadedId: number | null) {
  return useQuery({
    queryKey: postsKeys.newCheck(feed, newestLoadedId),
    queryFn: () => listPosts({ feed, sinceId: newestLoadedId, limit: 20 }),
    enabled: newestLoadedId != null,
    refetchInterval: POLL_INTERVAL_MS,
    refetchIntervalInBackground: false,
    staleTime: 0,
  });
}
