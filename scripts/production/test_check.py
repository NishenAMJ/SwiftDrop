import unittest
from check import SERVICES, container_problems, parse_rows, queue_problems


class CheckTests(unittest.TestCase):
    def test_missing_container_is_failure(self):
        self.assertEqual(len(container_problems([])), len(SERVICES))

    def test_running_without_health_is_not_success(self):
        rows = [{"Service": name, "State": "running", "Health": "healthy"} for name in SERVICES]
        self.assertEqual(container_problems(rows), [])
        rows[0]["Health"] = "unhealthy"
        self.assertEqual(len(container_problems(rows)), 1)

    def test_both_compose_json_formats(self):
        row = '{"Service":"web","State":"running","Health":"healthy"}'
        self.assertEqual(parse_rows(row), parse_rows("[" + row + "]"))
        self.assertEqual(len(parse_rows(row + "\n" + row)), 2)

    def test_dead_letters_and_stalled_consumers_are_failures(self):
        queue = {"name": "delivery.dlq", "messages_ready": 1,
                 "messages_unacknowledged": 0, "consumers": 0}
        self.assertEqual(len(queue_problems([queue], 1000)), 1)
        queue["name"] = "delivery"
        self.assertEqual(len(queue_problems([queue], 1000)), 1)
        queue["consumers"] = 1
        self.assertEqual(queue_problems([queue], 1000), [])
        queue["messages_unacknowledged"] = 1000
        self.assertEqual(len(queue_problems([queue], 1000)), 1)


if __name__ == "__main__":
    unittest.main()
