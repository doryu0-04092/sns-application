import { useEffect, useMemo, useRef, useState } from "react";
import { Navigate, useNavigate, useParams } from "react-router-dom";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { AppHeader } from "../components/AppHeader";
import { Avatar } from "../components/Avatar";
import { useCurrentUser } from "../hooks/useCurrentUser";
import { useCharCount } from "../hooks/useCharCount";
import { updateProfile } from "../api/users";
import { usersKeys } from "../api/queryKeys";
import { ApiError } from "../api/client";

export function ProfileEditPage() {
  const { userId } = useParams();
  const id = Number(userId);
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { data: currentUser, isLoading } = useCurrentUser();

  const [displayName, setDisplayName] = useState("");
  const [bio, setBio] = useState("");
  const [avatarFile, setAvatarFile] = useState<File | null>(null);
  const [initialized, setInitialized] = useState(false);
  const avatarInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (currentUser && !initialized) {
      setDisplayName(currentUser.displayName);
      setBio(currentUser.bio ?? "");
      setInitialized(true);
    }
  }, [currentUser, initialized]);

  const avatarPreview = useMemo(() => (avatarFile ? URL.createObjectURL(avatarFile) : null), [avatarFile]);
  useEffect(() => {
    return () => {
      if (avatarPreview) URL.revokeObjectURL(avatarPreview);
    };
  }, [avatarPreview]);

  const { remaining, isOver } = useCharCount(bio, 500);

  const mutation = useMutation({
    mutationFn: () => updateProfile({ displayName: displayName.trim(), bio, avatar: avatarFile }),
    onSuccess: (updated) => {
      queryClient.setQueryData(["me"], updated);
      queryClient.invalidateQueries({ queryKey: usersKeys.detail(updated.id) });
      navigate(`/users/${updated.id}`);
    },
  });

  if (isLoading) {
    return (
      <div className="mx-auto min-h-screen max-w-xl border-x border-gray-200 bg-white">
        <AppHeader />
        <p className="p-8 text-center text-gray-500">読み込み中...</p>
      </div>
    );
  }

  if (!currentUser || currentUser.id !== id) {
    return <Navigate to={`/users/${id}`} replace />;
  }

  const trimmedName = displayName.trim();
  const canSubmit = trimmedName.length > 0 && trimmedName.length <= 100 && !isOver && !mutation.isPending;

  const handleSubmit = (event: React.FormEvent) => {
    event.preventDefault();
    if (!canSubmit) return;
    mutation.mutate();
  };

  return (
    <div className="mx-auto min-h-screen max-w-xl border-x border-gray-200 bg-white">
      <AppHeader />
      <div className="border-b border-gray-200 px-4 py-2.5 text-sm font-bold text-gray-500">プロフィールを編集</div>
      <form onSubmit={handleSubmit} className="px-4 py-4">
        <div className="flex items-center gap-4">
          <Avatar
            avatarUrl={avatarPreview ?? currentUser.avatarUrl}
            displayName={displayName || currentUser.displayName}
            className="h-16 w-16 text-xl"
          />
          <button
            type="button"
            onClick={() => avatarInputRef.current?.click()}
            className="rounded-full border border-gray-300 px-3 py-1.5 text-sm font-semibold hover:bg-gray-50"
          >
            アイコンを変更
          </button>
          <input
            ref={avatarInputRef}
            type="file"
            accept="image/jpeg,image/png,image/webp,image/gif"
            onChange={(e) => setAvatarFile(e.target.files?.[0] ?? null)}
            className="hidden"
          />
        </div>

        <label className="mt-4 block text-sm font-semibold text-gray-700">表示名</label>
        <input
          value={displayName}
          onChange={(e) => setDisplayName(e.target.value)}
          maxLength={100}
          className="mt-1 w-full rounded border border-gray-300 p-2 text-base"
        />

        <label className="mt-4 block text-sm font-semibold text-gray-700">自己紹介</label>
        <textarea
          value={bio}
          onChange={(e) => setBio(e.target.value)}
          rows={4}
          className="mt-1 w-full rounded border border-gray-300 p-2 text-sm"
        />
        <div className={`mt-1 text-right text-xs ${isOver ? "text-red-600" : "text-gray-500"}`}>{remaining}文字</div>

        {mutation.isError && (
          <p className="mt-2 text-sm text-red-600">
            {mutation.error instanceof ApiError ? mutation.error.message : "更新に失敗しました"}
          </p>
        )}

        <div className="mt-4 flex justify-end gap-2">
          <button
            type="button"
            onClick={() => navigate(`/users/${id}`)}
            className="rounded-full border border-gray-300 px-4 py-1.5 text-sm"
          >
            キャンセル
          </button>
          <button
            type="submit"
            disabled={!canSubmit}
            className="rounded-full bg-blue-600 px-4 py-1.5 text-sm font-bold text-white disabled:opacity-50"
          >
            保存する
          </button>
        </div>
      </form>
    </div>
  );
}
