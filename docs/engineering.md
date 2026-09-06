# Frontend Engineering

This document is the canonical owner for Atlas Console frontend workflow and verification.

## Implementing A Feature

1. Identify the route, feature, service, state, and UI owners affected by the request.
2. Define typed data contracts in the feature. Keep transport configuration in `src/services`.
3. Use TanStack Query for backend data and mutations. Use Zustand only when client state must cross page boundaries.
4. Build feature-specific UI locally. Add or extend `src/ui` only for a stable shared need.
5. Style with CSS Modules and existing semantic theme variables.
6. Add the route and navigation entry only when the feature is reachable and meaningful.

## UI Dependency Rule

Do not install Arco Design or TDesign and do not copy their source. A new third-party UI or headless dependency needs a concrete requirement, a maintenance benefit, and an explanation of why native implementation is insufficient.

Lucide is the standard icon source. Do not draw replacement SVG icons when Lucide already provides the symbol.

## Required Checks

Run after implementation:

```bash
npm run lint
npm run build
```

For a visible UI change, also run the development server and inspect the affected workflow in a browser:

```bash
npm run dev
```

Check the supported desktop layout at 1200 px and a wider viewport, keyboard-relevant interactions, light and dark themes when affected, route transitions, loading, empty, error, and populated states as applicable.

## Documentation Continuity

Update the document that owns a changed decision:

- Architecture and dependency boundaries: `docs/architecture.md`
- Shared UI, themes, and viewport behavior: `docs/ui-system.md`
- Development and verification workflow: `docs/engineering.md`

Source code is authoritative for current behavior. When documentation drifts, reconcile it with verified code and preserve any still-valid product decision.

## Repository Handoff

For an explicitly requested GitHub handoff, inspect `git status`, the current branch, and `git remote -v` first. Run `npm run lint`, `npm run build`, and `git diff --check`; then commit the intended project scope with a descriptive message and push the requested branch. A missing remote may be added only for the repository the user named.
