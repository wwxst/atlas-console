# UI System

This document is the canonical owner for shared UI and visual-system decisions.

## Product Direction

Atlas Console owns its UI implementation. Arco Design and TDesign may be inspected for visual or interaction ideas, but their source code must not be copied and their component packages must not be runtime dependencies.

Build components when a current feature requires them. Match established behavior where it benefits users, but design APIs around Atlas use cases rather than reproducing another library's API.

## Layers

1. Tokens define reusable visual decisions such as color, spacing, type, radius, and elevation.
2. Primitives in `src/ui` provide focused controls such as buttons, panels, badges, progress bars, auth fields, and toasts.
3. Feature-local components combine primitives for one business domain.
4. Pages arrange features and route-level content.

Promote a feature-local component into `src/ui` only when it has a stable, business-neutral contract and proven reuse.

## Styling Contract

- CSS custom properties owned by `src/ui/theme` are the source of truth for runtime theme values.
- Use the `--atlas-*` prefix for every UI token. Keep global resets and document defaults in `src/index.css`.
- Component and page styles use `*.module.less`.
- Less is an authoring tool for nesting and mixins; do not hide theme values in compile-time-only variables.
- Use restrained surfaces, 6-10 px radii, clear borders, and compact typography suited to repeated operational work.
- Avoid nested cards and decorative page-section cards.
- Do not scale type with viewport width or use negative letter spacing.

## Component Contract

- Prefer semantic HTML and native behavior before adding abstraction.
- Buttons expose clear variants and preserve native button attributes.
- Logout and deletion actions share `AppButton variant="danger"`: danger-colored text, icons, and a visible 1 px border on a transparent background, with a danger-soft background on hover and press. Keep the border visible and dimensions stable across states. List actions may use compact dimensions while keeping this shared border, color, and interaction treatment.
- Standalone login-verification states use `auth-state-background.svg` as a full-page decorative CSS background over the theme's page color, with a clearly visible grid, corner orbit lines, isometric blocks, data nodes, server trays, and small geometric marks in both themes. Distribute decorations asymmetrically around the edges and fade the grid near the center to preserve card readability. Keep the centered card free of illustrations, the retry action neutral, and the logout action in the shared danger style.
- Icon-only buttons require an accessible label and visible hover tooltip through `title` or a future tooltip primitive.
- Form controls require labels, keyboard access, focus treatment, disabled behavior, and error messaging.
- Input, search, and select controls indicate focus with a 1 px border using `--atlas-color-border-focus`, a muted blue defined for each theme, and a 140 ms border-color transition. Keep the border width and control dimensions stable between states. Do not add focus shadows, glow, or outer rings; invalid fields use a danger-colored border without a shadow. Hover must not override the focused border color. Forms that highlight focus within also use the focus border token without a focus shadow. Keep the existing keyboard focus outlines for buttons and switches.
- `AuthField` is a style-only field shell. Features own the actual input content, labels, validation, and business-specific adornments.
- `Toast` is a controlled, fixed-position status surface in the browser's top layer (`popover="manual"`), so request failures remain visible above modal drawers without taking focus. Features own visibility and dismissal timing; the component owns the shared visual treatment and live-region semantics. `RequestErrorToast` is the application-level host for all request failures: it uses the danger tone, stays at the top center, dismisses after 4 seconds, and suppresses identical messages within a short dedupe window.
- Request error fallbacks are Chinese: 401 means “登录状态已失效，请重新登录”, 403 means “没有权限执行此操作”, 404 means “请求的资源不存在”, 408/timeout means “请求超时，请稍后重试”, 429 means “请求过于频繁，请稍后重试”, 5xx means “服务暂时不可用，请稍后重试”, and other network failures mean “网络异常，请检查连接后重试”. A valid backend business message takes precedence.
- Dialogs, menus, selects, date controls, and other complex widgets require deliberate focus and keyboard behavior; use a focused headless primitive dependency when implementing them correctly would otherwise dominate product work.
- Keep component dimensions stable so labels, icons, loading states, and dynamic content do not shift surrounding layout.

## Theme Contract

- Light and dark modes switch through `data-theme` on the document root.
- Components consume semantic tokens such as `--atlas-color-bg-surface` and `--atlas-color-text-primary`, not raw theme-specific colors.
- `tokens.css` owns theme-independent values; `light.css` and `dark.css` own their respective theme values; `index.css` is the public theme entrypoint.
- `--atlas-color-bg-login` is the dedicated backdrop for standalone auth screens: `#F3F4F6` in light mode and `#0A0D12` in dark mode.
- Brand, success, warning, and danger communicate different meanings and must remain visually distinguishable.
- New tokens must represent a reusable design decision, not a one-off component value.

## Desktop Viewport Contract

- Atlas Console targets desktop browsers only. Do not add narrow-screen or mobile layouts.
- Use 1200 px as the minimum application width.
- Below the minimum width, preserve the desktop layout and allow horizontal scrolling instead of reflowing, hiding, or replacing controls.
- Do not add device-specific rendering branches unless the product scope explicitly changes.

## Application Shell Contract

