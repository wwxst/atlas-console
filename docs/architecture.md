# Frontend Architecture

This document is the canonical owner for Atlas Console architecture decisions.

## Goals

- Keep product features easy to locate and change.
- Separate backend-owned data from browser-owned state.
- Own the UI surface without maintaining an unused general-purpose component library.
- Allow the visual system to evolve without coupling business code to a vendor API.

## Stack

| Concern | Choice | Responsibility |
| --- | --- | --- |
| Application | React + TypeScript | Components, composition, and type-safe product code |
| Build | Vite | Development server and production bundles |
| Routing | React Router | Route matching, navigation, and URL state |
| Server state | TanStack Query | Fetching, caching, invalidation, loading, and errors |
| Client state | Zustand | Theme, sidebar, workspace selection, and other cross-page UI state |
| HTTP | Axios | Base URL, authentication headers, and transport-level error handling |
| Styling | Less + CSS Modules + CSS variables | Scoped styles, authoring helpers, and runtime themes |
| Icons | Lucide React | Consistent interface icons |

Arco Design and TDesign are references only. Their packages and source code are not part of the runtime architecture.

## Product Implementation Boundary

Atlas owns the implementation of its UI. Arco Design and TDesign may inform visual density, interaction details, and information architecture, but this project does not install their runtime packages, copy their source, or retain their licenses and copyright notices. Components are implemented as the product needs them and are not required to reproduce either reference project's API or directory taxonomy.

## State Ownership

Choose the owner by where the truth lives:

- Backend truth belongs in TanStack Query: lists, details, permissions, mutations, and request status.
- Cross-page browser truth belongs in Zustand: theme, navigation state, and selected workspace.
- Shareable navigation state belongs in the URL: search, filters, sorting, pagination, and active tabs when deep links matter.
- Short-lived interaction state belongs in the component: open menus, draft toggles, and temporary selections.
- Form state stays with the form. Add a form library only when real form complexity justifies it.

Do not mirror Query data into Zustand. Do not put every local value into a global store.

## Module Boundaries

```text
src/
|-- features/       Business-domain data and logic
|-- layouts/        Application shell and route-independent layout
|-- pages/          Route entry components
|-- router/         Route definitions and guards
|-- services/       Shared transport and infrastructure clients
|-- stores/         Cross-page client state
|-- ui/             Shared UI primitives, compositions, and owned theme tokens
|-- index.css       Global reset and document defaults
`-- main.tsx        Application providers and bootstrap
```

Dependency direction is `pages -> features/ui -> services`. Infrastructure must not import pages. Shared UI must not import business features.

## Application Shell

```text
AppLayout
|-- TopBar: brand, global search, theme, notifications, account
|-- Sidebar: route navigation below the top bar
`-- Page content: route title, actions, and feature content
```

`AppLayout` owns only persistent application chrome. Pages retain ownership of route-specific headings and actions. The sidebar becomes an icon rail only when the user explicitly collapses it.

## Data Flow

1. A page or feature calls a typed query or mutation hook.
2. The feature calls a service client such as `src/services/http.ts`.
3. TanStack Query owns remote loading, error, cache, and invalidation state.
4. The page renders through shared UI primitives or feature-local compositions.

Mock data may implement the same typed async contract during early development. Replace it with HTTP calls without changing page ownership.

### Request Error Flow

- Axios transport failures are normalized by `src/services/api.ts` and published by `src/services/http.ts` to the shared request-error channel.
- HTTP 200 responses with a failed backend code are published by `requireApiData` through the same channel before the original error is rethrown to TanStack Query.
- `RequestErrorToast` is mounted at the application root, so login and protected routes use the same global Chinese error surface.
- Feature pages retain loading, failure, and retry controls but do not render transport-specific error strings; the global Toast owns that feedback.

The system-user workflow uses the backend contract documented at the workspace root. `src/features/auth/api.ts` owns login and current-system-user requests, while `src/features/systemUsers/api.ts` owns the paginated system-user query. The protected route validates the stored admin JWT before rendering the application shell. `SystemUsersPage` sends URL-owned `keyword`, `status`, and `page` values to the backend and keeps only dialog visibility and the selected row in local component state.

## Reference Projects

- Arco Design Pro informs visual density, theme concepts, permissions, and common admin page patterns.
- TDesign React Starter informs its straightforward SPA layout, modular route definitions, and separation of layouts, services, and state.
- Atlas improves on both by using current React tooling, separating server and client state, and maintaining only UI components required by the product.
