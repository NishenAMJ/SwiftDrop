# SwiftDrop UI Style Guide

Arctic Frost light theme for the shared Angular application. All members must use the same tokens, typography, layout and components. This document defines visual conventions; it does not grant permissions or change backend business rules.

## 1. Visual direction

Create a calm, clear courier platform using deep blue actions, restrained purple accents, white surfaces and dark slate text. Use the Arctic Frost gradient on authentication screens and landing sections. Operational dashboards use a quiet solid background so tables, forms and urgent work remain easy to scan.

- Primary action: deep blue.
- Secondary brand accent: purple, used sparingly.
- Content surfaces: solid white.
- Urgent parcels: amber badge with the word Urgent; never colour alone.
- One clear primary action per task area.
- Avoid gradients inside buttons, heavy glass effects, decorative shadows on every element and different colour themes for each role.

## 2. Shared colour tokens

Define these once in `src/styles/_tokens.scss`. Use CSS variables in feature styles rather than copying hex values.

```scss
:root {
  --sd-brand: #2457a7;
  --sd-brand-hover: #1d4686;
  --sd-brand-pressed: #183b70;
  --sd-brand-soft: #eaf1fb;
  --sd-accent: #705dd0;
  --sd-accent-soft: #f0edfc;

  --sd-auth-base: #faf8f2;
  --sd-page: #f5f7fb;
  --sd-surface: #ffffff;
  --sd-surface-subtle: #f8fafc;
  --sd-text: #1e293b;
  --sd-text-secondary: #475569;
  --sd-text-muted: #64748b;
  --sd-border: #dbe3ee;
  --sd-control-border: #7c8a9e;
  --sd-focus: #2457a7;

  --sd-success: #166534;
  --sd-success-bg: #ecfdf3;
  --sd-warning: #92400e;
  --sd-warning-bg: #fffbeb;
  --sd-danger: #b91c1c;
  --sd-danger-bg: #fef2f2;
  --sd-info: #1e40af;
  --sd-info-bg: #eff6ff;
  --sd-disabled-bg: #e2e8f0;
  --sd-disabled-text: #64748b;
}
```

Use `--sd-border` for separators and cards. Use the stronger `--sd-control-border` where a control needs a visible boundary. White text belongs on the solid brand, hover and danger action backgrounds. The gradient colours are decorative and are not text colours.

## 3. Typography

Use Inter throughout headings, navigation, forms, buttons and tables. Use weights 400, 500, 600 and 700 only. No different font families per role. Tracking numbers may use the system monospace stack.

Self-host the official Inter variable WOFF2 file at `public/fonts/InterVariable.woff2` and retain its SIL Open Font License with the font assets. The file must actually be downloaded; this guide does not include it. Until it is added, the system fallback must remain usable.

Add to the global stylesheet:

```scss
@font-face {
  font-family: 'Inter';
  src: url('/fonts/InterVariable.woff2') format('woff2');
  font-style: normal;
  font-weight: 100 900;
  font-display: swap;
}

:root {
  --sd-font: 'Inter', system-ui, -apple-system, 'Segoe UI', sans-serif;
  --sd-font-mono: ui-monospace, SFMono-Regular, Consolas, monospace;
}

html { font-size: 100%; }
body {
  margin: 0;
  font-family: var(--sd-font);
  font-size: 1rem;
  line-height: 1.5;
  color: var(--sd-text);
  background: var(--sd-page);
}
button, input, select, textarea { font: inherit; }
h1, h2, h3, p { margin-block-start: 0; }
h1, h2, h3 { color: var(--sd-text); }
```

| Use | Size | Weight | Line height |
| --- | --- | --- | --- |
| Landing headline | `clamp(2rem, 4vw, 3rem)` | 700 | 1.15 |
| Page title | `clamp(1.5rem, 3vw, 2rem)` | 700 | 1.25 |
| Section heading | 1.25rem | 600 | 1.35 |
| Card heading | 1.125rem | 600 | 1.4 |
| Body and form input | 1rem | 400 | 1.5 |
| Labels, buttons and navigation | 0.875rem to 1rem | 500 or 600 | 1.4 |
| Table content | 0.875rem | 400 | 1.5 |
| Helper text and badges | 0.8125rem | 400 or 600 | 1.5 |

