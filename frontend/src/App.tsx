import { Suspense, lazy } from "react";
import { Navigate, Route, Routes } from "react-router-dom";
import { ProtectedRoute } from "./routes/ProtectedRoute";

/**
 * 画面はすべて遅延読み込みにする。
 *
 * これまで全9画面を静的 import していたため、ビルド結果が1つのチャンクにまとまり、
 * /login を開いた時点でタイムライン・投稿詳細・プロフィール編集・検索・
 * フォロー一覧のコードまで全部ダウンロードして解析・実行していた。
 * ログイン画面にはメールとパスワードの入力欄しかない。
 *
 * 実測(docs/perf-test-report.md 7-1, 7-2):
 *   単一チャンク 310.57 kB(gzip 93.21 kB)、111モジュール。
 *   /login の LCP はモバイル条件(CPU 4x)で 598ms、そのうち 594ms が
 *   レンダー遅延(= JSの解析・実行)だった。TTFB は 4ms しかない。
 *
 * ProtectedRoute だけは遅延にしない。全保護ルートの入口であり、
 * 遅延にすると画面を開くたびにチャンク取得が1段挟まって効果が相殺されるため。
 */
const LoginPage = lazy(() => import("./pages/LoginPage").then((m) => ({ default: m.LoginPage })));
const SignupPage = lazy(() => import("./pages/SignupPage").then((m) => ({ default: m.SignupPage })));
const TimelinePage = lazy(() => import("./pages/TimelinePage").then((m) => ({ default: m.TimelinePage })));
const PostDetailPage = lazy(() => import("./pages/PostDetailPage").then((m) => ({ default: m.PostDetailPage })));
const ProfilePage = lazy(() => import("./pages/ProfilePage").then((m) => ({ default: m.ProfilePage })));
const ProfileEditPage = lazy(() => import("./pages/ProfileEditPage").then((m) => ({ default: m.ProfileEditPage })));
const FollowListPage = lazy(() => import("./pages/FollowListPage").then((m) => ({ default: m.FollowListPage })));
const SearchPage = lazy(() => import("./pages/SearchPage").then((m) => ({ default: m.SearchPage })));
const NotFoundPage = lazy(() => import("./pages/NotFoundPage").then((m) => ({ default: m.NotFoundPage })));

/**
 * チャンク取得中の表示。
 *
 * 画面いっぱいの高さを確保しているのは、読み込み完了時に本体が入っても
 * レイアウトが飛び跳ねないようにするため(CLSの悪化を避ける)。
 */
function RouteFallback() {
  return (
    <div className="flex min-h-screen items-center justify-center text-gray-500" role="status" aria-live="polite">
      読み込み中...
    </div>
  );
}

function App() {
  return (
    <Suspense fallback={<RouteFallback />}>
      <Routes>
        <Route path="/" element={<Navigate to="/login" replace />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/signup" element={<SignupPage />} />
        <Route
          path="/home"
          element={
            <ProtectedRoute>
              <TimelinePage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/posts/:postId"
          element={
            <ProtectedRoute>
              <PostDetailPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/users/:userId"
          element={
            <ProtectedRoute>
              <ProfilePage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/users/:userId/edit"
          element={
            <ProtectedRoute>
              <ProfileEditPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/users/:userId/followers"
          element={
            <ProtectedRoute>
              <FollowListPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/users/:userId/following"
          element={
            <ProtectedRoute>
              <FollowListPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/search"
          element={
            <ProtectedRoute>
              <SearchPage />
            </ProtectedRoute>
          }
        />
        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </Suspense>
  );
}

export default App;
