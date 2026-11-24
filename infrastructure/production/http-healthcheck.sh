#!/usr/bin/env bash
set -euo pipefail
# Uses Bash/coreutils already present in the Temurin runtime, not curl.
exec 3<>"/dev/tcp/127.0.0.1/${HEALTH_PORT:?HEALTH_PORT is required}"
printf 'GET /actuator/health HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n' >&3
response=$(cat <&3)
[[ "$response" == HTTP/1.1\ 200* || "$response" == HTTP/1.0\ 200* ]]
grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"' <<< "$response"
