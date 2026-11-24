# SwiftDrop production deployment and recovery

This setup targets one Linux server using Docker Compose 2.24.4 or newer. It serves
the Angular build with Caddy, forwards API requests to the gateway, and publishes
only ports 80 and 443. Preparing these files does not start Docker, deploy the app,
register a domain, or provision paid services.

Use the production wrapper for every operation. It combines `compose.yaml` with
`compose.production.yaml` and uses the separate `swiftdrop-prod` project name.
Running `docker compose up` alone starts the development configuration instead.

## Before the first deployment

Required server tools: Docker Engine, Docker Compose, Bash, Python 3 and OpenSSL.
Run the following commands in a Linux shell from the repository root. They are
instructions for deployment day, not commands that have already been run.

1. Point a domain's DNS records at the server. Allow inbound TCP 80 and 443, plus
   SSH restricted to administrators. Do not expose Docker's socket or service ports.
   This configuration assumes clients connect directly to Caddy; adding a CDN or
   another proxy requires reviewing the trusted forwarding-header arrangement.
2. Copy `.env.production.example` to `.env.production`. Fill every required blank.
   `APP_DOMAIN` is a hostname such as `delivery.example.com`, without a scheme/path.
   `ACME_EMAIL` is the certificate contact address. Use a real domain before launch.
3. Set unique database/broker passwords and a random `PARCEL_INTERNAL_API_KEY`.
   Use `openssl rand -hex 32` for each password/key. Do not reuse the example values
   from local development. Preserve the environment file in a restricted secret store.
4. Generate a persistent RSA signing key and webhook encryption key:

   ```bash
   umask 077
   mkdir -p secrets
   openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out secrets/identity-jwt.pem
   openssl rand -base64 32
   chmod 600 .env.production secrets/identity-jwt.pem
   ```

   Set `IDENTITY_JWT_PRIVATE_KEY_FILE` to the absolute path to that PEM file. Set
   `WEBHOOK_ENCRYPTION_KEY` to the generated base64 value. Generate these only once;
   overwriting the JWT key invalidates access tokens, and losing the webhook key
   makes existing encrypted webhook secrets unreadable. The JWT file is mounted
   read-only via Compose secrets. Missing/unreadable keys prevent startup rather
   than falling back to a temporary key in this configuration.
5. Set `BACKUP_DIR` to an absolute server directory, outside the repository if
   possible. Create it with mode 700. Choose separate encrypted off-server storage
   for copying completed backups; a directory on the same server is not disaster recovery.
6. Configure an authenticated SMTP account with STARTTLS on port 587 and a verified
   sender address. This template deliberately requires email settings and excludes
   Mailpit. It does not provision an email service. Port 465 implicit TLS requires
   changing the mail settings; it is not supported by the supplied STARTTLS template.
7. Set the initial dispatcher email/password for the first boot. After confirming
   the account exists and changing its password, remove the bootstrap password from
   the environment file and recreate identity. Leave Google credentials blank if unused.
8. Pin tested container image versions/digests in the release configuration and
   record the Git commit and image inventory. Current image tags follow supported
   series and can change; do not silently upgrade PostgreSQL/RabbitMQ during recovery.

Other passwords are passed as container environment variables because the existing
applications and database initialization scripts expect them. Docker administrators
can inspect those values. Restrict Docker access, protect the environment file,
and never paste `compose config` output from real deployment settings into tickets.
Secret values and backups are excluded from Git; only the blank template is tracked.

## Configuration checks without Docker Desktop or containers

These commands use dummy values and do not connect to a Docker engine:

```bash
python3 scripts/production/validate.py
python3 -m unittest discover -s scripts/production -p 'test_*.py'
for script in scripts/production/*.sh infrastructure/production/*.sh; do
  bash -n "$script"
done
```

If the Compose plugin is available as a standalone executable, pass its path using
`--compose-executable`. The validation checks the merged configuration, private
ports, restart/health settings, persistent volumes and rejection of missing required
values. CI runs these same checks. It does not prove containers will start or that
the complete application workflow works.

## Start the prepared release when ready

```bash
bash scripts/production/compose.sh config --quiet
bash scripts/production/compose.sh build
bash scripts/production/compose.sh up -d --wait --wait-timeout 300
python3 scripts/production/check.py --url https://YOUR_DOMAIN
```