Use sentence case, left alignment and descriptive headings. Avoid long all-capital labels and thin weights. Apply `font-variant-numeric: tabular-nums` to prices, counts and dates when alignment matters. Allow text zoom; do not fix container heights around copy.

## 4. Spacing, shape and elevation

```scss
:root {
  --sd-space-1: 0.25rem;
  --sd-space-2: 0.5rem;
  --sd-space-3: 0.75rem;
  --sd-space-4: 1rem;
  --sd-space-5: 1.25rem;
  --sd-space-6: 1.5rem;
  --sd-space-8: 2rem;
  --sd-space-10: 2.5rem;
  --sd-space-12: 3rem;
  --sd-radius-control: 0.5rem;
  --sd-radius-card: 0.875rem;
  --sd-radius-dialog: 1rem;
  --sd-shadow-card: 0 2px 8px rgb(15 23 42 / 0.04);
  --sd-shadow-overlay: 0 12px 32px rgb(15 23 42 / 0.14);
}
*, *::before, *::after { box-sizing: border-box; }
```

Use 16px mobile and 24-32px desktop page padding. Cards use 16-24px internal padding. Separate related fields by 16px and major sections by 24-32px. Use pill corners only for badges and chips. Cards use a subtle border; reserve stronger elevation for dialogs and drawers.

## 5. Arctic Frost background

The base colour belongs on the page or body. The aura container must remain transparent. Put real content above the decorative layers. No sample headings or placeholder content are part of the background component.

The following preserves the supplied blue and purple palette but moves the purple stop after 28 percent for a smoother progression. Desktop blur sizes are applied through a media query.

Global SCSS:

```scss
body.sd-auth-page { background-color: var(--sd-auth-base); }

.sd-aura {
  position: relative;
  overflow: hidden;
  min-height: 100vh;
  min-height: 100svh;
  // Deliberately no background and no isolation:isolate.
}
.sd-aura__layer {
  position: absolute;
  inset: 0;
  mix-blend-mode: multiply;
  transform: translateZ(0);
  pointer-events: none;
}
.sd-aura__layer--one {
  background: linear-gradient(
    transparent 0%,
    rgb(200 230 255 / 0.12) 28%,
    rgb(112 93 208) 42%,
    rgb(150 200 255) 68%,
    rgb(100 130 200) 100%
  );
  filter: blur(153px);
  opacity: 0.93;
}
.sd-aura__layer--two {
  background: linear-gradient(
    transparent 0%,
    rgb(8 114 201 / 0.22) 34%,
    white 66%,
    rgb(150 200 255) 82%,
    rgb(100 130 200) 100%
  );
  filter: blur(78px);
}
.sd-aura__content { position: relative; z-index: 1; }

@media (min-width: 64rem) {
  .sd-aura__layer--one { filter: blur(220px); }
  .sd-aura__layer--two { filter: blur(112px); }
}
```

Angular layout template:

```html
<div class="sd-aura">
  <div class="sd-aura__layer sd-aura__layer--one" aria-hidden="true"></div>
  <div class="sd-aura__layer sd-aura__layer--two" aria-hidden="true"></div>
  <div class="sd-aura__content">
    <ng-content></ng-content>
  </div>
</div>
```

The shared layout owner applies `sd-auth-page` to the body only while the authentication layout is active and removes it on exit. If the app is hosted under a subpath, adjust root-relative font asset URLs to the deployment base path. Keep cards opaque white; do not place essential text directly over the coloured part of the gradient. Keep the aura static and profile it on mobile. Do not add permanent `will-change` to every decorative element without measuring a benefit. Route animations, opacity, transforms and containment on ancestors can affect blending; visually check the actual layout.

## 6. Application layout and responsive behavior

- Desktop: 240-256px sidebar, approximately 64px header and a main content area capped at 1440px.
- Authentication forms: max-width 440px, white card, full-width fields and one primary submit button.
- At widths below 1024px: collapse the sidebar into a keyboard-accessible drawer.
- At widths below 768px: stack forms and action groups; use 16px page padding.
- Use CSS grid with `minmax(0, 1fr)` so content can shrink without overflowing.
- Keep reading content near 65-75 characters per line; data tables may be wider.
- At 320px viewport width and 200 percent zoom, primary tasks must remain usable. Only complex tables may scroll horizontally inside their own labelled container.
- Mobile courier tasks need large actions and a clear next step. Keep Delivered and Failed separate so they cannot be confused.
- Shared navigation uses the same active blue background and text treatment in every role. Show only relevant navigation; backend authorization remains mandatory.

