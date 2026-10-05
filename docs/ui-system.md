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
- The storage configuration preview follows a compact tabbed layout: general settings and six cloud-provider tabs above a dismissible notice. General settings use storage-method radio buttons, thumbnail/watermark switches, and conditional detail fields. Each provider tab instead shows setup guidance and the shared paginated list panel: add/sync actions on the left, configuration editing on the right, and a muted brand-colored table header. Empty tables show `暂无数据`; preview rows expose status, domain editing, and deletion. Centered modal drawers use horizontal, right-aligned labels, red required markers, and `取消` / `确定` footer actions. The configuration modal has only two credential fields. Addition collects credentials, name, and region; Alibaba uses a native region select and public-read/public-read-write radios. Keep secrets masked, reset cancelled addition drafts, preserve independent provider configuration drafts, and clearly label preview actions and unavailable save/sync functions. Tabs support Left/Right, Home, and End keys; each notice can be dismissed independently.
- Use feature-local dialogs for details and creation when the workflow needs them. Reset a cancelled creation form so reopening starts from a clean draft.
- User details use a 52rem wide drawer and shared `UserDetailsLayout` with a top identity summary, upper-right edit/cancel/save actions, and separate basic/account sections. Keep labels and values aligned horizontally with Chinese colons; nickname inputs are at most 260px wide and occupy one row. Within details, ID is shown only as `用户ID：N` beneath the name. Ordinary users use their real six-digit `userNo` here and in the table's first `用户ID` column; keep the ID on one line with tabular digits. Do not show a status badge beside the name or add a user-type field. Account status and creation time remain read-only text during editing. Creation and password-reset forms retain the default drawer width.
