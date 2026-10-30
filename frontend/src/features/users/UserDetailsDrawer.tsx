import { useMutation } from '@tanstack/react-query'
import { Pencil } from 'lucide-react'
import { useId, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { AppButton, Drawer, UserDetailsField, UserDetailsLayout, UserDetailsSection } from '@ui/index'
import { updateUser } from './api'
import type { User, UpdateUserInput } from './api'
import styles from './UserDetailsDrawer.module.less'

export function UserDetailsDrawer({ user, onClose, onSaved }: { user: User; onClose: () => void; onSaved: (saved: User) => void }) {
  const [editing, setEditing] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const formRef = useRef<HTMLFormElement>(null)
  const formId = useId()
  const nicknameId = useId()
  const phoneId = useId()
  const emailId = useId()
  const mutation = useMutation({
    mutationFn: (input: UpdateUserInput) => updateUser(user.id, input),
    onSuccess: (saved) => { setEditing(false); setError(null); onSaved(saved) },
  })
  const name = user.nickname || user.phone || user.email || `用户 ${user.userNo}`
  const cancelEdit = () => { formRef.current?.reset(); setEditing(false); setError(null) }
  const save = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (mutation.isPending) return
    const data = new FormData(event.currentTarget)
    const nickname = String(data.get('nickname') ?? '').trim()
    const phone = String(data.get('phone') ?? '').trim() || null
    const email = String(data.get('email') ?? '').trim() || null
    if (!nickname) { setError('请输入用户昵称'); return }
    if (!phone && !email) { setError('至少保留一个手机号或邮箱'); return }
    setError(null)
    mutation.mutate({ nickname, phone, email })
  }
  return <Drawer open title="用户详情" size="wide" onClose={onClose}>
    <form id={formId} ref={formRef} onSubmit={save}>
      <UserDetailsLayout id={user.userNo} name={name} avatar={user.avatar} initials={Array.from(name).slice(0, 2).join('')} actions={editing ? <>
        <AppButton type="button" disabled={mutation.isPending} onClick={cancelEdit}>取消</AppButton>
        <AppButton type="submit" variant="primary" disabled={mutation.isPending}>{mutation.isPending ? '保存中...' : '保存'}</AppButton>
      </> : <AppButton type="button" variant="primary" icon={<Pencil size={14} />} onClick={(event) => { event.preventDefault(); setEditing(true) }}>编辑</AppButton>}>
        <UserDetailsSection title="基本信息">
          <UserDetailsField label="用户昵称" htmlFor={editing ? nicknameId : undefined} full compact>{editing ? <input id={nicknameId} name="nickname" defaultValue={user.nickname ?? ''} maxLength={50} required autoFocus disabled={mutation.isPending} /> : user.nickname || '未设置'}</UserDetailsField>
          <UserDetailsField label="手机号" htmlFor={editing ? phoneId : undefined}>{editing ? <input id={phoneId} name="phone" type="tel" defaultValue={user.phone ?? ''} maxLength={11} pattern="1[0-9]{10}" disabled={mutation.isPending} /> : user.phone || '未绑定'}</UserDetailsField>
          <UserDetailsField label="邮箱" htmlFor={editing ? emailId : undefined}>{editing ? <input id={emailId} name="email" type="email" defaultValue={user.email ?? ''} maxLength={100} disabled={mutation.isPending} /> : user.email || '未绑定'}</UserDetailsField>
        </UserDetailsSection>
        <UserDetailsSection title="账号信息">
          <UserDetailsField label="用户状态">{user.status === 1 ? '正常' : '已停用'}</UserDetailsField>
          <UserDetailsField label="创建时间">{user.createdAt.replace('T', ' ').slice(0, 16)}</UserDetailsField>
        </UserDetailsSection>
        {error && <p className={styles.error} role="alert">{error}</p>}
      </UserDetailsLayout>
    </form>
  </Drawer>
}
