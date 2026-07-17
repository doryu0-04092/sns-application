import { Link, useParams } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { AppHeader } from "../components/AppHeader";
import { PostCard } from "../components/PostCard";
import { FollowButton } from "../components/FollowButton";
import { useUserPosts } from "../hooks/useUserPosts";
import { useInfiniteScrollSentinel } from "../hooks/useInfiniteScrollSentinel";
import { getProfile } from "../api/users";
import { usersKeys } from "../api/queryKeys";

export function ProfilePage() {
  const { userId } = useParams();
  const id = Number(userId);

  const profileQuery = useQuery({
    queryKey: usersKeys.detail(id),
    queryFn: () => getProfile(id),
  });

  const postsQuery = useUserPosts(id);
  const posts = postsQuery.data?.pages.flatMap((page) => page.items) ?? [];

  const sentinelRef = useInfiniteScrollSentinel(() => {
    if (postsQuery.hasNextPage && !postsQuery.isFetchingNextPage) {
      postsQuery.fetchNextPage();
    }
  }, profileQuery.isSuccess);

  return (
    <div className="mx-auto min-h-screen max-w-xl border-x border-gray-200 bg-white">
      <AppHeader />
      {profileQuery.isLoading && <p className="p-8 text-center text-gray-500">読み込み中...</p>}
      {profileQuery.isError && <p className="p-8 text-center text-gray-500">このユーザーは見つかりませんでした。</p>}
      {profileQuery.data && (
        <>
          <div className="border-b border-gray-200 px-4 py-4">
            <div className="flex items-start justify-between">
              <div className="flex h-16 w-16 items-center justify-center rounded-full bg-gradient-to-br from-purple-400 to-blue-400 text-xl font-bold text-white">
                {profileQuery.data.displayName.charAt(0)}
              </div>
              {profileQuery.data.isMine ? (
                <Link
                  to={`/users/${id}/edit`}
                  className="rounded-full border border-gray-300 px-4 py-1.5 text-sm font-semibold hover:bg-gray-50"
                >
                  プロフィールを編集
                </Link>
              ) : (
                <FollowButton userId={id} isFollowing={profileQuery.data.isFollowing} />
              )}
            </div>
            <div className="mt-3">
              <div className="text-xl font-bold">{profileQuery.data.displayName}</div>
              {profileQuery.data.bio && (
                <p className="mt-1 whitespace-pre-wrap text-sm text-gray-700">{profileQuery.data.bio}</p>
              )}
            </div>
            <div className="mt-3 flex gap-4 text-sm">
              <Link to={`/users/${id}/following`} className="hover:underline">
                <strong className="text-gray-900">{profileQuery.data.followingCount}</strong>{" "}
                <span className="text-gray-500">フォロー中</span>
              </Link>
              <Link to={`/users/${id}/followers`} className="hover:underline">
                <strong className="text-gray-900">{profileQuery.data.followerCount}</strong>{" "}
                <span className="text-gray-500">フォロワー</span>
              </Link>
            </div>
          </div>
          <div className="border-b border-gray-200 px-4 py-2.5 text-sm font-bold text-gray-500">投稿</div>
          {postsQuery.isLoading && <p className="p-8 text-center text-gray-500">読み込み中...</p>}
          {!postsQuery.isLoading && posts.length === 0 && (
            <p className="p-8 text-center text-gray-500">まだ投稿がありません。</p>
          )}
          {posts.map((post) => (
            <PostCard key={post.id} post={post} />
          ))}
          <div ref={sentinelRef} />
          {postsQuery.isFetchingNextPage && <p className="p-4 text-center text-sm text-gray-500">読み込み中...</p>}
        </>
      )}
    </div>
  );
}
