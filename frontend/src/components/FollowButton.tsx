import { useMutation, useQueryClient } from "@tanstack/react-query";
import { followUser, unfollowUser } from "../api/follows";
import { flipFollowInCaches, postsKeys } from "../api/queryKeys";
import { ApiError } from "../api/client";

interface FollowButtonProps {
  userId: number;
  isFollowing: boolean;
  className?: string;
}

export function FollowButton({ userId, isFollowing, className }: FollowButtonProps) {
  const queryClient = useQueryClient();

  const onSettled = (following: boolean) => {
    flipFollowInCaches(queryClient, userId, following);
    queryClient.invalidateQueries({ queryKey: postsKeys.list("following") });
  };

  const followMutation = useMutation({
    mutationFn: () => followUser(userId),
    onSuccess: () => onSettled(true),
  });

  const unfollowMutation = useMutation({
    mutationFn: () => unfollowUser(userId),
    onSuccess: () => onSettled(false),
  });

  const isPending = followMutation.isPending || unfollowMutation.isPending;
  const failedMutation = followMutation.isError ? followMutation : unfollowMutation.isError ? unfollowMutation : null;

  return (
    <span className={`inline-flex flex-col items-end ${className ?? ""}`}>
      <button
        type="button"
        onClick={() => (isFollowing ? unfollowMutation.mutate() : followMutation.mutate())}
        disabled={isPending}
        className={`rounded-full px-3 py-1 text-xs font-semibold disabled:opacity-50 ${
          isFollowing ? "border border-gray-300 text-gray-900" : "bg-gray-900 text-white"
        }`}
      >
        {isFollowing ? "フォロー中" : "フォローする"}
      </button>
      {failedMutation && (
        <span className="mt-1 text-xs text-red-600">
          {failedMutation.error instanceof ApiError ? failedMutation.error.message : "処理に失敗しました"}
        </span>
      )}
    </span>
  );
}
