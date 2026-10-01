export interface RegisterRequest {
  email: string;
  password: string;
  fullName: string;
  phone?: string | null;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RefreshRequest {
  refreshToken: string;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
}

export interface UserResponse {
  id: number;
  email: string;
  fullName: string;
  phone: string | null;
  role: string;
  createdAt: string;
}

/**
 * The single API error envelope produced by the backend `GlobalExceptionHandler`.
 *
 * `code` carries the `ErrorCode` enum value (VALIDATION_FAILED, UNAUTHORIZED,
 * FORBIDDEN, NOT_FOUND, CONFLICT, RATE_LIMITED, ACCOUNT_SUSPENDED,
 * VENDOR_NOT_APPROVED) and `validation` carries the per-field map that only a
 * 400 bean-validation failure populates. Both are optional because Spring
 * Security's entry point and access-denied handler omit them.
 */
export interface ErrorResponse {
  timestamp?: string;
  status: number;
  error?: string;
  code?: string | null;
  message: string;
  path?: string;
  validation?: Record<string, string> | null;
}

export interface AuthState {
  accessToken: string | null;
  refreshToken: string | null;
  user: UserResponse | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  error: string | null;
}

export interface LoginFormData {
  email: string;
  password: string;
}

export interface RegisterFormData {
  fullName: string;
  email: string;
  phone: string;
  password: string;
  confirmPassword: string;
}
