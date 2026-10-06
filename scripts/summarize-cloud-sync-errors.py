#!/usr/bin/env python3
"""Summarize Cloud Run failures without emitting request URLs or log payloads."""
import collections
import json
import math
import re
import statistics
import sys
import urllib.parse


ROUTES = {'contents', 'journals', 'associations', 'media', 'backups', 'health'}


def route_for(value):
    if not isinstance(value, str):
        return 'other'
    try:
        path = urllib.parse.urlsplit(value).path.split('/')
    except ValueError:
        return 'other'
    if path[:3] == ['', 'api', 'v1'] and len(path) > 3 and path[3] in ROUTES:
        return path[3]
    return 'health' if path == ['', 'health'] else 'other'


def summarize(entries):
    if not isinstance(entries, list):
        raise ValueError('Expected a log entry list')
    platform = collections.Counter()
    requests = collections.Counter()
    latencies = []
    for entry in entries:
        if not isinstance(entry, dict):
            continue
        log_name = entry.get('logName', '')
        if isinstance(log_name, str) and '%2fvarlog%2fsystem' in log_name.lower():
            payload = entry.get('textPayload', '')
            message = payload.lower() if isinstance(payload, str) else ''
            if 'memory limit' in message or 'too much memory' in message:
                platform['memoryTermination'] += 1
            if 'failed to start' in message or 'startup probe failed' in message:
                platform['startupFailure'] += 1
            if 'no available instance' in message:
                platform['noAvailableInstance'] += 1
        request = entry.get('httpRequest')
        if not isinstance(request, dict):
            continue
        status = request.get('status')
        if type(status) is not int or not 500 <= status <= 599:
            continue
        requests[f'{route_for(request.get("requestUrl"))}:{status}'] += 1
        raw_latency = request.get('latency')
        if isinstance(raw_latency, str) and re.fullmatch(r'\d+(?:\.\d+)?s', raw_latency):
            latency = float(raw_latency[:-1])
            if math.isfinite(latency) and 0 <= latency <= 3600:
                latencies.append(latency)
    timing = {}
    if latencies:
        timing = {'median': round(statistics.median(latencies), 3), 'max': round(max(latencies), 3)}
    return {'platformFailures': dict(platform), 'failedRequests': dict(requests),
            'failedRequestLatencySeconds': timing}


if __name__ == '__main__':
    try:
        print(json.dumps(summarize(json.load(sys.stdin)), sort_keys=True))
    except (ValueError, TypeError, RecursionError):
        print('Could not summarize the supplied log entries.', file=sys.stderr)
        sys.exit(1)
