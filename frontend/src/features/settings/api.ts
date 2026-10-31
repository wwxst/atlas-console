import { requireApiData } from '../../services/api.ts'
import type { ApiResult } from '../../services/api.ts'
import { http } from '../../services/http.ts'

export const LOGIN_SETTINGS_QUERY_KEY = ['login-settings'] as const
export const ALIYUN_SMS_SETTINGS_QUERY_KEY = ['login-provider-settings', 'sms', 'aliyun'] as const
export const TENCENT_SMS_SETTINGS_QUERY_KEY = ['login-provider-settings', 'sms', 'tencent'] as const
export const EMAIL_LOGIN_SETTINGS_QUERY_KEY = ['login-provider-settings', 'email'] as const

export type SmsProvider = 'aliyun' | 'tencent'
export type SmtpSecurity = 'SSL' | 'STARTTLS' | 'NONE'

export interface LoginSettings {
  phoneLoginEnabled: boolean
  emailLoginEnabled: boolean
  smsProvider: SmsProvider
}

export interface AliyunSmsSettings {
  accessKeyId: string
  signName: string
  templateCode: string
  accessKeySecretConfigured: boolean
  configured: boolean
}

export interface AliyunSmsSettingsUpdate {
  accessKeyId: string
  accessKeySecret: string
  signName: string
  templateCode: string
}

export interface TencentSmsSettings {
  secretId: string
  sdkAppId: string
  signName: string
  templateId: string
  region: string
  secretKeyConfigured: boolean
  configured: boolean
}

export interface TencentSmsSettingsUpdate {
  secretId: string
  secretKey: string
  sdkAppId: string
  signName: string
  templateId: string
  region: string
}

export interface EmailLoginSettings {
  host: string
  port: number
  username: string
  fromAddress: string
  fromName: string
  security: SmtpSecurity
  passwordConfigured: boolean
  configured: boolean
}

export interface EmailLoginSettingsUpdate {
  host: string
  port: number
  username: string
  password: string
  fromAddress: string
  fromName: string
  security: SmtpSecurity
}

const basePath = '/sys-user'

export async function getLoginSettings(): Promise<LoginSettings> {
  return requireApiData((await http.get<ApiResult<LoginSettings>>(`${basePath}/login-settings`)).data)
}

export async function updateLoginSettings(input: LoginSettings): Promise<LoginSettings> {
  return requireApiData((await http.put<ApiResult<LoginSettings>>(`${basePath}/login-settings`, input)).data)
}

export async function getAliyunSmsSettings(): Promise<AliyunSmsSettings> {
  return requireApiData((await http.get<ApiResult<AliyunSmsSettings>>(`${basePath}/login-providers/sms/aliyun`)).data)
}

export async function updateAliyunSmsSettings(input: AliyunSmsSettingsUpdate): Promise<AliyunSmsSettings> {
  return requireApiData((await http.put<ApiResult<AliyunSmsSettings>>(`${basePath}/login-providers/sms/aliyun`, input)).data)
}

export async function getTencentSmsSettings(): Promise<TencentSmsSettings> {
  return requireApiData((await http.get<ApiResult<TencentSmsSettings>>(`${basePath}/login-providers/sms/tencent`)).data)
}

export async function updateTencentSmsSettings(input: TencentSmsSettingsUpdate): Promise<TencentSmsSettings> {
  return requireApiData((await http.put<ApiResult<TencentSmsSettings>>(`${basePath}/login-providers/sms/tencent`, input)).data)
}

export async function getEmailLoginSettings(): Promise<EmailLoginSettings> {
  return requireApiData((await http.get<ApiResult<EmailLoginSettings>>(`${basePath}/login-providers/email`)).data)
}

export async function updateEmailLoginSettings(input: EmailLoginSettingsUpdate): Promise<EmailLoginSettings> {
  return requireApiData((await http.put<ApiResult<EmailLoginSettings>>(`${basePath}/login-providers/email`, input)).data)
}
