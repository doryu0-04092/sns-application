import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import './index.css'
import App from './App.tsx'
import { shouldRetryQuery } from './api/retry.ts'

/**
 * TanStack Query の既定設定。
 *
 * これまでオプション未指定で生成していたため staleTime が既定の 0 になり、
 * 取得したデータが即座に stale と判定されていた。その結果、同じデータを使う
 * 2つ目のコンポーネントがマウントした時点で再取得が走っていた。
 *
 * 実測(docs/perf-test-report.md 7-3):
 *   /home の表示1回で GET /auth/me が2回呼ばれていた(572ms と 788ms)。
 *   同時ではなく1回目の完了後に2回目が走っており、キャッシュが効いていなかった。
 *
 * 30秒にした理由:
 *   1回の画面遷移や、複数コンポーネントが同じデータを参照する場面を吸収できれば足りる。
 *   長くしすぎると他の利用者の更新が見えなくなる。
 *   なお投稿直後は PostComposer が resetQueries でキャッシュごと破棄するため、
 *   staleTime に関係なく即座に再取得される(自分の投稿が消えて見えることはない)。
 *
 * 個別に鮮度が要るクエリは、フック側で staleTime を上書きしている
 * (例: useNewPostsBanner は新着確認が目的なので 0)。
 *
 * retry を指定する理由は api/retry.ts を参照。
 * 既定のままだと 4xx まで3回再試行され、404の表示が数秒遅れていた。
 */
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      retry: shouldRetryQuery,
    },
  },
})

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <App />
      </BrowserRouter>
    </QueryClientProvider>
  </StrictMode>,
)
