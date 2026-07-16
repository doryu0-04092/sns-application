import { useInfiniteQuery } from "@tanstack/react-query";
import { listPosts } from "../api/posts";
import { postsKeys } from "../api/queryKeys";
import type { Feed } from "../types/post";

export function usePostsFeed(feed: Feed) {
  return useInfiniteQuery({
    queryKey: postsKeys.list(feed),
    queryFn: ({ pageParam }) => listPosts({ feed, cursor: pageParam, limit: 20 }),
    initialPageParam: null as string | null,
    getNextPageParam: (lastPage) => lastPage.nextCursor,
  });
}
