import type { User, updateUser, updateUserStatus } from '../../src/features/users/api.ts'

type ExpectString<T extends string> = T

export type ResponseUserId = ExpectString<User['id']>
export type UpdateUserId = ExpectString<Parameters<typeof updateUser>[0]>
export type StatusUserId = ExpectString<Parameters<typeof updateUserStatus>[0]>
