#!/usr/bin/env python3
"""Read one explicit report locally. No network access, discovery, repairs, or execution."""
import argparse
import collections
import json
import pathlib
import re
import sys
import uuid
import zipfile

MAX_REPORT = 256 * 1024
REPORT_FIELDS = {'reportId', 'schemaVersion', 'events', 'droppedEvents'}
ENUMS = {
    'route': set('UNKNOWN NOTES NOTE NOTE_CHANGES JOURNALS JOURNAL JOURNAL_CHANGES DRAFT DRAFT_CHANGES ASSOCIATIONS ASSOCIATION_CHANGES MEDIA MEDIA_ITEM MEDIA_BINARY BACKUPS BACKUP BACKUP_BINARY DIAGNOSTIC_REPORTS DIAGNOSTIC_REPORT'.split()),
    'code': set('PHASE_TRANSITION WORK_QUEUED ATTEMPT_STARTED REQUEST_COMPLETED PAGE_PERSISTED RECORD_APPLIED RETRY_SCHEDULED INTERRUPTED CONFLICT COMPLETED FAILED USER_RETRY'.split()),
    'phase': set('SCHEDULING IDENTITY UPLOAD FETCH PERSIST APPLY MEDIA ARCHIVE RESTORE RECOVERY'.split()),
    'outcome': set('QUEUED STARTED SUCCEEDED FAILED RETRY_SCHEDULED INTERRUPTED CONFLICT PAUSED'.split()),
    'reason': set('NONE OFFLINE SIGN_IN_REQUIRED SERVER_UNAVAILABLE RATE_LIMITED QUOTA_EXCEEDED LOCAL_STORAGE MISSING_MEDIA CORRUPT_PAYLOAD KEY_RECOVERY_REQUIRED INCOMPATIBLE_SERVER PERSISTENCE_FAILED UNSUPPORTED_FORMAT CONFLICT UNKNOWN'.split()),
    'action': set('NONE RETRY CONNECT SIGN_IN FREE_SPACE RECOVER_KEY UPDATE_SERVER UPDATE_APP REVIEW_CONFLICT CONTACT_SUPPORT'.split()),
}
IDS = {'runId', 'operationId', 'attemptId', 'requestId', 'recordAlias'}
COUNTERS = {'elapsedMs', 'attemptCount', 'pendingCount', 'durationMs', 'bytes'}
FLAGS = {'cursorAdvanced', 'retryable'}
EVENT_FIELDS = set(ENUMS) | IDS | COUNTERS | FLAGS | {'httpStatus', 'schemaVersion', 'context', 'frames'}


