# `:shared:activitypub`

**ActivityPub protocol implementation for federated features**

## Overview

Implements ActivityPub protocol for federated social features and potential future integration with decentralized platforms. Provides the foundation for social interactions and content federation.

## Architecture

```
ActivityPub Module
├── Core Protocol Implementation
├── Activity Streams
├── Actor Types
├── Object Types
└── Client Integration
```

## Key Components

### Core Implementation
- `ActivityPubClient.kt` - Main ActivityPub client
- `LogdateServerBaseClient.kt` - LogDate-specific server integration

### Activity Streams
- `ActivityTypes.kt` - Activity definitions (Create, Update, Delete, etc.)
- `ActorTypes.kt` - Actor definitions (Person, Organization, etc.)
- `CoreTypes.kt` - Core ActivityPub types
- `ObjectTypes.kt` - Content object types
- `LinkTypes.kt` - Link and reference types
- `Properties.kt` - Common properties and metadata

## Features

### ActivityPub Protocol
- Full ActivityPub specification implementation
- JSON-LD serialization support
- Actor discovery and verification
- Activity delivery and federation

### Activity Streams
- Create, Update, Delete activities
- Follow, Like, Share interactions
- Collections and ordering
- Audience targeting

### Federation Support
- Cross-instance communication
- Content discovery
- Identity verification
- Protocol compliance

## Current Status

**🚧 Early Development Phase**

This module is in early development and represents future plans for federated features. The basic protocol structures are in place but full implementation is pending.

## Dependencies

### Core Dependencies
- **Kotlinx Serialization**: JSON-LD serialization
- **Ktor Client**: HTTP communication
- **Kotlinx Coroutines**: Async operations

## Protocol Support

### ActivityPub Specification
- W3C ActivityPub recommendation compliance
- Activity Streams 2.0 vocabulary
- JSON-LD context support
- HTTP signature verification

### Federation Features
- Actor discovery via WebFinger
- Activity delivery to followers
- Inbox and outbox collections
- Public and private activities

## TODOs

### Core Protocol
- [ ] Complete ActivityPub specification implementation
- [ ] Add WebFinger discovery support
- [ ] Implement HTTP signature verification
- [ ] Add JSON-LD context processing
- [ ] Implement activity validation
- [ ] Add protocol compliance testing
- [ ] Implement actor verification
- [ ] Add federation error handling

### Social Features
- [ ] Implement follow/unfollow mechanics
- [ ] Add like and reaction support
- [ ] Implement content sharing and boosting
- [ ] Add comment and reply threading
- [ ] Implement content moderation tools
- [ ] Add privacy and visibility controls
- [ ] Implement notification system
- [ ] Add social graph management

### Integration
- [ ] Integrate with LogDate journal system
- [ ] Add federated timeline features
- [ ] Implement cross-instance search
- [ ] Add federation settings and controls
- [ ] Implement migration and backup tools
- [ ] Add federation analytics and insights
- [ ] Implement federation performance optimization
- [ ] Add federation security features