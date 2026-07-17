import { useInfiniteQuery } from "@tanstack/react-query";
import { listPosts } from "../api/posts";
import { postsKeys } from "../api/queryKeys";

export function useUserPosts(authorId: number) {
  return useInfiniteQuery({
    queryKey: postsKeys.byAuthor(authorId),
    queryFn: ({ pageParam }) => listPosts({ feed: "all", authorId, cursor: pageParam, limit: 20 }),
    initialPageParam: null as string | null,
    getNextPageParam: (lastPage) => lastPage.nextCursor,
  });
}
