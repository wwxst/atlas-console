import styles from './EmptyState.module.less'

export function EmptyState({ description }: { description: string }) {
  return <div className={styles.emptyState}>
    <svg className={styles.illustration} viewBox="0 0 112 84" fill="none" aria-hidden="true" focusable="false">
      <ellipse cx="56" cy="76" rx="40" ry="5" fill="currentColor" opacity="0.08" />
      <path d="M28 45 38 29h36l10 16" fill="var(--atlas-color-bg-subtle)" stroke="currentColor" strokeOpacity="0.3" strokeWidth="1.5" strokeLinejoin="round" />
      <rect x="39" y="12" width="34" height="43" rx="4" fill="var(--atlas-color-bg-surface)" stroke="currentColor" strokeOpacity="0.35" strokeWidth="1.5" />
      <path d="M47 23h18M47 31h18M47 39h11" stroke="currentColor" strokeOpacity="0.25" strokeWidth="2" strokeLinecap="round" />
      <path d="M26 45h18l5 9h14l5-9h18v20a5 5 0 0 1-5 5H31a5 5 0 0 1-5-5V45Z" fill="var(--atlas-color-bg-surface)" stroke="currentColor" strokeOpacity="0.35" strokeWidth="1.5" strokeLinejoin="round" />
      <path d="M18 25h6M21 22v6M89 16h4M91 14v4" stroke="currentColor" strokeOpacity="0.25" strokeWidth="1.5" strokeLinecap="round" />
      <circle cx="92" cy="36" r="2" fill="currentColor" opacity="0.2" />
    </svg>
    <p>{description}</p>
  </div>
}
