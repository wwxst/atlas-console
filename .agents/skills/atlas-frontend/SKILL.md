---
name: atlas-frontend
description: Implement or review Atlas Console frontend features, layouts, UI components, routes, state, API integration, and styles. Use for work inside the atlas-console repository; do not use for backend-only tasks.
---

# Atlas Frontend

Build the frontend as a demand-driven product, not as a generic component-library clone.

## Read The Relevant Contract

- Read [architecture.md](../../../docs/architecture.md) before changing dependencies, directories, routing, API data flow, or global state.
- Read [ui-system.md](../../../docs/ui-system.md) before creating or changing components, layouts, tokens, themes, or responsive behavior.
- Read [engineering.md](../../../docs/engineering.md) before implementing or verifying a change.

## Preserve These Decisions

- Implement UI components in this repository with React and semantic HTML. Do not copy Arco Design or TDesign source code, and do not add either as a runtime dependency unless the user explicitly changes this decision.
- Treat Arco Design and TDesign only as visual, interaction, and information-architecture references.
- Add a reusable component only when current product work needs it. Keep business-specific compositions near their feature until reuse is demonstrated.
- Pages and features import shared UI through `src/ui`; keep third-party implementation details out of business code.
- Use TanStack Query for server-owned asynchronous data. Use Zustand only for cross-page client-owned state. Keep local interaction state in components and shareable filters in the URL.
- Keep runtime theme values in `src/ui/theme` as `--atlas-*` CSS custom properties. Use Less for authoring and CSS Modules for local scoping.
- Target desktop browsers only. Preserve the desktop layout below the 1200 px minimum width instead of adding narrow-screen or mobile variants.

## Product Decisions To Preserve

- Atlas owns the UI implementation. Arco Design and TDesign are visual and interaction references only: do not install them at runtime, copy their source, or carry their licenses and copyright notices into this project.
- Build only the controls and compositions required by the current product workflow. A reference project's API or component taxonomy is not a reason to add an abstraction.
- The application shell uses a 60 px top bar, a 240 px expanded sidebar, and a 72 px explicitly collapsed sidebar. Top-bar search sits to the left of the theme switch; notification and account panels open on hover or activation and contain only actionable account items.
- The current workbench navigation exposes `用户列表` directly below `工作台` at `/users`. The page uses typed feature-local mock data until a backend exists, TanStack Query for the list/mutation contract, URL parameters for keyword, role, status, and page, and local dialog state for details and creation.

## Repository Handoff

- When the user explicitly requests a repository push, verify the current branch, remotes, staged scope, lint, build, and diff whitespace before committing.
- Configure the requested remote only when it is absent or points elsewhere, use a descriptive commit, and push the requested branch. Do not silently push unrelated repositories or create a new remote destination.

## Work Method

1. Inspect the current owner of the behavior before editing.
2. Make the smallest coherent change within the documented boundaries.
3. Update the relevant canonical document when an architectural or UI-system decision changes.
4. Run `npm run lint` and `npm run build`.
5. For visible changes, inspect the running page at 1200 px and a wider desktop viewport and exercise the affected interactions.
