"""Validate the merged production model using dummy values. No Docker engine needed."""
import argparse
import json
import os
import pathlib
import re
import subprocess

ROOT = pathlib.Path(__file__).resolve().parents[2]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--compose-executable", help="Optional standalone docker-compose executable")
    args = parser.parse_args()
    command = [args.compose_executable] if args.compose_executable else ["docker", "compose"]
    command += ["--project-name", "swiftdrop-validation", "--env-file", ".env.production.example",
                "-f", "compose.yaml", "-f", "compose.production.yaml",
                "--profile", "operations", "--profile", "development-mail", "config", "--format", "json"]
    env = os.environ.copy()
    for name in ("compose.yaml", "compose.production.yaml"):
        for key in re.findall(r"\$\{([A-Z_][A-Z_0-9]*)", (ROOT / name).read_text()):
            env.pop(key, None)
    # Never load .env or real deployment secrets into the validation output.
    for line in (ROOT / ".env.production.example").read_text().splitlines():
        if line and not line.startswith("#") and "=" in line:
            key, value = line.split("=", 1)
            env[key] = value
    for key in ("POSTGRES_ADMIN_PASSWORD", "IDENTITY_DB_PASSWORD", "PARCEL_DB_PASSWORD",
                "TRACKING_DB_PASSWORD", "PAYMENT_DB_PASSWORD", "DELIVERY_DB_PASSWORD",
                "COMMUNICATION_DB_PASSWORD", "RABBITMQ_PASSWORD", "PARCEL_INTERNAL_API_KEY",
                "SMTP_PASSWORD"):
        env[key] = "validation-only-not-a-production-secret"
    env.update(APP_DOMAIN="delivery.example.com", ACME_EMAIL="ops@example.com",
               IDENTITY_JWT_PRIVATE_KEY_FILE=str(ROOT / "secrets" / "validation-only.pem"),
               WEBHOOK_ENCRYPTION_KEY="AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
               BACKUP_DIR=str(ROOT / "backups"), SMTP_HOST="smtp.example.com",
               SMTP_USERNAME="validation", NOTIFICATION_FROM_EMAIL="noreply@example.com")
    result = subprocess.run(command, cwd=ROOT, env=env, capture_output=True, text=True, timeout=60)
    if result.returncode:
        raise SystemExit("Compose validation failed (dummy values only):\n" + result.stderr)
    model = json.loads(result.stdout)
    services = model["services"]
    for name, service in services.items():
        if name != "web":
            assert not service.get("ports"), f"{name} exposes host ports"
        if name not in {"mailpit", "archive"}:
            assert service["restart"] == "unless-stopped", name
            assert "healthcheck" in service, name
            assert service["logging"]["options"]["max-size"] == "10m", name
    assert {p["published"] for p in services["web"]["ports"]} == {"80", "443"}
    assert model["networks"]["backend"]["internal"] is True
    for name in ("postgres", "rabbitmq", "parcel-pricing-service",
                 "delivery-operations-service", "tracking-reporting-service", "payment-settlement-service"):
        assert set(services[name]["networks"]) == {"backend"}, name
    assert set(services["web"]["networks"]) == {"edge"}
    assert "development-mail" in services["mailpit"]["profiles"]
    notification = services["notification-webhook-service"]
    assert "mailpit" not in notification["depends_on"]
    assert notification["environment"]["WEBHOOK_ALLOW_PRIVATE_ADDRESSES"] == "false"
    assert notification["environment"]["WEBHOOK_ALLOW_HTTP"] == "false"
    assert notification["environment"]["MAIL_HOST"] == "smtp.example.com"
    identity = services["identity-merchant-service"]
    assert identity["environment"]["IDENTITY_COOKIE_SECURE"] == "true"
    assert identity["environment"]["IDENTITY_JWT_PRIVATE_KEY_PATH"] == "/run/secrets/identity-jwt.pem"
    assert identity["secrets"][0]["target"] == "identity-jwt.pem"
    gateway_headers = json.loads(services["api-gateway"]["environment"]["SPRING_APPLICATION_JSON"])
    assert gateway_headers["spring.cloud.gateway.server.webflux.x-forwarded.enabled"] is False
    assert gateway_headers["spring.cloud.gateway.server.webflux.forwarded.enabled"] is False
    sms = json.loads(notification["environment"]["SPRING_APPLICATION_JSON"])
    assert sms["swiftdrop"]["notification"]["parcel"]["sms-statuses"] == []
    proof_mounts = services["delivery-operations-service"]["volumes"]
    assert any(v["source"] == "delivery_proofs" and v["target"] == "/data/proofs" for v in proof_mounts)
    assert services["rabbitmq"]["hostname"] == "rabbitmq"
    for key in ("APP_DOMAIN", "POSTGRES_ADMIN_PASSWORD", "IDENTITY_JWT_PRIVATE_KEY_FILE",
                "WEBHOOK_ENCRYPTION_KEY", "SMTP_PASSWORD"):
        missing = env.copy()
        missing[key] = ""
        rejected = subprocess.run(command, cwd=ROOT, env=missing, capture_output=True, text=True, timeout=60)
        assert rejected.returncode != 0, f"Missing {key} was accepted"
    print("Production Compose checks passed: private ports, health checks, persistence, secrets and fail-closed inputs.")


if __name__ == "__main__":
    main()
