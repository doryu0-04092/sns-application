import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { CommentForm } from "./CommentForm";
import { renderWithProviders } from "../test/renderWithProviders";
import { comment } from "../test/fixtures";
import { ApiError } from "../api/client";
import * as commentsApi from "../api/comments";

/**
 * コメント投稿フォームのテスト。
 *
 * トップレベルのコメントと返信で同じコンポーネントを使い回しており、
 * parentCommentId の有無だけが違う。ここを取り違えると返信が別の場所にぶら下がる。
 */
describe("CommentForm", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("既定のラベルとプレースホルダーを表示する", () => {
    renderWithProviders(<CommentForm postId={1} parentCommentId={null} />);

    expect(screen.getByPlaceholderText("返信をポスト")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "返信する" })).toBeInTheDocument();
  });

  it("ラベルとプレースホルダーを差し替えられる", () => {
    renderWithProviders(
      <CommentForm postId={1} parentCommentId={null} submitLabel="コメントする" placeholder="コメントを入力" />,
    );

    expect(screen.getByPlaceholderText("コメントを入力")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "コメントする" })).toBeInTheDocument();
  });

  it("本文が空なら送信できない", () => {
    renderWithProviders(<CommentForm postId={1} parentCommentId={null} />);

    expect(screen.getByRole("button", { name: "返信する" })).toBeDisabled();
  });

  it("空白のみでも送信できない", async () => {
    renderWithProviders(<CommentForm postId={1} parentCommentId={null} />);

    await userEvent.type(screen.getByPlaceholderText("返信をポスト"), "   ");

    expect(screen.getByRole("button", { name: "返信する" })).toBeDisabled();
  });

  /** parentCommentId が null ならトップレベルのコメントとして送る。 */
  it("トップレベルのコメントはparentCommentIdをnullで送る", async () => {
    const createComment = vi.spyOn(commentsApi, "createComment").mockResolvedValue(comment());

    renderWithProviders(<CommentForm postId={42} parentCommentId={null} />);
    await userEvent.type(screen.getByPlaceholderText("返信をポスト"), "コメント本文");
    await userEvent.click(screen.getByRole("button", { name: "返信する" }));

    await waitFor(() => {
      expect(createComment).toHaveBeenCalledWith(42, { body: "コメント本文", parentCommentId: null });
    });
  });

  it("返信は親コメントIDを付けて送る", async () => {
    const createComment = vi.spyOn(commentsApi, "createComment").mockResolvedValue(comment());

    renderWithProviders(<CommentForm postId={42} parentCommentId={101} />);
    await userEvent.type(screen.getByPlaceholderText("返信をポスト"), "返信本文");
    await userEvent.click(screen.getByRole("button", { name: "返信する" }));

    await waitFor(() => {
      expect(createComment).toHaveBeenCalledWith(42, { body: "返信本文", parentCommentId: 101 });
    });
  });

  it("280文字ちょうどなら送信できる", async () => {
    renderWithProviders(<CommentForm postId={1} parentCommentId={null} />);

    await userEvent.click(screen.getByPlaceholderText("返信をポスト"));
    await userEvent.paste("あ".repeat(280));

    expect(screen.getByText("0")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "返信する" })).toBeEnabled();
  });

  it("281文字になると送信できない", async () => {
    renderWithProviders(<CommentForm postId={1} parentCommentId={null} />);

    await userEvent.click(screen.getByPlaceholderText("返信をポスト"));
    await userEvent.paste("あ".repeat(281));

    expect(screen.getByText("-1")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "返信する" })).toBeDisabled();
  });

  it("送信に成功すると入力欄がクリアされる", async () => {
    vi.spyOn(commentsApi, "createComment").mockResolvedValue(comment());

    renderWithProviders(<CommentForm postId={1} parentCommentId={null} />);
    await userEvent.type(screen.getByPlaceholderText("返信をポスト"), "コメント本文");
    await userEvent.click(screen.getByRole("button", { name: "返信する" }));

    await waitFor(() => {
      expect(screen.getByPlaceholderText("返信をポスト")).toHaveValue("");
    });
  });

  /** 返信フォームは送信後に閉じる。開いたままだと連続で返信を書ける状態が残る。 */
  it("送信に成功するとonDoneが呼ばれる", async () => {
    vi.spyOn(commentsApi, "createComment").mockResolvedValue(comment());
    const onDone = vi.fn();

    renderWithProviders(<CommentForm postId={1} parentCommentId={101} onDone={onDone} />);
    await userEvent.type(screen.getByPlaceholderText("返信をポスト"), "返信本文");
    await userEvent.click(screen.getByRole("button", { name: "返信する" }));

    await waitFor(() => {
      expect(onDone).toHaveBeenCalled();
    });
  });

  it("失敗するとonDoneは呼ばれず入力も残る", async () => {
    vi.spyOn(commentsApi, "createComment").mockRejectedValue(
      new ApiError("COMMENT_NOT_FOUND", "コメントが見つかりません", 404),
    );
    const onDone = vi.fn();

    renderWithProviders(<CommentForm postId={1} parentCommentId={101} onDone={onDone} />);
    await userEvent.type(screen.getByPlaceholderText("返信をポスト"), "返信本文");
    await userEvent.click(screen.getByRole("button", { name: "返信する" }));

    expect(await screen.findByText("コメントが見つかりません")).toBeInTheDocument();
    expect(onDone).not.toHaveBeenCalled();
    expect(screen.getByPlaceholderText("返信をポスト")).toHaveValue("返信本文");
  });

  it("ApiError以外なら固定の文言を表示する", async () => {
    vi.spyOn(commentsApi, "createComment").mockRejectedValue(new Error("network down"));

    renderWithProviders(<CommentForm postId={1} parentCommentId={null} />);
    await userEvent.type(screen.getByPlaceholderText("返信をポスト"), "コメント本文");
    await userEvent.click(screen.getByRole("button", { name: "返信する" }));

    expect(await screen.findByText("投稿に失敗しました")).toBeInTheDocument();
  });
});
