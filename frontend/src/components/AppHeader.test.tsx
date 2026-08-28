import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { AppHeader } from "./AppHeader";
import { renderWithProviders, createTestQueryClient } from "../test/renderWithProviders";
import { user } from "../test/fixtures";
import * as authApi from "../api/auth";

const navigate = vi.fn();
vi.mock("react-router-dom", async () => {
  const actual = await vi.importActual<typeof import("react-router-dom")>("react-router-dom");
  return { ...actual, useNavigate: () => navigate };
});

/**
 * ヘッダーのテスト。
 *
 * ログアウトでは「サーバー側のセッションを切る」だけでなく
 * 「クライアントのキャッシュから前のユーザーを消す」ことが必要。
 * 消し忘れると、ログアウト後も前のユーザーの表示名が残る。
 */
describe("AppHeader", () => {
  afterEach(() => {
    vi.restoreAllMocks();
    navigate.mockClear();
  });

  it("ログイン中ユーザーの表示名を表示する", async () => {
    vi.spyOn(authApi, "me").mockResolvedValue(user({ displayName: "山田太郎" }));

    renderWithProviders(<AppHeader />);

    expect(await screen.findByText("山田太郎")).toBeInTheDocument();
  });

  it("ユーザー未取得でもヘッダー自体は表示される", () => {
    vi.spyOn(authApi, "me").mockReturnValue(new Promise(() => {}));

    renderWithProviders(<AppHeader />);

    expect(screen.getByRole("link", { name: "SNS App" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "ログアウト" })).toBeInTheDocument();
  });

  it("表示名からプロフィールへのリンクがある", async () => {
    vi.spyOn(authApi, "me").mockResolvedValue(user({ id: 42, displayName: "山田太郎" }));

    renderWithProviders(<AppHeader />);

    await waitFor(() => {
      expect(screen.getByRole("link", { name: /山田太郎/ })).toHaveAttribute("href", "/users/42");
    });
  });

  it("ユーザー検索へのリンクがある", () => {
    vi.spyOn(authApi, "me").mockResolvedValue(user());

    renderWithProviders(<AppHeader />);

    expect(screen.getByRole("link", { name: "ユーザー検索" })).toHaveAttribute("href", "/search");
  });

  it("ログアウトを押すとログアウトAPIを呼ぶ", async () => {
    vi.spyOn(authApi, "me").mockResolvedValue(user());
    const logout = vi.spyOn(authApi, "logout").mockResolvedValue(null);

    renderWithProviders(<AppHeader />);
    await userEvent.click(screen.getByRole("button", { name: "ログアウト" }));

    await waitFor(() => {
      expect(logout).toHaveBeenCalled();
    });
  });

  it("ログアウト成功でログイン画面へ遷移する", async () => {
    vi.spyOn(authApi, "me").mockResolvedValue(user());
    vi.spyOn(authApi, "logout").mockResolvedValue(null);

    renderWithProviders(<AppHeader />);
    await userEvent.click(screen.getByRole("button", { name: "ログアウト" }));

    await waitFor(() => {
      expect(navigate).toHaveBeenCalledWith("/login");
    });
  });

  /**
   * ログアウトではキャッシュ全体を捨てる。
   *
   * 以前はログイン中ユーザー(meKeys)だけを無効化しており、そのことを
   * 「invalidateQueries が meKeys で呼ばれたか」で検証していた。しかし投稿一覧や
   * プロフィールには isLiked / isFollowing / isMine という「誰が見ているか」で
   * 変わる値が含まれる。meだけ消しても、これらは前のユーザーの判定を保持したまま残る。
   *
   * 実際にデプロイ先で、別ユーザーでログインした直後に前のユーザーのいいね・フォロー状態が
   * 表示される事象が出た。呼び出しを検証する形だと、テストと実装が同じ思い込みを共有して
   * しまい気づけない。ここではキャッシュの中身が消えることそのものを見る。
   */
  it("ログアウトで他のユーザー依存のキャッシュも消える", async () => {
    vi.spyOn(authApi, "me").mockResolvedValue(user({ displayName: "山田太郎" }));
    vi.spyOn(authApi, "logout").mockResolvedValue(null);

    const queryClient = createTestQueryClient();
    // 投稿一覧のように、閲覧者によって内容が変わるキャッシュを置いておく。
    queryClient.setQueryData(["posts", "list"], [{ id: 1, isLiked: true, isFollowing: true }]);

    renderWithProviders(<AppHeader />, { queryClient });
    await screen.findByText("山田太郎");
    await userEvent.click(screen.getByRole("button", { name: "ログアウト" }));

    await waitFor(() => {
      expect(queryClient.getQueryData(["posts", "list"])).toBeUndefined();
    });

    // meKeys は見ない。このコンポーネントが useCurrentUser を購読したままなので、
    // clear() の直後に再取得が走ってすぐ埋め直される。実際のアプリではログイン画面へ
    // 遷移してヘッダーごと破棄されるため起きないが、描画したままのテストでは起きる。
  });

  it("ログアウト送信中はボタンが無効になる", async () => {
    vi.spyOn(authApi, "me").mockResolvedValue(user());
    vi.spyOn(authApi, "logout").mockReturnValue(new Promise(() => {}));

    renderWithProviders(<AppHeader />);
    await userEvent.click(screen.getByRole("button", { name: "ログアウト" }));

    await waitFor(() => {
      expect(screen.getByRole("button", { name: "ログアウト" })).toBeDisabled();
    });
  });

  /** 失敗したら遷移しない(ログアウトできていないのに画面だけ変わるのを防ぐ)。 */
  it("ログアウトに失敗したら遷移しない", async () => {
    vi.spyOn(authApi, "me").mockResolvedValue(user());
    vi.spyOn(authApi, "logout").mockRejectedValue(new Error("failed"));

    renderWithProviders(<AppHeader />);
    await userEvent.click(screen.getByRole("button", { name: "ログアウト" }));

    await waitFor(() => {
      expect(screen.getByRole("button", { name: "ログアウト" })).toBeEnabled();
    });
    expect(navigate).not.toHaveBeenCalled();
  });
});
