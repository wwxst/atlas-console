import { requireApiData, requireApiSuccess } from '@/services/api'
import type { ApiResult, PageResult } from '@/services/api'
import { http } from '@/services/http'

export type SysUserStatus = 0 | 1

export interface SysUser {
  id: number
  username: string
  nickname: string
  avatar?: string | null
  status: SysUserStatus
  createdAt: string
  updatedAt: string
}

export interface SysUserQuery {
  page: number
  pageSize: number
  keyword?: string
  status?: SysUserStatus
  createdAtOrder?: 'asc' | 'desc'
}

export interface CreateSysUserInput {
  username: string
  password: string
  nickname: string
  status: SysUserStatus
}

export interface UpdateSysUserInput {
  username: string
  nickname: string
}

export async function getSysUsers(query: SysUserQuery): Promise<PageResult<SysUser>> {
  const response = await http.get<ApiResult<PageResult<SysUser>>>('/sys-user/sys-users', {
    params: query,
  })

  return requireApiData(response.data)
}

export async function updateSysUserStatus(id: number, status: SysUserStatus): Promise<void> {
  const response = await http.patch<ApiResult<null>>(`/sys-user/sys-users/${id}/status`, { status })
  requireApiSuccess(response.data)
}

export async function createSysUser(input: CreateSysUserInput): Promise<SysUser> {
  const response = await http.post<ApiResult<SysUser>>('/sys-user/sys-users', input)
  return requireApiData(response.data)
}

export async function updateSysUser(id: number, input: UpdateSysUserInput): Promise<SysUser> {
  const response = await http.put<ApiResult<SysUser>>(`/sys-user/sys-users/${id}`, input)
  return requireApiData(response.data)
}

export async function resetSysUserPassword(id: number, password: string): Promise<void> {
  const response = await http.patch<ApiResult<null>>(`/sys-user/sys-users/${id}/password`, { password })
  requireApiSuccess(response.data)
}

export async function deleteSysUser(id: number): Promise<void> {
  const response = await http.delete<ApiResult<null>>(`/sys-user/sys-users/${id}`)
  requireApiSuccess(response.data)
}

export async function uploadSysUserAvatar(id: number, file: File): Promise<SysUser> {
  const formData = new FormData()
  formData.append('file', file)
  const response = await http.post<ApiResult<SysUser>>(`/sys-user/sys-users/${id}/avatar`, formData)
  return requireApiData(response.data)
}
