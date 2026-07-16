import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { createComment } from "../api/comments";
import { commentsKeys, postsKeys } from "../api/queryKeys";
import { ApiError } from "../api/client";
import { useCharCount } from "../hooks/useCharCount";

interface CommentFormProps {
  postId: number;
  parentCommentId: number | null;
  submitLabel?: string;
  placeholder?: string;
  onDone?: () => void;
}

export function CommentForm({
  postId,
  parentCommentId,
  submitLabel = "返信する",
  placeholder = "返信をポスト",
  onDone,
}: CommentFormProps) {
  const [body, setBody] = useState("");
  const queryClient = useQueryClient();
  const { remaining, isOver } = useCharCount(body);

  const mutation = useMutation({
    mutationFn: () => createComment(postId, { body, parentCommentId }),
    onSuccess: () => {
      setBody("");
      queryClient.invalidateQueries({ queryKey: commentsKeys.list(postId) });
      queryClient.invalidateQueries({ queryKey: postsKeys.detail(postId) });
      queryClient.invalidateQueries({ queryKey: ["posts", "list"] });
      onDone?.();
    },
  });

  const canSubmit = body.trim().length > 0 && !isOver && !mutation.isPending;

  const handleSubmit = (event: React.FormEvent) => {
    event.preventDefault();
    if (!canSubmit) return;
    mutation.mutate();
  };

  return (
    <form onSubmit={handleSubmit}>
      <div className="flex gap-2">
        <textarea
          value={body}
          onChange={(e) => setBody(e.target.value)}
          placeholder={placeholder}
          rows={2}
          className="flex-1 rounded border border-gray-300 p-2 text-sm"
        />
        <div className="flex flex-col items-end justify-between gap-2">
          <span className={`text-xs ${isOver ? "text-red-600" : "text-gray-500"}`}>{remaining}</span>
          <button
            type="submit"
            disabled={!canSubmit}
            className="whitespace-nowrap rounded-full bg-blue-600 px-3 py-1.5 text-sm font-bold text-white disabled:opacity-50"
          >
            {submitLabel}
          </button>
        </div>
      </div>
      {mutation.isError && (
        <p className="mt-1 text-sm text-red-600">
          {mutation.error instanceof ApiError ? mutation.error.message : "投稿に失敗しました"}
        </p>
      )}
    </form>
  );
}
