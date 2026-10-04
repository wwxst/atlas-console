import { requireApiData, requireApiSuccess } from '../../services/api.ts'
import type { ApiResult } from '../../services/api.ts'
import { http } from '../../services/http.ts'

export const AUTH_CHANNELS_QUERY_KEY = ['auth-channels'] as const
export type AuthChannel = 'phone' | 'email'

export interface AuthChannels {
  phoneEnabled: boolean
  phoneCodeEnabled: boolean
  emailEnabled: boolean
  emailCodeEnabled: boolean
}

export interface AuthChannelUpdate {
  enabled: boolean
  codeEnabled: boolean
}

export async function getAuthChannels(): Promise<AuthChannels> {
  const response = await http.get<ApiResult<AuthChannels>>('/sys-user/auth-channels')
  return requireApiData(response.data)
}

export async function updateAuthChannel(channel: AuthChannel, update: AuthChannelUpdate): Promise<void> {
  const response = await http.put<ApiResult<null>>(`/sys-user/auth-channels/${channel}`, update)
  requireApiSuccess(response.data)
}
