export interface User {
  id: number;
  email: string;
  displayName: string;
  bio: string | null;
  avatarUrl: string | null;
}

export interface SignupPayload {
  email: string;
  password: string;
  displayName: string;
}

export interface LoginPayload {
  email: string;
  password: string;
}
