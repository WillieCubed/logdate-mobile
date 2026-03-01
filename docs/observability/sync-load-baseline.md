# Sync Load Test Baseline

## Run

```bash
SYNC_TOKEN="<token>" SYNC_BASE_URL="https://api.logdate.example/api/v1" ./tests/load/run-sync-load.sh
SYNC_TOKEN="<token>" SYNC_BASE_URL="https://api.logdate.example/api/v1" \
  SYNC_SUMMARY_OUTPUT="docs/observability/sync-load-baseline.json" \
  ./tests/load/run-sync-load.sh
```

## Record

Fill in the baseline after a stable run:

```
Date:
Environment:
Commit:
Target RPS:
Median latency (p50):
High latency (p95):
Error rate:
Conflict rate:
Notes:
```

Tip: when using `SYNC_SUMMARY_OUTPUT`, copy the relevant metrics into this section and keep the
JSON file for audit history.

## Acceptance targets (defaults)

- Error rate < 1%
- p95 < 1500ms for `sync/content/changes`
- p95 < 2500ms for uploads under 1MB
- Conflict rate < 1/min under normal load
