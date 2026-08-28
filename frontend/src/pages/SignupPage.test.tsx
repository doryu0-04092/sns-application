import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { SignupPage } from "./SignupPage";
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
 * 新規登録画面のテスト。
 *
 * サーバーは登録と同時に認証クッキーを発行するため、登録後にログインを呼び直す必要がない。
 * その前提でキャッシュへの投入と /home への遷移が行われることを確認する。
 */
describe("SignupPage", () => {
  afterEach(() => {
    vi.restoreAllMocks();
    navigate.mockClear();
  });

  async function submitSignup(
    email = "new@example.com",
    password = "password123",
    displayName = "新規ユーザー",
  ) {
    await userEvent.type(screen.getByLabelText("メールアドレス"), email);
    // ラベルは「パスワード(8文字以上)」。文言の微修正で落ちないよう前方一致で拾う。
    await userEvent.type(screen.getByLabelText(/^パスワード/), password);
    await userEvent.type(screen.getByLabelText("表示名"), displayName);
    await userEvent.click(screen.getByRole("button", { name: "登録する" }));
  }

  it("入力した内容で登録APIを呼ぶ", async () => {
    const signup = vi.spyOn(authApi, "signup").mockResolvedValue(user());

    renderWithProviders(<SignupPage />);
    await submitSignup("new@example.com", "password123", "新規ユーザー");

    await waitFor(() => {
      expect(signup.mock.calls[0]?.[0]).toEqual({
        email: "new@example.com",
        password: "password123",
        displayName: "新規ユーザー",
      });
    });
  });

  /** 登録時点でログイン済みになるため、キャッシュへ入れておく必要がある。 */
  it("成功するとログイン中ユーザーがキャッシュへ入る", async () => {
    const created = user({ id: 99, displayName: "新規ユーザー" });
    vi.spyOn(authApi, "signup").mockResolvedValue(created);

    const { queryClient } = renderWithProviders(<SignupPage />);
    await submitSignup();

    await waitFor(() => {
      expect(queryClient.getQueryData(meKeys.all)).toEqual(created);
    });
  });

  it("成功するとホームへ遷移する", async () => {
    vi.spyOn(authApi, "signup").mockResolvedValue(user());

    renderWithProviders(<SignupPage />);
    await submitSignup();

    await waitFor(() => {
      expect(navigate).toHaveBeenCalledWith("/home");
    });
  });

  it("メールアドレス重複のエラーを表示する", async () => {
    vi.spyOn(authApi, "signup").mockRejectedValue(
      new ApiError("EMAIL_ALREADY_EXISTS", "このメールアドレスは既に登録されています", 400),
    );

    renderWithProviders(<SignupPage />);
    await submitSignup();

    expect(await screen.findByText("このメールアドレスは既に登録されています")).toBeInTheDocument();
    expect(navigate).not.toHaveBeenCalled();
  });

  it("ApiError以外なら固定の文言を表示する", async () => {
    vi.spyOn(authApi, "signup").mockRejectedValue(new Error("network down"));

    renderWithProviders(<SignupPage />);
    await submitSignup();

    expect(await screen.findByText("登録に失敗しました")).toBeInTheDocument();
  });

  it("送信中はボタンが無効になる", async () => {
    vi.spyOn(authApi, "signup").mockReturnValue(new Promise(() => {}));

    renderWithProviders(<SignupPage />);
    await submitSignup();

    await waitFor(() => {
      expect(screen.getByRole("button", { name: "登録する" })).toBeDisabled();
    });
  });

  it("ログインへのリンクがある", () => {
    renderWithProviders(<SignupPage />);

    expect(screen.getByRole("link", { name: "ログイン" })).toHaveAttribute("href", "/login");
  });

  /**
   * 登録も「別の利用者として使い始める」入口なので、前のセッションの残りを持ち込まない。
   * ログインと同じ理由で、キャッシュを捨ててから新しいユーザーを入れる。
   */
  it("登録成功で前のセッションのキャッシュが残らない", async () => {
    vi.spyOn(authApi, "signup").mockResolvedValue(user({ id: 2, displayName: "新しい人" }));

    const queryClient = createTestQueryClient();
    queryClient.setQueryData(["posts", "list"], [{ id: 1, isLiked: true, isFollowing: true }]);

    renderWithProviders(<SignupPage />, { queryClient });
    await submitSignup();

    await waitFor(() => {
      expect(queryClient.getQueryData(["posts", "list"])).toBeUndefined();
    });
    expect(queryClient.getQueryData(meKeys.all)).toMatchObject({ id: 2, displayName: "新しい人" });
  });
});
