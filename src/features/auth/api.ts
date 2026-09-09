import { requireApiData } from '@/services/api'
import type { ApiResult } from '@/services/api'
import { ADMIN_TOKEN_KEY, http } from '@/services/http'

export { ADMIN_TOKEN_KEY }
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

export interface SysUserLoginResult extends SysUserInfo {
  token: string
}

export async function loginSysUser(input: LoginInput): Promise<SysUserLoginResult> {
  const response = await http.post<ApiResult<SysUserLoginResult>>('/sys-user/auth/login', input)
  return requireApiData(response.data)
}

export async function getCurrentSysUser(): Promise<SysUserInfo> {
  const response = await http.get<ApiResult<SysUserInfo>>('/sys-user/auth/me')
  return requireApiData(response.data)
}
