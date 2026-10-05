import { requireApiData, requireApiSuccess } from '../../services/api.ts'
import type { ApiResult, PageResult } from '../../services/api.ts'
import { http } from '../../services/http.ts'

export type UserStatus = 0 | 1

export interface User {
  id: number
  userNo: string
  phone?: string | null
  email?: string | null
  nickname?: string | null
  avatar?: string | null
  status: UserStatus
  createdAt: string
  updatedAt: string
}

export interface UserQuery {
  page: number
  pageSize: number
  keyword?: string
  status?: UserStatus
  createdAtOrder?: 'asc' | 'desc'
}

export interface UpdateUserInput {
  nickname: string
  phone: string | null
  email: string | null
}

export async function updateUser(id: number, input: UpdateUserInput): Promise<User> {
  const response = await http.put<ApiResult<User>>(`/sys-user/users/${id}`, input)
  return requireApiData(response.data)
}

export async function updateUserStatus(id: number, status: UserStatus): Promise<void> {
  const response = await http.patch<ApiResult<null>>(`/sys-user/users/${id}/status`, { status })
  requireApiSuccess(response.data)
}

export async function getUsers(query: UserQuery): Promise<PageResult<User>> {
  const response = await http.get<ApiResult<PageResult<User>>>('/sys-user/users', {
    params: query,
  })

  return requireApiData(response.data)
}
