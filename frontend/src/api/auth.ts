import { apiFetch } from "./client";
import type { LoginPayload, SignupPayload, User } from "../types/auth";

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

/**
 * POST /api/auth/signup does not set the auth cookie by itself (only
 * /api/auth/login does, per docs/api-design.md), but docs/screens.md's
 * screen transition (S-02 登録成功 -> S-03) expects the user to already
 * be logged in right after signing up. Chain a login call with the same
 * credentials to bridge that gap.
 */
export async function signupAndLogin(payload: SignupPayload): Promise<User> {
  await signup(payload);
  return login({ email: payload.email, password: payload.password });
}

export function logout(): Promise<null> {
  return apiFetch<null>("/auth/logout", { method: "POST" });
}

export function me(): Promise<User> {
  return apiFetch<User>("/auth/me");
}
