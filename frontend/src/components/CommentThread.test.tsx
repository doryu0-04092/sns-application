import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { CommentThread } from "./CommentThread";
import { renderWithProviders } from "../test/renderWithProviders";
import { comment } from "../test/fixtures";
import { buildCommentTree } from "../utils/commentTree";
import { ApiError } from "../api/client";
import * as commentsApi from "../api/comments";

/**
 * コメント1件とその返信ツリーの表示・編集・削除のテスト。
 *
 * 権限まわりが要点。編集・削除のボタンは自分のコメントにだけ出す必要がある
 * (他人のものに出しても、押した先でサーバーが403を返すだけでUXが悪い)。
 * また削除済みコメントはツームストーンとして残るため、本文の代わりに
 * 「削除されました」を出し、操作ボタンを一切出さないことを確認する。
 */
describe("CommentThread", () => {
  beforeEach(() => {
    // 削除は window.confirm を通す。既定では jsdom が未実装で例外になる。
    vi.spyOn(window, "confirm").mockReturnValue(true);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  /** 単一コメントのノードを作る。 */
  function node(overrides: Parameters<typeof comment>[0] = {}) {
    return { comment: comment({ id: 101, ...overrides }), children: [] };
  }

  it("コメントの本文と投稿者名を表示する", () => {
    renderWithProviders(
      <CommentThread node={node({ body: "参考になりました", authorDisplayName: "山田" })} postId={42} />,
    );

    expect(screen.getByText("参考になりました")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "山田" })).toBeInTheDocument();
  });

  it("投稿者名からプロフィールへ遷移できる", () => {
    renderWithProviders(<CommentThread node={node({ authorId: 7, authorDisplayName: "山田" })} postId={42} />);

    expect(screen.getByRole("link", { name: "山田" })).toHaveAttribute("href", "/users/7");
  });

  // --- 権限による出し分け ---

  it("自分のコメントには編集と削除のボタンが出る", () => {
    renderWithProviders(<CommentThread node={node({ isMine: true })} postId={42} />);

    expect(screen.getByRole("button", { name: "編集" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "削除" })).toBeInTheDocument();
  });

  it("他人のコメントには編集と削除のボタンが出ない", () => {
    renderWithProviders(<CommentThread node={node({ isMine: false })} postId={42} />);

    expect(screen.queryByRole("button", { name: "編集" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "削除" })).not.toBeInTheDocument();
  });

  it("他人のコメントにはフォローボタンが出る", () => {
    renderWithProviders(<CommentThread node={node({ isMine: false, isFollowing: false })} postId={42} />);

    expect(screen.getByRole("button", { name: "フォローする" })).toBeInTheDocument();
  });

  it("自分のコメントにはフォローボタンが出ない", () => {
    renderWithProviders(<CommentThread node={node({ isMine: true })} postId={42} />);

    expect(screen.queryByRole("button", { name: "フォローする" })).not.toBeInTheDocument();
  });

  // --- ツームストーン ---

  it("削除済みコメントは削除された旨を表示する", () => {
    renderWithProviders(
      <CommentThread node={node({ deleted: true, body: null, isMine: true })} postId={42} />,
    );

    expect(screen.getByText("このコメントは削除されました")).toBeInTheDocument();
  });

  /** 削除済みには編集・削除・返信・いいねのいずれも出さない。 */
  it("削除済みコメントには操作ボタンが出ない", () => {
    renderWithProviders(
      <CommentThread node={node({ deleted: true, body: null, isMine: true })} postId={42} />,
    );

    expect(screen.queryByRole("button", { name: "編集" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "削除" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "返信" })).not.toBeInTheDocument();
  });

  // --- 編集 ---

  it("編集を押すと入力欄が開き本文が入る", async () => {
    renderWithProviders(<CommentThread node={node({ isMine: true, body: "編集前の本文" })} postId={42} />);

    await userEvent.click(screen.getByRole("button", { name: "編集" }));

    expect(screen.getByRole("textbox")).toHaveValue("編集前の本文");
    expect(screen.getByRole("button", { name: "保存" })).toBeInTheDocument();
  });

  it("編集して保存すると更新APIを呼ぶ", async () => {
    const updateComment = vi.spyOn(commentsApi, "updateComment").mockResolvedValue(comment());

    renderWithProviders(<CommentThread node={node({ isMine: true, body: "編集前" })} postId={42} />);
    await userEvent.click(screen.getByRole("button", { name: "編集" }));
    await userEvent.clear(screen.getByRole("textbox"));
    await userEvent.type(screen.getByRole("textbox"), "編集後の本文");
    await userEvent.click(screen.getByRole("button", { name: "保存" }));

    await waitFor(() => {
      expect(updateComment.mock.calls[0]?.[0]).toBe(101);
      expect(updateComment.mock.calls[0]?.[1]).toBe("編集後の本文");
    });
  });

  /** 前後の空白は落として送る。空白だけの編集は保存させない。 */
  it("編集内容が空白のみなら保存できない", async () => {
    renderWithProviders(<CommentThread node={node({ isMine: true, body: "編集前" })} postId={42} />);

    await userEvent.click(screen.getByRole("button", { name: "編集" }));
    await userEvent.clear(screen.getByRole("textbox"));
    await userEvent.type(screen.getByRole("textbox"), "   ");

    expect(screen.getByRole("button", { name: "保存" })).toBeDisabled();
  });

  it("キャンセルすると編集前の本文に戻る", async () => {
    renderWithProviders(<CommentThread node={node({ isMine: true, body: "編集前の本文" })} postId={42} />);

    await userEvent.click(screen.getByRole("button", { name: "編集" }));
    await userEvent.clear(screen.getByRole("textbox"));
    await userEvent.type(screen.getByRole("textbox"), "書きかけ");
    await userEvent.click(screen.getByRole("button", { name: "キャンセル" }));

    expect(screen.getByText("編集前の本文")).toBeInTheDocument();
    expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
  });

  it("編集に失敗するとエラーを表示し編集欄は閉じない", async () => {
    vi.spyOn(commentsApi, "updateComment").mockRejectedValue(
      new ApiError("COMMENT_FORBIDDEN", "自分のコメントのみ編集・削除できます", 403),
    );

    renderWithProviders(<CommentThread node={node({ isMine: true, body: "編集前" })} postId={42} />);
    await userEvent.click(screen.getByRole("button", { name: "編集" }));
    await userEvent.click(screen.getByRole("button", { name: "保存" }));

    expect(await screen.findByText("自分のコメントのみ編集・削除できます")).toBeInTheDocument();
    expect(screen.getByRole("textbox")).toBeInTheDocument();
  });

  // --- 削除 ---

  /** 誤操作を防ぐため確認ダイアログを挟む。キャンセルされたら消さない。 */
  it("削除は確認ダイアログでキャンセルすると実行されない", async () => {
    vi.spyOn(window, "confirm").mockReturnValue(false);
    const deleteComment = vi.spyOn(commentsApi, "deleteComment").mockResolvedValue(null);

    renderWithProviders(<CommentThread node={node({ isMine: true })} postId={42} />);
    await userEvent.click(screen.getByRole("button", { name: "削除" }));

    expect(deleteComment).not.toHaveBeenCalled();
  });

  it("削除を確認すると削除APIを呼ぶ", async () => {
    const deleteComment = vi.spyOn(commentsApi, "deleteComment").mockResolvedValue(null);

    renderWithProviders(<CommentThread node={node({ isMine: true })} postId={42} />);
    await userEvent.click(screen.getByRole("button", { name: "削除" }));

    await waitFor(() => {
      expect(deleteComment).toHaveBeenCalledWith(101);
    });
  });

  it("削除に失敗するとエラーを表示する", async () => {
    vi.spyOn(commentsApi, "deleteComment").mockRejectedValue(
      new ApiError("COMMENT_FORBIDDEN", "自分のコメントのみ編集・削除できます", 403),
    );

    renderWithProviders(<CommentThread node={node({ isMine: true })} postId={42} />);
    await userEvent.click(screen.getByRole("button", { name: "削除" }));

    expect(await screen.findByText("自分のコメントのみ編集・削除できます")).toBeInTheDocument();
  });

  // --- 返信 ---

  it("返信を押すと返信フォームが開く", async () => {
    renderWithProviders(<CommentThread node={node()} postId={42} />);

    expect(screen.queryByPlaceholderText("返信をポスト")).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "返信" }));

    expect(screen.getByPlaceholderText("返信をポスト")).toBeInTheDocument();
  });

  it("返信をもう一度押すとフォームが閉じる", async () => {
    renderWithProviders(<CommentThread node={node()} postId={42} />);

    await userEvent.click(screen.getByRole("button", { name: "返信" }));
    await userEvent.click(screen.getByRole("button", { name: "返信" }));

    expect(screen.queryByPlaceholderText("返信をポスト")).not.toBeInTheDocument();
  });

  // --- 再帰的なネスト ---

  it("ネストした返信を再帰的に描画する", () => {
    const tree = buildCommentTree([
      comment({ id: 1, parentCommentId: null, body: "親コメント" }),
      comment({ id: 2, parentCommentId: 1, body: "子コメント" }),
      comment({ id: 3, parentCommentId: 2, body: "孫コメント" }),
    ]);

    renderWithProviders(<CommentThread node={tree[0]} postId={42} />);

    expect(screen.getByText("親コメント")).toBeInTheDocument();
    expect(screen.getByText("子コメント")).toBeInTheDocument();
    expect(screen.getByText("孫コメント")).toBeInTheDocument();
  });

  /** 削除済みの親でも子は表示され続ける(ツームストーンが接続点になる)。 */
  it("削除済みの親でも返信は表示される", () => {
    const tree = buildCommentTree([
      comment({ id: 1, parentCommentId: null, body: null, deleted: true }),
      comment({ id: 2, parentCommentId: 1, body: "残る返信" }),
    ]);

    renderWithProviders(<CommentThread node={tree[0]} postId={42} />);

    expect(screen.getByText("このコメントは削除されました")).toBeInTheDocument();
    expect(screen.getByText("残る返信")).toBeInTheDocument();
  });

  it("同じ階層の複数の返信をすべて描画する", () => {
    const tree = buildCommentTree([
      comment({ id: 1, parentCommentId: null, body: "親" }),
      comment({ id: 2, parentCommentId: 1, body: "返信A" }),
      comment({ id: 3, parentCommentId: 1, body: "返信B" }),
    ]);

    const { container } = renderWithProviders(<CommentThread node={tree[0]} postId={42} />);

    expect(within(container).getByText("返信A")).toBeInTheDocument();
    expect(within(container).getByText("返信B")).toBeInTheDocument();
  });
});
