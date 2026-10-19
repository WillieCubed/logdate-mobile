mock_provider "google" {}
mock_provider "google-beta" {}

variables {
  project_id               = "logdate-contract-test"
  cloud_run_image          = "example.invalid/logdate:test"
  enable_services          = false
  enable_artifact_registry = false
  enable_cloud_run_service = false
}

run "no_monitor_without_a_canary_account" {
  command = plan

  assert {
    condition     = length(google_monitoring_uptime_check_config.handle_resolution) == 0 && length(google_monitoring_alert_policy.handle_resolution_failing) == 0
    error_message = "The handle resolution monitor must stay absent until a canary account is configured."
  }
}

run "monitor_checks_the_handle_host_for_its_exact_did" {
  command = plan

  variables {
    handle_resolution_monitor = {
      handle = "resolution-canary.logdate.app"
      did    = "did:plc:abcdefghijklmnopqrstuvwx"
    }
    handle_resolution_notification_channels = ["projects/logdate-contract-test/notificationChannels/123"]
  }

  override_resource {
    target          = google_monitoring_uptime_check_config.handle_resolution
    override_during = plan
    values = {
      uptime_check_id = "handle-resolution-check"
    }
  }

  assert {
    condition = (
      google_monitoring_uptime_check_config.handle_resolution[0].monitored_resource[0].labels.host == "resolution-canary.logdate.app" &&
      google_monitoring_uptime_check_config.handle_resolution[0].http_check[0].path == "/.well-known/atproto-did" &&
      google_monitoring_uptime_check_config.handle_resolution[0].http_check[0].use_ssl &&
      google_monitoring_uptime_check_config.handle_resolution[0].http_check[0].validate_ssl
    )
    error_message = "The monitor must fetch https://<handle>/.well-known/atproto-did with certificate validation."
  }

  assert {
    condition = (
      google_monitoring_uptime_check_config.handle_resolution[0].content_matchers[0].matcher == "MATCHES_REGEX" &&
      google_monitoring_uptime_check_config.handle_resolution[0].content_matchers[0].content == "^did:plc:abcdefghijklmnopqrstuvwx\\s*$"
    )
    error_message = "The monitor must require the response body to be exactly the configured DID."
  }

  assert {
    condition = (
      strcontains(google_monitoring_alert_policy.handle_resolution_failing[0].conditions[0].condition_threshold[0].filter, "metric.labels.check_id = \"handle-resolution-check\"") &&
      google_monitoring_alert_policy.handle_resolution_failing[0].notification_channels == tolist(["projects/logdate-contract-test/notificationChannels/123"])
    )
    error_message = "The alert must watch this uptime check and notify the configured channels."
  }
}

run "did_web_dots_are_matched_literally" {
  command = plan

  variables {
    handle_resolution_monitor = {
      handle = "resolution-canary.logdate.app"
      did    = "did:web:resolution-canary.logdate.app"
    }
  }

  assert {
    condition     = google_monitoring_uptime_check_config.handle_resolution[0].content_matchers[0].content == "^did:web:resolution-canary\\.logdate\\.app\\s*$"
    error_message = "Dots in a did:web identifier must not match arbitrary characters."
  }
}

run "rejects_a_monitor_without_a_valid_did" {
  command = plan

  variables {
    handle_resolution_monitor = {
      handle = "resolution-canary.logdate.app"
      did    = "resolution-canary.logdate.app"
    }
  }

  expect_failures = [var.handle_resolution_monitor]
}
