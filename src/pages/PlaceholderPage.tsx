import { Inbox } from 'lucide-react'
import { Panel } from '@ui/index'
import styles from './PlaceholderPage.module.less'

export default function PlaceholderPage({ description }: { description: string }) {
  return <div className={styles.page}><Panel><div className={styles.empty}><Inbox size={36} strokeWidth={1.5} /><p>{description}</p></div></Panel></div>
}
