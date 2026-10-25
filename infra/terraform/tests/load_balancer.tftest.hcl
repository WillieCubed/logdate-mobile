mock_provider "google" {}
mock_provider "google-beta" {}

variables {
  project_id      = "logdate-contract-test"
  cloud_run_image = "example.invalid/logdate:test"
  service_name    = "logdate-server-staging"
  domains         = ["cloud-staging.logdate.app"]
  enable_services = false
}

run "sends_the_repository_event_stream_to_its_own_service" {
  command = plan

  variables {
    enable_load_balancer = true
  }

  assert {
    condition     = google_compute_region_network_endpoint_group.server[0].cloud_run[0].service == "logdate-server-staging"
    error_message = "Every path except the event stream must reach the main service."
  }

  assert {
    condition     = google_compute_region_network_endpoint_group.repo_event_stream[0].cloud_run[0].service == "logdate-server-staging-repo-events"
    error_message = "The event stream must reach the HTTP/1 service the deploy workflow creates, whose name the contract renderer derives the same way."
  }

  assert {
    condition = (
      length(google_compute_url_map.load_balancer[0].path_matcher[0].path_rule) == 1 &&
      google_compute_url_map.load_balancer[0].path_matcher[0].path_rule[0].paths == toset(["/xrpc/com.atproto.sync.subscribeRepos"])
    )
    error_message = "Only com.atproto.sync.subscribeRepos may leave the main service."
  }

  assert {
    condition     = one(google_compute_url_map.load_balancer[0].host_rule).hosts == toset(["cloud-staging.logdate.app"])
    error_message = "The load balancer must serve the configured domains."
  }

  assert {
    condition     = google_compute_backend_service.server[0].protocol == "HTTP2"
    error_message = "The main service keeps end-to-end HTTP/2 so uploads over 32 MiB still reach it."
  }

  assert {
    condition     = google_compute_backend_service.repo_event_stream[0].protocol == "HTTP"
    error_message = "The event stream service must use HTTP/1, the only protocol Cloud Run passes WebSockets over."
  }

  assert {
    condition     = google_certificate_manager_certificate.load_balancer[0].managed[0].domains == tolist(["cloud-staging.logdate.app"])
    error_message = "The certificate must cover the configured domains."
  }

  assert {
    condition     = google_compute_global_forwarding_rule.load_balancer_https[0].port_range == "443"
    error_message = "The load balancer must accept HTTPS."
  }
}

run "creates_nothing_until_enabled" {
  command = plan

  assert {
    condition = (
      length(google_compute_global_address.load_balancer) == 0 &&
      length(google_compute_url_map.load_balancer) == 0 &&
      length(google_certificate_manager_dns_authorization.load_balancer) == 0 &&
      length(google_compute_global_forwarding_rule.load_balancer_https) == 0
    )
    error_message = "Deployments without the load balancer must keep serving through the domain mapping."
  }
}
