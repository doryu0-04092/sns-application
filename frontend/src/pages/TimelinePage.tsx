import { useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { AppHeader } from "../components/AppHeader";
import { PostComposer } from "../components/PostComposer";
import { PostCard } from "../components/PostCard";
import { NewPostsBanner } from "../components/NewPostsBanner";
import { usePostsFeed } from "../hooks/usePostsFeed";
import { useNewPostsBanner } from "../hooks/useNewPostsBanner";
import { useInfiniteScrollSentinel } from "../hooks/useInfiniteScrollSentinel";
import { postsKeys } from "../api/queryKeys";
import type { Feed } from "../types/post";

function FeedPanel({ feed, active }: { feed: Feed; active: boolean }) {
  const query = usePostsFeed(feed);
  const queryClient = useQueryClient();
  const posts = query.data?.pages.flatMap((page) => page.items) ?? [];
  const newestLoadedId = query.data?.pages[0]?.items[0]?.id ?? null;
  const banner = useNewPostsBanner(feed, newestLoadedId);

  const sentinelRef = useInfiniteScrollSentinel(() => {
    if (query.hasNextPage && !query.isFetchingNextPage) {
      query.fetchNextPage();
    }
  }, active);

  const handleBannerClick = () => {
    queryClient.resetQueries({ queryKey: postsKeys.list(feed), exact: true });
    window.scrollTo({ top: 0, behavior: "smooth" });
  };

  const newCount = banner.data?.items.length ?? 0;
  const hasMoreNew = banner.data?.nextCursor != null;

  return (
    <div className={active ? "" : "hidden"}>
      {newCount > 0 && <NewPostsBanner count={newCount} hasMore={hasMoreNew} onClick={handleBannerClick} />}
      {query.isLoading && <p className="p-8 text-center text-gray-500">読み込み中...</p>}
      {query.isError && <p className="p-8 text-center text-red-600">読み込みに失敗しました</p>}
      {!query.isLoading && posts.length === 0 && (
        <p className="p-8 text-center text-gray-500">
          {feed === "following" ? "フォロー中のユーザーの投稿はまだありません。" : "まだ投稿がありません。"}
        </p>
      )}
      {posts.map((post) => (
        <PostCard key={post.id} post={post} />
      ))}
      <div ref={sentinelRef} />
      {query.isFetchingNextPage && <p className="p-4 text-center text-sm text-gray-500">読み込み中...</p>}
    </div>
  );
}

export function TimelinePage() {
  const [feed, setFeed] = useState<Feed>("all");

  return (
    <div className="mx-auto min-h-screen max-w-xl border-x border-gray-200 bg-white">
      <AppHeader />
      <div className="flex border-b border-gray-200">
        <button
          type="button"
          onClick={() => setFeed("all")}
          className={`flex-1 py-3.5 text-sm font-semibold ${
            feed === "all" ? "border-b-[3px] border-blue-500 text-gray-900" : "text-gray-500"
          }`}
        >
          全体
        </button>
        <button
          type="button"
          onClick={() => setFeed("following")}
          className={`flex-1 py-3.5 text-sm font-semibold ${
            feed === "following" ? "border-b-[3px] border-blue-500 text-gray-900" : "text-gray-500"
          }`}
        >
          フォロー中
        </button>
      </div>
      <PostComposer />
      <FeedPanel feed="all" active={feed === "all"} />
      <FeedPanel feed="following" active={feed === "following"} />
    </div>
  );
}
