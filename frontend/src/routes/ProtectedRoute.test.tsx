import { screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { Route, Routes } from "react-router-dom";
import { QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import { render } from "@testing-library/react";
import { ProtectedRoute } from "./ProtectedRoute";
import { createTestQueryClient } from "../test/renderWithProviders";
import { user } from "../test/fixtures";
import { ApiError } from "../api/client";
import * as authApi from "../api/auth";

/**
 * 認証ガードのテスト(docs/test-plan.md 4.5 の最優先項目)。
 *
 * ここが壊れると未認証のユーザーに保護されたページが見えるため、
 * ローディング・エラー・成功の3分岐すべてを固定する。
 *
 * useCurrentUser は内部で api/auth の me() を呼ぶ。fetch をモックすると
 * apiFetch の401リトライまで巻き込むため、me() 自体を差し替えて分岐だけを見る。
 */
describe("ProtectedRoute", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  /** MemoryRouter に /login のルートも用意し、リダイレクト先を観測できるようにする。 */
  function renderGuard() {
    const queryClient = createTestQueryClient();
    return render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={["/home"]}>
          <Routes>
            <Route
              path="/home"
              element={
                <ProtectedRoute>
                  <div>保護されたコンテンツ</div>
                </ProtectedRoute>
              }
            />
            <Route path="/login" element={<div>ログイン画面</div>} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    );
  }

  it("認証確認中はローディングを表示する", () => {
    // 解決しないPromiseを返して isLoading の状態で止める
    vi.spyOn(authApi, "me").mockReturnValue(new Promise(() => {}));

    renderGuard();

    expect(screen.getByText("読み込み中...")).toBeInTheDocument();
    expect(screen.queryByText("保護されたコンテンツ")).not.toBeInTheDocument();
  });

  it("認証済みなら子要素を表示する", async () => {
    vi.spyOn(authApi, "me").mockResolvedValue(user());

    renderGuard();

    expect(await screen.findByText("保護されたコンテンツ")).toBeInTheDocument();
    expect(screen.queryByText("ログイン画面")).not.toBeInTheDocument();
  });

  it("未認証ならログイン画面へリダイレクトする", async () => {
    vi.spyOn(authApi, "me").mockRejectedValue(new ApiError("UNAUTHENTICATED", "認証が必要です", 401));

    renderGuard();

    expect(await screen.findByText("ログイン画面")).toBeInTheDocument();
    expect(screen.queryByText("保護されたコンテンツ")).not.toBeInTheDocument();
  });

  /** 401以外のエラー(通信断など)でも、認証状態が確認できない以上は入れない。 */
  it("通信エラーでもログイン画面へリダイレクトする", async () => {
    vi.spyOn(authApi, "me").mockRejectedValue(new Error("network error"));

    renderGuard();

    await waitFor(() => {
      expect(screen.getByText("ログイン画面")).toBeInTheDocument();
    });
  });
});
