import { requireApiData } from '@/services/api'
import type { ApiResult } from '@/services/api'
import { http } from '@/services/http'

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
  await http.post<ApiResult<null>>('/sys-user/auth/logout')
}
