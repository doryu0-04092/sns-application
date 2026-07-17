import { useState } from "react";
import { Link, useLocation, useNavigate, useParams } from "react-router-dom";
import { AppHeader } from "../components/AppHeader";
import { Avatar } from "../components/Avatar";
import { FollowButton } from "../components/FollowButton";
import { useCurrentUser } from "../hooks/useCurrentUser";
import { useUserConnections, type ConnectionTab } from "../hooks/useUserConnections";
import { useInfiniteScrollSentinel } from "../hooks/useInfiniteScrollSentinel";

function ConnectionsPanel({
  userId,
  tab,
  active,
  currentUserId,
}: {
  userId: number;
  tab: ConnectionTab;
  active: boolean;
  currentUserId?: number;
}) {
  const query = useUserConnections(userId, tab);
  const users = query.data?.pages.flatMap((page) => page.items) ?? [];

  const sentinelRef = useInfiniteScrollSentinel(() => {
    if (query.hasNextPage && !query.isFetchingNextPage) {
      query.fetchNextPage();
    }
  }, active);

  return (
    <div className={active ? "" : "hidden"}>
      {query.isLoading && <p className="p-8 text-center text-gray-500">読み込み中...</p>}
      {query.isError && <p className="p-8 text-center text-red-600">読み込みに失敗しました</p>}
      {!query.isLoading && users.length === 0 && (
        <p className="p-8 text-center text-gray-500">
          {tab === "followers" ? "フォロワーはまだいません。" : "フォロー中のユーザーはまだいません。"}
        </p>
      )}
      {users.map((user) => (
        <div key={user.id} className="flex items-center gap-3 border-b border-gray-100 px-4 py-3">
          <Link to={`/users/${user.userId}`} className="flex-shrink-0">
            <Avatar avatarUrl={user.avatarUrl} displayName={user.displayName} />
          </Link>
          <Link to={`/users/${user.userId}`} className="min-w-0 flex-1 truncate font-bold hover:underline">
            {user.displayName}
          </Link>
          {user.userId !== currentUserId && <FollowButton userId={user.userId} isFollowing={user.isFollowing} />}
        </div>
      ))}
      <div ref={sentinelRef} />
      {query.isFetchingNextPage && <p className="p-4 text-center text-sm text-gray-500">読み込み中...</p>}
    </div>
  );
}

export function FollowListPage() {
  const { userId } = useParams();
  const id = Number(userId);
  const location = useLocation();
  const navigate = useNavigate();
  const { data: currentUser } = useCurrentUser();

  const [tab, setTab] = useState<ConnectionTab>(location.pathname.endsWith("/followers") ? "followers" : "following");

  const switchTab = (next: ConnectionTab) => {
    setTab(next);
    navigate(`/users/${id}/${next}`, { replace: true });
  };

  return (
    <div className="mx-auto min-h-screen max-w-xl border-x border-gray-200 bg-white">
      <AppHeader />
      <div className="flex border-b border-gray-200">
        <button
          type="button"
          onClick={() => switchTab("following")}
          className={`flex-1 py-3.5 text-sm font-semibold ${
            tab === "following" ? "border-b-[3px] border-blue-500 text-gray-900" : "text-gray-500"
          }`}
        >
          フォロー中
        </button>
        <button
          type="button"
          onClick={() => switchTab("followers")}
          className={`flex-1 py-3.5 text-sm font-semibold ${
            tab === "followers" ? "border-b-[3px] border-blue-500 text-gray-900" : "text-gray-500"
          }`}
        >
          フォロワー
        </button>
      </div>
      <ConnectionsPanel userId={id} tab="following" active={tab === "following"} currentUserId={currentUser?.id} />
      <ConnectionsPanel userId={id} tab="followers" active={tab === "followers"} currentUserId={currentUser?.id} />
    </div>
  );
}
