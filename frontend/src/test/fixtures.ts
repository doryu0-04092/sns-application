import type { User } from "../types/auth";
import type { Comment } from "../types/comment";
import type { CursorPage, Post } from "../types/post";
import type { Profile, UserSummary } from "../types/user";

/**
 * テスト用のダミーデータ。
 *
 * すべて上書き可能なファクトリにしてあるのは、テストごとに「何を変えたのか」だけを
 * 書けるようにするため(全フィールドを毎回並べると、そのテストの意図が埋もれる)。
 *
 * 元は queryKeys.test.ts 内に閉じていたが、コンポーネントテストからも使うため切り出した。
 */

export function post(overrides: Partial<Post> = {}): Post {
  return {
    id: 1,
    body: "本文",
    authorId: 10,
    authorDisplayName: "投稿者",
    authorAvatarUrl: null,
    createdAt: "2026-01-01T00:00:00.000000Z",
    updatedAt: "2026-01-01T00:00:00.000000Z",
    commentCount: 0,
    likeCount: 0,
    isMine: false,
    isFollowing: false,
    isLiked: false,
    deleted: false,
    imageUrls: [],
    ...overrides,
  };
}

export function comment(overrides: Partial<Comment> = {}): Comment {
  return {
    id: 1,
    postId: 1,
    parentCommentId: null,
    body: "コメント",
    authorId: 10,
    authorDisplayName: "投稿者",
    authorAvatarUrl: null,
    createdAt: "2026-01-01T00:00:00.000000Z",
    updatedAt: "2026-01-01T00:00:00.000000Z",
    likeCount: 0,
    isMine: false,
    isFollowing: false,
    isLiked: false,
    deleted: false,
    ...overrides,
  };
}

export function user(overrides: Partial<User> = {}): User {
  return {
    id: 10,
    email: "user@example.com",
    displayName: "自分",
    bio: null,
    avatarUrl: null,
    ...overrides,
  };
}

export function userSummary(overrides: Partial<UserSummary> = {}): UserSummary {
  return {
    id: 58,
    userId: 10,
    displayName: "ユーザー",
    avatarUrl: null,
    isFollowing: false,
    ...overrides,
  };
}

export function profile(overrides: Partial<Profile> = {}): Profile {
  return {
    id: 10,
    displayName: "ユーザー",
    bio: null,
    avatarUrl: null,
    followerCount: 0,
    followingCount: 0,
    isMine: false,
    isFollowing: false,
    ...overrides,
  };
}

export function page<T>(items: T[], nextCursor: string | null = null): CursorPage<T> {
  return { items, nextCursor };
}

export function infinite<T>(pages: CursorPage<T>[]) {
  return { pages, pageParams: [null] };
}
