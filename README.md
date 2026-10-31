# SwiftDrop

## Frontend (Angular)

The web client lives in [`frontend/`](frontend) (Angular 22, standalone components, SCSS, Vitest).

### Prerequisites

- [Node.js](https://nodejs.org) LTS (v20.19+ or v22+)
- npm 11 (ships with recent Node; `npm -v` to check)

### Run the existing app

```bash
cd frontend
npm install
npm start          # ng serve -> http://localhost:4200
```

Other scripts: `npm run build`, `npm test`, `npm run watch`.

### Setting up Angular from scratch (only if `frontend/` is missing)

```bash
npm install -g @angular/cli
ng new frontend --style=scss --routing --ssr=false
cd frontend
ng serve
```

### Folder structure

```
frontend/src/app/
├── core/          # singletons: auth, layout (shell, header, guards, interceptors)
├── shared/        # reusable components, pipes, directives
└── features/      # one folder per role/domain
    ├── auth/  customer/  merchant/  courier/
    └── dispatcher/  warehouse/  tracking/  payments/  notifications/
```

Empty folders contain a `.gitkeep` so they are tracked by git; delete it once the folder has real files.

### Creating pages

Use the `create-frontend-page` Claude Code skill (`.claude/skills/create-frontend-page`) to generate pages from a given style guide.
