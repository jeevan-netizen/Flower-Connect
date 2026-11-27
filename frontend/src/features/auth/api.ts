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
import type { FarmerRegisterRequest, FarmerRegisterResponse } from "@/features/farmer/types";

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

export async function registerFarmer(
  data: FarmerRegisterRequest,
): Promise<FarmerRegisterResponse> {
  const response = await api.post<FarmerRegisterResponse>("/farmer/register", data);
  return response.data;
}

export async function changePassword(data: ChangePasswordRequest): Promise<void> {
  await api.post("/users/me/password", data);
}

export interface ProfileUpdateRequest {
  fullName?: string;
  phone?: string | null;
}

export async function updateCurrentUser(data: ProfileUpdateRequest): Promise<UserResponse> {
  const response = await api.patch<UserResponse>("/users/me", data);
  return response.data;
}

export async function forgotPassword(data: ForgotPasswordRequest): Promise<void> {
  await api.post("/auth/forgot-password", data);
}

export async function resetPassword(data: ResetPasswordRequest): Promise<void> {
  await api.post("/auth/reset-password", data);
}
