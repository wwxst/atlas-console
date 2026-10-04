import { requireApiData } from '@/services/api'
import type { ApiResult, PageResult } from '@/services/api'
import { http } from '@/services/http'

export type UserStatus = 0 | 1

export interface User {
  id: number
  phone?: string
  email?: string
  nickname?: string
  avatar?: string
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

export async function getUsers(query: UserQuery): Promise<PageResult<User>> {
  const response = await http.get<ApiResult<PageResult<User>>>('/sys-user/users', {
    params: query,
  })

  return requireApiData(response.data)
}
