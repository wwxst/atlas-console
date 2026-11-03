import { http } from '@/services/http'
import { requireApiData, type ApiResult } from '@/services/api'

export interface DeploymentStatus {
  configured: boolean
  state: string
  conclusion: string | null
  runUrl: string | null
  title: string | null
  requestId: string | null
  rollbackAvailable: boolean
  runId: number | null
  recoveryAvailable: boolean
  currentVersion: string | null
  latestVersion: string | null
  latestReleaseUrl: string | null
  releaseNotes: string | null
  publishedAt: string | null
  updateAvailable: boolean
  message: string | null
  releaseCheckError: string | null
  deploymentMode: 'docker' | 'baota' | null
}

export async function getDeploymentStatus(): Promise<DeploymentStatus> {
  const response = await http.get<ApiResult<DeploymentStatus>>('/sys-user/deployment')
  return requireApiData(response.data)
}

export async function startDeployment(): Promise<DeploymentStatus> {
  const response = await http.post<ApiResult<DeploymentStatus>>('/sys-user/deployment')
  return requireApiData(response.data)
}

export async function rollbackDeployment(): Promise<DeploymentStatus> {
  const response = await http.post<ApiResult<DeploymentStatus>>('/sys-user/deployment/rollback')
  return requireApiData(response.data)
}

export interface ReleaseHistoryItem {
  releaseId: string
  version: string
  frontendSha: string
  backendSha: string
  deployedAt: string
  current: boolean
}

export async function getReleaseHistory(): Promise<ReleaseHistoryItem[]> {
  const response = await http.get<ApiResult<ReleaseHistoryItem[]>>('/sys-user/deployment/history')
  return requireApiData(response.data)
}

export async function rollbackToHistory(releaseId: string): Promise<DeploymentStatus> {
  const response = await http.post<ApiResult<DeploymentStatus>>('/sys-user/deployment/rollback/history', { releaseId })
  return requireApiData(response.data)
}