## 7. Shared components

| Component | Rule |
| --- | --- |
| Primary button | Solid brand blue, white text, 44px minimum height, 16-20px horizontal padding |
| Secondary button | White background, visible border, dark blue text |
| Danger button | Solid danger colour and white text; use for destructive decisions only |
| Text link | Brand blue; underline inline links and make hover/focus clear |
| Icon button | At least 44 by 44px project target and an accessible name |
| Input or select | At least 44px high, white surface, visible border, 16px text |
| Textarea | Resizable vertically, visible label, useful minimum height |
| Card | White surface, subtle border, shared card radius and padding |
| Dialog or drawer | Clear title, close control, focus management and keyboard support |
| Toast | Short result message; persistent errors also appear near the relevant action |
| Empty state | Explain what is missing and provide an action when appropriate |

Example shared action styles:

```scss
.sd-button {
  min-height: 2.75rem;
  padding: 0.625rem 1.125rem;
  border: 1px solid transparent;
  border-radius: var(--sd-radius-control);
  font-weight: 600;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  cursor: pointer;
  transition: background-color 150ms ease, border-color 150ms ease;
}
.sd-button--primary { background: var(--sd-brand); color: white; }
.sd-button--primary:hover:not(:disabled) { background: var(--sd-brand-hover); }
.sd-button--primary:active:not(:disabled) { background: var(--sd-brand-pressed); }
.sd-button:disabled {
  background: var(--sd-disabled-bg);
  color: var(--sd-disabled-text);
  cursor: not-allowed;
}
:where(a, button, input, select, textarea, [tabindex]):focus-visible {
  outline: 3px solid var(--sd-focus);
  outline-offset: 3px;
}
@media (prefers-reduced-motion: reduce) {
  .sd-button { transition: none; }
}
```

When a button is loading, show a spinner plus a useful label and prevent duplicate submissions. Preserve the control width so the page does not jump. Use `disabled` only for a real unavailable action, with an explanation when necessary.

## 8. Forms and feedback

- Put a permanent label above every field; placeholders are examples only.
- Mark required fields consistently and explain the convention once.
- Show units explicitly: Approximate weight (kg), COD amount (LKR).
- Distinguish pickup and drop-off sections visually with headings.
- Use input types, `autocomplete` and `inputmode` suited to email, phone and amounts.
- Show field errors after interaction or submission. Use an error summary for a long failed form and focus it when appropriate.
- Link helper and error text through `aria-describedby`; mark invalid fields with `aria-invalid`.
- Preserve entered values after failures. Never display raw server exceptions.
- Show the fee and any urgent surcharge before final confirmation.
- Display pending account approval as a clear state with the next step; hide operational actions that are unavailable.
- Use toasts for ordinary success and inline guidance for errors requiring correction.

## 9. Parcel urgency, statuses and operational views

Urgency, delivery status, payment status and approval status are different fields. Never show a single badge that combines them ambiguously.

| Meaning | Text | Colour pair |
| --- | --- | --- |
| Urgent service request | Urgent - requested today | warning text / warning background |
| Conventional service | Standard | secondary text / subtle surface |
| Created | Created | muted text / subtle surface |
| Picked up or in transit | Picked up / In transit | info text / info background |
| Out for delivery | Out for delivery | accent text / accent background |
| Delivered | Delivered | success text / success background |
| Failed | Failed | danger text / danger background |
| Approval awaiting review | Pending approval | warning text / warning background |
| Approved | Approved | success text / success background |
| Rejected | Rejected | danger text / danger background |

Use text plus a small consistent icon where helpful. Separate Urgent requested from Same-day accepted; an urgent tag is not a guarantee. Show the actual requested date in detail views, not only the relative word today. Display dates using Asia/Colombo and label time-sensitive cutoffs. Merchants can set urgency for individual orders or all applicable orders, with the scope clearly shown. Courier category labels remain Urgent and Conventional in signup and administration.

Public tracking shows a readable vertical timeline, latest update time, loading state, unknown-number state and reconnect feedback. Do not expose addresses, phone numbers, private proofs or internal issue notes. No live GPS map is required.