def reject_duplicates(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError('Duplicate field')
        result[key] = value
    return result


def valid_id(value):
    if not isinstance(value, str) or len(value) != 36:
        return False
    parsed = uuid.UUID(value)
    return str(parsed) == value and parsed.version == 4 and parsed.variant == uuid.RFC_4122


CONTEXT_ENUMS = {
    'platform': set('UNKNOWN ANDROID IOS DESKTOP SERVER'.split()),
    'network': set('UNKNOWN OFFLINE ONLINE'.split()),
    'scheduler': set('UNKNOWN RUNNING WAITING'.split()),
}
PROTOCOLS = set('SYNC_V1 RICH_DRAFTS_V1 MEDIA_V2 DIAGNOSTICS_V1'.split())
COMPONENTS = set('SYNC MEDIA ARCHIVE STORAGE NETWORK SERVER'.split())


def validate_context(context):
    if context is None:
        return
    if not isinstance(context, dict) or set(context) - (set(CONTEXT_ENUMS) | {'appBuild', 'appVersion', 'serverBuild', 'osVersion', 'protocols'}):
        raise ValueError('Invalid context')
    for key, choices in CONTEXT_ENUMS.items():
        if key in context and context[key] not in choices:
            raise ValueError('Invalid context classification')
    if context.get('appBuild') is not None and (type(context['appBuild']) is not int or not 0 <= context['appBuild'] <= 2147483647):
        raise ValueError('Invalid build')
    for key in ('appVersion', 'osVersion'):
        parts = context.get(key, [])
        if not isinstance(parts, list) or len(parts) > 4 or any(type(part) is not int or not 0 <= part <= 99999 for part in parts):
            raise ValueError('Invalid version')
    build = context.get('serverBuild')
    if build is not None and (not isinstance(build, str) or not re.fullmatch('[0-9a-f]{7,64}', build)):
        raise ValueError('Invalid server build')
    protocols = context.get('protocols', [])
    if not isinstance(protocols, list) or len(protocols) > len(PROTOCOLS) or any(type(value) is not str or value not in PROTOCOLS for value in protocols) or len(set(protocols)) != len(protocols):
        raise ValueError('Invalid protocols')


def validate_frames(frames):
    if not isinstance(frames, list) or len(frames) > 8:
        raise ValueError('Invalid frames')
    for frame in frames:
        if not isinstance(frame, dict) or set(frame) - {'component', 'line'} or frame.get('component') not in COMPONENTS:
            raise ValueError('Invalid frame')
        if frame.get('line') is not None and (type(frame['line']) is not int or not 1 <= frame['line'] <= 100000):
            raise ValueError('Invalid frame line')


def validate(report):
    if not isinstance(report, dict) or set(report) - REPORT_FIELDS:
        raise ValueError('Invalid report')
    if not valid_id(report.get('reportId')) or type(report.get('schemaVersion', 1)) is not int or report.get('schemaVersion', 1) != 1:
        raise ValueError('Unsupported report')
    if type(report.get('droppedEvents', 0)) is not int or not 0 <= report.get('droppedEvents', 0) <= 2147483647:
        raise ValueError('Invalid drop count')
    events = report.get('events', [])
    if not isinstance(events, list) or len(events) > 512:
        raise ValueError('Invalid event count')
    for event in events:
        if not isinstance(event, dict) or set(event) - EVENT_FIELDS or not {'phase', 'outcome'} <= set(event):
            raise ValueError('Invalid event')
        if type(event.get('schemaVersion', 1)) is not int or event.get('schemaVersion', 1) != 1:
            raise ValueError('Unsupported event')
        validate_context(event.get('context'))
        validate_frames(event.get('frames', []))
        for key, choices in ENUMS.items():
            if key in event and event[key] not in choices:
                raise ValueError('Unknown classification')
        for key in IDS:
            if event.get(key) is not None and not valid_id(event[key]):
                raise ValueError('Invalid reference')
        for key in COUNTERS:
            if key in event and (type(event[key]) is not int or not 0 <= event[key] <= (2147483647 if key in {'attemptCount', 'pendingCount'} else 9223372036854775807)):
                raise ValueError('Invalid counter')
        for key in FLAGS:
            if key in event and type(event[key]) is not bool:
                raise ValueError('Invalid flag')
        if event.get('httpStatus') is not None and (type(event['httpStatus']) is not int or not 100 <= event['httpStatus'] <= 599):
            raise ValueError('Invalid HTTP status')
    return events


def load(path):
    if not path.is_file() or path.stat().st_size > 2 * 1024 * 1024:
        raise ValueError('Input too large')
    if zipfile.is_zipfile(path):
        with zipfile.ZipFile(path) as archive:
            infos = archive.infolist()
            names = [entry.filename for entry in infos]
            if len(names) != len(set(names)) or set(names) - {'report.json', 'summary.md', 'events.jsonl', 'schema.json'}:
                raise ValueError('Unexpected archive entries')
            entry = archive.getinfo('report.json')
            if entry.file_size > MAX_REPORT:
                raise ValueError('Report too large')
            with archive.open(entry) as stream:
                raw = stream.read(MAX_REPORT + 1)
    else:
        with path.open('rb') as stream:
            raw = stream.read(MAX_REPORT + 1)
    if len(raw) > MAX_REPORT:
        raise ValueError('Report too large')
    return json.loads(raw, object_pairs_hook=reject_duplicates)


def inspect(report):
    events = validate(report)
    failures = collections.Counter(e['phase'] for e in events if e['outcome'] == 'FAILED')
    latest = {e['attemptId']: e for e in events if e.get('attemptId')}
    def correlation(event):
        return next(((key, event[key]) for key in ('operationId', 'attemptId', 'requestId')
                     if event.get(key)), None)
    latest_operations = {correlation(e): e for e in events if correlation(e)}
    active = [e for e in events if not correlation(e) or latest_operations[correlation(e)] is e]
    active = [e for e in active if e['outcome'] != 'SUCCEEDED']
    successes = [e for e in events if e['outcome'] == 'SUCCEEDED']
    actions_by_reason = {
        'OFFLINE': 'CONNECT', 'SIGN_IN_REQUIRED': 'SIGN_IN', 'LOCAL_STORAGE': 'FREE_SPACE',
        'KEY_RECOVERY_REQUIRED': 'RECOVER_KEY', 'INCOMPATIBLE_SERVER': 'UPDATE_SERVER', 'UNSUPPORTED_FORMAT': 'UPDATE_APP',
        'CONFLICT': 'REVIEW_CONFLICT', 'MISSING_MEDIA': 'CONTACT_SUPPORT',
        'CORRUPT_PAYLOAD': 'CONTACT_SUPPORT', 'SERVER_UNAVAILABLE': 'RETRY',
        'RATE_LIMITED': 'RETRY', 'PERSISTENCE_FAILED': 'FREE_SPACE',
    }
    actions = {e.get('action', 'NONE') for e in active}
    actions.update(actions_by_reason[e['reason']] for e in active
                   if e.get('action', 'NONE') == 'NONE' and e.get('reason') in actions_by_reason)
    if any(e['outcome'] in ('STARTED', 'INTERRUPTED') for e in active):
        actions.add('RETRY')
    actions.discard('NONE')
    findings = set()
    if any(e['outcome'] == 'STARTED' for e in latest.values()):
        findings.add('UNFINISHED_ATTEMPT')
    if any(e['outcome'] == 'INTERRUPTED' for e in events):
        findings.add('INTERRUPTION_OBSERVED')
    if any(e.get('attemptCount', 0) >= 3 and e['outcome'] == 'FAILED' for e in active):
        findings.add('REPEATED_FAILURE')
    reasons = {e.get('reason') for e in active}
    for reason, finding in [('MISSING_MEDIA', 'MISSING_ATTACHMENT'), ('KEY_RECOVERY_REQUIRED', 'KEY_RECOVERY_NEEDED'),
                            ('INCOMPATIBLE_SERVER', 'SERVER_UPGRADE_NEEDED'), ('UNSUPPORTED_FORMAT', 'APP_UPGRADE_NEEDED'), ('PERSISTENCE_FAILED', 'LOCAL_PERSISTENCE_FAILURE')]:
        if reason in reasons:
            findings.add(finding)
    if any(e.get('cursorAdvanced') and e['outcome'] == 'FAILED' for e in events):
        findings.add('CURSOR_FAILURE_REQUIRES_REVIEW')
    pending = [e.get('pendingCount', 0) for e in events]
    if len(pending) >= 3 and pending[-1] > 0 and min(pending[-3:]) == max(pending[-3:]):
        findings.add('POSSIBLY_STALLED_QUEUE')
    return {
        'schemaVersion': 1,
        'observedFacts': {'eventCount': len(events), 'failureCountsByPhase': dict(failures), 'droppedEvents': report.get('droppedEvents', 0),
                          'lastSuccessfulPhase': successes[-1]['phase'] if successes else None,
                          'lastObservedPhase': events[-1]['phase'] if events else None,
                          'lastObservedOutcome': events[-1]['outcome'] if events else None},
        'findings': sorted(findings),
        'inferredCauses': ['An unfinished attempt may indicate interruption or missing retained events.'] if 'UNFINISHED_ATTEMPT' in findings else [],
        'missingEvidence': ['This report does not prove content equality, media integrity, or current server state.',
                            'History is bounded; an absent completion event does not prove that work never completed.'],
        'suggestedActions': sorted(actions),
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--report', type=pathlib.Path, required=True)
    parser.add_argument('--json', action='store_true')
    args = parser.parse_args()
    try:
        result = inspect(load(args.report))
    except (OSError, ValueError, TypeError, KeyError, zipfile.BadZipFile, RuntimeError):
        sys.stderr.write('Invalid, unreadable, or unsupported diagnostic report.\n')
        return 2
    if args.json:
        sys.stdout.write(json.dumps(result, sort_keys=True) + '\n')
    else:
        for heading, value in result.items():
            sys.stdout.write(f'{heading}: {json.dumps(value, sort_keys=True)}\n')
    return 0


if __name__ == '__main__':
    sys.exit(main())
