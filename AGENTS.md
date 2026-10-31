# Instructions for AI coding agents

This file applies to every AI agent (Claude Code, Copilot, Cursor, Codex, etc.) working in this repository.

## Frontend work (`frontend/`)

**Before creating or changing any UI, read [`frontend/style.md`](frontend/style.md) and follow it.** It is the single source of truth for the SwiftDrop look and feel (Arctic Frost light theme).

Hard rules:

- Use the shared `--sd-*` CSS variables from `frontend/src/styles/_tokens.scss`. Never copy hex values or invent new colours, fonts, radii or spacing in feature code.
- Font is Inter only (weights 400/500/600/700). No per-role themes, no gradients inside buttons.
- Use the shared components/classes (`.sd-button`, cards, inputs, badges) from `shared/components/`. If something is missing or needs to change, propose it as a shared token/component change instead of adding a feature-local copy.
- Component-specific layout goes in the component's own SCSS. Load shared SCSS only via `@use` from `src/styles.scss`. Avoid `::ng-deep` and `!important`.
- Arctic Frost gradient is for authentication/landing screens only. Dashboards use the solid `--sd-page` background and opaque white cards.
- Status, urgency, payment and approval are separate fields with separate badges; always text, never colour alone (see style.md section 9).
- Accessibility is required: labels on every field, `aria-describedby`/`aria-invalid` for errors, 44px minimum targets, visible focus, reduced-motion support.
- Never expose addresses, phone numbers, private proofs or raw server errors in public/user-facing UI.
- Finish by walking through the checklist in style.md section 14.

To create a new page, use the `create-frontend-page` skill in `.claude/skills/create-frontend-page/SKILL.md` (other agents: follow the same steps manually).

## General

- Do not change backend business rules or permissions as part of styling work.
- Don't commit build output (`dist/`, `.angular/`, `node_modules/`).