Warehouse views emphasize origin, destination, manifest reference, sent count, received count and discrepancies. Avoid implying a parcel is physically in its origin warehouse while a transfer is in progress.

## 10. Tables and reports

- Use semantic tables with a caption or associated heading and real header cells.
- Header surface: subtle background with 600-weight dark text.
- Row height: approximately 48-56px; allow growth when content wraps.
- Put text on the left, amounts on the right, and controls consistently at the end.
- Apply tabular numerals to amounts and metrics. Use LKR consistently and format money to two decimal places.
- Keep tracking numbers selectable and provide a labelled copy action.
- Provide filters, result counts, pagination and meaningful empty states.
- Sorting must communicate the active column and direction.
- Status colour is supplemental to text. Avoid colouring full rows for every status.
- On mobile, choose accessible cards for simple lists or a contained scrolling table for complex records.

## 11. Icons, motion and language

Use one shared SVG icon set selected by the frontend owner. Standard sizes: 16px inside compact controls, 20px for actions and 24px for navigation. Keep stroke weight consistent. Decorative icons are hidden from assistive technology; icon-only buttons have accessible names. Do not use emoji as operational status icons.

Use 150-200ms transitions for small interactions. Do not animate the gradient, continuously pulse urgent badges or use bounce effects. Disable nonessential motion when reduced motion is requested.

Use short, direct labels: Create delivery, Assign courier, Receive transfer, Confirm handover. Prefer explicit confirmation messages such as Delivery request created. For destructive actions, explain what will happen before confirmation.

## 12. Dispatcher AI assistant

Use a normal white panel with a small purple accent and the label AI suggestion. Show the issue summary, proposed action and reason separately. Give the Dispatcher visible Accept, Edit and Dismiss actions where applicable. Accept must lead through existing backend validation before a change is saved.

Show an unavailable state with manual resolution still accessible. Do not use celebratory effects or language that implies certainty. Never mix generated advice with verified parcel facts without labelling the distinction.

## 13. Angular styling organization

```text
frontend/
├── style.md
├── public/fonts/
└── src/
    ├── styles.scss
    ├── styles/
    │   ├── _tokens.scss
    │   ├── _typography.scss
    │   ├── _base.scss
    │   └── _utilities.scss
    └── app/
        ├── core/layout/
        ├── shared/components/
        └── features/
```

Load shared SCSS once from `styles.scss` using `@use`. Keep page-specific layout inside component SCSS. Prefer component host classes to global element overrides. Avoid `::ng-deep` and broad `!important` overrides. If a UI component library is introduced, theme it using its supported API and map its tokens to this guide. Do not mix several UI libraries.

The shared frontend owner maintains tokens, layout, typography and shared controls. Each member uses them for their assigned pages. Request a shared token/component change through a pull request instead of adding a slightly different copy in a feature folder.

## 14. Accessibility and review checklist

- [ ] Normal text reaches at least 4.5:1 contrast; qualifying large text reaches at least 3:1. Test rendered states, not just palette swatches.
- [ ] Essential control boundaries and state indicators remain distinguishable; verify non-text contrast against adjacent colours.
- [ ] All tasks work with the keyboard, with a visible unobscured focus indicator.
- [ ] Project touch targets are at least 44 by 44px where practical; adjacent actions have adequate spacing.
- [ ] Labels, errors, headings and dialogs expose correct semantics to assistive technology.
- [ ] Status and urgency never depend on colour alone.
- [ ] Loading, empty, error, success and disabled states are implemented.
- [ ] Screens work at mobile widths and with enlarged text.
- [ ] Forms and tables use shared tokens and components.
- [ ] Text is readable over its actual surface; dense operational content is not laid over the aura.
- [ ] No personal data appears in public tracking or user-facing exception text.

A palette or checklist does not establish WCAG conformance. Review the implemented screens and interactions.

## References

- Inter font and license: https://github.com/rsms/inter
- WCAG text contrast guidance: https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum.html
- WCAG non-text contrast guidance: https://www.w3.org/WAI/WCAG22/Understanding/non-text-contrast.html

Arctic Frost gradient colours and blend behavior are based on the user-provided design. Layout sizes, font scale and shared tokens are the proposed SwiftDrop team conventions.
