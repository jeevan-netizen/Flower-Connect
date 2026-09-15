import api from "@/shared/lib/api";
import type {
  AuthResponse,
  LoginRequest,
  RegisterRequest,
  RefreshRequest,
  UserResponse,
} from "@/features/auth/types";

export async function register(data: RegisterRequest): Promise<AuthResponse> {
  const response = await api.post<AuthResponse>("/auth/register", data);
  return response.data;
}

export async function login(data: LoginRequest): Promise<AuthResponse> {
  const response = await api.post<AuthResponse>("/auth/login", data);
  return response.data;
}

export async function refresh(data: RefreshRequest): Promise<AuthResponse> {
  const response = await api.post<AuthResponse>("/auth/refresh", data);
  return response.data;
}

export async function logout(data: RefreshRequest): Promise<void> {
  await api.post("/auth/logout", data);
}

export async function fetchCurrentUser(): Promise<UserResponse> {
  const response = await api.get<UserResponse>("/users/me");
  return response.data;
}
