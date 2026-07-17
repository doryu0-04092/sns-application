import type { QueryClient } from "@tanstack/react-query";
import type { CursorPage, Feed, Post } from "../types/post";
import type { Comment } from "../types/comment";
import type { Profile, UserSummary } from "../types/user";

export const postsKeys = {
  all: ["posts"] as const,
  list: (feed: Feed) => ["posts", "list", feed] as const,
  byAuthor: (authorId: number) => ["posts", "list", "author", authorId] as const,
  detail: (postId: number) => ["posts", "detail", postId] as const,
  newCheck: (feed: Feed, sinceId: number | null) => ["posts", "newCheck", feed, sinceId] as const,
};

export const commentsKeys = {
  all: ["comments"] as const,
  list: (postId: number) => ["comments", "list", postId] as const,
};

export const usersKeys = {
  all: ["users"] as const,
  detail: (userId: number) => ["users", "detail", userId] as const,
  followers: (userId: number) => ["users", userId, "followers"] as const,
  following: (userId: number) => ["users", userId, "following"] as const,
  search: (query: string) => ["users", "search", query] as const,
};

/**
 * フォロー/フォロー解除の楽観的反映先はinfinite query(pages形式)・
 * 単発query(CursorPageまたはPost単体)・コメント一覧(フラット配列)と
 * データ形状がバラバラなため、形状ごとに分岐して同じauthorIdを持つ
 * 要素のisFollowingだけを書き換える。
 */
export function flipFollowInCaches(queryClient: QueryClient, userId: number, following: boolean) {
  queryClient.setQueriesData({ queryKey: postsKeys.all }, (data: unknown) => {
    if (!data || typeof data !== "object") return data;

    if ("pages" in data) {
      const infinite = data as { pages: CursorPage<Post>[]; pageParams: unknown[] };
      return {
        ...infinite,
        pages: infinite.pages.map((page) => ({
          ...page,
          items: page.items.map((post) => (post.authorId === userId ? { ...post, isFollowing: following } : post)),
        })),
      };
    }

    if ("items" in data) {
      const page = data as CursorPage<Post>;
      return {
        ...page,
        items: page.items.map((post) => (post.authorId === userId ? { ...post, isFollowing: following } : post)),
      };
    }

    if ("authorId" in data) {
      const post = data as Post;
      return post.authorId === userId ? { ...post, isFollowing: following } : post;
    }

    return data;
  });

  queryClient.setQueriesData({ queryKey: commentsKeys.all }, (data: unknown) => {
    if (!Array.isArray(data)) return data;
    return (data as Comment[]).map((comment) =>
      comment.authorId === userId ? { ...comment, isFollowing: following } : comment,
    );
  });

  queryClient.setQueriesData({ queryKey: usersKeys.all }, (data: unknown) => {
    if (!data || typeof data !== "object") return data;

    if ("pages" in data) {
      const infinite = data as { pages: CursorPage<UserSummary>[]; pageParams: unknown[] };
      return {
        ...infinite,
        pages: infinite.pages.map((page) => ({
          ...page,
          items: page.items.map((u) => (u.userId === userId ? { ...u, isFollowing: following } : u)),
        })),
      };
    }

    if ("followerCount" in data) {
      const profile = data as Profile;
      if (profile.id !== userId) return profile;
      return { ...profile, isFollowing: following, followerCount: profile.followerCount + (following ? 1 : -1) };
    }

    return data;
  });
}

/**
 * いいね/いいね解除の楽観的反映。投稿はinfinite query(pages形式)・
 * 単発query(CursorPageまたはPost単体)のいずれの形状にも対応し、
 * 対象postIdのisLiked反転とlikeCountの±1を同時に行う。
 */
export function flipLikeInCaches(queryClient: QueryClient, postId: number, liked: boolean) {
  const patchPost = (post: Post): Post =>
    post.id === postId ? { ...post, isLiked: liked, likeCount: post.likeCount + (liked ? 1 : -1) } : post;

  queryClient.setQueriesData({ queryKey: postsKeys.all }, (data: unknown) => {
    if (!data || typeof data !== "object") return data;

    if ("pages" in data) {
      const infinite = data as { pages: CursorPage<Post>[]; pageParams: unknown[] };
      return {
        ...infinite,
        pages: infinite.pages.map((page) => ({ ...page, items: page.items.map(patchPost) })),
      };
    }

    if ("items" in data) {
      const page = data as CursorPage<Post>;
      return { ...page, items: page.items.map(patchPost) };
    }

    if ("authorId" in data) {
      return patchPost(data as Post);
    }

    return data;
  });
}

/**
 * コメントへのいいね/いいね解除の楽観的反映。コメント一覧はpostIdごとの
 * フラット配列で保持されているため、対象commentIdのisLiked反転と
 * likeCountの±1のみ行う。
 */
export function flipCommentLikeInCaches(
  queryClient: QueryClient,
  postId: number,
  commentId: number,
  liked: boolean,
) {
  queryClient.setQueriesData({ queryKey: commentsKeys.list(postId) }, (data: unknown) => {
    if (!Array.isArray(data)) return data;
    return (data as Comment[]).map((comment) =>
      comment.id === commentId
        ? { ...comment, isLiked: liked, likeCount: comment.likeCount + (liked ? 1 : -1) }
        : comment,
    );
  });
}
