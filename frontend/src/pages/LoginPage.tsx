import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useNavigate, Link } from "react-router-dom";
import { login } from "../api/auth";
import { ApiError } from "../api/client";
import { meKeys } from "../api/queryKeys";

export function LoginPage() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const mutation = useMutation({
    mutationFn: login,
    onSuccess: (user) => {
      // 新しいセッションを、前のセッションの残りが無い状態から始める。
      //
      // ログアウト側でも消しているが、それだけでは足りない。トークンが失効して
      // ログイン画面へ送られた場合はログアウトを通らないため、その経路では
      // 前のユーザーのキャッシュが残ったままここへ到達する。
      queryClient.clear();
      queryClient.setQueryData(meKeys.all, user);
      navigate("/home");
    },
  });

  const handleSubmit = (event: React.FormEvent) => {
    event.preventDefault();
    mutation.mutate({ email, password });
  };

  return (
    <div className="mx-auto mt-16 max-w-sm p-6">
      <h1 className="mb-6 text-2xl font-bold">ログイン</h1>
      <form onSubmit={handleSubmit} className="flex flex-col gap-4">
        <label className="flex flex-col gap-1">
          <span className="text-sm text-gray-600">メールアドレス</span>
          <input
            id="login-email"
            name="email"
            type="email"
            autoComplete="username"
            required
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            className="rounded border border-gray-300 px-3 py-2"
          />
        </label>
        <label className="flex flex-col gap-1">
          <span className="text-sm text-gray-600">パスワード</span>
          <input
            id="login-password"
            name="password"
            type="password"
            autoComplete="current-password"
            required
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            className="rounded border border-gray-300 px-3 py-2"
          />
        </label>
        {mutation.isError && (
          <p className="text-sm text-red-600">
            {mutation.error instanceof ApiError ? mutation.error.message : "ログインに失敗しました"}
          </p>
        )}
        <button
          type="submit"
          disabled={mutation.isPending}
          className="rounded bg-blue-600 px-4 py-2 text-white disabled:opacity-50"
        >
          ログイン
        </button>
      </form>
      <p className="mt-4 text-sm text-gray-600">
        アカウントをお持ちでない方は<Link to="/signup" className="text-blue-600">新規登録</Link>
      </p>
    </div>
  );
}
