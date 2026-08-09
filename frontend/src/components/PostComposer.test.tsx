import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { PostComposer } from "./PostComposer";
import { renderWithProviders } from "../test/renderWithProviders";
import { post, user } from "../test/fixtures";
import { ApiError } from "../api/client";
import * as authApi from "../api/auth";
import * as postsApi from "../api/posts";
import * as uploadsApi from "../api/uploads";

/**
 * 投稿フォームのテスト。
 *
 * 280文字の上限はサーバー側の @Size(max=280) と対になっている。フロントで送信を止められないと
 * 400が返ってから初めてユーザーが気づくことになるため、境界での送信可否を固定する。
 */
describe("PostComposer", () => {
  beforeEach(() => {
    vi.spyOn(authApi, "me").mockResolvedValue(user());
    vi.spyOn(uploadsApi, "uploadImages").mockResolvedValue([]);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  const textarea = () => screen.getByPlaceholderText("いまどうしてる?");
  const submitButton = () => screen.getByRole("button", { name: "投稿する" });

  it("入力欄と投稿ボタンが表示される", () => {
    renderWithProviders(<PostComposer />);

    expect(textarea()).toBeInTheDocument();
    expect(submitButton()).toBeInTheDocument();
  });

  /** 空の状態では投稿できない(画像だけの投稿も不可)。 */
  it("本文が空なら投稿ボタンは無効", () => {
    renderWithProviders(<PostComposer />);

    expect(submitButton()).toBeDisabled();
  });

  it("空白のみでも投稿ボタンは無効", async () => {
    renderWithProviders(<PostComposer />);

    await userEvent.type(textarea(), "   ");

    expect(submitButton()).toBeDisabled();
  });

  it("本文を入力すると投稿できるようになる", async () => {
    renderWithProviders(<PostComposer />);

    await userEvent.type(textarea(), "こんにちは");

    expect(submitButton()).toBeEnabled();
  });

  it("残り文字数が表示される", async () => {
    renderWithProviders(<PostComposer />);

    expect(screen.getByText("280文字")).toBeInTheDocument();

    await userEvent.type(textarea(), "あいう");

    expect(screen.getByText("277文字")).toBeInTheDocument();
  });

  /** 上限ちょうどはまだ投稿できる。 */
  it("280文字ちょうどなら投稿できる", async () => {
    renderWithProviders(<PostComposer />);

    await userEvent.click(textarea());
    await userEvent.paste("あ".repeat(280));

    expect(screen.getByText("0文字")).toBeInTheDocument();
    expect(submitButton()).toBeEnabled();
  });

  it("281文字になると投稿ボタンが無効になる", async () => {
    renderWithProviders(<PostComposer />);

    await userEvent.click(textarea());
    await userEvent.paste("あ".repeat(281));

    expect(screen.getByText("-1文字")).toBeInTheDocument();
    expect(submitButton()).toBeDisabled();
  });

  it("入力した本文で投稿APIを呼ぶ", async () => {
    const createPost = vi.spyOn(postsApi, "createPost").mockResolvedValue(post());

    renderWithProviders(<PostComposer />);
    await userEvent.type(textarea(), "投稿する本文");
    await userEvent.click(submitButton());

    await waitFor(() => {
      expect(createPost).toHaveBeenCalledWith("投稿する本文", []);
    });
  });

  /** 投稿が成功したら入力欄を空にする。残ったままだと二重投稿しやすい。 */
  it("投稿に成功すると入力欄がクリアされる", async () => {
    vi.spyOn(postsApi, "createPost").mockResolvedValue(post());

    renderWithProviders(<PostComposer />);
    await userEvent.type(textarea(), "投稿する本文");
    await userEvent.click(submitButton());

    await waitFor(() => {
      expect(textarea()).toHaveValue("");
    });
  });

  it("ApiErrorならサーバーのメッセージを表示する", async () => {
    vi.spyOn(postsApi, "createPost").mockRejectedValue(
      new ApiError("TOO_MANY_IMAGES", "画像は4枚まで添付できます", 400),
    );

    renderWithProviders(<PostComposer />);
    await userEvent.type(textarea(), "投稿する本文");
    await userEvent.click(submitButton());

    expect(await screen.findByText("画像は4枚まで添付できます")).toBeInTheDocument();
  });

  /** 失敗したときは入力を消さない。書いた本文を失わせないため。 */
  it("投稿に失敗しても入力内容は残る", async () => {
    vi.spyOn(postsApi, "createPost").mockRejectedValue(new ApiError("INTERNAL_ERROR", "失敗しました", 500));

    renderWithProviders(<PostComposer />);
    await userEvent.type(textarea(), "消えては困る本文");
    await userEvent.click(submitButton());

    await screen.findByText("失敗しました");
    expect(textarea()).toHaveValue("消えては困る本文");
  });

  it("送信中は投稿ボタンが無効になる", async () => {
    vi.spyOn(postsApi, "createPost").mockReturnValue(new Promise(() => {}));

    renderWithProviders(<PostComposer />);
    await userEvent.type(textarea(), "投稿する本文");
    await userEvent.click(submitButton());

    await waitFor(() => {
      expect(submitButton()).toBeDisabled();
    });
  });
});
