import { useInfiniteQuery } from "@tanstack/react-query";
import { searchUsers } from "../api/users";
import { usersKeys } from "../api/queryKeys";

export function useUserSearch(query: string) {
  return useInfiniteQuery({
    queryKey: usersKeys.search(query),
    queryFn: ({ pageParam }) => searchUsers(query, pageParam, 20),
    initialPageParam: null as string | null,
    getNextPageParam: (lastPage) => lastPage.nextCursor,
    enabled: query.trim().length > 0,
  });
}
