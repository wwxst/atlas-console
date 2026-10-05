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

`PaginatedListPanel` in `src/ui/index.tsx` owns the shared list card, toolbar layout, table scroll container, content-area minimum height, and fixed-height pagination footer used by ordinary-user and system-user pages. Pages provide their filters, table content, pagination values, and URL update callback; requests and row details stay in the pages and features.

Both user pages use `ListFilters` for keyword search, status selection, and reset, and `DataTable` for table density and 14px headers. Each page sets the pagination total unit to `个用户`, so the footer displays `共 N 个用户` at the bottom left; the filter toolbar does not repeat the count. Pages keep ownership of column widths, rows, request state, and URL updates. `Avatar` provides shared neutral placeholders or images (40px in rows, 50px in details). User rows show the name without a secondary account ID. Avatar colors follow the active theme.

Both user detail drawers use the shared `UserDetailsLayout`, `UserDetailsSection`, and `UserDetailsField` compositions with `Drawer size="wide"`. The summary shows the avatar, name, and `用户ID：N` without a status badge. Ordinary users display the backend's six-digit `userNo` in the table's first column and detail summary; their internal `id` remains the key for editing and status requests. Keyword search also accepts `userNo`. System-user details continue to display their internal `id`. Basic information and account information use separate sections, horizontal label/value rows with Chinese colons, and two columns. The nickname occupies its own row and its input is at most 260px wide. Within details, ID appears only in the summary; there is no user-type field. Status and creation time are read-only text. Edit switches fields in place and replaces the summary's edit button with cancel/save; cancelling discards drafts and failed saves retain them.

`features/users/UserDetailsDrawer.tsx` owns ordinary-user editing and calls `PUT /sys-user/users/{id}` with only nickname, phone, and email. Admins directly edit contacts without verification codes; at least one contact must remain, while phone format, email normalization, and uniqueness are enforced by the backend. User self-service contact changes continue to use the separate verification-based rebind endpoints. Successful saves update the open detail and invalidate the ordinary-user list. Request failures use the global Chinese Toast; field validation remains local.

The system-user page groups the avatar and nickname in the user column and uses `PATCH /sys-user/sys-users/{id}/status` for enable/disable actions. Its creation-time order defaults to ascending (oldest first); the header switches to descending and then restores the default ascending order. The URL and query key include `createdAtOrder`; sorting resets to page 1 and the backend sorts before pagination. Resetting filters restores ascending order. The mutation invalidates the paginated system-user query so the returned `status` remains the source of truth. Each table row provides view, enable/disable, and delete actions; deletion requires confirmation and refreshes the list on success. The details drawer provides editing and password reset, while the toolbar opens the creation form.

## Application Shell

The ordinary-user table provides view and enable/disable actions. `PATCH /sys-user/users/{id}/status`
sets status to 0 or 1, accepts successful void responses, and rejects failed business responses through
the global Chinese Toast. Status actions are disabled during a mutation and list refetch; successful
changes update an open detail's status and invalidate all ordinary-user list queries, including status filters.

```text
AppLayout
|-- TopBar: brand, global search, theme, notifications, account
|-- Sidebar: route navigation below the top bar
`-- Page content: route title, actions, and feature content
```

`AppLayout` owns only persistent application chrome. Pages retain ownership of route-specific headings and actions. The sidebar becomes an icon rail only when the user explicitly collapses it.

The sidebar's `系统设置` group expands to `认证配置` at `/settings` and `存储配置` at `/settings/storage`. The group uses a native disclosure button; child routes retain their own active state and labeled icons when the sidebar is collapsed. Existing `/settings` links continue to open authentication settings.

`StorageSettingsPage` is a frontend-only configuration preview. A general tab selects local storage or Qiniu, Alibaba, Tencent, JD, Huawei, or Tianyi cloud storage, with thumbnail and watermark settings. Each provider tab shows its setup guidance and a paginated storage-space table through the page-local `StorageProviderPanel`. The table includes name, region, domain, status, creation/update times, domain editing, and deletion. It starts empty; adding a space, toggling status, editing its domain, and deleting it operate only on explicitly labeled preview state. Duplicate names within one provider are rejected. `修改配置信息` opens a two-field credentials modal; credential saving and space synchronization stay disabled until APIs are implemented. Adding a space requires provider credentials, a name, and a region. Alibaba's add dialog uses the eight region choices in the supplied reference and public-read (default) or public-read-write permissions, retained in the preview row; these choices are not live service discovery. Domain editing remains a separate row action. Cancelling and reopening addition clears its draft. The `tab` URL parameter owns the active tab; switching tabs preserves independent provider previews and configuration drafts without writing credentials to browser storage. Notice dismissal is independent per tab. The preview does not configure uploads, create real cloud buckets, generate thumbnails, or apply watermarks; do not treat its defaults as the deployment's effective configuration.

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

The system-user workflow follows `../backend/docs/api.md`. `src/features/auth/api.ts` owns login and current-system-user requests, while `src/features/systemUsers/api.ts` owns the paginated system-user query. The protected route validates the session through `/me`, with Access/Refresh renewal handled in the transport layer. `SystemUsersPage` sends URL-owned `keyword`, `status`, and `page` values to the backend and keeps only dialog visibility and the selected row in local component state.

`/settings` uses `src/features/settings/api.ts` for the ordinary-user phone and email authentication channels. Query owns the four saved server flags; each channel form owns its unsaved draft. PUT sends both `enabled` and `codeEnabled` for one channel, checks the business code even when response data is null, updates that channel's cached flags on success, then refetches server state. Saving is serialized across the two forms. The page blocks closing the final enabled channel based on saved server state; the backend remains authoritative for concurrent updates. A disabled channel retains its code flag. Failed saves preserve the draft, use the global request-error Toast, and refetch current configuration to reconcile concurrent or uncertain writes; query failure exposes retry and disables editing until a successful read. No organization, notification, or system-user account settings are presented without corresponding backend APIs.

## Reference Projects

- Arco Design Pro informs visual density, theme concepts, permissions, and common admin page patterns.
- TDesign React Starter informs its straightforward SPA layout, modular route definitions, and separation of layouts, services, and state.
- Atlas improves on both by using current React tooling, separating server and client state, and maintaining only UI components required by the product.
