import api from "@/shared/lib/api";
import type {
  AuthResponse,
  ChangePasswordRequest,
  ForgotPasswordRequest,
  LoginRequest,
  RegisterRequest,
  ResetPasswordRequest,
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

export async function refresh(): Promise<AuthResponse> {
  const response = await api.post<AuthResponse>("/auth/refresh");
  return response.data;
}

export async function logout(): Promise<void> {
  await api.post("/auth/logout");
}

export async function fetchCurrentUser(): Promise<UserResponse> {
  const response = await api.get<UserResponse>("/users/me");
  return response.data;
}

export async function changePassword(data: ChangePasswordRequest): Promise<void> {
  await api.post("/users/me/password", data);
}

export async function forgotPassword(data: ForgotPasswordRequest): Promise<void> {
  await api.post("/auth/forgot-password", data);
}

export async function resetPassword(data: ResetPasswordRequest): Promise<void> {
  await api.post("/auth/reset-password", data);
}
