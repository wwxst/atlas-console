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
- `AuthField` is a style-only field shell. Features own the actual input content, labels, validation, and business-specific adornments.
- `Toast` is a controlled, fixed-position status surface. Features own visibility and dismissal timing; the component owns the shared visual treatment and live-region semantics.
- Dialogs, menus, selects, date controls, and other complex widgets require deliberate focus and keyboard behavior; use a focused headless primitive dependency when implementing them correctly would otherwise dominate product work.
- Keep component dimensions stable so labels, icons, loading states, and dynamic content do not shift surrounding layout.

## Theme Contract

- Light and dark modes switch through `data-theme` on the document root.
- Components consume semantic tokens such as `--atlas-color-bg-surface` and `--atlas-color-text-primary`, not raw theme-specific colors.
- `tokens.css` owns theme-independent values; `light.css` and `dark.css` own their respective theme values; `index.css` is the public theme entrypoint.
- `--atlas-color-bg-login` is the light-gray backdrop for the login page (and future standalone auth screens): `#f2f3f5` in light mode, following `--atlas-color-bg-page` in dark mode.
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
- Change sidebar width only through the explicit collapse control, not through viewport breakpoints.

## Current Top-Bar Interaction

- Place global search immediately to the left of the theme switch.
- Notification and account surfaces open from pointer hover or explicit activation, remain open while the pointer is inside, and close when focus/pointer leaves the surface.
- The account surface contains direct actions such as personal information, system settings, and logout. Do not add a redundant explanatory block below the avatar/name trigger.

## Product Page Pattern

- List pages should provide a route-level title and description, a primary action, compact filters, a count, a semantic table, and pagination when the dataset needs it.
- Use feature-local dialogs for details and creation when the workflow needs them. Reset a cancelled creation form so reopening starts from a clean draft.
