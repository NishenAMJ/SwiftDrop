---
name: create-frontend-page
description: Create Angular pages/components in frontend/ that follow a given style (style guide, design tokens, Figma link, screenshot, or CSS/SCSS file). Use when asked to build, add, or restyle a frontend page, screen, or feature view in SwiftDrop.
---

# Create a frontend page from a given style

## Inputs

Get these from the user's request; ask only if missing and not inferable:

1. **Page** – name, purpose, which feature area (`auth`, `customer`, `merchant`, `courier`, `dispatcher`, `warehouse`, `tracking`, `payments`, `notifications`) or `core`/`shared`.
2. **Style source** – defaults to `frontend/style.md` (the SwiftDrop Arctic Frost guide; always read it in full). The user may add a Figma URL, screenshots or an existing page to match, but `style.md` tokens win on conflicts unless told otherwise. If Figma, use the Figma MCP tools (`get_design_context`) and adapt the output.

## Steps

1. **Read the project first**: `frontend/src/styles.scss`, `frontend/src/app/app.routes.ts`, `app.config.ts`, and neighbouring components in the same feature folder. Match their conventions (standalone components, SCSS, naming, indentation per `.editorconfig`/`.prettierrc`).
2. **Ensure the shared styles exist** per style.md section 13: `src/styles/_tokens.scss`, `_typography.scss`, `_base.scss`, `_utilities.scss`, loaded once via `@use` in `src/styles.scss`. If missing, create them from style.md verbatim. Reuse existing `--sd-*` tokens; never add feature-local copies. Never hard-code values in components when a token exists.
3. **Scaffold** inside `frontend/`:
   ```bash
   ng generate component features/<area>/<page-name> --skip-tests=false
   ```
   Files: `<page-name>.ts`, `.html`, `.scss`, `.spec.ts` (follow the existing naming; Angular 22 omits the `.component` suffix). Delete the `.gitkeep` in the folder once it has files.
4. **Build the page**:
   - Standalone component, `ChangeDetectionStrategy.OnPush`, signals for state, `@if`/`@for` control flow, new `inject()` style DI.
   - Semantic HTML, labels on inputs, keyboard-focusable controls, sufficient contrast, responsive layout (mobile first).
   - Component styles in its own `.scss` using the tokens; shared pieces (buttons, cards, inputs) go to `shared/components/`.
   - Forms use Reactive Forms with validation messages.
   - Data via a service in the feature folder (stub with mock data if the API isn't ready; do not invent endpoints without checking `contracts/`).
5. **Route it**: add a lazy route in `app.routes.ts`:
   ```ts
   { path: '<path>', loadComponent: () => import('./features/<area>/<page-name>/<page-name>').then(m => m.<ClassName>) }
   ```
   Add guards from `core/auth` when the page is role-restricted.
6. **Review** the page against the checklist in style.md section 14.
7. **Verify**: `cd frontend && npm run build` and `npm test`; fix errors. Optionally run `npm start` and check the page visually against the style source.
8. **Report**: list files created/changed, the route, and any style decisions or assumptions. Do not commit unless asked.

## Rules

- Don't add UI libraries unless the user asks; use plain SCSS + tokens.
- Don't touch `dist/`, `.angular/`, or `node_modules/` (gitignored).
- Keep one page per component folder; keep components small and extract repeated UI into `shared/`.
