import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { LikeButton } from "./LikeButton";
import { FollowButton } from "./FollowButton";
import { renderWithProviders, createTestQueryClient } from "../test/renderWithProviders";
import { infinite, page, post } from "../test/fixtures";
import { postsKeys } from "../api/queryKeys";
import { ApiError } from "../api/client";
import * as likesApi from "../api/likes";
import * as followsApi from "../api/follows";

/**
 * いいね・フォローのボタンのテスト。
 *
 * これらは成功時にキャッシュを直接書き換えて画面へ反映する(再取得しない)。
 * 書き換えが漏れると「押しても見た目が変わらない」、
 * 失敗時に書き換えてしまうと「押せたように見えるがサーバーには反映されていない」となる。
 * どちらも例外は出ないため、キャッシュの中身まで踏み込んで確認する。
 */
describe("LikeButton", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("未いいねなら白いハートといいね数を表示する", () => {
    renderWithProviders(<LikeButton postId={1} isLiked={false} likeCount={3} />);

    expect(screen.getByRole("button")).toHaveTextContent("🤍");
    expect(screen.getByRole("button")).toHaveTextContent("3");
  });

  it("いいね済みなら赤いハートを表示する", () => {
    renderWithProviders(<LikeButton postId={1} isLiked likeCount={4} />);

    expect(screen.getByRole("button")).toHaveTextContent("❤️");
  });

  /** 自分の投稿ではボタンではなく表示だけになる(自己いいねはサーバーが400で拒否する)。 */
  it("disabledならボタンではなく表示のみになる", () => {
    renderWithProviders(<LikeButton postId={1} isLiked={false} likeCount={2} disabled />);

    expect(screen.queryByRole("button")).not.toBeInTheDocument();
    expect(screen.getByText("2")).toBeInTheDocument();
  });

  it("未いいねの状態で押すといいねAPIを呼ぶ", async () => {
    const likePost = vi.spyOn(likesApi, "likePost").mockResolvedValue(null);

    renderWithProviders(<LikeButton postId={7} isLiked={false} likeCount={0} />);
    await userEvent.click(screen.getByRole("button"));

    await waitFor(() => {
      expect(likePost).toHaveBeenCalledWith(7);
    });
  });

  it("いいね済みの状態で押すと解除APIを呼ぶ", async () => {
    const unlikePost = vi.spyOn(likesApi, "unlikePost").mockResolvedValue(null);

    renderWithProviders(<LikeButton postId={7} isLiked likeCount={1} />);
    await userEvent.click(screen.getByRole("button"));

    await waitFor(() => {
      expect(unlikePost).toHaveBeenCalledWith(7);
    });
  });

  /** 成功したらタイムラインのキャッシュ上でも isLiked と likeCount が動くこと。 */
  it("いいね成功でタイムラインのキャッシュが更新される", async () => {
    vi.spyOn(likesApi, "likePost").mockResolvedValue(null);
    const queryClient = createTestQueryClient();
    queryClient.setQueryData(
      postsKeys.list("all"),
      infinite([page([post({ id: 7, isLiked: false, likeCount: 0 })])]),
    );

    renderWithProviders(<LikeButton postId={7} isLiked={false} likeCount={0} />, { queryClient });
    await userEvent.click(screen.getByRole("button"));

    await waitFor(() => {
      const cached = queryClient.getQueryData(postsKeys.list("all")) as ReturnType<typeof infinite<
        ReturnType<typeof post>
      >>;
      expect(cached.pages[0].items[0].isLiked).toBe(true);
      expect(cached.pages[0].items[0].likeCount).toBe(1);
    });
  });

  /** 失敗したらキャッシュを触らない。触ると実態と表示がずれたまま残る。 */
  it("いいね失敗ではキャッシュを更新しない", async () => {
    vi.spyOn(likesApi, "likePost").mockRejectedValue(new ApiError("POST_NOT_FOUND", "投稿が見つかりません", 404));
    const queryClient = createTestQueryClient();
    queryClient.setQueryData(
      postsKeys.list("all"),
      infinite([page([post({ id: 7, isLiked: false, likeCount: 0 })])]),
    );

    renderWithProviders(<LikeButton postId={7} isLiked={false} likeCount={0} />, { queryClient });
    await userEvent.click(screen.getByRole("button"));

    await screen.findByText("投稿が見つかりません");
    const cached = queryClient.getQueryData(postsKeys.list("all")) as ReturnType<typeof infinite<
      ReturnType<typeof post>
    >>;
    expect(cached.pages[0].items[0].isLiked).toBe(false);
    expect(cached.pages[0].items[0].likeCount).toBe(0);
  });

  it("ApiError以外なら固定の文言を表示する", async () => {
    vi.spyOn(likesApi, "likePost").mockRejectedValue(new Error("network down"));

    renderWithProviders(<LikeButton postId={7} isLiked={false} likeCount={0} />);
    await userEvent.click(screen.getByRole("button"));

    expect(await screen.findByText("処理に失敗しました")).toBeInTheDocument();
  });

  /** 連打で複数回リクエストが飛ばないようにする。 */
  it("送信中はボタンが無効になる", async () => {
    vi.spyOn(likesApi, "likePost").mockReturnValue(new Promise(() => {}));

    renderWithProviders(<LikeButton postId={7} isLiked={false} likeCount={0} />);
    await userEvent.click(screen.getByRole("button"));

    await waitFor(() => {
      expect(screen.getByRole("button")).toBeDisabled();
    });
  });
});

