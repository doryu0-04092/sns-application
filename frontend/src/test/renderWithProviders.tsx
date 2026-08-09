import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, type RenderOptions, type RenderResult } from "@testing-library/react";
import type { ReactElement, ReactNode } from "react";
import { MemoryRouter, Route, Routes } from "react-router-dom";

/**
 * コンポーネントを QueryClientProvider + MemoryRouter で包んで描画する。
 *
 * QueryClient はテストごとに作り直す。使い回すとキャッシュが漏れて、
 * 単独では通るのに連続実行すると落ちるテストになる。
 */

interface Options extends Omit<RenderOptions, "wrapper"> {
  /** 初期URL。useParams を読むコンポーネントでは route と合わせて使う。 */
  initialEntry?: string;
  /** ルート定義のパス(例: "/users/:userId")。指定するとその位置に children を配置する。 */
  route?: string;
}

/**
 * テスト用の QueryClient。
 *
 * retry を切るのは、失敗を再試行されるとエラー表示のテストがタイムアウトするため。
 *
 * gcTime は既定のままにしている。0にすると setQueryData で入れた値が
 * 観測者のいない時点で即座に破棄され、「ログイン成功でキャッシュへ入る」ような検証ができない。
 * テストごとにクライアントを作り直すので、持ち越しの心配は元々ない。
 */
export function createTestQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: { retry: false, staleTime: 0 },
      mutations: { retry: false },
    },
  });
}

export interface RenderWithProvidersResult extends RenderResult {
  queryClient: QueryClient;
}

export function renderWithProviders(
  ui: ReactElement,
  { initialEntry = "/", route, queryClient = createTestQueryClient(), ...options }: Options & {
    queryClient?: QueryClient;
  } = {},
): RenderWithProvidersResult {
  function Wrapper({ children }: { children: ReactNode }) {
    return (
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={[initialEntry]}>
          {route ? (
            <Routes>
              <Route path={route} element={children} />
            </Routes>
          ) : (
            children
          )}
        </MemoryRouter>
      </QueryClientProvider>
    );
  }

  return { ...render(ui, { wrapper: Wrapper, ...options }), queryClient };
}
