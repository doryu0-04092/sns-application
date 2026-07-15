const API_BASE_URL = import.meta.env.VITE_API_BASE_URL;

export class ApiError extends Error {
  code: string;
  status: number;

  constructor(code: string, message: string, status: number) {
    super(message);
    this.code = code;
    this.status = status;
  }
}

interface ErrorEnvelope {
  error: { code: string; message: string };
}

interface DataEnvelope<T> {
  data: T;
}

export async function apiFetch<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
      ...init?.headers,
    },
  });

  const body = (await res.json()) as DataEnvelope<T> | ErrorEnvelope;

  if (!res.ok) {
    const { code, message } = (body as ErrorEnvelope).error;
    throw new ApiError(code, message, res.status);
  }

  return (body as DataEnvelope<T>).data;
}
