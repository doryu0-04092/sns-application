import { QueryClient } from "@tanstack/react-query";
import { beforeEach, describe, expect, it } from "vitest";
import {
  commentsKeys,
  flipCommentLikeInCaches,
  flipFollowInCaches,
  flipLikeInCaches,
  postsKeys,
  usersKeys,
} from "./queryKeys";
import type { Comment } from "../types/comment";
import type { CursorPage, Post } from "../types/post";
import type { Profile, UserSummary } from "../types/user";

/**
 * 楽観的更新のテスト。
 *
 * これらの関数は `"pages" in data` のような**構造の推測**でキャッシュの形状を判別しているため、
 * レスポンスの型が少し変わるだけで「例外は出ないが何も更新されない」という壊れ方をする。
 * infinite query形式・単発CursorPage形式・単体オブジェクト形式のすべてを明示的に固定する。
 */

function post(overrides: Partial<Post> = {}): Post {
  return {
    id: 1,
    body: "本文",
    authorId: 10,
    authorDisplayName: "投稿者",
    authorAvatarUrl: null,
    createdAt: "2026-01-01T00:00:00",
    updatedAt: "2026-01-01T00:00:00",
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

function comment(overrides: Partial<Comment> = {}): Comment {
  return {
    id: 1,
    postId: 1,
    parentCommentId: null,
    body: "コメント",
    authorId: 10,
    authorDisplayName: "投稿者",
    authorAvatarUrl: null,
    createdAt: "2026-01-01T00:00:00",
    updatedAt: "2026-01-01T00:00:00",
    likeCount: 0,
    isMine: false,
    isFollowing: false,
    isLiked: false,
    deleted: false,
    ...overrides,
  };
}

function page<T>(items: T[]): CursorPage<T> {
  return { items, nextCursor: null };
}

function infinite<T>(pages: CursorPage<T>[]) {
  return { pages, pageParams: [null] };
}

describe("flipFollowInCaches", () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    queryClient = new QueryClient();
  });

  it("infinite query形式のタイムラインで対象ユーザーの投稿だけisFollowingを反転する", () => {
    queryClient.setQueryData(
      postsKeys.list("all"),
      infinite([page([post({ id: 1, authorId: 10 }), post({ id: 2, authorId: 99 })])]),
    );

    flipFollowInCaches(queryClient, 10, true);

    const data = queryClient.getQueryData<{ pages: CursorPage<Post>[] }>(postsKeys.list("all"))!;
    expect(data.pages[0].items[0].isFollowing).toBe(true);
    expect(data.pages[0].items[1].isFollowing).toBe(false);
  });

  it("複数ページにまたがっても全ページを更新する", () => {
    queryClient.setQueryData(
      postsKeys.list("all"),
      infinite([page([post({ id: 1, authorId: 10 })]), page([post({ id: 2, authorId: 10 })])]),
    );

    flipFollowInCaches(queryClient, 10, true);

    const data = queryClient.getQueryData<{ pages: CursorPage<Post>[] }>(postsKeys.list("all"))!;
    expect(data.pages[0].items[0].isFollowing).toBe(true);
    expect(data.pages[1].items[0].isFollowing).toBe(true);
  });

  it("単発CursorPage形式でも反転する", () => {
    queryClient.setQueryData(postsKeys.byAuthor(10), page([post({ authorId: 10 })]));

    flipFollowInCaches(queryClient, 10, true);

    expect(queryClient.getQueryData<CursorPage<Post>>(postsKeys.byAuthor(10))!.items[0].isFollowing).toBe(true);
  });

  it("投稿詳細(単体オブジェクト)でも反転する", () => {
    queryClient.setQueryData(postsKeys.detail(1), post({ authorId: 10 }));

    flipFollowInCaches(queryClient, 10, true);

    expect(queryClient.getQueryData<Post>(postsKeys.detail(1))!.isFollowing).toBe(true);
  });

  it("コメント一覧(フラット配列)でも反転する", () => {
    queryClient.setQueryData(commentsKeys.list(1), [
      comment({ id: 1, authorId: 10 }),
      comment({ id: 2, authorId: 99 }),
    ]);

    flipFollowInCaches(queryClient, 10, true);

    const comments = queryClient.getQueryData<Comment[]>(commentsKeys.list(1))!;
    expect(comments[0].isFollowing).toBe(true);
    expect(comments[1].isFollowing).toBe(false);
  });

  it("ユーザー検索結果でも反転する", () => {
    const summary: UserSummary = { id: 10, userId: 10, displayName: "ユーザー", avatarUrl: null, isFollowing: false };
    queryClient.setQueryData(usersKeys.search(""), infinite([page([summary])]));

    flipFollowInCaches(queryClient, 10, true);

    const data = queryClient.getQueryData<{ pages: CursorPage<UserSummary>[] }>(usersKeys.search(""))!;
    expect(data.pages[0].items[0].isFollowing).toBe(true);
  });

  it("プロフィールではisFollowingとfollowerCountを同時に更新する", () => {
    const profile: Profile = {
      id: 10,
      displayName: "ユーザー",
      bio: null,
      avatarUrl: null,
      followerCount: 5,
      followingCount: 0,
      isMine: false,
      isFollowing: false,
    };
    queryClient.setQueryData(usersKeys.detail(10), profile);

    flipFollowInCaches(queryClient, 10, true);

    const updated = queryClient.getQueryData<Profile>(usersKeys.detail(10))!;
    expect(updated.isFollowing).toBe(true);
    expect(updated.followerCount).toBe(6);
  });

  it("フォロー解除ではfollowerCountが減る", () => {
    const profile: Profile = {
      id: 10,
      displayName: "ユーザー",
      bio: null,
      avatarUrl: null,
      followerCount: 5,
      followingCount: 0,
      isMine: false,
      isFollowing: true,
    };
    queryClient.setQueryData(usersKeys.detail(10), profile);

    flipFollowInCaches(queryClient, 10, false);

    const updated = queryClient.getQueryData<Profile>(usersKeys.detail(10))!;
    expect(updated.isFollowing).toBe(false);
    expect(updated.followerCount).toBe(4);
  });

  it("別ユーザーのプロフィールは変更しない", () => {
    const profile: Profile = {
      id: 99,
      displayName: "別人",
      bio: null,
      avatarUrl: null,
      followerCount: 5,
      followingCount: 0,
      isMine: false,
      isFollowing: false,
    };
    queryClient.setQueryData(usersKeys.detail(99), profile);

    flipFollowInCaches(queryClient, 10, true);

    expect(queryClient.getQueryData<Profile>(usersKeys.detail(99))).toEqual(profile);
  });
});

