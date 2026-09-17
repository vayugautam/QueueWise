import { createContext, useContext, useMemo, useState, type ReactNode } from 'react'
import { apiClient } from '../api/client'
import { normalizeApiError, type ApiErrorBody } from '../api/errors'

export interface AuthUser {
  id: string
  organizationId: string
  firstName?: string
  lastName?: string
  email?: string
  phone?: string
  roles: string[]
}

interface AuthResponse {
  accessToken: string
  tokenType: string
  expiresAt: string
  user: AuthUser
}

interface LoginInput {
  organizationId: string
  login: string
  password: string
}

interface AuthContextValue {
  user: AuthUser | null
  isAuthenticated: boolean
  isLoading: boolean
  error: ApiErrorBody | null
  login: (input: LoginInput) => Promise<void>
  logout: () => void
  clearError: () => void
}

const ACCESS_TOKEN_KEY = 'queuewise_access_token'
const USER_KEY = 'queuewise_user'
const AuthContext = createContext<AuthContextValue | null>(null)

function readUser(): AuthUser | null {
  const stored = sessionStorage.getItem(USER_KEY)
  if (!stored) return null
  try {
    return JSON.parse(stored) as AuthUser
  } catch {
    sessionStorage.removeItem(USER_KEY)
    return null
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(readUser)
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState<ApiErrorBody | null>(null)

  const value = useMemo<AuthContextValue>(() => ({
    user,
    isAuthenticated: Boolean(user && sessionStorage.getItem(ACCESS_TOKEN_KEY)),
    isLoading,
    error,
    clearError: () => setError(null),
    logout: () => {
      sessionStorage.removeItem(ACCESS_TOKEN_KEY)
      sessionStorage.removeItem(USER_KEY)
      setUser(null)
      setError(null)
    },
    login: async (input) => {
      setIsLoading(true)
      setError(null)
      try {
        const response = await apiClient.post<AuthResponse>('/auth/login', input)
        sessionStorage.setItem(ACCESS_TOKEN_KEY, response.data.accessToken)
        sessionStorage.setItem(USER_KEY, JSON.stringify(response.data.user))
        setUser(response.data.user)
      } catch (requestError) {
        const normalized = normalizeApiError(requestError)
        setError({ status: normalized.status, error: normalized.code, message: normalized.message, correlationId: normalized.correlationId, errors: normalized.fieldErrors })
        throw normalized
      } finally {
        setIsLoading(false)
      }
    },
  }), [error, isLoading, user])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used within AuthProvider')
  return context
}
