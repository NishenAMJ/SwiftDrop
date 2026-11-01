# SwiftDrop

SwiftDrop is a delivery platform with customers, merchants, couriers and dispatchers. It is built as separate backend services (Spring Boot) plus an Angular frontend.

## Repository layout

| Folder | What is in it |
|---|---|
| `services/` | Backend services. Currently `identity-merchant-service` (port 8081) |
| `frontend/` | Angular frontend (port 4200) |
| `infrastructure/` | Docker/database setup, for example `postgres/init` (runs when the database is first created) |
| `contracts/` | API contracts shared between services |
| `docs/` | Documentation, including [prerequisites](docs/contributions/prerequisites.md) |
| `compose.yaml` | Starts PostgreSQL in Docker |

## 1. Install the tools

You need Git, Java JDK 21, Docker Desktop and (for the frontend) Node.js 24 LTS. Full list and versions: [docs/contributions/prerequisites.md](docs/contributions/prerequisites.md).

Check they work:

```powershell
git --version
java -version      # must say 21
docker --version
node -v            # only needed for the frontend
```

## 2. Get the code

```powershell
git clone <repository-url>
cd SwiftDrop
```

## 3. Create your `.env` file

The `.env` file holds the database passwords. It is **never committed** to Git, so each person creates their own.

```powershell
Copy-Item .env.example .env        # macOS/Linux: cp .env.example .env
```

Open `.env` and set a value for both lines:

```
POSTGRES_ADMIN_PASSWORD=choose-a-password
IDENTITY_DB_PASSWORD=choose-another-password
```

Each person can pick their own passwords, because the database runs only on their own computer.

## 4. Start the database

```powershell
docker compose up -d
docker compose ps
```

Wait until the `postgres` status says `healthy`. The first start creates the `swiftdrop` database, the `identity_merchant_user` login and the `identity_merchant` schema.

## 5. Run the identity-merchant service

The service automatically loads the root `.env` file when launched from the service directory or the SwiftDrop root. No terminal password setup is needed. Use plain, unquoted `KEY=value` entries (Java properties format), without `export` prefixes or inline comments.

Windows (PowerShell):

```powershell
cd services\identity-merchant-service
.\mvnw spring-boot:run
```

macOS/Linux:

```bash
cd services/identity-merchant-service
./mvnw spring-boot:run
```

On start, Flyway creates the tables (for example `roles`) from `src/main/resources/db/migration`. The service is ready when you see `Started ... in ... seconds` in the log, on http://localhost:8081.

If you run it from an IDE, set the working directory to the service directory or the SwiftDrop root. Other working directories require explicit environment variables.

Environment variables override `.env`. If you previously set an outdated password in PowerShell, clear it with `Remove-Item Env:IDENTITY_DB_PASSWORD -ErrorAction SilentlyContinue` before restarting.

## 6. Check that everything worked

After step 4 (database running), confirm the `identity_merchant` schema exists:

```powershell
docker compose exec postgres psql -U postgres -d swiftdrop -c "\dn"
```

After step 5 (service started once), confirm the roles were created:

```powershell
docker compose exec postgres psql -U postgres -d swiftdrop -c "SELECT * FROM identity_merchant.roles;"
```

You should see four rows: CUSTOMER, MERCHANT, COURIER and DISPATCHER.

## 7. Run the frontend

The web client lives in [`frontend/`](frontend) (Angular, standalone components, SCSS, Vitest). It does not need the database to start.

```bash
cd frontend
npm install
npm start          # ng serve -> http://localhost:4200
```

Other scripts: `npm run build`, `npm test`, `npm run watch`.

If `ng` reports a Node.js version error, upgrade Node (see [prerequisites](docs/contributions/prerequisites.md)).

Folder structure:

```
frontend/src/app/
├── core/          # singletons: auth, layout (shell, header, guards, interceptors)
├── shared/        # reusable components, pipes, directives
└── features/      # one folder per role/domain
    ├── auth/  customer/  merchant/  courier/
    └── dispatcher/  warehouse/  tracking/  payments/  notifications/
```

Empty folders contain a `.gitkeep` so Git tracks them; delete it once the folder has real files.

To create new pages, use the `create-frontend-page` skill in `.claude/skills/create-frontend-page`, and follow [frontend/style.md](frontend/style.md).

## Optional settings

The service connects to `jdbc:postgresql://localhost:5432/swiftdrop` as `identity_merchant_user` by default. To change that, set `IDENTITY_DB_URL` or `IDENTITY_DB_USER`.

## Everyday commands

| Task | Command |
|---|---|
| Stop the database (keeps data) | `docker compose stop` |
| Start it again | `docker compose up -d` |
| See database logs | `docker compose logs -f postgres` |
| Open a SQL prompt | `docker compose exec postgres psql -U postgres -d swiftdrop` |
| **Wipe the database and start clean** | `docker compose down -v` then `docker compose up -d` |

## Troubleshooting

**"password authentication failed for user identity_merchant_user"**
The configured password differs from the one the database was created with. Clear any outdated terminal override. The database keeps its original password even if you edit `.env` later. To reset it without deleting data, run `docker compose exec postgres psql -U postgres -d swiftdrop` from the root, enter `\password identity_merchant_user`, supply the password from `.env` at the prompts, then enter `\q` and restart the service.

**"Could not resolve placeholder 'IDENTITY_DB_PASSWORD'"**
Check that the root `.env` contains `IDENTITY_DB_PASSWORD` and the working directory is the service directory or the SwiftDrop root. For other working directories, set the variable in the terminal or IDE run configuration.

**"schema identity_merchant does not exist" or "role identity_merchant_user does not exist"**
The database was created before the setup script existed or failed. Run `docker compose down -v`, then `docker compose up -d`. The scripts in `infrastructure/postgres/init` run only when the database is created from empty.

**"Set POSTGRES_ADMIN_PASSWORD" when running docker compose**
`.env` is missing, is not in the project root (next to `compose.yaml`), or the value is empty.

**Port 5432 already in use**
Another PostgreSQL is running on your machine. Stop it, or change the left-hand port in `compose.yaml` (for example `"127.0.0.1:5433:5432"`) and set `IDENTITY_DB_URL` to match.

## Rules for contributors

- Never commit `.env` or real passwords.
- Database changes go in a new Flyway file such as `V2__create_users.sql`. Never edit a migration that has already been merged.
- Frontend work must follow [frontend/style.md](frontend/style.md).
- Don't commit build output (`dist/`, `.angular/`, `node_modules/`).
