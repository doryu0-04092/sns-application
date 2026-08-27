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
// Comment はDOMのグローバル型と名前が衝突するため、必ず明示的にimportすること。
import type { Comment } from "../types/comment";
import type { CursorPage, Post } from "../types/post";
import type { Profile, UserSummary } from "../types/user";
import { comment, infinite, page, post } from "../test/fixtures";

/**
 * 楽観的更新のテスト。
 *
 * これらの関数は `"pages" in data` のような**構造の推測**でキャッシュの形状を判別しているため、
 * レスポンスの型が少し変わるだけで「例外は出ないが何も更新されない」という壊れ方をする。
 * infinite query形式・単発CursorPage形式・単体オブジェクト形式のすべてを明示的に固定する。
 *
 * ダミーデータのファクトリは src/test/fixtures.ts に集約している(コンポーネントテストと共用)。
 */

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

/**
 * ロールバックのテスト。
 *
 * flip系関数は書き換える前のキャッシュを控え、それを書き戻す関数を返す。
 * 楽観的更新では、失敗したときにこれが呼ばれて画面が元へ戻る。
 * 戻し漏れがあると、サーバーには反映されていないのに画面上は成功したまま残る。
 *
 * 差分を逆向きに当て直すのではなく控えた値を書き戻す方式なので、
 * likeCount や followerCount のような ±1 の更新でも値がずれない。
 */
describe("ロールバック", () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    queryClient = new QueryClient();
  });

  /** flipFollowInCaches は投稿・コメント・ユーザーの3系統に触る。3つともまとめて戻ること。 */
  it("flipFollowInCaches は触った3系統すべてを元に戻す", () => {
    queryClient.setQueryData(postsKeys.list("all"), infinite([page([post({ id: 1, authorId: 10 })])]));
    queryClient.setQueryData(commentsKeys.list(1), [comment({ id: 5, authorId: 10 })]);
    const profile: Profile = {
      id: 10,
      displayName: "u10",
      bio: null,
      avatarUrl: null,
      followerCount: 3,
      followingCount: 0,
      isFollowing: false,
      isMine: false,
    };
    queryClient.setQueryData(usersKeys.detail(10), profile);

    const rollback = flipFollowInCaches(queryClient, 10, true);

    // 先に反映を確かめる。戻す前と後が同じ値だと、テストが何も検証していないことになる。
    expect(
      queryClient.getQueryData<{ pages: CursorPage<Post>[] }>(postsKeys.list("all"))!.pages[0].items[0].isFollowing,
    ).toBe(true);
    expect(queryClient.getQueryData<Comment[]>(commentsKeys.list(1))![0].isFollowing).toBe(true);
    expect(queryClient.getQueryData<Profile>(usersKeys.detail(10))!.followerCount).toBe(4);

    rollback();

    expect(
      queryClient.getQueryData<{ pages: CursorPage<Post>[] }>(postsKeys.list("all"))!.pages[0].items[0].isFollowing,
    ).toBe(false);
    expect(queryClient.getQueryData<Comment[]>(commentsKeys.list(1))![0].isFollowing).toBe(false);
    expect(queryClient.getQueryData<Profile>(usersKeys.detail(10))!.isFollowing).toBe(false);
    expect(queryClient.getQueryData<Profile>(usersKeys.detail(10))!.followerCount).toBe(3);
  });

  it("flipLikeInCaches は isLiked と likeCount を元に戻す", () => {
    queryClient.setQueryData(
      postsKeys.list("all"),
      infinite([page([post({ id: 7, isLiked: false, likeCount: 2 })])]),
    );

    const rollback = flipLikeInCaches(queryClient, 7, true);

    const applied = queryClient.getQueryData<{ pages: CursorPage<Post>[] }>(postsKeys.list("all"))!;
    expect(applied.pages[0].items[0].isLiked).toBe(true);
    expect(applied.pages[0].items[0].likeCount).toBe(3);

    rollback();

    const restored = queryClient.getQueryData<{ pages: CursorPage<Post>[] }>(postsKeys.list("all"))!;
    expect(restored.pages[0].items[0].isLiked).toBe(false);
    expect(restored.pages[0].items[0].likeCount).toBe(2);
  });

  it("flipCommentLikeInCaches は isLiked と likeCount を元に戻す", () => {
    queryClient.setQueryData(commentsKeys.list(1), [comment({ id: 5, isLiked: false, likeCount: 4 })]);

    const rollback = flipCommentLikeInCaches(queryClient, 1, 5, true);

    expect(queryClient.getQueryData<Comment[]>(commentsKeys.list(1))![0].likeCount).toBe(5);

    rollback();

    const restored = queryClient.getQueryData<Comment[]>(commentsKeys.list(1))!;
    expect(restored[0].isLiked).toBe(false);
    expect(restored[0].likeCount).toBe(4);
  });

  /** 解除の失敗でも戻ること。増減の向きが逆になる経路を別に固定しておく。 */
  it("いいね解除の失敗でも元の値に戻る", () => {
    queryClient.setQueryData(
      postsKeys.list("all"),
      infinite([page([post({ id: 7, isLiked: true, likeCount: 5 })])]),
    );

    const rollback = flipLikeInCaches(queryClient, 7, false);
    expect(
      queryClient.getQueryData<{ pages: CursorPage<Post>[] }>(postsKeys.list("all"))!.pages[0].items[0].likeCount,
    ).toBe(4);

    rollback();

    const restored = queryClient.getQueryData<{ pages: CursorPage<Post>[] }>(postsKeys.list("all"))!;
    expect(restored.pages[0].items[0].isLiked).toBe(true);
    expect(restored.pages[0].items[0].likeCount).toBe(5);
  });
});