Caddy obtains and renews certificates and redirects HTTP to HTTPS. Its certificate
state is persisted in `caddy_data`. Public API requests keep their paths and headers;
unknown API paths are not rewritten to Angular. Angular deep links return `index.html`.
Public requests to `/actuator`, `/internal` and `/dev` are rejected at Caddy.

Backend ports are removed from the merged configuration. Database, broker, parcel,
delivery, tracking and payment containers use an internal network. Identity and
notification also have outbound access for Google/SMTP/webhooks. The gateway shares
the edge network with Caddy and has no published host port. Only trusted application
containers belong on these networks; private Docker networking is not protection
against a compromised host/container or a Docker administrator.

Caddy replaces client-supplied forwarding headers. The gateway passes those values
through without appending its own HTTP address. Identity interprets them for HTTPS
redirects and per-client rate limiting. Before launch, test forged `Forwarded` and
`X-Forwarded-*` headers and two distinct client IPs. Never publish identity directly
while this trusted-proxy configuration is enabled.

## Health and operational visibility

Every active service has a health check and `unless-stopped` restart policy. Java
checks require an HTTP 200 and `UP` response from `/actuator/health`, rather than
merely an open TCP port. Database and broker use their native health commands.
Caddy's container check is local liveness; the optional public HTTPS check verifies
domain/TLS reachability. Neither is a full business transaction test.

```bash
bash scripts/production/compose.sh ps --all
bash scripts/production/compose.sh logs --since 15m --tail 200 api-gateway
bash scripts/production/compose.sh logs --since 15m --tail 200 notification-webhook-service
python3 scripts/production/check.py --url https://YOUR_DOMAIN --queue-threshold 1000
```

The check exits nonzero for missing/stopped/unhealthy services, failed queue checks,
nonempty dead-letter queues, queued messages without consumers, or excessive queue
backlogs. Schedule it every five minutes using your existing host scheduler and send
failures to your existing monitoring system. For a Linux server, an example cron entry is:

```cron
*/5 * * * * cd /opt/swiftdrop && /usr/bin/python3 scripts/production/check.py --url https://YOUR_DOMAIN 2>&1 | /usr/bin/logger -t swiftdrop-check
```

This records results in the host journal/syslog; it does not send email/SMS alerts.
Configure an external uptime monitor and alert destination separately before launch.
Also monitor server disk space, memory, backup age, and unexpected container restarts.
An unhealthy container is not automatically restarted by Docker: restart policies
handle process exits, and persistent unhealthy status requires investigation.

Container logs rotate at 10 MB with five files per container. Caddy access logging
is omitted to avoid retaining tracking numbers and OAuth codes in URLs; operational
and TLS errors remain available. Limit access to backend logs, which may contain
personal data. Avoid debug logging in production.

## Back up the database, proofs and broker

The backup script intentionally takes maintenance downtime. It stops running
applications (including the public web container), dumps PostgreSQL, stops RabbitMQ,
then archives delivery proofs and the broker's durable state. This avoids backing up
proof files while an upload is changing their corresponding database records.
On success it restarts the services that were running. It never deletes old backups.
On failure it leaves stopped services for explicit recovery and reports that fact.

```bash
bash scripts/production/backup.sh
```

Each UTC timestamped directory contains `database.dump`, `database.contents`,
`proofs.tar.gz`, `rabbitmq.tar.gz`, `manifest.txt`, `images.txt` and `SHA256SUMS`.
Check that the script reports completion, then copy the directory to encrypted
off-server storage. Preserve `.env.production`, the JWT key, webhook encryption key,
release commit and exact image versions separately. The broker archive includes
its Erlang cookie and must be treated as a secret.

Take a backup before upgrades and at least daily in an agreed maintenance window.
Choose retention according to recovery needs; an initial policy is seven daily
and four weekly copies. No scheduled backup job or deletion policy is installed
automatically by this repository. Measure backup downtime on representative data.

## Restore rehearsal on an isolated server

Restore into **new, empty volumes**, never over a live production deployment. The
following steps deliberately do not delete existing volumes. Use a separate host
or a distinct project name, the matching code/images, and restricted network access.
Do not permit restored workers to send real notifications or repeat external actions
during a rehearsal. Use test SMTP/webhook destinations and a staging domain.

