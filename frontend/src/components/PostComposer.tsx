import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { createPost } from "../api/posts";
import { ApiError } from "../api/client";
import { useCharCount } from "../hooks/useCharCount";

export function PostComposer() {
  const [body, setBody] = useState("");
  const queryClient = useQueryClient();
  const { remaining, isOver } = useCharCount(body);

  const mutation = useMutation({
    mutationFn: () => createPost(body),
    onSuccess: () => {
      setBody("");
      queryClient.resetQueries({ queryKey: ["posts", "list"] });
    },
  });

  const canSubmit = body.trim().length > 0 && !isOver && !mutation.isPending;

  const handleSubmit = (event: React.FormEvent) => {
    event.preventDefault();
    if (!canSubmit) return;
    mutation.mutate();
  };

  return (
    <form onSubmit={handleSubmit} className="flex gap-3 border-b border-gray-200 px-4 py-3">
      <div className="flex h-11 w-11 flex-shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-purple-400 to-blue-400 font-bold text-white">
        投
      </div>
      <div className="flex-1">
        <textarea
          value={body}
          onChange={(e) => setBody(e.target.value)}
          placeholder="いまどうしてる?"
          rows={2}
          className="w-full resize-none border-none text-base outline-none"
        />
        {mutation.isError && (
          <p className="text-sm text-red-600">
            {mutation.error instanceof ApiError ? mutation.error.message : "投稿に失敗しました"}
          </p>
        )}
        <div className="mt-2 flex items-center justify-end gap-3">
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
    </form>
  );
}
