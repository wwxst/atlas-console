import { requireApiData, requireApiSuccess } from '../../services/api.ts'
import type { ApiResult, PageResult } from '../../services/api.ts'
import { http } from '../../services/http.ts'

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

export class PartialSysUserUpdateError extends Error {
  readonly savedUser: SysUser

  constructor(savedUser: SysUser, cause: unknown) {
    super('系统用户资料已保存，但头像上传失败，请重试头像上传。', { cause })
    this.name = 'PartialSysUserUpdateError'
    this.savedUser = savedUser
  }
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

export async function updateSysUserWithAvatar(id: number, input: UpdateSysUserInput, avatarFile?: File): Promise<SysUser> {
  const savedUser = await updateSysUser(id, input)
  if (!avatarFile) return savedUser
  try {
    return await uploadSysUserAvatar(savedUser.id, avatarFile)
  } catch (error) {
    throw new PartialSysUserUpdateError(savedUser, error)
  }
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
