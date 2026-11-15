export interface LoginRequest {
  name: string | null;
  email: string;
  password: string;
  role: string | null;
}
