import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { LoginPage } from "./LoginPage";
import { renderWithProviders, createTestQueryClient } from "../test/renderWithProviders";
import { user } from "../test/fixtures";
import { meKeys } from "../api/queryKeys";
import { ApiError } from "../api/client";
import * as authApi from "../api/auth";

const navigate = vi.fn();
vi.mock("react-router-dom", async () => {
  const actual = await vi.importActual<typeof import("react-router-dom")>("react-router-dom");
  return { ...actual, useNavigate: () => navigate };
});

/**
 * ログイン画面のテスト。
 *
 * 成功時は「キャッシュへユーザーを入れる」「/home へ遷移する」の2つが起きる必要がある。
 * キャッシュ投入が漏れると、遷移先で me() を取り直すまでヘッダーがちらつく。
 *
 * エラー表示は ApiError かどうかで文言が分岐するため、両方を確認する。
 */
describe("LoginPage", () => {
  afterEach(() => {
    vi.restoreAllMocks();
    navigate.mockClear();
  });

  async function submitLogin(email = "user@example.com", password = "password123") {
    await userEvent.type(screen.getByLabelText("メールアドレス"), email);
    await userEvent.type(screen.getByLabelText("パスワード"), password);
    await userEvent.click(screen.getByRole("button", { name: "ログイン" }));
  }

  it("フォームが表示される", () => {
    renderWithProviders(<LoginPage />);

    expect(screen.getByLabelText("メールアドレス")).toBeInTheDocument();
    expect(screen.getByLabelText("パスワード")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "ログイン" })).toBeInTheDocument();
  });

  it("入力した内容でログインAPIを呼ぶ", async () => {
    const login = vi.spyOn(authApi, "login").mockResolvedValue(user());

    renderWithProviders(<LoginPage />);
    await submitLogin("me@example.com", "secret1234");

    // react-query は mutationFn を (variables, context) の2引数で呼ぶため、
    // toHaveBeenCalledWith で引数全体を指定すると引数の数が合わずに落ちる。第1引数だけを見る。
    await waitFor(() => {
      expect(login.mock.calls[0]?.[0]).toEqual({ email: "me@example.com", password: "secret1234" });
    });
  });

  it("成功するとログイン中ユーザーがキャッシュへ入る", async () => {
    const loggedIn = user({ id: 42, displayName: "ログイン太郎" });
    vi.spyOn(authApi, "login").mockResolvedValue(loggedIn);

    const { queryClient } = renderWithProviders(<LoginPage />);
    await submitLogin();

    await waitFor(() => {
      expect(queryClient.getQueryData(meKeys.all)).toEqual(loggedIn);
    });
  });

  it("成功するとホームへ遷移する", async () => {
    vi.spyOn(authApi, "login").mockResolvedValue(user());

    renderWithProviders(<LoginPage />);
    await submitLogin();

    await waitFor(() => {
      expect(navigate).toHaveBeenCalledWith("/home");
    });
  });

  /** ApiError はサーバーの文言をそのまま出す(「パスワードが違います」等を伝えたいため)。 */
  it("ApiErrorならサーバーのメッセージを表示する", async () => {
    vi.spyOn(authApi, "login").mockRejectedValue(
      new ApiError("INVALID_CREDENTIALS", "メールアドレスまたはパスワードが正しくありません", 401),
    );

    renderWithProviders(<LoginPage />);
    await submitLogin();

    expect(
      await screen.findByText("メールアドレスまたはパスワードが正しくありません"),
    ).toBeInTheDocument();
  });

  /** ApiError以外(通信断など)はサーバー由来でないため固定文言にする。 */
  it("ApiError以外なら固定の文言を表示する", async () => {
    vi.spyOn(authApi, "login").mockRejectedValue(new Error("network down"));

    renderWithProviders(<LoginPage />);
    await submitLogin();

    expect(await screen.findByText("ログインに失敗しました")).toBeInTheDocument();
    expect(screen.queryByText("network down")).not.toBeInTheDocument();
  });

  it("失敗したときは遷移しない", async () => {
    vi.spyOn(authApi, "login").mockRejectedValue(new ApiError("INVALID_CREDENTIALS", "認証に失敗", 401));

    renderWithProviders(<LoginPage />);
    await submitLogin();

    await screen.findByText("認証に失敗");
    expect(navigate).not.toHaveBeenCalled();
  });

  /** 二重送信でログインが2回走らないよう、送信中はボタンを無効にする。 */
  it("送信中はボタンが無効になる", async () => {
    vi.spyOn(authApi, "login").mockReturnValue(new Promise(() => {}));

    renderWithProviders(<LoginPage />);
    await submitLogin();

    await waitFor(() => {
      expect(screen.getByRole("button", { name: "ログイン" })).toBeDisabled();
    });
  });

  it("新規登録へのリンクがある", () => {
    renderWithProviders(<LoginPage />);

    expect(screen.getByRole("link", { name: "新規登録" })).toHaveAttribute("href", "/signup");
  });

  /**
   * ログイン時にもキャッシュを捨てる。
   *
   * ログアウト側でも捨てているが、それだけでは足りない。トークンが失効して
   * ログイン画面へ送られた場合はログアウトを通らないため、その経路では前のユーザーの
   * キャッシュが残ったままここへ到達する。ここではその状況を、ログアウトを経由せずに
   * 前のユーザーのキャッシュが存在する状態から再現している。
   */
  it("ログイン成功で前のセッションのキャッシュが残らない", async () => {
    vi.spyOn(authApi, "login").mockResolvedValue(user({ id: 2, displayName: "新しい人" }));

    const queryClient = createTestQueryClient();
    queryClient.setQueryData(["posts", "list"], [{ id: 1, isLiked: true, isFollowing: true }]);
    queryClient.setQueryData(meKeys.all, user({ id: 1, displayName: "前の人" }));

    renderWithProviders(<LoginPage />, { queryClient });
    await submitLogin();

    await waitFor(() => {
      expect(queryClient.getQueryData(["posts", "list"])).toBeUndefined();
    });
    // 新しいユーザーは消さずに入れ直す(消す→入れるの順序が逆だと消えてしまう)。
    expect(queryClient.getQueryData(meKeys.all)).toMatchObject({ id: 2, displayName: "新しい人" });
  });
});
