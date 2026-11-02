# Message management frontend API contract

This document describes the API contract for SMS configuration embedded as a tab in the **Login settings** page and the independent **Message management** page. SMTP and Aliyun/Tencent SMS provider settings, plus SMS template CRUD, are implemented in the backend. Business notification scenario APIs remain a frontend contract and are not implemented in this backend phase.

All endpoints are under `/api` and use the standard response envelope:

```json
{ "code": 200, "msg": null, "data": {} }
```

## SMS template library

### Template model

```ts
type SmsTemplateProvider = 'aliyun' | 'tencent'
type SmsTemplateKind = 'verification' | 'notification' | 'marketing'
type SmsTemplateReviewStatus = 'pending' | 'approved' | 'rejected'

interface SmsTemplate {
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
```

- `id` is the Atlas local template record ID.
- `templateCode` is the template identifier issued by Aliyun or Tencent.
- Business notification selection should only offer templates with `reviewStatus = approved` and `enabled = true`; the notification API and this reference check are not part of the current backend implementation.
- Template registration records a template already created in the provider console. It does not submit a provider-side template application.

### Endpoints

| Method | Path | Purpose |
|---|---|---|
| GET | `/sys-user/sms-templates` | Paged list; query: `page`, `pageSize`, `keyword`, `provider`, `kind`, `reviewStatus` |
| POST | `/sys-user/sms-templates` | Create a local template record |
| PUT | `/sys-user/sms-templates/{id}` | Update a local template record |
| DELETE | `/sys-user/sms-templates/{id}` | Delete a local template record |

List response data uses the existing `PageResult<T>` shape: `total`, `page`, `pageSize`, `records`.

Create/update request body:

```ts
interface SmsTemplateInput {
  provider: 'aliyun' | 'tencent'
  name: string
  templateCode: string
  kind: 'verification' | 'notification' | 'marketing'
  content: string
  variables: string[]
  reviewStatus: 'pending' | 'approved' | 'rejected'
  enabled: boolean
}
```

## Business message notifications

### Notification model

```ts
type MessageCategory = 'member' | 'platform' | 'custom'

interface MessageNotification {
  id: number
  category: MessageCategory
  code: string
  name: string
  description: string
  channels: {
    siteMessage: boolean
    sms: boolean
  }
  siteMessageTitle: string
  siteMessageContent: string
  smsTemplateId: number | null
  smsVariableMappings: Record<string, string>
  updatedAt: string
}
```

- `smsTemplateId` references the Atlas local SMS template record ID, not the provider template code.
- SMS template choices must be approved, enabled, and match the currently selected SMS provider in login base settings.
- `smsVariableMappings` maps each provider-template variable to a business data field name.
- Atlas currently supports only the in-app message and SMS notification channels. Official-account and enterprise-WeChat channels are not part of this system and are not exposed by the frontend contract.

### Endpoints

| Method | Path | Purpose |
|---|---|---|
| GET | `/sys-user/message-notifications?category=member|platform|custom` | List scenarios in a category |
| POST | `/sys-user/message-notifications` | Create a custom notification scenario |
| PUT | `/sys-user/message-notifications/{id}` | Update channel switches and notification content/template association |
| DELETE | `/sys-user/message-notifications/{id}` | Delete a custom notification scenario |

Create/update request body is the `MessageNotification` model without `id` and `updatedAt`.

## Compatibility note: phone-login verification template

The existing Aliyun `templateCode` and Tencent `templateId` fields on the SMS provider configuration are the **phone-login verification templates** used by the authentication sender. They remain separate from the reusable SMS template registry, which is maintained for future business notification scenarios.
