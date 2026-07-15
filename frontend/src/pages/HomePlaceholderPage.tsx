import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";
import { logout } from "../api/auth";
import { useCurrentUser } from "../hooks/useCurrentUser";

export function HomePlaceholderPage() {
  const { data: user } = useCurrentUser();
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const mutation = useMutation({
    mutationFn: logout,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["me"] });
      navigate("/login");
    },
  });

  return (
    <div className="mx-auto mt-16 max-w-sm p-6 text-center">
      <p className="text-lg">ログインできました、{user?.displayName}さん。</p>
      <button
        type="button"
        onClick={() => mutation.mutate()}
        disabled={mutation.isPending}
        className="mt-6 rounded bg-gray-200 px-4 py-2 disabled:opacity-50"
      >
        ログアウト
      </button>
    </div>
  );
}
