import importlib.util
import json
import pathlib
import unittest

SCRIPT = pathlib.Path(__file__).resolve().parents[2] / 'summarize-cloud-sync-errors.py'
spec = importlib.util.spec_from_file_location('cloud_errors', SCRIPT)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class CloudErrorSummaryTest(unittest.TestCase):
    def test_platform_memory_termination_and_request_failure_are_correlated_without_private_fields(self):
        entries = [
            {'logName': 'projects/private-project/logs/run.googleapis.com%2Fvarlog%2Fsystem',
             'textPayload': 'Memory limit of 512 MiB exceeded. private-marker',
             'resource': {'labels': {'instance_id': 'private-marker'}}},
            {'httpRequest': {'status': 503, 'requestUrl': 'https://private-marker/api/v1/contents/private-marker?token=private-marker',
                             'latency': '16.5s', 'userAgent': 'private-marker'},
             'textPayload': 'private-marker', 'jsonPayload': {'token': 'private-marker'}},
        ]
        result = module.summarize(entries)
        self.assertEqual(1, result['platformFailures'].get('memoryTermination', 0))
        self.assertEqual({'contents:503': 1}, result['failedRequests'])
        self.assertEqual(16.5, result['failedRequestLatencySeconds']['median'])
        self.assertNotIn('private-marker', json.dumps(result))
        self.assertNotIn('private-project', json.dumps(result))

    def test_private_application_text_is_not_treated_as_platform_evidence(self):
        result = module.summarize([{'logName': 'stdout', 'textPayload': 'Memory limit exceeded'}])
        self.assertEqual({}, result['platformFailures'])

    def test_unknown_paths_and_malformed_latency_cannot_enter_the_summary(self):
        result = module.summarize([
            {'httpRequest': {'status': 503, 'requestUrl': 'https://example.test/private-marker', 'latency': 'NaNs'}},
            {'httpRequest': {'status': 'private-marker', 'latency': 'private-marker'}},
            {'httpRequest': {'status': True, 'latency': '-1s'}},
        ])
        self.assertEqual({'other:503': 1}, result['failedRequests'])
        self.assertEqual({}, result['failedRequestLatencySeconds'])
        self.assertNotIn('private-marker', json.dumps(result))


if __name__ == '__main__':
    unittest.main()
