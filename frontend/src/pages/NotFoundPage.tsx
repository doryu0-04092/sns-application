import { Link } from "react-router-dom";

/**
 * 定義済みルートに一致しないパスで表示する404画面。
 * 未ログイン状態でも到達しうるため、認証情報を参照するAppHeaderは置かない。
 */
export function NotFoundPage() {
  return (
    <div className="mx-auto flex min-h-screen max-w-xl flex-col items-center justify-center gap-4 border-x border-gray-200 bg-white px-4 text-center">
      <p className="text-5xl font-extrabold text-gray-300">404</p>
      <h1 className="text-lg font-bold">ページが見つかりません</h1>
      <p className="text-sm text-gray-500">
        お探しのページは削除されたか、URLが間違っている可能性があります。
      </p>
      <Link
        to="/home"
        className="rounded-full bg-blue-500 px-5 py-2 text-sm font-bold text-white hover:bg-blue-600"
      >
        タイムラインへ戻る
      </Link>
    </div>
  );
}
