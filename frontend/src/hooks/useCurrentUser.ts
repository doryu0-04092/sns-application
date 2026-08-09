import { useQuery } from "@tanstack/react-query";
import { me } from "../api/auth";
import { meKeys } from "../api/queryKeys";

export function useCurrentUser() {
  return useQuery({
    queryKey: meKeys.all,
    queryFn: me,
    retry: false,
  });
}
