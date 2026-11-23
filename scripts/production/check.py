"""Read-only deployment check. Prints problems and exits nonzero for a scheduler."""
import argparse
import json
import pathlib
import subprocess
import sys
import urllib.error
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[2]
SERVICES = {
    "postgres", "rabbitmq", "identity-merchant-service", "parcel-pricing-service",
    "delivery-operations-service", "tracking-reporting-service",
    "payment-settlement-service", "notification-webhook-service", "api-gateway", "web",
}


def parse_rows(text):
    text = text.strip()
    if not text:
        return []
    if text.startswith("["):
        return json.loads(text)
    return [json.loads(line) for line in text.splitlines() if line.strip()]


def container_problems(rows):
    problems = []
    found = set()
    for row in rows:
        name = row.get("Service")
        if name not in SERVICES:
            continue
        found.add(name)
        if row.get("State") != "running" or row.get("Health") != "healthy":
            problems.append(f"{name}: state={row.get('State')}, health={row.get('Health')}")
    problems.extend(f"{name}: missing container" for name in sorted(SERVICES - found))
    return problems


def queue_problems(rows, threshold):
    problems = []
    for row in rows:
        name = row["name"]
        ready = int(row["messages_ready"])
        unacked = int(row["messages_unacknowledged"])
        dead_letter = "dlq" in name.lower() or "dead" in name.lower()
        if ready + unacked > threshold or (dead_letter and ready + unacked > 0):
            problems.append(f"queue {name}: ready={ready}, unacknowledged={unacked}")
        elif ready and not int(row["consumers"]) and not dead_letter:
            problems.append(f"queue {name}: {ready} waiting messages and no consumer")
    return problems


def dc(*args):
    result = subprocess.run(
        ["bash", str(ROOT / "scripts/production/compose.sh"), *args],
        cwd=ROOT, text=True, capture_output=True, timeout=90,
    )
    if result.returncode:
        # Do not echo arbitrary command output that might contain secrets.
        raise RuntimeError(f"Compose {args[0]} failed; inspect the deployment locally")
    return result.stdout


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--url", help="Public HTTPS URL, e.g. https://delivery.example.com")
    parser.add_argument("--queue-threshold", type=int, default=1000)
    args = parser.parse_args()
    if args.queue_threshold < 0:
        parser.error("--queue-threshold must be nonnegative")
    problems = []
    try:
        problems.extend(container_problems(parse_rows(dc("ps", "--all", "--format", "json"))))
        queues = dc("exec", "-T", "rabbitmq", "rabbitmqctl", "-q", "list_queues",
                    "name", "messages_ready", "messages_unacknowledged", "consumers", "--formatter=json")
        problems.extend(queue_problems(json.loads(queues), args.queue_threshold))
        if args.url:
            if not args.url.startswith("https://"):
                raise ValueError("--url must use HTTPS")
            with urllib.request.urlopen(args.url, timeout=15) as response:
                if response.status != 200:
                    problems.append(f"Public site returned HTTP {response.status}")
    except (OSError, RuntimeError, ValueError, KeyError, subprocess.TimeoutExpired) as error:
        problems.append(f"Check failed: {error}")
    if problems:
        print("\n".join(problems), file=sys.stderr)
        return 1
    print("All required containers healthy; queue checks passed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
