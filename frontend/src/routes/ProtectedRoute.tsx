import type { ReactNode } from "react";
import { Navigate } from "react-router-dom";
import { useCurrentUser } from "../hooks/useCurrentUser";

export function ProtectedRoute({ children }: { children: ReactNode }) {
  const { isLoading, isError } = useCurrentUser();

  if (isLoading) {
    return <div className="p-8 text-center text-gray-500">読み込み中...</div>;
  }

  if (isError) {
    return <Navigate to="/login" replace />;
  }

  return <>{children}</>;
}
