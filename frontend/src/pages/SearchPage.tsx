import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { AppHeader } from "../components/AppHeader";
import { Avatar } from "../components/Avatar";
import { FollowButton } from "../components/FollowButton";
import { useCurrentUser } from "../hooks/useCurrentUser";
import { useUserSearch } from "../hooks/useUserSearch";
import { useInfiniteScrollSentinel } from "../hooks/useInfiniteScrollSentinel";

export function SearchPage() {
  const [inputValue, setInputValue] = useState("");
  const [debouncedQuery, setDebouncedQuery] = useState("");
  const { data: currentUser } = useCurrentUser();

  useEffect(() => {
    const timer = setTimeout(() => setDebouncedQuery(inputValue.trim()), 300);
    return () => clearTimeout(timer);
  }, [inputValue]);

  const query = useUserSearch(debouncedQuery);
  const users = query.data?.pages.flatMap((page) => page.items) ?? [];

  const sentinelRef = useInfiniteScrollSentinel(() => {
    if (query.hasNextPage && !query.isFetchingNextPage) {
      query.fetchNextPage();
    }
  }, debouncedQuery.length > 0);

  return (
    <div className="mx-auto min-h-screen max-w-xl border-x border-gray-200 bg-white">
      <AppHeader />
      <div className="border-b border-gray-200 px-4 py-3">
        <input
          value={inputValue}
          onChange={(e) => setInputValue(e.target.value)}
          placeholder="表示名で検索"
          className="w-full rounded-full border border-gray-300 px-4 py-2 text-sm outline-none focus:border-blue-500"
        />
      </div>

      {debouncedQuery.length === 0 && (
        <p className="p-8 text-center text-gray-500">表示名でユーザーを検索できます。</p>
      )}
      {debouncedQuery.length > 0 && query.isLoading && (
        <p className="p-8 text-center text-gray-500">読み込み中...</p>
      )}
      {debouncedQuery.length > 0 && query.isError && (
        <p className="p-8 text-center text-red-600">検索に失敗しました</p>
      )}
      {debouncedQuery.length > 0 && !query.isLoading && users.length === 0 && (
        <p className="p-8 text-center text-gray-500">該当するユーザーが見つかりませんでした。</p>
      )}

      {users.map((user) => (
        <div key={user.id} className="flex items-center gap-3 border-b border-gray-100 px-4 py-3">
          <Link to={`/users/${user.userId}`} className="flex-shrink-0">
            <Avatar avatarUrl={user.avatarUrl} displayName={user.displayName} />
          </Link>
          <Link to={`/users/${user.userId}`} className="min-w-0 flex-1 truncate font-bold hover:underline">
            {user.displayName}
          </Link>
          {user.userId !== currentUser?.id && <FollowButton userId={user.userId} isFollowing={user.isFollowing} />}
        </div>
      ))}
      <div ref={sentinelRef} />
      {query.isFetchingNextPage && <p className="p-4 text-center text-sm text-gray-500">読み込み中...</p>}
    </div>
  );
}