describe("flipLikeInCaches", () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    queryClient = new QueryClient();
  });

  it("infinite query形式でisLikedとlikeCountを同時に更新する", () => {
    queryClient.setQueryData(postsKeys.list("all"), infinite([page([post({ id: 1, likeCount: 3 })])]));

    flipLikeInCaches(queryClient, 1, true);

    const data = queryClient.getQueryData<{ pages: CursorPage<Post>[] }>(postsKeys.list("all"))!;
    expect(data.pages[0].items[0].isLiked).toBe(true);
    expect(data.pages[0].items[0].likeCount).toBe(4);
  });

  it("いいね解除ではlikeCountが減る", () => {
    queryClient.setQueryData(postsKeys.detail(1), post({ id: 1, likeCount: 3, isLiked: true }));

    flipLikeInCaches(queryClient, 1, false);

    const updated = queryClient.getQueryData<Post>(postsKeys.detail(1))!;
    expect(updated.isLiked).toBe(false);
    expect(updated.likeCount).toBe(2);
  });

  it("対象外の投稿のlikeCountは変えない", () => {
    queryClient.setQueryData(
      postsKeys.list("all"),
      infinite([page([post({ id: 1, likeCount: 3 }), post({ id: 2, likeCount: 7 })])]),
    );

    flipLikeInCaches(queryClient, 1, true);

    const data = queryClient.getQueryData<{ pages: CursorPage<Post>[] }>(postsKeys.list("all"))!;
    expect(data.pages[0].items[1].likeCount).toBe(7);
    expect(data.pages[0].items[1].isLiked).toBe(false);
  });

  it("単発CursorPage形式でも更新する", () => {
    queryClient.setQueryData(postsKeys.byAuthor(10), page([post({ id: 1, likeCount: 1 })]));

    flipLikeInCaches(queryClient, 1, true);

    expect(queryClient.getQueryData<CursorPage<Post>>(postsKeys.byAuthor(10))!.items[0].likeCount).toBe(2);
  });
});

describe("flipCommentLikeInCaches", () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    queryClient = new QueryClient();
  });

  it("対象コメントだけisLikedとlikeCountを更新する", () => {
    queryClient.setQueryData(commentsKeys.list(1), [
      comment({ id: 1, likeCount: 2 }),
      comment({ id: 2, likeCount: 5 }),
    ]);

    flipCommentLikeInCaches(queryClient, 1, 1, true);

    const comments = queryClient.getQueryData<Comment[]>(commentsKeys.list(1))!;
    expect(comments[0]).toMatchObject({ isLiked: true, likeCount: 3 });
    expect(comments[1]).toMatchObject({ isLiked: false, likeCount: 5 });
  });

  it("いいね解除ではlikeCountが減る", () => {
    queryClient.setQueryData(commentsKeys.list(1), [comment({ id: 1, likeCount: 2, isLiked: true })]);

    flipCommentLikeInCaches(queryClient, 1, 1, false);

    expect(queryClient.getQueryData<Comment[]>(commentsKeys.list(1))![0]).toMatchObject({
      isLiked: false,
      likeCount: 1,
    });
  });

  it("別の投稿のコメント一覧は変更しない", () => {
    const other = [comment({ id: 1, likeCount: 2 })];
    queryClient.setQueryData(commentsKeys.list(2), other);

    flipCommentLikeInCaches(queryClient, 1, 1, true);

    expect(queryClient.getQueryData<Comment[]>(commentsKeys.list(2))).toEqual(other);
  });
});
