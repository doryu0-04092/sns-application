import { useEffect, useMemo, useRef, useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { createPost } from "../api/posts";
import { ApiError } from "../api/client";
import { useCharCount } from "../hooks/useCharCount";
import { useCurrentUser } from "../hooks/useCurrentUser";
import { Avatar } from "./Avatar";

const MAX_IMAGES = 4;

export function PostComposer() {
  const [body, setBody] = useState("");
  const [images, setImages] = useState<File[]>([]);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const queryClient = useQueryClient();
  const { data: currentUser } = useCurrentUser();
  const { remaining, isOver } = useCharCount(body);

  const previews = useMemo(() => images.map((file) => URL.createObjectURL(file)), [images]);
  useEffect(() => {
    return () => previews.forEach((url) => URL.revokeObjectURL(url));
  }, [previews]);

  const mutation = useMutation({
    mutationFn: () => createPost(body, images),
    onSuccess: () => {
      setBody("");
      setImages([]);
      queryClient.resetQueries({ queryKey: ["posts", "list"] });
    },
  });

  const canSubmit = body.trim().length > 0 && !isOver && !mutation.isPending;

  const handleSubmit = (event: React.FormEvent) => {
    event.preventDefault();
    if (!canSubmit) return;
    mutation.mutate();
  };

  const handleFilesSelected = (event: React.ChangeEvent<HTMLInputElement>) => {
    const selected = Array.from(event.target.files ?? []);
    setImages((prev) => [...prev, ...selected].slice(0, MAX_IMAGES));
    event.target.value = "";
  };

  const removeImage = (index: number) => {
    setImages((prev) => prev.filter((_, i) => i !== index));
  };

  return (
    <form onSubmit={handleSubmit} className="flex gap-3 border-b border-gray-200 px-4 py-3">
      <Avatar avatarUrl={currentUser?.avatarUrl} displayName={currentUser?.displayName ?? "投"} />
      <div className="flex-1">
        <textarea
          value={body}
          onChange={(e) => setBody(e.target.value)}
          placeholder="いまどうしてる?"
          rows={2}
          className="w-full resize-none border-none text-base outline-none"
        />
        {previews.length > 0 && (
          <div className="mt-2 grid grid-cols-2 gap-2">
            {previews.map((url, index) => (
              <div key={url} className="relative">
                <img src={url} alt="" className="h-32 w-full rounded-lg object-cover" />
                <button
                  type="button"
                  onClick={() => removeImage(index)}
                  className="absolute right-1 top-1 flex h-6 w-6 items-center justify-center rounded-full bg-black/60 text-xs text-white"
                  aria-label="画像を削除"
                >
                  ✕
                </button>
              </div>
            ))}
          </div>
        )}
        {mutation.isError && (
          <p className="text-sm text-red-600">
            {mutation.error instanceof ApiError ? mutation.error.message : "投稿に失敗しました"}
          </p>
        )}
        <div className="mt-2 flex items-center justify-between gap-3">
          <button
            type="button"
            onClick={() => fileInputRef.current?.click()}
            disabled={images.length >= MAX_IMAGES}
            className="text-lg text-blue-600 disabled:opacity-40"
            aria-label="画像を添付"
          >
            🖼
          </button>
          <input
            ref={fileInputRef}
            type="file"
            accept="image/jpeg,image/png,image/webp,image/gif"
            multiple
            onChange={handleFilesSelected}
            className="hidden"
          />
          <div className="ml-auto flex items-center gap-3">
            <span className={`text-xs ${isOver ? "text-red-600" : "text-gray-500"}`}>{remaining}文字</span>
            <button
              type="submit"
              disabled={!canSubmit}
              className="rounded-full bg-blue-600 px-4 py-2 text-sm font-bold text-white disabled:opacity-50"
            >
              投稿する
            </button>
          </div>
        </div>
      </div>
    </form>
  );
}
