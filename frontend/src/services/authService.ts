import api from './api'

export interface AuthResponse {
  token: string
  userId: string
  email: string
  name: string
  role: 'PATIENT' | 'PHYSIO'
}

export const authService = {
  login: (email: string, password: string) =>
    api.post<AuthResponse>('/auth/login', { email, password }).then(r => r.data),

  register: (email: string, password: string, name: string, phone?: string, role = 'PATIENT') =>
    api.post<AuthResponse>('/auth/register', { email, password, name, phone, role }).then(r => r.data),
}
