import { requireApiData, requireApiSuccess } from '../../services/api.ts'
import type { ApiResult, PageResult } from '../../services/api.ts'
import { http } from '../../services/http.ts'

export type SmsTemplateProvider = 'aliyun' | 'tencent'
export type SmsTemplateKind = 'verification' | 'notification' | 'marketing'
export type SmsTemplateReviewStatus = 'pending' | 'approved' | 'rejected'
export type MessageCategory = 'member' | 'platform' | 'custom'
export type MessageChannel = 'siteMessage' | 'sms'

export interface SmsTemplate {
  id: number
  provider: SmsTemplateProvider
  name: string
  templateCode: string
  kind: SmsTemplateKind
  content: string
  variables: string[]
  reviewStatus: SmsTemplateReviewStatus
  enabled: boolean
  updatedAt: string
}

export interface SmsTemplateQuery {
  page: number
  pageSize: number
  keyword?: string
  provider?: SmsTemplateProvider
  kind?: SmsTemplateKind
  reviewStatus?: SmsTemplateReviewStatus
}

export interface SmsTemplateInput {
  provider: SmsTemplateProvider
  name: string
  templateCode: string
  kind: SmsTemplateKind
  content: string
  variables: string[]
  reviewStatus: SmsTemplateReviewStatus
  enabled: boolean
}

export interface MessageChannels {
  siteMessage: boolean
  sms: boolean
}

export interface MessageNotification {
  id: number
  category: MessageCategory
  code: string
  name: string
  description: string
  channels: MessageChannels
  siteMessageTitle: string
  siteMessageContent: string
  smsTemplateId: number | null
  smsVariableMappings: Record<string, string>
  updatedAt: string
}

export type MessageNotificationInput = Omit<MessageNotification, 'id' | 'updatedAt'>

const basePath = '/sys-user'

// These endpoints define the frontend contract for the SMS template library and
// business notification settings. The backend implementation is a separate step.
export async function getSmsTemplates(query: SmsTemplateQuery): Promise<PageResult<SmsTemplate>> {
  return requireApiData((await http.get<ApiResult<PageResult<SmsTemplate>>>(`${basePath}/sms-templates`, { params: query })).data)
}

export async function createSmsTemplate(input: SmsTemplateInput): Promise<SmsTemplate> {
  return requireApiData((await http.post<ApiResult<SmsTemplate>>(`${basePath}/sms-templates`, input)).data)
}

export async function updateSmsTemplate(id: number, input: SmsTemplateInput): Promise<SmsTemplate> {
  return requireApiData((await http.put<ApiResult<SmsTemplate>>(`${basePath}/sms-templates/${id}`, input)).data)
}

export async function deleteSmsTemplate(id: number): Promise<void> {
  requireApiSuccess((await http.delete<ApiResult<null>>(`${basePath}/sms-templates/${id}`)).data)
}

export async function getMessageNotifications(category: MessageCategory): Promise<MessageNotification[]> {
  return requireApiData((await http.get<ApiResult<MessageNotification[]>>(`${basePath}/message-notifications`, { params: { category } })).data)
}

export async function createMessageNotification(input: MessageNotificationInput): Promise<MessageNotification> {
  return requireApiData((await http.post<ApiResult<MessageNotification>>(`${basePath}/message-notifications`, input)).data)
}

export async function updateMessageNotification(id: number, input: MessageNotificationInput): Promise<MessageNotification> {
  return requireApiData((await http.put<ApiResult<MessageNotification>>(`${basePath}/message-notifications/${id}`, input)).data)
}

export async function deleteMessageNotification(id: number): Promise<void> {
  requireApiSuccess((await http.delete<ApiResult<null>>(`${basePath}/message-notifications/${id}`)).data)
}
