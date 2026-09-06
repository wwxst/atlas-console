export interface CurrentUser {
  name: string
  role: string
  initials: string
}

export const getCurrentUser = async (): Promise<CurrentUser> => ({
  name: '林晓',
  role: '运营管理员',
  initials: '林',
})
