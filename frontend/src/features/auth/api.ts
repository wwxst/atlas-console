import { requireApiData, requireApiSuccess } from '../../services/api.ts'
import type { ApiResult } from '../../services/api.ts'
import { http } from '../../services/http.ts'

export const CURRENT_SYS_USER_QUERY_KEY = ['current-sys-user'] as const

export interface SysUserInfo {
  id: number
  username: string
  nickname: string
}

export interface LoginInput {
  username: string
  password: string
}

export interface TokenPair {
  accessToken: string
  refreshToken: string
}

export async function loginSysUser(input: LoginInput): Promise<TokenPair> {
  const response = await http.post<ApiResult<TokenPair>>('/sys-user/auth/login', input)
  return requireApiData(response.data)
}

export async function getCurrentSysUser(): Promise<SysUserInfo> {
  const response = await http.get<ApiResult<SysUserInfo>>('/sys-user/auth/me')
  return requireApiData(response.data)
}

export async function logoutSysUser(): Promise<void> {
  const response = await http.post<ApiResult<null>>('/sys-user/auth/logout')
  requireApiSuccess(response.data)
}
