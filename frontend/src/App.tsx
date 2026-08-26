import { Navigate, Route, Routes } from "react-router-dom";
import { LoginPage } from "./pages/LoginPage";
import { SignupPage } from "./pages/SignupPage";
import { TimelinePage } from "./pages/TimelinePage";
import { PostDetailPage } from "./pages/PostDetailPage";
import { ProfilePage } from "./pages/ProfilePage";
import { ProfileEditPage } from "./pages/ProfileEditPage";
import { FollowListPage } from "./pages/FollowListPage";
import { SearchPage } from "./pages/SearchPage";
import { NotFoundPage } from "./pages/NotFoundPage";
import { ProtectedRoute } from "./routes/ProtectedRoute";

/**
 * 画面は静的 import のままにしている（React.lazy によるコード分割を行わない）。
 *
 * 一度 React.lazy へ変更して計測したが、**遅くなったため取り消した**。
 * 詳細は docs/perf-test-report.md 14-3。
 *
 * 理由:
 *   このアプリの画面コードは9画面すべて合わせても約33kB(gzip 約11kB)しかなく、
 *   バンドルの大部分(222kB)は React / React Router / TanStack Query が占める。
 *   フレームワークは全画面で必要なので分割しても遅延できない。
 *
 *   一方で遅延読み込みには往復が1回増える代償がある。
 *   遅延チャンクは index.js をダウンロードして実行するまで「必要だ」と分からないため、
 *   最初のバッチと並列にならず、直列で後ろに付く。
 *
 *   実測(モバイル・CPU 4x・Fast 4G、キャッシュ温状態):
 *     静的 import : LCP 598ms。資産3本が 227ms に並列で開始
 *     React.lazy  : LCP 914ms。LoginPage と useMutation が 516ms まで開始されない
 *
 *   遅延できる量(gzip 11kB)が、増える往復1回分の時間に見合わない。
 *
 * 見直すべき条件:
 *   画面固有のコードが大きく育った場合(重いエディタや地図ライブラリを
 *   特定画面だけで使う等)は再検討する価値がある。判断は必ず実測で行うこと。
 */
function App() {
  return (
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
  );
}

export default App;