```bash
export PROD_PROJECT=swiftdrop-restore
export PROD_ENV_FILE=/absolute/path/to/.env.restore
```

The restore environment uses the saved JWT/webhook keys, the original RabbitMQ user
and password, and `BACKUP_DIR` pointing at the downloaded backup directory's parent.
Database credentials can be supplied for the fresh cluster because initialization
creates the required service roles; the database dump itself excludes role passwords.
The stable RabbitMQ hostname and node name in the production Compose file must remain
`rabbitmq` and `rabbit@rabbitmq`, and the RabbitMQ image must match the backup.

```bash
# Substitute the actual timestamp folder. Verify checksums before reading archives.
bash scripts/production/compose.sh --profile operations run --rm --no-deps archive -ec \
  'cd /backup/20261123T120000Z && sha256sum -c SHA256SUMS'

# This creates only the fresh database and its service roles/schemas.
bash scripts/production/compose.sh up -d --wait postgres

# Run from Bash so binary input is not transformed by Windows PowerShell.
bash scripts/production/compose.sh exec -T postgres \
  pg_restore -U postgres -d swiftdrop --clean --if-exists --exit-on-error \
  < /absolute/backup/path/20261123T120000Z/database.dump

# The utility refuses to extract onto nonempty proof or broker volumes.
bash scripts/production/compose.sh --profile operations run --rm --no-deps archive -ec '
  test -z "$(ls -A /proofs)"
  test -z "$(ls -A /rabbitmq)"
  tar -xzpf /backup/20261123T120000Z/proofs.tar.gz -C /proofs
  tar -xzpf /backup/20261123T120000Z/rabbitmq.tar.gz -C /rabbitmq
'

bash scripts/production/compose.sh up -d --wait rabbitmq
# Start applications only after confirming the rehearsal's outbound destinations.
bash scripts/production/compose.sh up -d --wait --wait-timeout 300
python3 scripts/production/check.py --url https://YOUR_STAGING_DOMAIN
```

Check record counts and representative bookings, log in, download existing delivery
proofs, inspect merchant balances, and verify RabbitMQ queues/consumers. Replayed
events must not duplicate financial records. Record recovery duration and results.
Retain the original backup if a restore fails; do not retry over partially restored
volumes without first planning a clean isolated restore target.

## Updates and rollback

Run tests, take a completed backup, and record the current release before rebuilding.
Use the same environment file and project name to preserve volumes. Rebuild/recreate
with the production wrapper and rerun the health check. Do not run `down -v`.
Rollback to older application images only if their schema compatibility is confirmed;
otherwise use the isolated restore procedure and plan the traffic cutover. Restoring
a backup loses changes made after that backup, so reconcile any intervening deliveries
and cash records before resuming service.

## Remaining release gates

- Run a real container startup and Caddy configuration/TLS check when Docker is available.
- Rehearse the backup/restore sequence on disposable data; static validation cannot prove recovery.
- Verify login/refresh/logout, upload/download, webhook security, and the complete COD flow
  through the HTTPS entry point. Confirm forwarded headers cannot be spoofed.
- Identity outbox publishing remains a separate application gap. Deployment files do not implement it.
- COD and cancellation consumers remain explicitly off in the template until integration is
  verified. Set `PAYMENT_COD_CONSUMER_ENABLED=true` when the COD flow is ready; parcel
  verification and the shared key are already configured. Disabled consumers with queued
  messages are deliberately reported by the monitoring check.
- No real payment gateway or SMS provider is added. Parcel SMS statuses are empty in production.
  The COD-only application switch hides online checkout and blocks new online payment/refund
  operations in the backend. These Member 8 changes and their tests remain unverified;
  run the commands in the payment service README before release.
- Configure external alert delivery and encrypted off-server backups. This is a single-server
  deployment, so it does not provide high availability during host failure or maintenance.

## Configuration references

- [Docker Compose merge rules](https://docs.docker.com/reference/compose-file/merge/) explain
  the reset/override tags used to remove development ports and Mailpit dependencies.
- [Caddy reverse proxy](https://caddyserver.com/docs/caddyfile/directives/reverse_proxy) and
  [automatic HTTPS](https://caddyserver.com/docs/automatic-https) describe TLS and forwarding.
- [Spring Cloud Gateway header filters](https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/httpheadersfilters.html)
  document the forwarding filters configured for the private gateway.
