"""Exercise the real health probe against local HTTP responses, without Docker."""
import http.server
import os
import pathlib
import shutil
import subprocess
import threading
import unittest

PROBE = pathlib.Path(__file__).resolve().parents[2] / "infrastructure/production/http-healthcheck.sh"
BASH = os.environ.get("BASH_EXE") or shutil.which("bash")


@unittest.skipUnless(BASH, "Bash is required")
class HealthcheckTests(unittest.TestCase):
    def probe(self, status, body):
        class Handler(http.server.BaseHTTPRequestHandler):
            def do_GET(self):
                if self.path != "/actuator/health":
                    self.send_error(404)
                    return
                self.send_response(status)
                self.end_headers()
                self.wfile.write(body.encode())

            def log_message(self, *args):
                pass

        with http.server.HTTPServer(("127.0.0.1", 0), Handler) as server:
            worker = threading.Thread(target=server.serve_forever, daemon=True)
            worker.start()
            try:
                env = dict(os.environ, HEALTH_PORT=str(server.server_port))
                return subprocess.run([BASH, str(PROBE)], env=env, capture_output=True, timeout=10).returncode
            finally:
                server.shutdown()
                worker.join()

    def test_healthy_json(self):
        self.assertEqual(self.probe(200, '{"status":"UP"}'), 0)

    def test_unhealthy_json(self):
        self.assertNotEqual(self.probe(503, '{"status":"DOWN","components":{"db":{"status":"UP"}}}'), 0)

    def test_open_http_port_is_not_enough(self):
        self.assertNotEqual(self.probe(200, "not an actuator response"), 0)


if __name__ == "__main__":
    unittest.main()
