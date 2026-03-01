# Sync Metrics & Alerts

## Endpoints

### JSON snapshot
`GET /api/v1/sync/metrics`

- Auth required (Bearer token).
- Returns cumulative counters per operation.

### Prometheus text
`GET /api/v1/sync/metrics/prometheus`

- Auth required (Bearer token).
- Metrics:
  - `logdate_sync_conflicts_total`
  - `logdate_sync_operation_success_total{operation="..."}`
  - `logdate_sync_operation_error_total{operation="..."}`
  - `logdate_sync_operation_duration_ms_total{operation="..."}`
  - `logdate_sync_operation_bytes_total{operation="..."}`

## Example Prometheus scrape config

```yaml
scrape_configs:
  - job_name: logdate-sync
    metrics_path: /api/v1/sync/metrics/prometheus
    scheme: https
    authorization:
      credentials: "Bearer <service-token>"
    static_configs:
      - targets: ["api.logdate.example"]
```

## Alert rules

Default alert rules live in `docs/observability/sync-alerts.yaml`. Import or adapt them to your
Prometheus/Alertmanager setup.

## Dashboard

A starter Grafana dashboard is available in `docs/observability/sync-dashboard.json`.

## Dashboard notes

- Use operation labels to break down upload, update, delete, and changes.
- Track error rate and duration totals per endpoint.
- Consider per-user vs. aggregate metrics depending on token scope.
