import { requireApiData, requireApiDataWithMessage, requireApiSuccess } from './api.ts'
import type { ApiResult, PageResult } from './api.ts'
import { http } from './http.ts'

export const STORAGE_SETTINGS_QUERY_KEY = ['storage-settings'] as const
export const OSS_CREDENTIALS_QUERY_KEY = ['oss-credentials'] as const
export const STORAGE_SPACES_QUERY_KEY = ['storage-spaces'] as const

export type StorageType = 'local' | 'aliyun'
export type StorageAccessPermission = 'public-read' | 'public-read-write'
export interface StorageSettings { storageType: StorageType }
export interface OssCredentialStatus { configured: boolean }
export interface OssCredentials { accessKeyId: string; accessKeySecret: string }
export interface StorageSpace {
  id: number
  bucketName: string
  regionId: string
  accessDomain: string | null
  isDefault: boolean
  createdAt: string
  updatedAt: string
}
export interface BindStorageSpaceInput { bucketName: string; regionId: string; accessPermission: StorageAccessPermission }
export interface StorageSpaceDomainInput { accessDomain: string | null }

const basePath = '/sys-user/storage'

export async function getStorageSettings(): Promise<StorageSettings> {
  return requireApiData((await http.get<ApiResult<StorageSettings>>(`${basePath}/settings`)).data)
}

export async function updateStorageSettings(input: StorageSettings): Promise<{ data: StorageSettings; message: string | null }> {
  return requireApiDataWithMessage((await http.put<ApiResult<StorageSettings>>(`${basePath}/settings`, input)).data)
}

export async function getOssCredentials(): Promise<OssCredentialStatus> {
  return requireApiData((await http.get<ApiResult<OssCredentialStatus>>(`${basePath}/oss/credentials`)).data)
}

export async function updateOssCredentials(input: OssCredentials): Promise<OssCredentialStatus> {
  return requireApiData((await http.put<ApiResult<OssCredentialStatus>>(`${basePath}/oss/credentials`, input)).data)
}

export async function getStorageSpaces(params: { page: number; pageSize: number }): Promise<PageResult<StorageSpace>> {
  return requireApiData((await http.get<ApiResult<PageResult<StorageSpace>>>(`${basePath}/spaces`, { params })).data)
}

export async function bindStorageSpace(input: BindStorageSpaceInput): Promise<StorageSpace> {
  return requireApiData((await http.post<ApiResult<StorageSpace>>(`${basePath}/spaces`, input)).data)
}

export async function syncStorageSpaces(): Promise<{ syncedCount: number }> {
  return requireApiData((await http.post<ApiResult<{ syncedCount: number }>>(`${basePath}/spaces/sync`)).data)
}

export async function setDefaultStorageSpace(id: number): Promise<StorageSpace> {
  return requireApiData((await http.put<ApiResult<StorageSpace>>(`${basePath}/spaces/${id}/default`)).data)
}

export async function updateStorageSpaceDomain(id: number, input: StorageSpaceDomainInput): Promise<StorageSpace> {
  return requireApiData((await http.patch<ApiResult<StorageSpace>>(`${basePath}/spaces/${id}/domain`, input)).data)
}

export async function deleteStorageSpace(id: number): Promise<void> {
  requireApiSuccess((await http.delete<ApiResult<null>>(`${basePath}/spaces/${id}`)).data)
}
