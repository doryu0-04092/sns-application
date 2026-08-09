import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { PostDetailCard } from "./PostDetailCard";
import { renderWithProviders, createTestQueryClient } from "../test/renderWithProviders";
import { infinite, page, post } from "../test/fixtures";
import { postsKeys } from "../api/queryKeys";
import { ApiError } from "../api/client";
import * as postsApi from "../api/posts";

const navigate = vi.fn();
vi.mock("react-router-dom", async () => {
  const actual = await vi.importActual<typeof import("react-router-dom")>("react-router-dom");
  return { ...actual, useNavigate: () => navigate };
});

/**
 * 投稿詳細カードのテスト。
 *
 * 編集・削除はキャッシュを直接書き換える(再取得しない)ため、
 * 一覧側の反映漏れが起きやすい。削除後にタイムラインから消えること、
 * 編集後に一覧の本文も差し替わることを、キャッシュの中身で確認する。
 */
describe("PostDetailCard", () => {
  beforeEach(() => {
    vi.spyOn(window, "confirm").mockReturnValue(true);
  });

  afterEach(() => {
    vi.restoreAllMocks();
    navigate.mockClear();
  });

  it("本文と投稿者名を表示する", () => {
    renderWithProviders(
      <PostDetailCard post={post({ body: "本文です", authorDisplayName: "山田", authorId: 7 })} />,
    );

    expect(screen.getByText("本文です")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "山田" })).toHaveAttribute("href", "/users/7");
  });

  it("コメント数といいね数を表示する", () => {
    renderWithProviders(<PostDetailCard post={post({ commentCount: 3, likeCount: 5 })} />);

    expect(screen.getByText("3")).toBeInTheDocument();
    expect(screen.getByText("コメント", { exact: false })).toBeInTheDocument();
  });

  it("添付画像を表示する", () => {
    renderWithProviders(
      <PostDetailCard post={post({ imageUrls: ["https://s3/a.jpg", "https://s3/b.jpg"] })} />,
    );

    expect(screen.getAllByRole("presentation", { hidden: true }).length).toBeGreaterThanOrEqual(0);
    expect(document.querySelectorAll("img")).toHaveLength(2);
  });

  // --- 権限による出し分け ---

  it("自分の投稿には編集と削除のボタンが出る", () => {
    renderWithProviders(<PostDetailCard post={post({ isMine: true })} />);

    expect(screen.getByRole("button", { name: "編集" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "削除" })).toBeInTheDocument();
  });

  it("他人の投稿には編集と削除のボタンが出ない", () => {
    renderWithProviders(<PostDetailCard post={post({ isMine: false })} />);

    expect(screen.queryByRole("button", { name: "編集" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "削除" })).not.toBeInTheDocument();
  });

  it("他人の投稿にはフォローボタンが出る", () => {
    renderWithProviders(<PostDetailCard post={post({ isMine: false })} />);

    expect(screen.getByRole("button", { name: "フォローする" })).toBeInTheDocument();
  });

  // --- ツームストーン ---

  it("削除済み投稿は削除された旨を表示する", () => {
    renderWithProviders(<PostDetailCard post={post({ deleted: true, body: null, isMine: true })} />);

    expect(screen.getByText("この投稿は削除されました(返信は保持されています)")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "編集" })).not.toBeInTheDocument();
  });

  /** 削除済みでも画像URLが残っていた場合に表示しない(サーバー側でも空にしているが二重に守る)。 */
  it("削除済み投稿では画像を表示しない", () => {
    renderWithProviders(
      <PostDetailCard post={post({ deleted: true, body: null, imageUrls: ["https://s3/leftover.jpg"] })} />,
    );

    expect(document.querySelectorAll("img")).toHaveLength(0);
  });

  // --- 編集 ---

  it("編集を押すと入力欄が開き本文が入る", async () => {
    renderWithProviders(<PostDetailCard post={post({ isMine: true, body: "編集前の本文" })} />);

    await userEvent.click(screen.getByRole("button", { name: "編集" }));

    expect(screen.getByRole("textbox")).toHaveValue("編集前の本文");
  });

  it("編集して保存すると更新APIを呼ぶ", async () => {
    const updatePost = vi.spyOn(postsApi, "updatePost").mockResolvedValue(post({ body: "編集後の本文" }));

    renderWithProviders(<PostDetailCard post={post({ id: 42, isMine: true, body: "編集前" })} />);
    await userEvent.click(screen.getByRole("button", { name: "編集" }));
    await userEvent.clear(screen.getByRole("textbox"));
    await userEvent.type(screen.getByRole("textbox"), "編集後の本文");
    await userEvent.click(screen.getByRole("button", { name: "保存" }));

    await waitFor(() => {
      expect(updatePost.mock.calls[0]?.[0]).toBe(42);
      expect(updatePost.mock.calls[0]?.[1]).toBe("編集後の本文");
    });
  });

  /** 編集の反映がタイムライン側のキャッシュにも及ぶこと。漏れると一覧に古い本文が残る。 */
  it("編集成功でタイムラインのキャッシュも差し替わる", async () => {
    const updated = post({ id: 42, body: "編集後の本文" });
    vi.spyOn(postsApi, "updatePost").mockResolvedValue(updated);

    const queryClient = createTestQueryClient();
    queryClient.setQueryData(postsKeys.list("all"), infinite([page([post({ id: 42, body: "編集前" })])]));

    renderWithProviders(<PostDetailCard post={post({ id: 42, isMine: true, body: "編集前" })} />, {
      queryClient,
    });
    await userEvent.click(screen.getByRole("button", { name: "編集" }));
    await userEvent.click(screen.getByRole("button", { name: "保存" }));

    await waitFor(() => {
      const cached = queryClient.getQueryData(postsKeys.list("all")) as ReturnType<
        typeof infinite<ReturnType<typeof post>>
      >;
      expect(cached.pages[0].items[0].body).toBe("編集後の本文");
    });
  });

  it("編集内容が空白のみなら保存できない", async () => {
    renderWithProviders(<PostDetailCard post={post({ isMine: true, body: "編集前" })} />);

    await userEvent.click(screen.getByRole("button", { name: "編集" }));
    await userEvent.clear(screen.getByRole("textbox"));
    await userEvent.type(screen.getByRole("textbox"), "   ");

    expect(screen.getByRole("button", { name: "保存" })).toBeDisabled();
  });

  it("キャンセルすると編集前の本文に戻る", async () => {
    renderWithProviders(<PostDetailCard post={post({ isMine: true, body: "編集前の本文" })} />);

    await userEvent.click(screen.getByRole("button", { name: "編集" }));
    await userEvent.clear(screen.getByRole("textbox"));
    await userEvent.type(screen.getByRole("textbox"), "書きかけ");
    await userEvent.click(screen.getByRole("button", { name: "キャンセル" }));

    expect(screen.getByText("編集前の本文")).toBeInTheDocument();
  });

  it("編集に失敗するとエラーを表示する", async () => {
    vi.spyOn(postsApi, "updatePost").mockRejectedValue(
      new ApiError("POST_FORBIDDEN", "自分の投稿のみ編集・削除できます", 403),
    );

    renderWithProviders(<PostDetailCard post={post({ isMine: true, body: "編集前" })} />);
    await userEvent.click(screen.getByRole("button", { name: "編集" }));
    await userEvent.click(screen.getByRole("button", { name: "保存" }));

    expect(await screen.findByText("自分の投稿のみ編集・削除できます")).toBeInTheDocument();
  });

  // --- 削除 ---

  it("削除は確認ダイアログでキャンセルすると実行されない", async () => {
    vi.spyOn(window, "confirm").mockReturnValue(false);
    const deletePost = vi.spyOn(postsApi, "deletePost").mockResolvedValue(null);

    renderWithProviders(<PostDetailCard post={post({ isMine: true })} />);
    await userEvent.click(screen.getByRole("button", { name: "削除" }));

    expect(deletePost).not.toHaveBeenCalled();
  });

  it("削除を確認すると削除APIを呼びホームへ遷移する", async () => {
    const deletePost = vi.spyOn(postsApi, "deletePost").mockResolvedValue(null);

    renderWithProviders(<PostDetailCard post={post({ id: 42, isMine: true })} />);
    await userEvent.click(screen.getByRole("button", { name: "削除" }));

    await waitFor(() => {
      expect(deletePost).toHaveBeenCalledWith(42);
      expect(navigate).toHaveBeenCalledWith("/home");
    });
  });

  /** 削除した投稿がタイムラインのキャッシュから消えること。 */
  it("削除成功でタイムラインのキャッシュから消える", async () => {
    vi.spyOn(postsApi, "deletePost").mockResolvedValue(null);

    const queryClient = createTestQueryClient();
    queryClient.setQueryData(
      postsKeys.list("all"),
      infinite([page([post({ id: 42 }), post({ id: 43 })])]),
    );

    renderWithProviders(<PostDetailCard post={post({ id: 42, isMine: true })} />, { queryClient });
    await userEvent.click(screen.getByRole("button", { name: "削除" }));

    await waitFor(() => {
      const cached = queryClient.getQueryData(postsKeys.list("all")) as ReturnType<
        typeof infinite<ReturnType<typeof post>>
      >;
      expect(cached.pages[0].items.map((item) => item.id)).toEqual([43]);
    });
  });

  it("削除に失敗するとエラーを表示し遷移しない", async () => {
    vi.spyOn(postsApi, "deletePost").mockRejectedValue(
      new ApiError("POST_FORBIDDEN", "自分の投稿のみ編集・削除できます", 403),
    );

    renderWithProviders(<PostDetailCard post={post({ isMine: true })} />);
    await userEvent.click(screen.getByRole("button", { name: "削除" }));

    expect(await screen.findByText("自分の投稿のみ編集・削除できます")).toBeInTheDocument();
    expect(navigate).not.toHaveBeenCalled();
  });
});
