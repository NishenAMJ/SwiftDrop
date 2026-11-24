#!/usr/bin/env bash
# Linux host: Bash, Python 3, Docker Compose. Planned maintenance downtime.
set -euo pipefail
umask 077
script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
dc() { bash "$script_dir/compose.sh" "$@"; }
backup_root=$(dc --profile operations config --format json | python3 -c '
import json,sys
c=json.load(sys.stdin)
print(next(v["source"] for v in c["services"]["archive"]["volumes"] if v["target"] == "/backup"))')
[[ "$backup_root" == /* ]] || { echo "BACKUP_DIR must be an absolute Linux path" >&2; exit 1; }
run_id=$(date -u +%Y%m%dT%H%M%SZ)
destination="$backup_root/$run_id"
mkdir -p -- "$backup_root"
mkdir -- "$destination" # Refuse overwriting an existing backup.

# Pull the small archive utility before stopping anything.
dc --profile operations pull archive
running=$(dc ps --status running --services)
grep -qx postgres <<< "$running"
grep -qx rabbitmq <<< "$running"
apps=()
while IFS= read -r service; do
  case "$service" in postgres|rabbitmq|archive|'') ;; *) apps+=("$service");; esac
done <<< "$running"
resume_needed=false
on_exit() {
  result=$?
  if [[ "$resume_needed" == true ]]; then
    echo "Backup interrupted. Keep the partial directory for diagnosis." >&2
    echo "Recover services explicitly: bash scripts/production/compose.sh start --wait" >&2
  fi
  exit "$result"
}
trap on_exit EXIT
resume_needed=true
if (( ${#apps[@]} )); then dc stop "${apps[@]}"; fi

# No application writers: the dump and proof archive describe the same point in time.
dc exec -T postgres pg_dump -U postgres -d swiftdrop --format=custom > "$destination/database.dump"
dc exec -T postgres pg_restore --list < "$destination/database.dump" > "$destination/database.contents"
dc stop rabbitmq
dc --profile operations run --rm --no-deps archive -ec \
  'tar -czpf "/backup/$1/proofs.tar.gz" -C /proofs .; tar -czpf "/backup/$1/rabbitmq.tar.gz" -C /rabbitmq .' sh "$run_id"
printf 'created_utc=%s\nproject=%s\n' "$run_id" "${PROD_PROJECT:-swiftdrop-prod}" > "$destination/manifest.txt"
dc images > "$destination/images.txt"
dc --profile operations run --rm --no-deps archive -ec \
  'cd "/backup/$1"; sha256sum database.dump proofs.tar.gz rabbitmq.tar.gz manifest.txt images.txt > SHA256SUMS' sh "$run_id"
dc start --wait rabbitmq
if (( ${#apps[@]} )); then dc start --wait "${apps[@]}"; fi
resume_needed=false
echo "Backup complete: $destination"
echo "Copy it encrypted to separate storage; preserve the JWT and webhook keys separately."
