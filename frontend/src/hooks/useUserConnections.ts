import { useInfiniteQuery } from "@tanstack/react-query";
import { listFollowers, listFollowing } from "../api/users";
import { usersKeys } from "../api/queryKeys";

export type ConnectionTab = "followers" | "following";

export function useUserConnections(userId: number, tab: ConnectionTab) {
  return useInfiniteQuery({
    queryKey: tab === "followers" ? usersKeys.followers(userId) : usersKeys.following(userId),
    queryFn: ({ pageParam }) =>
      (tab === "followers" ? listFollowers : listFollowing)(userId, pageParam, 20),
    initialPageParam: null as string | null,
    getNextPageParam: (lastPage) => lastPage.nextCursor,
  });
}
