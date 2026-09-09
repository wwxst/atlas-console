import { requireApiData } from '@/services/api'
import type { ApiResult, PageResult } from '@/services/api'
import { http } from '@/services/http'

export type SysUserStatus = 0 | 1

export interface SysUser {
  id: number
  username: string
  nickname: string
  status: SysUserStatus
  createdAt: string
  updatedAt: string
}

export interface SysUserQuery {
  page: number
  pageSize: number
  keyword?: string
  status?: SysUserStatus
}

export async function getSysUsers(query: SysUserQuery): Promise<PageResult<SysUser>> {
  const response = await http.get<ApiResult<PageResult<SysUser>>>('/sys-user/sys-users', {
    params: query,
  })

  return requireApiData(response.data)
}
