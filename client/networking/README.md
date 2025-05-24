# `:client:networking`

**Network layer implementation with Ktor**

## Overview

Handles all network communications including API calls, authentication, and network state monitoring. Provides offline-first networking with retry mechanisms and intelligent request handling.

## Architecture

```
Networking Module
├── HTTP Client (Ktor)
├── API Service Implementations
├── Network Availability Monitoring
├── Request/Response Interceptors
└── Platform-specific Clients
```

## Key Components

### Core Networking
- `Client.kt` - Common HTTP client interface
- `NetworkAvailabilityMonitor.kt` - Network state monitoring
- `NetworkState.kt` - Network state definitions

### Platform Implementations
- `Client.android.kt` - Android HTTP client with OkHttp
- `Client.ios.kt` - iOS HTTP client with Darwin
- `Client.jvm.kt` - Desktop HTTP client
- `Client.wasmJs.kt` - Web client implementation

### Network Monitoring
- `AndroidNetworkAvailabilityMonitor.kt` - Android connectivity
- `DesktopNetworkAvailabilityMonitor.kt` - Desktop connectivity
- Real-time network state updates

## Features

### HTTP Client Configuration
- Platform-optimized client implementations
- Automatic request/response serialization
- Authentication header management
- Request timeout configuration

### Network State Management
- Real-time connectivity monitoring
- Network type detection (WiFi, cellular, etc.)
- Bandwidth-aware request scheduling
- Offline state handling

### Request Handling
- Automatic retry mechanisms
- Request queuing for offline scenarios
- Response caching strategies
- Error handling and recovery

## Dependencies

### Core Dependencies
- **Ktor Client**: HTTP client framework
- **Kotlinx Serialization**: JSON serialization
- **Kotlinx Coroutines**: Async operations

### Platform Dependencies
- **Android**: OkHttp engine
- **iOS**: Darwin engine
- **JVM**: CIO engine
- **Web**: JS engine

## Network Architecture

### Request Flow
```
Repository
    ↓
HTTP Client
    ↓
Network Interceptors
    ↓
Platform Engine
    ↓
Remote API
```

### Offline Handling
```
Request → Network Check → Cache/Queue → Retry → Response
                ↓              ↓         ↓
            Offline        Local Cache  Success
```

## Configuration

### Client Setup
```kotlin
val httpClient = HttpClient(platformEngine) {
    install(ContentNegotiation) {
        json()
    }
    install(Logging)
    install(HttpTimeout)
}
```

### Network Monitoring
```kotlin
networkMonitor.networkState.collect { state ->
    when (state) {
        is NetworkState.Available -> enableSync()
        is NetworkState.Unavailable -> disableSync()
    }
}
```

## Testing

### Test Structure
The networking module includes comprehensive test coverage with 66 test cases across 5 test files:

```
networking/src/commonTest/kotlin/
├── HttpClientTest.kt (14 tests)
├── NetworkAvailabilityMonitorTest.kt (12 tests)  
├── NetworkStateTest.kt (14 tests)
├── NetworkErrorHandlingTest.kt (16 tests)
├── PlatformSpecificNetworkingTest.kt (10 tests)
└── fakes/
    └── FakeNetworkAvailabilityMonitor.kt
```

### Test Coverage

#### HTTP Client Testing (`HttpClientTest.kt`)
- **JSON Serialization**: Tests request/response JSON parsing and content negotiation
- **HTTP Methods**: Validates GET, POST, PUT, DELETE operations with MockEngine
- **Error Handling**: Tests various HTTP status codes (4xx, 5xx) and network failures
- **Timeout Handling**: Validates request timeout behavior and configuration
- **Configuration**: Tests client setup, logging, and plugin installation

#### Network Monitoring Testing (`NetworkAvailabilityMonitorTest.kt`)
- **State Changes**: Tests network state transitions and real-time updates
- **Interface Compliance**: Validates contract implementation across platforms
- **Flow Behavior**: Tests Flow emissions and subscription handling
- **Timestamp Accuracy**: Validates state change timing and metadata

#### Network State Testing (`NetworkStateTest.kt`)
- **Sealed Interface**: Tests state definitions and type safety
- **Equality & Hashing**: Validates proper data class behavior
- **Immutability**: Tests state object consistency
- **Timestamp Handling**: Tests instant-based state tracking

#### Error Handling Testing (`NetworkErrorHandlingTest.kt`)
- **HTTP Status Codes**: Tests 400, 401, 403, 404, 500, 503 error scenarios
- **Network Exceptions**: Tests connectivity, timeout, and DNS failures
- **Retry Mechanisms**: Validates exponential backoff and retry strategies
- **Circuit Breaker**: Tests failure thresholds and recovery patterns

#### Platform-Specific Testing (`PlatformSpecificNetworkingTest.kt`)
- **Expect/Actual Pattern**: Tests platform interface consistency
- **Engine Configuration**: Validates platform-specific HTTP client setup
- **Cross-Platform Behavior**: Tests common functionality across platforms
- **Interface Contracts**: Validates platform implementation compliance

### Test Infrastructure

#### Mock Engine Setup
```kotlin
val mockEngine = MockEngine { request ->
    respond(
        content = ByteReadChannel("""{"key": "value"}"""),
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json")
    )
}
```

#### Fake Network Monitor
```kotlin
class FakeNetworkAvailabilityMonitor : NetworkAvailabilityMonitor {
    fun setConnected() { /* Controllable state changes */ }
    fun setDisconnected() { /* For deterministic testing */ }
}
```

### Running Tests

#### All Tests

From the project root, run:

```bash
./gradlew :client:networking:test
```

#### Specific Test Classes

From the project root, run:

```bash
./gradlew :client:networking:test --tests "HttpClientTest"
./gradlew :client:networking:test --tests "NetworkErrorHandlingTest"
```

#### Test Reports
Test coverage reports are generated at:
```
client/networking/build/reports/tests/test/index.html
```

### Test Dependencies
```kotlin
// HTTP Client Mocking
implementation(libs.ktor.client.mock)

// Coroutine Testing
implementation(libs.kotlinx.coroutines.test)

// Dependency Injection Testing
implementation(libs.koin.test)

// Test Framework
implementation(libs.kotlin.test)
```

### Best Practices

#### Mock Data
- Use realistic JSON response structures
- Test both success and error scenarios
- Include edge cases (empty responses, malformed JSON)

#### Async Testing
```kotlin
@Test
fun testNetworkCall() = runTest {
    val result = httpClient.get("https://example.com/data")
    assertEquals(HttpStatusCode.OK, result.status)
}
```

#### State Testing
```kotlin
@Test
fun testNetworkStateChanges() = runTest {
    val monitor = FakeNetworkAvailabilityMonitor()
    monitor.setConnected()
    assertTrue(monitor.networkState.value is NetworkState.Connected)
}
```

## TODOs

### Core Features
- [ ] Implement request caching strategies
- [ ] Implement retry strategies with exponential backoff
- [ ] Implement certificate pinning for security
- [ ] Add request batching for efficiency

### Performance & Reliability
- [ ] Add request/response compression
- [ ] Implement adaptive timeout strategies
- [ ] Add circuit breaker pattern

### Security & Monitoring
- [ ] Implement request authentication refresh
- [ ] Add basic network error analytics