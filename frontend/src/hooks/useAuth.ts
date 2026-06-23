import { useState, useCallback } from 'react'
import { authService, AuthResponse } from '../services/authService'

interface AuthState {
  token: string | null
  userId: string | null
  role: string | null
  name: string | null
}

function getStoredAuth(): AuthState {
  return {
    token:  localStorage.getItem('physiolink_token'),
    userId: localStorage.getItem('physiolink_userId'),
    role:   localStorage.getItem('physiolink_role'),
    name:   localStorage.getItem('physiolink_name'),
  }
}

function storeAuth(res: AuthResponse) {
  localStorage.setItem('physiolink_token',  res.token)
  localStorage.setItem('physiolink_userId', res.userId)
  localStorage.setItem('physiolink_role',   res.role)
  localStorage.setItem('physiolink_name',   res.name)
}

function clearAuth() {
  localStorage.removeItem('physiolink_token')
  localStorage.removeItem('physiolink_userId')
  localStorage.removeItem('physiolink_role')
  localStorage.removeItem('physiolink_name')
}

export function useAuth() {
  const [auth, setAuth] = useState<AuthState>(getStoredAuth)

  const login = useCallback(async (email: string, password: string) => {
    const res = await authService.login(email, password)
    storeAuth(res)
    setAuth({ token: res.token, userId: res.userId, role: res.role, name: res.name })
    return res
  }, [])

  const register = useCallback(async (
    email: string, password: string, name: string, phone?: string, role = 'PATIENT'
  ) => {
    const res = await authService.register(email, password, name, phone, role)
    storeAuth(res)
    setAuth({ token: res.token, userId: res.userId, role: res.role, name: res.name })
    return res
  }, [])

  const logout = useCallback(() => {
    clearAuth()
    setAuth({ token: null, userId: null, role: null, name: null })
  }, [])

  return { ...auth, isAuthenticated: !!auth.token, login, register, logout }
}