- Use a fixed, full-width 60 px top bar as the global application frame.
- Keep the product brand, global search, theme switch, notifications, and account actions in the top bar.
- Open notification and account panels on pointer hover or explicit activation; keep the panel open while the pointer is inside it.
- Start the sidebar below the top bar. Use a 240 px expanded width and a 72 px user-collapsed width.
- Keep route-specific titles, descriptions, and actions inside page content rather than repeating them in the global top bar.
- Page roots fill the available content width without a centered maximum-width cap. The shell owns the outer spacing: 12 px at the top and both sides, with `--atlas-space-xl` bottom padding; pages do not add extra side margins. Keep this behavior when the sidebar is collapsed and on wide desktop screens.
- Change sidebar width only through the explicit collapse control, not through viewport breakpoints.
- `系统设置` is an expandable navigation group with `认证配置` and `存储配置` children. Indent child items in the expanded sidebar; retain labeled icons and active-route highlighting in the collapsed sidebar. The group button exposes its expanded state and supports native keyboard activation.
- The sidebar footer centers an unboxed 20 px SVG package icon and the version from `package.json` in `--atlas-text-md` in the space between the sidebar's left edge and the collapse button, without a secondary caption or badge background. Keep the collapse control at the right. Activating the version opens a nonmodal popover with the current version and a GitHub release/changelog link. Native popover dismissal supports outside clicks and Escape; opening focuses the close control and Escape restores the trigger focus. In the collapsed sidebar, keep a labeled version icon above the expand control. Do not claim the latest-version status or expose update/rollback actions without a real update source.

## Current Top-Bar Interaction

- Place global search immediately to the left of the theme switch.
- Notification and account surfaces open from pointer hover or explicit activation, remain open while the pointer is inside, and close when focus/pointer leaves the surface.
- The account surface contains direct actions such as personal information, system settings, and logout. Do not add a redundant explanatory block below the avatar/name trigger.

## Product Page Pattern

- List pages should provide a route-level title and description, a primary action, compact filters, a count, a semantic table, and pagination when the dataset needs it.
- Storage configuration supports only local storage and Alibaba Cloud OSS. Keep the compact `存储配置` / `阿里云存储` tab layout, storage-method radios, and real save action; thumbnail and watermark controls remain deferred without reserved space. The Alibaba tab keeps dismissible setup guidance, the shared paginated list panel, left-aligned add/sync actions, right-aligned credentials action, and muted brand table header. Load server data with explicit pending states, disabled conflicting actions, and retry controls; empty results show `暂无数据`. Default status uses `默认` / `设为默认`, with no direct off toggle. Domain editing stays a row action, and deletion requires confirmation. Centered drawers retain horizontal right-aligned labels, required markers, and `取消` / `确定` actions. Keep addition accessible before credentials exist. The add drawer contains AccessKeyId, masked AccessKeySecret, Bucket name, a single native region select grouped by geography, and mutually exclusive public-read (default recommended) and public-read-write radios. Credentials are required for initial setup; configured users may leave both blank to reuse shared credentials, while a supplied pair updates the global credentials before binding. Reject partial pairs. Show configured Secrets as fixed twelve-dot placeholders in the normal text color in both add and configuration drawers. Mask all newly typed Secrets with password inputs. The display dots stay outside submitted credential values. Never prefill saved keys; clear drafts immediately after a successful credential save or closing. Failed binding retains space input for retry. Keep custom-domain editing as a separate row action. Synchronization requires configured credentials. Keep drawers limited to fields, actions, validation errors, and the deletion confirmation; omit auxiliary small-print explanations about credentials, permissions, and domains. Omit the persistent credential-status explanation below the list while retaining loading, retry, and success feedback. Submit the selected accessPermission for backend verification against the actual Bucket ACL; preserve it on failure and reset to public-read when reopening. Server checks decide actual region and permission. Empty domains use the default OSS domain. Keep form input on failed requests and use the root Chinese Toast as the sole request-error feedback; show concise positive status after successful saves. Preserve outlined red deletion styling. Tabs support Left/Right, Home, and End keys; unsupported tab query values fall back to general settings.
- Use feature-local dialogs for details and creation when the workflow needs them. Reset a cancelled creation form so reopening starts from a clean draft.
- Alibaba OSS region choices are a static snapshot of the official [Regions and endpoints](https://help.aliyun.com/zh/oss/user-guide/regions-and-endpoints), verified on 2026-10-06. `aliyunOssRegions.ts` contains 30 standard regions grouped into mainland China, Hong Kong, Asia-Pacific, the Americas/Europe, and the Middle East. Show the official Chinese name and region ID in the select, store the region ID (for example `cn-hangzhou`), and display its Chinese name in the table. Exclude Nanjing/Fuzhou marked as closing, finance/government-only regions, and the regionless endpoint from new-space selection. Recheck the official source when updating the snapshot; these choices do not indicate availability for a particular Alibaba account.
- User details use a 52rem wide drawer and shared `UserDetailsLayout` with a top identity summary, upper-right edit/cancel/save actions, and separate basic/account sections. Keep labels and values aligned horizontally with Chinese colons; nickname inputs are at most 260px wide and occupy one row. Within details, ID is shown only as `用户ID：N` beneath the name. Ordinary users use their real six-digit `userNo` here and in the table's first `用户ID` column; keep the ID on one line with tabular digits. Do not show a status badge beside the name or add a user-type field. Account status and creation time remain read-only text during editing. Creation and password-reset forms retain the default drawer width.
