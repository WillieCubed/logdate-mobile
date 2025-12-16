# `:client:billing`

**Subscription and billing integration for LogDate Cloud**

## Overview

Provides the subscription and billing functionality specifically for LogDate Cloud services. This module handles in-app purchases, subscription management, and billing operations across different platforms using platform-specific implementations.

This module is designed exclusively for first-party LogDate Cloud implementation and is not intended for third-party integrations.

## Architecture

```
Billing Module
├── Subscription Interface
├── Plan Definitions
└── Platform Implementations
```

## Key Components

### Core Components

- `SubscriptionBiller.kt` - Main billing interface
- `LogDateBackupPlanOption.kt` - Plan definitions and pricing
- `StubSubscriptionBiller.kt` - Testing implementation

### Platform Implementations

- `PlayStoreSubscriptionBiller.kt` - Google Play Store implementation
- (Future iOS implementation)

## Features

### Subscription Management

- **Plan Options**: Tiered subscription plans
- **Purchase Flow**: Platform-specific purchase handling
- **Subscription Status**: Active subscription tracking
- **Plan Management**: Subscription upgrades/downgrades
- **Cancellation**: Subscription cancellation handling

### LogDate Cloud Plans

- **Basic Plan**: Free tier with limited storage
- **Standard Plan**: Premium tier with expanded storage
- (Future additional tiers)

### Platform Integration

- **Google Play Billing**: Android implementation
- **StoreKit Integration**: (Future iOS implementation)
- **Web Payments**: (Future web implementation)

## Dependencies

### Core Dependencies

- **Kotlinx Coroutines**: Asynchronous operations
- **Koin**: Dependency injection

### Android Dependencies

- **Google Play Billing**: In-app purchase and subscription handling

## Usage Patterns

### Subscription Checking

```kotlin
class SubscriptionViewModel(private val subscriptionBiller: SubscriptionBiller) {
    val isSubscribed: StateFlow<Boolean> = subscriptionBiller.isSubscribed
        .stateIn(viewModelScope, SharingStarted.Lazily, false)
    
    val availablePlans: StateFlow<List<LogDateBackupPlanOption>> = subscriptionBiller.availablePlans
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}
```

### Purchase Flow

```kotlin
suspend fun purchaseSubscription(plan: LogDateBackupPlanOption) {
    try {
        subscriptionBiller.purchasePlan(plan)
        // Handle successful purchase
    } catch (e: Exception) {
        // Handle purchase failure
    }
}

suspend fun cancelSubscription() {
    try {
        subscriptionBiller.cancelPlan()
        // Handle successful cancellation
    } catch (e: Exception) {
        // Handle cancellation failure
    }
}
```

### Platform-Specific Implementation

```kotlin
// Android implementation using Google Play Billing
class PlayStoreSubscriptionBiller(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SubscriptionBiller {
    // Implementation details using Google Play Billing Library
}

// iOS implementation (future)
class AppleStoreSubscriptionBiller : SubscriptionBiller {
    // Implementation details using StoreKit
}
```

## TODOs

### Core Features
- [ ] Complete Google Play Billing implementation
- [ ] Add iOS StoreKit implementation
- [ ] Implement subscription status caching
- [ ] Add offline subscription verification
- [ ] Implement purchase flow error handling

### Plan Management
- [ ] Support additional subscription tiers
- [ ] Add quota display based on subscription
- [ ] Implement subscription upgrade/downgrade UI
- [ ] Add subscription renewal notifications
- [ ] Support promotional codes and offers

### User Experience
- [ ] Implement subscription benefits explainer
- [ ] Add graceful degradation during billing service outages
- [ ] Create streamlined purchase flow
- [ ] Implement subscription management UI
- [ ] Add transaction history viewer

### Platform Integration
- [ ] Complete Play Store subscription testing
- [ ] Add TestFlight subscription testing
- [ ] Implement sandbox environment detection
- [ ] Add proper error handling for declined transactions
- [ ] Support subscription restore functionality