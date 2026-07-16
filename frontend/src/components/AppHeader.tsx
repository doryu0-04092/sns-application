import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useNavigate, Link } from "react-router-dom";
import { logout } from "../api/auth";
import { useCurrentUser } from "../hooks/useCurrentUser";

export function AppHeader() {
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
    <header className="sticky top-0 z-10 flex items-center justify-between border-b border-gray-200 bg-white/90 px-4 py-3 backdrop-blur">
      <Link to="/home" className="text-lg font-extrabold">
        SNS App
      </Link>
      <div className="flex items-center gap-3">
        <div className="flex items-center gap-2">
          <div className="flex h-8 w-8 items-center justify-center rounded-full bg-gradient-to-br from-purple-400 to-blue-400 text-sm font-bold text-white">
            {user?.displayName?.charAt(0) ?? "?"}
          </div>
          <span className="text-sm font-semibold">{user?.displayName}</span>
        </div>
        <button
          type="button"
          onClick={() => mutation.mutate()}
          disabled={mutation.isPending}
          className="text-sm text-gray-500 hover:text-gray-800 disabled:opacity-50"
        >
          ログアウト
        </button>
      </div>
    </header>
  );
}
