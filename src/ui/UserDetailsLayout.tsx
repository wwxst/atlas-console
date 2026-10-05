import type { ReactNode } from 'react'
import { Avatar } from './index'
import styles from './UserDetailsLayout.module.less'

interface UserDetailsLayoutProps {
  id: number
  name: string
  avatar?: string | null
  initials: string
  actions: ReactNode
  children: ReactNode
}

export function UserDetailsLayout({ id, name, avatar, initials, actions, children }: UserDetailsLayoutProps) {
  return <>
    <div className={styles.identity}>
      <Avatar size={50} src={avatar ?? undefined}>{initials}</Avatar>
      <div className={styles.person}><strong>{name}</strong><span>用户ID：{id}</span></div>
      <div className={styles.actions}>{actions}</div>
    </div>
    <div className={styles.strip}>用户信息</div>
    <div className={styles.content}>{children}</div>
  </>
}

export function UserDetailsSection({ title, children }: { title: string; children: ReactNode }) {
  return <section className={styles.section} aria-label={title}><h3>{title}</h3><dl className={styles.fields}>{children}</dl></section>
}

export function UserDetailsField({ label, htmlFor, full = false, compact = false, children }: {
  label: string
  htmlFor?: string
  full?: boolean
  compact?: boolean
  children: ReactNode
}) {
  return <div className={[styles.field, full ? styles.full : '', compact ? styles.compact : ''].join(' ')}>
    <dt>{htmlFor ? <label htmlFor={htmlFor}>{label}：</label> : `${label}：`}</dt><dd>{children}</dd>
  </div>
}
