import { useQuery } from "@tanstack/react-query";
import { me } from "../api/auth";

export function useCurrentUser() {
  return useQuery({
    queryKey: ["me"],
    queryFn: me,
    retry: false,
  });
}