describe("FollowButton", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("未フォローならフォローするを表示する", () => {
    renderWithProviders(<FollowButton userId={10} isFollowing={false} />);

    expect(screen.getByRole("button", { name: "フォローする" })).toBeInTheDocument();
  });

  it("フォロー中ならフォロー中を表示する", () => {
    renderWithProviders(<FollowButton userId={10} isFollowing />);

    expect(screen.getByRole("button", { name: "フォロー中" })).toBeInTheDocument();
  });

  it("未フォローの状態で押すとフォローAPIを呼ぶ", async () => {
    const followUser = vi.spyOn(followsApi, "followUser").mockResolvedValue(null);

    renderWithProviders(<FollowButton userId={10} isFollowing={false} />);
    await userEvent.click(screen.getByRole("button"));

    await waitFor(() => {
      expect(followUser).toHaveBeenCalledWith(10);
    });
  });

  it("フォロー中の状態で押すと解除APIを呼ぶ", async () => {
    const unfollowUser = vi.spyOn(followsApi, "unfollowUser").mockResolvedValue(null);

    renderWithProviders(<FollowButton userId={10} isFollowing />);
    await userEvent.click(screen.getByRole("button"));

    await waitFor(() => {
      expect(unfollowUser).toHaveBeenCalledWith(10);
    });
  });

  /** 同じ投稿者の投稿すべてで isFollowing が揃うこと。1件だけ変わると表示が食い違う。 */
  it("フォロー成功で同じ投稿者の投稿すべてが更新される", async () => {
    vi.spyOn(followsApi, "followUser").mockResolvedValue(null);
    const queryClient = createTestQueryClient();
    queryClient.setQueryData(
      postsKeys.list("all"),
      infinite([
        page([
          post({ id: 1, authorId: 10, isFollowing: false }),
          post({ id: 2, authorId: 10, isFollowing: false }),
          post({ id: 3, authorId: 99, isFollowing: false }),
        ]),
      ]),
    );

    renderWithProviders(<FollowButton userId={10} isFollowing={false} />, { queryClient });
    await userEvent.click(screen.getByRole("button"));

    await waitFor(() => {
      const cached = queryClient.getQueryData(postsKeys.list("all")) as ReturnType<typeof infinite<
        ReturnType<typeof post>
      >>;
      expect(cached.pages[0].items[0].isFollowing).toBe(true);
      expect(cached.pages[0].items[1].isFollowing).toBe(true);
      // 別の投稿者は変わらない
      expect(cached.pages[0].items[2].isFollowing).toBe(false);
    });
  });

  it("失敗するとエラーを表示する", async () => {
    vi.spyOn(followsApi, "followUser").mockRejectedValue(
      new ApiError("SELF_FOLLOW_NOT_ALLOWED", "自分自身はフォローできません", 400),
    );

    renderWithProviders(<FollowButton userId={10} isFollowing={false} />);
    await userEvent.click(screen.getByRole("button"));

    expect(await screen.findByText("自分自身はフォローできません")).toBeInTheDocument();
  });
});
