import { apiFetch } from "./client";
import type { LoginPayload, SignupPayload, User } from "../types/auth";

/**
 * 新規ユーザーを登録する。
 * サーバー側で認証クッキーも発行されるため、成功時点でログイン済みになる。
 */
export function signup(payload: SignupPayload): Promise<User> {
  return apiFetch<User>("/auth/signup", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export function login(payload: LoginPayload): Promise<User> {
  return apiFetch<User>("/auth/login", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export function logout(): Promise<null> {
  return apiFetch<null>("/auth/logout", { method: "POST" });
}

export function me(): Promise<User> {
  return apiFetch<User>("/auth/me");
}
