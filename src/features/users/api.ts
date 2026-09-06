export type UserStatus = 'active' | 'disabled'
export type UserRole = '超级管理员' | '管理员' | '运营人员' | '财务人员' | '审计员'

export interface ConsoleUser {
  id: string
  name: string
  initials: string
  email: string
  phone: string
  department: string
  role: UserRole
  status: UserStatus
  lastLogin: string
  createdAt: string
}

export interface CreateUserInput {
  name: string
  email: string
  phone: string
  department: string
  role: UserRole
}

let users: ConsoleUser[] = [
  { id: 'USR-1001', name: '林晓', initials: '林', email: 'lin.xiao@atlas.cn', phone: '138 0013 8000', department: '运营中心', role: '超级管理员', status: 'active', lastLogin: '今天 09:42', createdAt: '2025-08-12' },
  { id: 'USR-1002', name: '刘晨', initials: '刘', email: 'liu.chen@atlas.cn', phone: '139 0218 6632', department: '产品研发', role: '管理员', status: 'active', lastLogin: '今天 09:18', createdAt: '2025-09-03' },
  { id: 'USR-1003', name: '周敏', initials: '周', email: 'zhou.min@atlas.cn', phone: '136 1187 4502', department: '市场中心', role: '运营人员', status: 'active', lastLogin: '昨天 18:26', createdAt: '2025-09-18' },
  { id: 'USR-1004', name: '陈宇', initials: '陈', email: 'chen.yu@atlas.cn', phone: '137 8892 1106', department: '财务中心', role: '财务人员', status: 'active', lastLogin: '昨天 16:05', createdAt: '2025-10-09' },
  { id: 'USR-1005', name: '王欣', initials: '王', email: 'wang.xin@atlas.cn', phone: '135 6620 7188', department: '客户成功', role: '运营人员', status: 'active', lastLogin: '09-05 14:32', createdAt: '2025-10-21' },
  { id: 'USR-1006', name: '赵磊', initials: '赵', email: 'zhao.lei@atlas.cn', phone: '138 7741 9033', department: '风控合规', role: '审计员', status: 'active', lastLogin: '09-05 11:47', createdAt: '2025-11-06' },
  { id: 'USR-1007', name: '孙婷', initials: '孙', email: 'sun.ting@atlas.cn', phone: '139 5564 8021', department: '运营中心', role: '管理员', status: 'disabled', lastLogin: '08-28 17:20', createdAt: '2025-11-19' },
  { id: 'USR-1008', name: '李明', initials: '李', email: 'li.ming@atlas.cn', phone: '136 4458 2290', department: '产品研发', role: '运营人员', status: 'active', lastLogin: '09-04 20:14', createdAt: '2025-12-02' },
  { id: 'USR-1009', name: '吴凡', initials: '吴', email: 'wu.fan@atlas.cn', phone: '137 3309 6127', department: '市场中心', role: '运营人员', status: 'active', lastLogin: '09-04 15:39', createdAt: '2026-01-08' },
  { id: 'USR-1010', name: '何静', initials: '何', email: 'he.jing@atlas.cn', phone: '135 8891 2476', department: '财务中心', role: '财务人员', status: 'disabled', lastLogin: '08-19 10:08', createdAt: '2026-01-22' },
  { id: 'USR-1011', name: '郑凯', initials: '郑', email: 'zheng.kai@atlas.cn', phone: '138 2014 5791', department: '客户成功', role: '管理员', status: 'active', lastLogin: '09-03 19:52', createdAt: '2026-02-11' },
  { id: 'USR-1012', name: '方琪', initials: '方', email: 'fang.qi@atlas.cn', phone: '139 6627 3185', department: '风控合规', role: '审计员', status: 'active', lastLogin: '09-02 16:31', createdAt: '2026-03-04' },
]

export const getUsers = async (): Promise<ConsoleUser[]> => [...users]

export const createUser = async (input: CreateUserInput): Promise<ConsoleUser> => {
  const user: ConsoleUser = {
    id: `USR-${1001 + users.length}`,
    ...input,
    initials: Array.from(input.name).slice(-2).join(''),
    status: 'active',
    lastLogin: '尚未登录',
    createdAt: new Date().toISOString().slice(0, 10),
  }
  users = [user, ...users]
  return user
}
